import {html, nothing} from 'lit';
import {RxElement, keyIsFree, noDialogOpen} from '../../components/base.js';
import {glyph, icon} from '../../components/icons.js';
import {breadcrumb} from '../../components/common.js';
import {routes, navigate} from '../../routing/router.js';
import {plural, typeLabel} from '../../descriptor/format.js';
import {GraphStage} from '../../graph-engine/stage.js';
import {layout} from '../../graph-engine/layout.js';
import {loadD3} from '../../graph-engine/vendor.js';
import {DEPTHS, describeGraph, filterGraph, focusGraph, fullGraph, toElk} from './graph-model.js';
import {applyHover, applySelection, renderDependencyGraph} from './graph-render.js';
import {graphAside} from './graph-aside.js';

const fullGraphs = new WeakMap();

/** Above this many nodes the whole-application view drops the package boxes: ELK's compound layout is
 * fine for a few hundred artifacts but takes minutes at a thousand, while the flat layout takes a second. */
export const GROUP_LIMIT = 200;

/**
 * The dependency graph page (FR-50 to FR-56, S-GraphFocus, S-GraphAll): focus mode around one
 * artifact with a depth control, or the whole application grouped by package. Selection and
 * focus live in the route, so every view has an address; filters are page state.
 */
class RxGraph extends RxElement {

    static properties = {types: {state: true}, packageId: {state: true}, legendOpen: {state: true}, minimapOn: {state: true}, laying: {state: true}, failure: {state: true}};

    constructor() {
        super();
        this.types = new Set(['ruleflow', 'ruleset', 'rule']);
        this.packageId = '';
        this.legendOpen = true;
        this.minimapOn = true;
        this.laying = false;
        this.failure = null;
        this.stage = null;
        this.renderedKey = null;
        this.svg = null;
        this.graph = null;
        this.hovered = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.onKey = (e) => { if (e.key === 'f' && !e.ctrlKey && !e.metaKey && !e.altKey && keyIsFree(e) && noDialogOpen() && this.stage) { e.preventDefault(); this.stage.fit(32, true); } };
        addEventListener('keydown', this.onKey);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        removeEventListener('keydown', this.onKey);
        if (this.stage) this.stage.destroy();
        this.stage = null;
        this.renderedKey = null;
    }

    get query() {
        return this.state.route.query;
    }

    get focusNode() {
        const id = this.query.focus;
        return id && this.state.index ? this.state.index.byId.get(id) || null : null;
    }

    get depth() {
        return DEPTHS.includes(this.query.depth) ? this.query.depth : '1';
    }

    /** The graph for the current mode and filters. */
    model() {
        const index = this.state.index;
        let full = fullGraphs.get(index);
        if (!full) { full = fullGraph(index); fullGraphs.set(index, full); }
        const focus = this.focusNode;
        const base = focus ? focusGraph(full, focus.id, this.depth) : {nodes: full.nodes, edges: full.edges, hidden: 0};
        return {full, graph: filterGraph(base, {types: this.types, packageId: this.packageId}), focus};
    }

    go(patch) {
        const q = {...this.query, ...patch};
        navigate(routes.graph(q));
    }

    select(id) {
        this.go({selected: id || ''});
    }

    render() {
        const {index, descriptor} = this.state;
        if (this.state.route.name !== 'graph') return nothing;
        const {graph, focus} = this.model();
        this.graph = graph;
        const selectedId = this.query.selected || (focus ? focus.id : '');
        const selected = selectedId ? graph.nodes.get(selectedId) : null;
        const total = index.descriptor.artifacts.length;
        const missing = [...graph.nodes.values()].filter(n => n.type === 'missing').length;
        const depthWords = {1: 'one step', 2: 'two steps', all: 'everything connected'};
        return html`<div class=${'rx-canvas-page' + (selected ? ' rx-with-aside' : '')}>
            <div class="rx-canvas-main">
                <div class="rx-flow-head">
                    ${breadcrumb([{text: 'Dependency graph', href: routes.graph()}, {text: focus ? focus.name : 'Whole application'}])}
                    <h1 class="rx-h1 rx-h1-md">Dependency graph</h1>
                    ${focus
                        ? html`<p class="rx-subtitle rx-subtitle-sm" style="display: flex; align-items: center; gap: 7px; flex-wrap: wrap">Focused on <span class=${'rx-art-link rx-art-link-' + focus.type} style="font-family: var(--rx-font-ui); font-size: 14px">${glyph(focus.type, {size: 11})}${focus.name}</span> and ${this.depth === 'all' ? 'everything connected to it' : 'everything ' + depthWords[this.depth] + ' away from it'}.</p>`
                        : html`<p class="rx-subtitle rx-subtitle-sm">All ${plural(total, 'artifact')} in ${descriptor.application.name || 'the application'}${total <= GROUP_LIMIT ? ', grouped by the package they are defined in' : '; flows on the left, rules on the right'}.</p>`}
                    <div class="rx-flow-bar">
                        <div class="rx-tabs" role="tablist" aria-label="Graph views">
                            <button type="button" role="tab" class="rx-tab" aria-selected=${!!focus} @click=${() => { if (!focus) { const id = this.query.selected; if (id && index.byId.has(id)) this.go({focus: id}); } }} aria-disabled=${!focus && !(this.query.selected && index.byId.has(this.query.selected))} title=${focus ? nothing : 'Select an artifact, then focus on it'}>Focus</button>
                            <button type="button" role="tab" class="rx-tab" aria-selected=${!focus} @click=${() => navigate(routes.graph({selected: this.query.selected}))}>Whole application</button>
                        </div>
                        ${this.zoomControls()}
                    </div>
                </div>
                <div class="rx-gtoolbar" role="toolbar" aria-label="Graph filters">
                    <span class="rx-overline">Show</span>
                    <div class="rx-gchips">
                        ${['ruleflow', 'ruleset', 'rule'].map(t => html`<button type="button" class="rx-gchip" aria-pressed=${this.types.has(t)} @click=${() => this.toggleType(t)}>${glyph(t, {size: 10})}${typeLabel(t, true)}</button>`)}
                    </div>
                    <span class="rx-vbar" aria-hidden="true" style="height: 20px"></span>
                    <span class="rx-overline">Package</span>
                    <select class="rx-gselect" aria-label="Package" .value=${this.packageId} @change=${(e) => { this.packageId = e.target.value; }}>
                        <option value="">All packages</option>
                        ${index.packages.map(p => html`<option value=${p.pkg.id} ?selected=${p.pkg.id === this.packageId}>${p.pkg.id}</option>`)}
                    </select>
                    ${focus ? html`<span class="rx-vbar" aria-hidden="true" style="height: 20px"></span>
                        <span class="rx-overline">Depth</span>
                        <div class="rx-seg" role="group" aria-label="Depth">${DEPTHS.map(d => html`<button type="button" aria-pressed=${this.depth === d} @click=${() => this.go({depth: d})}>${d === 'all' ? 'All' : d + (d === '1' ? ' step' : ' steps')}</button>`)}</div>` : nothing}
                    <span class="rx-spacer"></span>
                    ${!focus && graph.nodes.size > GROUP_LIMIT ? html`<span class="rx-small" style="font-size: 12px" title="ELK's grouped layout takes minutes at this size; filter by package to see one package with its box">Packages are not drawn above ${GROUP_LIMIT} artifacts</span>` : nothing}
                    <span class="rx-small" style="font-size: 12.5px">${focus
                        ? 'Showing ' + (graph.nodes.size - missing) + ' of ' + total + ' artifacts'
                        : plural(graph.nodes.size - missing, 'artifact') + ' · ' + plural(index.packages.length, 'package') + (missing ? ' · ' + missing + ' not registered' : '')}</span>
                </div>
                <div class="rx-stage-wrap">
                    <div class="rx-stage" role="group" aria-label=${describeGraph(graph, index, focus)} @click=${() => this.select('')}></div>
                    ${this.laying ? html`<div class="rx-stage-status" role="status">Laying out…</div>` : nothing}
                    ${this.failure ? html`<div class="rx-stage-status rx-stage-error" role="alert">${this.failure}</div>` : nothing}
                    ${!graph.nodes.size ? html`<div class="rx-stage-status">Nothing to show with these filters.</div>` : nothing}
                    ${this.legend()}
                    <div class=${'rx-minimap' + (this.minimapOn ? '' : ' rx-hidden')} aria-hidden="true"></div>
                </div>
            </div>
            ${graphAside(selected, graph, index, this, {onClose: () => this.select(''), onFocus: (id) => this.go({focus: id, selected: id}), focusId: focus ? focus.id : null})}
        </div>`;
    }

    zoomControls() {
        return html`<div class="rx-flow-tools">
            <div class="rx-zoom">
                <button type="button" aria-label="Zoom out" @click=${() => this.stage && this.stage.zoomBy(1 / 1.25)}>${icon('zoomOut', {size: 14})}</button>
                <span class="rx-zoom-pct">100%</span>
                <button type="button" aria-label="Zoom in" @click=${() => this.stage && this.stage.zoomBy(1.25)}>${icon('zoomIn', {size: 14})}</button>
            </div>
            <button type="button" class="rx-icon-btn rx-icon-btn-sm" aria-label="Fit to screen" title="Fit to screen (F)" @click=${() => this.stage && this.stage.fit(32, true)}>${icon('fit', {size: 14})}</button>
            <button type="button" class="rx-icon-btn rx-icon-btn-sm" aria-label=${this.minimapOn ? 'Hide minimap' : 'Show minimap'} aria-pressed=${this.minimapOn} @click=${() => { this.minimapOn = !this.minimapOn; }}>${icon('minimap', {size: 14})}</button>
        </div>`;
    }

    legend() {
        return html`<div class="rx-legend-box" role="group" aria-label="Legend">
            <div class="rx-legend-head"><span class="rx-overline" style="font-size: 10.5px">Legend</span><button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label=${this.legendOpen ? 'Collapse legend' : 'Expand legend'} aria-expanded=${this.legendOpen} @click=${() => { this.legendOpen = !this.legendOpen; }}>${icon(this.legendOpen ? 'chevronDown' : 'chevronUp', {size: 13, width: 2.2})}</button></div>
            ${this.legendOpen ? html`<div class="rx-legend-grid">
                <span>${glyph('rule', {size: 14})}Rule</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" style="stroke: var(--rx-edge); stroke-width: 1.5"></path><path d="M18 0.5L25 4L18 7.5z" style="fill: var(--rx-edge)"></path></svg>runs</span>
                <span>${glyph('ruleset', {size: 14})}Rule set</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H26" style="stroke: var(--rx-line-strong); stroke-width: 1.2"></path></svg>contains</span>
                <span>${glyph('ruleflow', {size: 14})}Rule flow</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" stroke-dasharray="4 3" style="stroke: var(--rx-edge); stroke-width: 1.5"></path><path d="M18 0.5L25 4L18 7.5z" style="fill: var(--rx-edge)"></path></svg>dynamic lookup</span>
                <span>${glyph('rule', {size: 14, undescribed: true})}Missing target</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" stroke-dasharray="4 3" style="stroke: var(--rx-error); stroke-width: 1.5"></path><path d="M18 0.5L25 4L18 7.5z" style="fill: var(--rx-error)"></path></svg>not registered</span>
                <span><span class="rx-legend-sel"></span>Selected</span>
                <span><svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><circle cx="7" cy="7" r="3.5" style="fill: var(--rx-info)"></circle></svg>Has a problem</span>
            </div>` : nothing}
        </div>`;
    }

    toggleType(t) {
        const next = new Set(this.types);
        if (next.has(t)) next.delete(t); else next.add(t);
        this.types = next;
    }

    updated() {
        this.draw();
    }

    async draw() {
        const stageEl = this.querySelector('.rx-stage');
        if (!stageEl || !this.graph || this.state.route.name !== 'graph') return;
        const index = this.state.index;
        const focus = this.focusNode;
        if (!this.stage) {
            this.stage = new GraphStage(stageEl, {onZoom: (k) => { const el = this.querySelector('.rx-zoom-pct'); if (el) el.textContent = Math.round(k * 100) + '%'; }});
        }
        this.stage.setMinimap(this.querySelector('.rx-minimap'));
        const grouped = !focus && this.graph.nodes.size <= GROUP_LIMIT;
        const ids = [...this.graph.nodes.keys()].join(',');
        const key = 'dep|' + (focus ? focus.id + '|' + this.depth : grouped ? 'all' : 'flat') + '|' + ids + '|' + this.graph.edges.length;
        const selectedId = this.query.selected || (focus ? focus.id : '');
        if (key === this.renderedKey) {
            applySelection(this.svg, selectedId);
            return;
        }
        this.renderedKey = key;
        if (!this.graph.nodes.size) { if (this.svg) { this.svg.remove(); this.svg = null; } return; }
        this.laying = true;
        this.failure = null;
        try {
            await document.fonts.ready;
            const t0 = performance.now();
            const [d3, result] = await Promise.all([loadD3(), layout(key, toElk(this.graph, index, grouped))]);
            const layoutMs = performance.now() - t0;
            if (this.renderedKey !== key) return;
            const graph = this.graph;
            const svg = renderDependencyGraph({
                d3, graph, layout: result, index, grouped, selected: selectedId,
                onSelect: (id) => this.select(id),
                onHover: (id) => { this.hovered = id; applyHover(this.svg, graph, id); }
            });
            d3.select(svg).selectAll('.rx-gnode').on('dblclick', (event, n) => { event.stopPropagation(); if (n.type !== 'missing') this.go({focus: n.id, selected: n.id}); });
            this.svg = svg;
            const minimapNodes = [...result.nodes.entries()].map(([id, n]) => ({...n, kind: n.isGroup ? 'group' : (graph.nodes.get(id) || {}).type, selected: id === selectedId}));
            await this.stage.setContent(svg, {width: result.width, height: result.height}, minimapNodes);
            this.stage.fit(32, false);
            document.dispatchEvent(new CustomEvent('rx-canvas-rendered', {detail: {kind: 'graph', key, nodes: graph.nodes.size, edges: graph.edges.length, layoutMs: Math.round(layoutMs), renderMs: Math.round(performance.now() - t0 - layoutMs), elements: svg.querySelectorAll('*').length}}));
        } catch (e) {
            console.error(e);
            this.failure = 'The layout failed: ' + (e && e.message || e);
        } finally {
            this.laying = false;
        }
    }
}

customElements.define('rx-graph', RxGraph);
