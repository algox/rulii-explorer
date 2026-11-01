import {html, nothing} from 'lit';
import {RxElement, keyIsFree, noDialogOpen} from '../../components/base.js';
import {glyph, icon, severityIcon} from '../../components/icons.js';
import {artifactLink, beanLink, breadcrumb, typeBadge} from '../../components/common.js';
import {routes, navigate} from '../../routing/router.js';
import {beanKindLabel, kindLabel, plural, typeLabel} from '../../descriptor/format.js';
import {artifactSummary, beanSummary} from '../../descriptor/summaries.js';
import {GraphStage} from '../../graph-engine/stage.js';
import {layout} from '../../graph-engine/layout.js';
import {loadD3} from '../../graph-engine/vendor.js';
import {DEPTHS} from '../graph/graph-model.js';
import {applyHover, applySelection, renderDependencyGraph} from '../graph/graph-render.js';
import {beansGraph, describeBeansGraph, focusBeansGraph, toElkBeans} from './graph-model.js';

const fullGraphs = new WeakMap();

/**
 * The Beans graph (FR-75): every bean with its cascades and the rules it runs, or the
 * neighbourhood of one bean with a depth control. Built on the shared stage, layout and
 * renderer; the Rules dependency graph is untouched. Selection, focus and depth live in the
 * address.
 */
class RxBeansGraph extends RxElement {

    static properties = {legendOpen: {state: true}, minimapOn: {state: true}, laying: {state: true}, failure: {state: true}};

    constructor() {
        super();
        this.legendOpen = true;
        this.minimapOn = true;
        this.laying = false;
        this.failure = null;
        this.stage = null;
        this.renderedKey = null;
        this.svg = null;
        this.graph = null;
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

    get depth() {
        return DEPTHS.includes(this.query.depth) ? this.query.depth : '1';
    }

    model() {
        const index = this.state.index;
        let full = fullGraphs.get(index);
        if (!full) { full = beansGraph(index); fullGraphs.set(index, full); }
        const focusId = this.query.focus;
        const focus = focusId && full.nodes.has(focusId) ? full.nodes.get(focusId) : null;
        const graph = focus ? focusBeansGraph(full, focus.id, this.depth) : {nodes: full.nodes, edges: full.edges, hidden: 0};
        return {full, graph, focus};
    }

    go(patch) {
        navigate(routes.beansGraph({...this.query, ...patch}));
    }

    select(id) {
        this.go({selected: id || ''});
    }

    render() {
        const {index, descriptor} = this.state;
        if (this.state.route.name !== 'graph' || this.state.route.section !== 'beans') return nothing;
        const {graph, focus} = this.model();
        this.graph = graph;
        const selectedId = this.query.selected || (focus ? focus.id : '');
        const selected = selectedId ? graph.nodes.get(selectedId) : null;
        const beans = [...graph.nodes.values()].filter(n => n.type === 'bean').length;
        const rules = [...graph.nodes.values()].filter(n => n.type === 'rule').length;
        const missing = [...graph.nodes.values()].filter(n => n.type === 'missing').length;
        const depthWords = {1: 'one step', 2: 'two steps', all: 'everything connected'};
        return html`<div class=${'rx-canvas-page' + (selected ? ' rx-with-aside' : '')}>
            <div class="rx-canvas-main">
                <div class="rx-flow-head">
                    ${breadcrumb([{text: 'Beans', href: routes.beans()}, {text: 'Graph', href: routes.beansGraph()}, {text: focus ? focus.name : 'All beans'}])}
                    <h1 class="rx-h1 rx-h1-md">Beans graph</h1>
                    ${focus
                        ? html`<p class="rx-subtitle rx-subtitle-sm" style="display: flex; align-items: center; gap: 7px; flex-wrap: wrap">Focused on <span class=${'rx-art-link rx-art-link-' + focus.type} style="font-family: var(--rx-font-ui); font-size: 14px">${glyph(focus.type, {size: 11})}${focus.name}</span> and ${this.depth === 'all' ? 'everything connected to it' : 'everything ' + depthWords[this.depth] + ' away from it'}.</p>`
                        : html`<p class="rx-subtitle rx-subtitle-sm">${plural(index.beans.list.length, 'bean')} in ${descriptor.application.name || 'the application'}: which cascade into which, and the registry rules they run.</p>`}
                    <div class="rx-flow-bar">
                        <div class="rx-tabs" role="tablist" aria-label="Graph views">
                            <button type="button" role="tab" class="rx-tab" aria-selected=${!!focus} @click=${() => { if (!focus && this.query.selected && graph.nodes.has(this.query.selected)) this.go({focus: this.query.selected}); }} aria-disabled=${!focus && !(this.query.selected && graph.nodes.has(this.query.selected))} title=${focus ? nothing : 'Select a bean, then focus on it'}>Focus</button>
                            <button type="button" role="tab" class="rx-tab" aria-selected=${!focus} @click=${() => navigate(routes.beansGraph({selected: this.query.selected}))}>All beans</button>
                        </div>
                        ${this.zoomControls()}
                    </div>
                </div>
                <div class="rx-gtoolbar" role="toolbar" aria-label="Graph options">
                    ${focus ? html`<span class="rx-overline">Depth</span>
                        <div class="rx-seg" role="group" aria-label="Depth">${DEPTHS.map(d => html`<button type="button" aria-pressed=${this.depth === d} @click=${() => this.go({depth: d})}>${d === 'all' ? 'All' : d + (d === '1' ? ' step' : ' steps')}</button>`)}</div>` : nothing}
                    <span class="rx-spacer"></span>
                    <span class="rx-small" style="font-size: 12.5px">${plural(beans, 'bean')}${rules ? ' · ' + plural(rules, 'rule') : ''}${missing ? ' · ' + missing + ' not registered' : ''}${focus && graph.hidden ? ' · ' + graph.hidden + ' hidden' : ''}</span>
                </div>
                <div class="rx-stage-wrap">
                    <div class="rx-stage" role="group" aria-label=${describeBeansGraph(graph, focus)} @click=${() => this.select('')}></div>
                    ${this.laying ? html`<div class="rx-stage-status" role="status">Laying out…</div>` : nothing}
                    ${this.failure ? html`<div class="rx-stage-status rx-stage-error" role="alert">${this.failure}</div>` : nothing}
                    ${!graph.nodes.size ? html`<div class="rx-stage-status">Nothing to show.</div>` : nothing}
                    ${this.legend()}
                    <div class=${'rx-minimap' + (this.minimapOn ? '' : ' rx-hidden')} aria-hidden="true"></div>
                </div>
            </div>
            ${this.aside(selected, graph, index, focus)}
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
                <span>${glyph('bean', {size: 14})}Bean</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" style="stroke: var(--rx-edge); stroke-width: 1.5"></path><path d="M18 0.5L25 4L18 7.5z" style="fill: var(--rx-edge)"></path></svg>cascades (@Valid)</span>
                <span>${glyph('rule', {size: 14})}Rule · Rules section</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" stroke-dasharray="4 3" style="stroke: var(--rx-edge); stroke-width: 1.5"></path><path d="M18 0.5L25 4L18 7.5z" style="fill: var(--rx-edge)"></path></svg>runs (@Validate, by name)</span>
                <span>${glyph('rule', {size: 14, undescribed: true})}Missing target</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" stroke-dasharray="4 3" style="stroke: var(--rx-error); stroke-width: 1.5"></path><path d="M18 0.5L25 4L18 7.5z" style="fill: var(--rx-error)"></path></svg>not registered</span>
                <span><span class="rx-legend-sel"></span>Selected</span>
                <span><svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><circle cx="7" cy="7" r="3.5" style="fill: var(--rx-info)"></circle></svg>Has a problem</span>
            </div>` : nothing}
        </div>`;
    }

    /** The panel of the selected node: a bean, a rule run from a bean, or a missing name. */
    aside(node, graph, index, focus) {
        if (!node) return nothing;
        const close = () => this.select('');
        const focusId = focus ? focus.id : null;
        if (node.type === 'missing') {
            const from = graph.edges.filter(e => e.to === node.id).map(e => index.beans.byId.get(e.from)).filter(Boolean);
            return html`<aside class="rx-gaside" aria-label="Selected target">
                <div class="rx-gaside-head"><span class="rx-overline">Selected · Missing target</span><button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label="Close details" @click=${close}>${icon('close', {size: 14})}</button></div>
                <div class="rx-gaside-title">
                    <div class="rx-meta-row"><span class="rx-type-badge" style="color: var(--rx-error); background: var(--rx-error-soft); border-color: var(--rx-error)">Not registered</span></div>
                    <h2 class="rx-mono" style="font-size: 20px; font-weight: 600">${node.name}</h2>
                    <p>Nothing with this name is in the registry, so validating the bean that runs it fails.</p>
                </div>
                <div class="rx-kv rx-kv-84">${from.map(b => html`<span class="rx-k">Run by</span><span class="rx-v">${beanLink(b, {class: 'rx-art-link rx-art-link-bean'})}</span>`)}</div>
                <div class="rx-gaside-actions"><a class="rx-btn rx-btn-primary" href=${routes.beansProblems({severity: 'error'})}>See the problem${icon('arrowRight', {size: 14})}</a></div>
            </aside>`;
        }
        if (node.type === 'rule') {
            const a = node.artifact;
            const from = graph.edges.filter(e => e.to === a.id).map(e => index.beans.byId.get(e.from)).filter(Boolean);
            return html`<aside class="rx-gaside" aria-label="Selected rule">
                <div class="rx-gaside-head"><span class="rx-overline">Selected · Rule</span><button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label="Close details" @click=${close}>${icon('close', {size: 14})}</button></div>
                <div class="rx-gaside-title">
                    <div class="rx-meta-row">${typeBadge('rule', {undescribed: index.undescribed.has(a.id)})}<span class="rx-kind" style="font-size: 12px">${kindLabel(a)}</span></div>
                    <h2>${a.name}</h2>
                    ${a.description ? html`<p>${a.description}</p>` : html`<p class="rx-muted">No description.</p>`}
                </div>
                <div class="rx-summary rx-summary-sm">${artifactSummary(a, index)}</div>
                <div class="rx-kv rx-kv-84">${from.length ? html`<span class="rx-k">Run by</span><span class="rx-v">${from.map(b => beanLink(b, {class: 'rx-art-link rx-art-link-bean'}))}</span>` : nothing}</div>
                <div class="rx-gaside-actions">
                    <a class="rx-btn rx-btn-primary" href=${routes.artifact(a)}>Open rule${icon('arrowRight', {size: 14})}</a>
                    <a class="rx-btn rx-btn-secondary" href=${routes.graph({focus: a.id})}>In the Rules graph</a>
                </div>
            </aside>`;
        }
        const b = node.bean;
        const beans = index.beans;
        const into = graph.edges.filter(e => e.to === b.id && e.type === 'cascades').map(e => beans.byId.get(e.from)).filter(Boolean);
        const out = graph.edges.filter(e => e.from === b.id && e.type === 'cascades').map(e => beans.byId.get(e.to)).filter(Boolean);
        const runs = graph.edges.filter(e => e.from === b.id && e.type === 'runs');
        const problems = (beans.problemsByBean.get(b.id) || []).slice(0, 4);
        return html`<aside class="rx-gaside" aria-label="Selected bean">
            <div class="rx-gaside-head"><span class="rx-overline">Selected · Bean</span><button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label="Close details" @click=${close}>${icon('close', {size: 14})}</button></div>
            <div class="rx-gaside-title">
                <div class="rx-meta-row">${typeBadge('bean', {undescribed: beans.undescribed.has(b.id)})}<span class="rx-kind" style="font-size: 12px">${beanKindLabel(b)}</span></div>
                <h2>${b.name}</h2>
                <p class="rx-mono rx-muted" style="font-size: 12px">${b.id}</p>
            </div>
            <div class="rx-summary rx-summary-sm">${beanSummary(b, index).map(p => p.text !== undefined ? p.text : p.bean ? beanLink(p.bean, {glyph: false, class: 'rx-via-link'}) : p.artifact ? artifactLink(p.artifact, {glyph: false, class: 'rx-via-link'}) : html`<span class="rx-chip-error rx-mono">${p.missing}</span>`)}</div>
            <div class="rx-kv rx-kv-84">
                ${into.length ? html`<span class="rx-k">Cascaded from</span><span class="rx-v">${into.map(x => beanLink(x, {class: 'rx-art-link rx-art-link-bean'}))}</span>` : nothing}
                ${out.length ? html`<span class="rx-k">Cascades into</span><span class="rx-v">${out.map(x => beanLink(x, {class: 'rx-art-link rx-art-link-bean'}))}</span>` : nothing}
                ${runs.length ? html`<span class="rx-k">Runs</span><span class="rx-v" style="color: var(--rx-ink)">${plural(runs.length, 'registry rule')}${runs.some(e => e.resolution === 'unresolved') ? ', one missing' : ''}</span>` : nothing}
                ${b.flow ? html`<span class="rx-k">Flow</span><span class="rx-v"><span class="rx-chip">${b.flow.name}</span></span>` : nothing}
                ${b.packageId ? html`<span class="rx-k">Package</span><span class="rx-v"><span class="rx-mono">${b.packageId}</span></span>` : nothing}
            </div>
            ${problems.length ? html`<div class="rx-gaside-section">
                <span class="rx-gaside-h">Problems</span>
                ${problems.map(p => html`<div class="rx-problem-row"><span class=${'rx-sev-icon rx-sev-icon-sm rx-sev-' + p.severity}>${severityIcon(p.severity, 11)}</span><p>${p.message}</p></div>`)}
            </div>` : nothing}
            <div class="rx-gaside-actions">
                <a class="rx-btn rx-btn-primary" href=${routes.bean(b)}>Open bean${icon('arrowRight', {size: 14})}</a>
                ${focusId !== b.id ? html`<button type="button" class="rx-btn rx-btn-secondary" @click=${() => this.go({focus: b.id, selected: b.id})}>Focus here</button>` : nothing}
            </div>
        </aside>`;
    }

    updated() {
        this.draw();
    }

    async draw() {
        const stageEl = this.querySelector('.rx-stage');
        if (!stageEl || !this.graph || this.state.route.name !== 'graph') return;
        const index = this.state.index;
        const {focus} = this.model();
        if (!this.stage) {
            this.stage = new GraphStage(stageEl, {onZoom: (k) => { const el = this.querySelector('.rx-zoom-pct'); if (el) el.textContent = Math.round(k * 100) + '%'; }});
        }
        this.stage.setMinimap(this.querySelector('.rx-minimap'));
        const ids = [...this.graph.nodes.keys()].join(',');
        const key = 'beans|' + (focus ? focus.id + '|' + this.depth : 'all') + '|' + ids + '|' + this.graph.edges.length;
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
            const [d3, result] = await Promise.all([loadD3(), layout(key, toElkBeans(this.graph))]);
            if (this.renderedKey !== key) return;
            const graph = this.graph;
            const svg = renderDependencyGraph({
                d3, graph, layout: result, index, grouped: false, selected: selectedId,
                onSelect: (id) => this.select(id),
                onHover: (id) => applyHover(this.svg, graph, id)
            });
            d3.select(svg).selectAll('.rx-gnode').on('dblclick', (event, n) => { event.stopPropagation(); if (n.type !== 'missing') this.go({focus: n.id, selected: n.id}); });
            this.svg = svg;
            const minimapNodes = [...result.nodes.entries()].map(([id, n]) => ({...n, kind: (graph.nodes.get(id) || {}).type, selected: id === selectedId}));
            await this.stage.setContent(svg, {width: result.width, height: result.height}, minimapNodes);
            this.stage.fit(32, false);
        } catch (e) {
            console.error(e);
            this.failure = 'The layout failed: ' + (e && e.message || e);
        } finally {
            this.laying = false;
        }
    }
}

export {typeLabel};

customElements.define('rx-beans-graph', RxBeansGraph);
