import {roundedPath} from '../../graph-engine/layout.js';
import {clipText, FONT_CAPTION, FONT_NAME} from '../../graph-engine/vendor.js';
import {groupLabel} from './graph-model.js';

const NS = 'http://www.w3.org/2000/svg';

/**
 * Draws a laid-out dependency graph as SVG with d3 (DESIGN-SYSTEM §4). Hover and selection are
 * class changes on the existing elements, never re-renders.
 *
 * @param {object} args
 * @param {typeof d3} args.d3
 * @param {{nodes: Map, edges: Array}} args.graph
 * @param {import('../../graph-engine/layout.js').Layout} args.layout
 * @param {object} args.index
 * @param {boolean} args.grouped
 * @param {string|null} args.selected
 * @param {(id: string) => void} args.onSelect
 * @param {(id: string|null) => void} args.onHover
 * @returns {SVGSVGElement}
 */
export function renderDependencyGraph({d3, graph, layout, index, grouped, selected, onSelect, onHover}) {
    const svg = d3.create('svg').attr('class', 'rx-gsvg').attr('xmlns', NS);
    const defs = svg.append('defs');
    marker(defs, 'rx-ah', 'rx-garrow');
    marker(defs, 'rx-ah-err', 'rx-garrow-error');
    marker(defs, 'rx-ah-warn', 'rx-garrow-warning');
    marker(defs, 'rx-ah-hi', 'rx-garrow-hi');

    // Groups (whole application)
    if (grouped) {
        const groups = [...layout.nodes.entries()].filter(([id, n]) => n.isGroup);
        const g = svg.append('g').attr('class', 'rx-ggroups').selectAll('g').data(groups).join('g')
            .attr('class', ([id]) => 'rx-ggroup' + (id === 'group:missing' ? ' rx-ggroup-missing' : ''))
            .attr('transform', ([, n]) => 'translate(' + n.x + ',' + n.y + ')');
        g.append('rect').attr('width', ([, n]) => n.width).attr('height', ([, n]) => n.height).attr('rx', 14);
        g.each(function ([id, n]) {
            const label = groupLabel(id.slice('group:'.length), graph, index);
            const el = d3.select(this);
            const nameW = Math.ceil(label.name.length * 7) + 20;
            const capW = label.caption ? Math.ceil(label.caption.length * 6.3) + 8 : 0;
            el.append('rect').attr('class', 'rx-ggroup-chip').attr('x', 16).attr('y', -9).attr('width', nameW + capW).attr('height', 18).attr('rx', 9);
            const t = el.append('text').attr('x', 26).attr('y', 4);
            t.append('tspan').attr('class', label.missing ? 'rx-ggroup-missing-name' : 'rx-ggroup-name').text(label.name);
            if (label.caption) t.append('tspan').attr('dx', 8).attr('class', 'rx-ggroup-cap').text(label.caption);
        });
    }

    // Edges
    const edgeData = graph.edges.map(e => ({...e, points: layout.routes.get(e.id) || []})).filter(e => e.points.length > 1);
    svg.append('g').attr('class', 'rx-gedges').selectAll('path').data(edgeData).join('path')
        .attr('class', e => 'rx-gedge rx-gedge-' + e.type + (e.resolution === 'unresolved' ? ' rx-gedge-unresolved' : e.resolution !== 'direct' ? ' rx-gedge-lookup' : ''))
        .attr('data-from', e => e.from).attr('data-to', e => e.to)
        .attr('d', e => roundedPath(e.points, 8))
        .attr('marker-end', e => e.type === 'contains' ? null : e.resolution === 'unresolved' ? 'url(#rx-ah-err)' : 'url(#rx-ah)');

    // Nodes
    const nodeData = [...graph.nodes.values()].map(n => ({...n, pos: layout.nodes.get(n.id)})).filter(n => n.pos);
    const node = svg.append('g').attr('class', 'rx-gnodes').selectAll('g').data(nodeData).join('g')
        .attr('class', n => 'rx-gnode rx-gnode-' + n.type + (n.id === selected ? ' rx-gnode-selected' : ''))
        .attr('data-id', n => n.id)
        .attr('transform', n => 'translate(' + n.pos.x + ',' + n.pos.y + ')')
        .attr('tabindex', 0).attr('role', 'button')
        .attr('aria-label', n => n.name + ', ' + (n.type === 'missing' ? 'missing target' : n.type) + (n.caption ? ', ' + n.caption : ''))
        .on('click', (event, n) => { event.stopPropagation(); onSelect(n.id); })
        .on('keydown', (event, n) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onSelect(n.id); } })
        .on('mouseenter', (event, n) => onHover(n.id))
        .on('mouseleave', () => onHover(null))
        .on('focus', (event, n) => onHover(n.id))
        .on('blur', () => onHover(null));

    node.each(function (n) {
        const g = d3.select(this);
        const w = n.width, boxH = n.type === 'ruleflow' ? 30 : 36;
        if (n.type === 'rule') {
            g.append('rect').attr('class', 'rx-gnode-halo').attr('x', -6).attr('y', 0).attr('width', w + 6).attr('height', n.height).attr('rx', 8);
            g.append('rect').attr('class', 'rx-gnode-ring').attr('x', -6).attr('y', -2).attr('width', w + 6).attr('height', n.height + 4).attr('rx', 9);
            if (n.undescribed) g.append('circle').attr('cx', 10).attr('cy', 12).attr('r', 5).attr('class', 'rx-gdot-undescribed');
            else g.append('circle').attr('cx', 10).attr('cy', 12).attr('r', 5).attr('class', 'rx-gdot');
            let x = 22;
            if (n.order) { g.append('text').attr('class', 'rx-gorder').attr('x', x).attr('y', 16).text(n.order); x += n.order >= 10 ? 18 : 12; }
            g.append('text').attr('class', 'rx-gname').attr('x', x).attr('y', 16).text(clipText(n.name, w - x - 4, FONT_NAME));
            g.append('text').attr('class', 'rx-gcap').attr('x', 22).attr('y', 30).text(clipText(n.caption, w - 26, FONT_CAPTION));
            if (n.problem) g.append('circle').attr('class', 'rx-gproblem rx-gproblem-' + n.problem).attr('cx', w - 4).attr('cy', 12).attr('r', 3.5);
        } else if (n.type === 'missing') {
            g.append('rect').attr('class', 'rx-gnode-ring').attr('x', -4).attr('y', -4).attr('width', w + 8).attr('height', 30 + 8).attr('rx', 19);
            g.append('rect').attr('class', 'rx-gbox').attr('width', w).attr('height', 30).attr('rx', 15);
            g.append('circle').attr('class', 'rx-gdot-missing').attr('cx', 15).attr('cy', 15).attr('r', 5);
            g.append('text').attr('class', 'rx-gname').attr('x', 28).attr('y', 19.5).text(clipText(n.name, w - 34, FONT_NAME));
            g.append('text').attr('class', 'rx-gcap').attr('x', 0).attr('y', 44).text(n.caption);
        } else {
            const pill = n.type === 'ruleflow';
            g.append('rect').attr('class', 'rx-gnode-ring').attr('x', -4).attr('y', -4).attr('width', w + 8).attr('height', boxH + 8).attr('rx', pill ? 19 : 12);
            g.append('rect').attr('class', 'rx-gbox').attr('width', w).attr('height', boxH).attr('rx', pill ? 15 : 8);
            if (pill) g.append('polygon').attr('class', 'rx-gglyph').attr('points', hex(15, 15));
            else g.append('rect').attr('class', 'rx-gglyph').attr('x', 14).attr('y', 12).attr('width', 12).attr('height', 12).attr('rx', 3);
            const textW = w - 34 - 12 - (n.problem ? 12 : 0);
            g.append('text').attr('class', 'rx-gname').attr('x', 34).attr('y', boxH / 2 + 4.5).text(clipText(n.name, textW, FONT_NAME));
            g.append('text').attr('class', 'rx-gcap').attr('x', 0).attr('y', boxH + 14).text(clipText(n.caption, w, FONT_CAPTION));
            if (n.problem) g.append('circle').attr('class', 'rx-gproblem rx-gproblem-' + n.problem).attr('cx', w - 10).attr('cy', boxH / 2).attr('r', 3.5);
        }
    });

    return svg.node();
}

function marker(defs, id, cls) {
    defs.append('marker').attr('id', id).attr('viewBox', '0 0 10 10').attr('refX', 9).attr('refY', 5)
        .attr('markerUnits', 'userSpaceOnUse').attr('markerWidth', 10).attr('markerHeight', 10).attr('orient', 'auto')
        .append('path').attr('d', 'M0 1L9 5L0 9z').attr('class', cls);
}

export function hex(cx, cy, r = 6) {
    const h = r * 0.866;
    return [[cx, cy - r], [cx + h, cy - r / 2], [cx + h, cy + r / 2], [cx, cy + r], [cx - h, cy + r / 2], [cx - h, cy - r / 2]].map(p => p.join(',')).join(' ');
}

/** Dims everything outside the hovered node's neighbourhood (DESIGN-SYSTEM §4). */
export function applyHover(svg, graph, hoveredId) {
    if (!svg) return;
    const nodes = svg.querySelectorAll('.rx-gnode');
    const edges = svg.querySelectorAll('.rx-gedge');
    if (!hoveredId) {
        nodes.forEach(n => n.classList.remove('rx-dim', 'rx-hover'));
        edges.forEach(e => e.classList.remove('rx-dim', 'rx-hi'));
        return;
    }
    const near = new Set([hoveredId]);
    for (const e of graph.edges) {
        if (e.from === hoveredId) near.add(e.to);
        if (e.to === hoveredId) near.add(e.from);
    }
    nodes.forEach(n => { const id = n.getAttribute('data-id'); n.classList.toggle('rx-dim', !near.has(id)); n.classList.toggle('rx-hover', id === hoveredId); });
    edges.forEach(e => { const touches = e.getAttribute('data-from') === hoveredId || e.getAttribute('data-to') === hoveredId; e.classList.toggle('rx-dim', !touches); e.classList.toggle('rx-hi', touches); });
}

export function applySelection(svg, selectedId) {
    if (!svg) return;
    svg.querySelectorAll('.rx-gnode').forEach(n => n.classList.toggle('rx-gnode-selected', n.getAttribute('data-id') === selectedId));
}
