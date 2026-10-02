import {html, nothing} from 'lit';
import {RxElement, keyIsFree, noDialogOpen} from '../../components/base.js';
import {GraphStage} from '../../graph-engine/stage.js';
import {layout, roundedPath} from '../../graph-engine/layout.js';
import {clipText, FONT_CAPTION, FONT_MONO, FONT_NAME, loadD3} from '../../graph-engine/vendor.js';
import {buildFlowchart, toElkFlow} from './flow-model.js';
import {hex} from '../graph/graph-render.js';
import {routes, navigate} from '../../routing/router.js';

const NS = 'http://www.w3.org/2000/svg';
const models = new WeakMap();

/**
 * The flowchart of one rule flow (FR-53, Lavender-Flow): laid out by ELK top to bottom, drawn
 * with d3 on a GraphStage. The selected step is the `step` route query, shared with the outline.
 */
class RxFlowchart extends RxElement {

    static properties = {artifact: {attribute: false}, laying: {state: true}, failure: {state: true}, minimapOn: {state: true}};

    constructor() {
        super();
        this.artifact = null;
        this.laying = false;
        this.failure = null;
        this.minimapOn = true;
        this.stage = null;
        this.renderedKey = null;
        this.svg = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.onKey = (e) => { if (e.key === 'f' && !e.ctrlKey && !e.metaKey && !e.altKey && keyIsFree(e) && noDialogOpen() && this.stage) { e.preventDefault(); this.stage.fit(28, true); } };
        addEventListener('keydown', this.onKey);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        removeEventListener('keydown', this.onKey);
        if (this.stage) this.stage.destroy();
        this.stage = null;
        this.renderedKey = null;
    }

    /** The flowchart model for an artifact, built once. */
    static modelFor(artifact, index) {
        let m = models.get(artifact);
        if (!m) { m = buildFlowchart(artifact, index); models.set(artifact, m); }
        return m;
    }

    get selectedStep() {
        return this.state.route.query.step || '';
    }

    select(path) {
        const a = this.artifact;
        navigate(routes.artifact(a, {...this.state.route.query, view: 'flowchart', step: path || ''}));
    }

    render() {
        return html`<div class="rx-stage-wrap">
            <div class="rx-stage" role="group" aria-label=${'Flowchart of ' + (this.artifact ? this.artifact.name : 'the flow')} @click=${() => this.select('')}></div>
            ${this.laying ? html`<div class="rx-stage-status" role="status">Laying out…</div>` : nothing}
            ${this.failure ? html`<div class="rx-stage-status rx-stage-error" role="alert">${this.failure}</div>` : nothing}
            <div class=${'rx-minimap' + (this.minimapOn ? '' : ' rx-hidden')} aria-hidden="true"></div>
        </div>`;
    }

    updated() {
        this.draw();
    }

    async draw() {
        const stageEl = this.querySelector('.rx-stage');
        const a = this.artifact;
        if (!stageEl || !a) return;
        const index = this.state.index;
        if (!this.stage) this.stage = new GraphStage(stageEl, {onZoom: (k) => { const el = document.querySelector('.rx-zoom-pct'); if (el) el.textContent = Math.round(k * 100) + '%'; }});
        this.stage.setMinimap(this.querySelector('.rx-minimap'));
        const key = 'flow|' + a.id;
        const selected = this.selectedStep;
        if (key === this.renderedKey) {
            if (this.svg) this.svg.querySelectorAll('.rx-fnode').forEach(n => n.classList.toggle('rx-fnode-selected', n.getAttribute('data-id') === selected));
            return;
        }
        this.renderedKey = key;
        this.laying = true;
        this.failure = null;
        try {
            await document.fonts.ready;
            const t0 = performance.now();
            const model = RxFlowchart.modelFor(a, index);
            const [d3, result] = await Promise.all([loadD3(), layout(key, toElkFlow(model))]);
            const layoutMs = performance.now() - t0;
            if (this.renderedKey !== key) return;
            const svg = renderFlowchart({d3, model, layout: result, index, selected, problems: index.problemsByArtifact.get(a.id) || [], onSelect: (id) => this.select(id)});
            this.svg = svg;
            const minimapNodes = [...result.nodes.entries()].map(([id, n]) => ({...n, kind: n.isGroup ? 'group' : (model.nodes.find(x => x.id === id) || {}).targetType || 'step', selected: id === selected}));
            await this.stage.setContent(svg, {width: result.width, height: result.height}, minimapNodes);
            this.stage.fit(28, false);
            document.dispatchEvent(new CustomEvent('rx-canvas-rendered', {detail: {kind: 'flowchart', key, nodes: model.nodes.length, edges: model.edges.length, layoutMs: Math.round(layoutMs), renderMs: Math.round(performance.now() - t0 - layoutMs)}}));
            if (selected && result.nodes.has(selected)) { const n = result.nodes.get(selected); const k = this.stage.scale; if (k < 0.5) this.stage.centerOn(n.x + n.width / 2, n.y + n.height / 2, false); }
        } catch (e) {
            console.error(e);
            this.failure = 'The layout failed: ' + (e && e.message || e);
        } finally {
            this.laying = false;
        }
    }
}

/** Draws a laid-out flowchart model (DESIGN-SYSTEM §4, flowchart steps). */
export function renderFlowchart({d3, model, layout, index, selected, problems, onSelect}) {
    const svg = d3.create('svg').attr('class', 'rx-gsvg rx-fsvg').attr('xmlns', NS);
    const defs = svg.append('defs');
    for (const [id, cls] of [['rx-fah', 'rx-garrow'], ['rx-fah-warn', 'rx-garrow-warning'], ['rx-fah-hi', 'rx-garrow-hi']]) {
        defs.append('marker').attr('id', id).attr('viewBox', '0 0 10 10').attr('refX', 9).attr('refY', 5).attr('markerUnits', 'userSpaceOnUse').attr('markerWidth', 9).attr('markerHeight', 9).attr('orient', 'auto')
            .append('path').attr('d', 'M0 1L9 5L0 9z').attr('class', cls);
    }
    const problemAt = new Map(problems.filter(p => p.path).map(p => [p.path, p]));

    // Async lane: the box around the async steps and their handlers
    const asyncBoxes = [...model.asyncIds].map(id => layout.nodes.get(id)).filter(Boolean);
    if (asyncBoxes.length) {
        const x0 = Math.min(...asyncBoxes.map(b => b.x)) - 16, y0 = Math.min(...asyncBoxes.map(b => b.y)) - 28;
        const x1 = Math.max(...asyncBoxes.map(b => b.x + b.width)) + 16, y1 = Math.max(...asyncBoxes.map(b => b.y + b.height)) + 16;
        const lane = svg.append('g').attr('class', 'rx-flane');
        lane.append('rect').attr('x', x0).attr('y', y0).attr('width', x1 - x0).attr('height', y1 - y0).attr('rx', 12);
        lane.append('text').attr('x', x1 - 10).attr('y', y1 - 7).attr('text-anchor', 'end').text('ASYNC LANE');
    }

    // Containers
    const containers = model.containers.map(c => ({...c, pos: layout.nodes.get(c.id)})).filter(c => c.pos);
    const cg = svg.append('g').attr('class', 'rx-fcontainers').selectAll('g').data(containers).join('g').attr('class', 'rx-fcontainer').attr('transform', c => 'translate(' + c.pos.x + ',' + c.pos.y + ')');
    cg.append('rect').attr('width', c => c.pos.width).attr('height', c => c.pos.height).attr('rx', 12);
    cg.append('text').attr('x', 16).attr('y', 20).text(c => c.title);

    // Edges
    const edgeData = model.edges.map(e => ({...e, points: layout.routes.get(e.id) || []})).filter(e => e.points.length > 1);
    const eg = svg.append('g').attr('class', 'rx-fedges');
    eg.selectAll('path').data(edgeData).join('path')
        .attr('class', e => 'rx-fedge ' + (e.cls || ''))
        .attr('d', e => roundedPath(e.points, 8))
        .attr('marker-end', e => e.cls === 'rx-fedge-handler' ? 'url(#rx-fah-warn)' : 'url(#rx-fah)');
    const labelled = edgeData.filter(e => e.label);
    const lg = eg.selectAll('g').data(labelled).join('g').attr('class', 'rx-fedge-label').attr('transform', e => { const [x, y] = labelPoint(e.points); return 'translate(' + x + ',' + y + ')'; });
    lg.append('rect').attr('x', e => -(e.label.length * 6.5 + 10) / 2).attr('y', -8).attr('width', e => e.label.length * 6.5 + 10).attr('height', 16).attr('rx', 8);
    lg.append('text').attr('y', 4).attr('text-anchor', 'middle').text(e => e.label);

    // Nodes
    const nodeData = model.nodes.map(n => ({...n, pos: layout.nodes.get(n.id)})).filter(n => n.pos);
    const ng = svg.append('g').attr('class', 'rx-fnodes').selectAll('g').data(nodeData).join('g')
        .attr('class', n => 'rx-fnode rx-fnode-' + n.kind + (n.targetType ? ' rx-fnode-' + n.targetType : '') + (n.resolution === 'unresolved' ? ' rx-fnode-unresolved' : n.resolution && n.resolution !== 'direct' ? ' rx-fnode-lookup' : '') + (n.id === selected ? ' rx-fnode-selected' : ''))
        .attr('data-id', n => n.id)
        .attr('transform', n => 'translate(' + n.pos.x + ',' + n.pos.y + ')')
        .attr('tabindex', n => n.command || n.handler ? 0 : null).attr('role', n => n.command || n.handler ? 'button' : null)
        .attr('aria-label', n => (n.overline ? n.overline + ': ' : '') + n.text)
        .on('click', (event, n) => { event.stopPropagation(); if (n.command || n.handler) onSelect(n.id); })
        .on('keydown', (event, n) => { if ((event.key === 'Enter' || event.key === ' ') && (n.command || n.handler)) { event.preventDefault(); onSelect(n.id); } });

    ng.each(function (n) {
        const g = d3.select(this);
        const w = n.width, h = n.height;
        g.append('rect').attr('class', 'rx-fnode-ring').attr('x', -4).attr('y', -4).attr('width', w + 8).attr('height', h + 8).attr('rx', n.kind === 'start' || n.kind === 'return' || n.kind === 'end' || n.kind === 'exit' ? h / 2 + 4 : 14);
        if (n.kind === 'decision') {
            g.append('polygon').attr('class', 'rx-fshape').attr('points', [[0, h / 2], [20, 0], [w - 20, 0], [w, h / 2], [w - 20, h], [20, h]].map(p => p.join(',')).join(' '));
            g.append('text').attr('class', 'rx-fover').attr('x', w / 2).attr('y', 16).attr('text-anchor', 'middle').text(n.overline);
            g.append('text').attr('class', 'rx-ftext').attr('x', w / 2).attr('y', 33).attr('text-anchor', 'middle').text(clipText(n.text, w - 52, FONT_NAME));
        } else if (n.kind === 'start' || n.kind === 'return' || n.kind === 'end') {
            g.append('rect').attr('class', 'rx-fshape rx-fpill').attr('width', w).attr('height', h).attr('rx', h / 2);
            const t = g.append('text').attr('x', w / 2).attr('y', h / 2 + 4.5).attr('text-anchor', 'middle');
            if (n.kind === 'start') {
                const [head, ...rest] = n.text.split(' · ');
                t.append('tspan').attr('class', 'rx-ftext').text(head);
                if (rest.length) t.append('tspan').attr('class', 'rx-fcap').text(' · ' + rest.join(' · '));
            } else if (n.kind === 'return') {
                t.append('tspan').attr('class', 'rx-fover').text('RETURN  ');
                t.append('tspan').attr('class', 'rx-fmono').text(clipText(n.text, w - 90, FONT_MONO));
            } else t.append('tspan').attr('class', 'rx-ftext').text(n.text);
        } else {
            const pill = n.kind === 'exit';
            g.append('rect').attr('class', 'rx-fshape').attr('width', w).attr('height', h).attr('rx', pill ? h / 2 : 10);
            let x = 24;
            if (n.kind === 'handler' || n.kind === 'global') x = 12;
            else if (n.targetType === 'ruleset') g.append('rect').attr('class', 'rx-fglyph').attr('x', 13).attr('y', h / 2 - 6).attr('width', 12).attr('height', 12).attr('rx', 3);
            else if (n.targetType === 'ruleflow') g.append('polygon').attr('class', 'rx-fglyph').attr('points', hex(19, h / 2));
            else if (n.targetType === 'rule' || n.resolution === 'unresolved') g.append('circle').attr('class', 'rx-fglyph').attr('cx', 19).attr('cy', h / 2).attr('r', 5.5);
            else if (pill) x = 24;
            else x = 14;
            x = n.targetType || n.resolution === 'unresolved' ? 34 : x;
            if (n.compiled) g.append('path').attr('class', 'rx-flock').attr('d', 'M' + (x - 2) + ' ' + (h / 2 - 1) + 'h10v8h-10zM' + x + ' ' + (h / 2 - 1) + 'v-3a3 3 0 0 1 6 0v3'), x += 16;
            const right = n.as ? textWidthApprox('→ ' + n.as) + 10 : 0;
            g.append('text').attr('class', 'rx-fover').attr('x', x).attr('y', 17).text(clipText(n.overline, w - x - 8 - right, FONT_CAPTION));
            if (n.as) g.append('text').attr('class', 'rx-fas').attr('x', w - 12).attr('y', 17).attr('text-anchor', 'end').text('→ ' + n.as);
            g.append('text').attr('class', n.mono ? 'rx-fmono' : 'rx-ftext').attr('x', x).attr('y', 33).text(clipText(n.text, w - x - 12, n.mono ? FONT_MONO : FONT_NAME));
            if (n.id !== 'global' && n.kind !== 'handler') {
                const p = problemAt.get(n.id);
                if (p) g.append('circle').attr('class', 'rx-gproblem rx-gproblem-' + p.severity).attr('cx', w - 10).attr('cy', 10).attr('r', 3.5).append('title').text(p.message);
            }
        }
    });
    return svg.node();
}

function textWidthApprox(text) {
    return text.length * 6.4;
}

/** Where an edge label sits: a little way along the first segment, clear of the node. */
function labelPoint(points) {
    const [a, b] = points;
    const len = Math.hypot(b[0] - a[0], b[1] - a[1]);
    const d = Math.min(18, len / 2);
    if (points.length > 2 && len < 24) { const [c, dd] = [points[1], points[2]]; const l2 = Math.hypot(dd[0] - c[0], dd[1] - c[1]); const t = Math.min(22, l2 / 2); return [c[0] + (dd[0] - c[0]) / l2 * t, c[1] + (dd[1] - c[1]) / l2 * t]; }
    return [a[0] + (b[0] - a[0]) / len * d, a[1] + (b[1] - a[1]) / len * d];
}

customElements.define('rx-flowchart', RxFlowchart);
export {RxFlowchart};
