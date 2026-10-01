import {html, nothing, svg} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph} from '../../components/icons.js';
import {breadcrumb, card, copyLinkButton, typeBadge} from '../../components/common.js';
import {routes} from '../../routing/router.js';
import {compareNatural, expressionsOf, shortType, walkCommands} from '../../descriptor/format.js';
import {bindingSummary} from '../../descriptor/summaries.js';

/** The binding cross-reference (S-Binding): who writes it, who reads it, which properties. */
class RxBinding extends RxElement {

    render() {
        const {index, route} = this.state;
        const b = index.bindings.get(route.id);
        if (!b) return html`<rx-states kind="missing"></rx-states>`;
        const paths = index.bindingPaths.get(b.name) || {reads: new Set(), writes: new Set()};
        const type = bindingType(b.name, index);
        const model = xrefModel(b, index);
        return html`<div class="rx-page rx-page-split">
            <div class="rx-page-head">
                ${breadcrumb([{text: 'Bindings', href: routes.overview()}, {text: b.name}])}
                <div class="rx-head-row">
                    <div class="rx-page-head" style="gap: 8px">
                        <div class="rx-meta-row">${typeBadge('binding')}<span class="rx-kind">a named value the rules share while they run</span></div>
                        <div class="rx-title-row"><h1 class="rx-h1 rx-h1-mono">${b.name}</h1>${type ? html`<span class="rx-mono" style="font-size: 13px; font-weight: 500; color: var(--rx-ink-3)" title=${type}>${type}</span>` : nothing}</div>
                        <p class="rx-lede">${bindingSummary(b)}</p>
                    </div>
                    <div class="rx-actions" style="padding-top: 22px">${copyLinkButton('Copy link to this binding')}</div>
                </div>
                ${paths.reads.size || paths.writes.size ? html`<div class="rx-props-row">
                    <span>Properties used</span>
                    ${[...paths.reads].sort(compareNatural).map(p => html`<span class="rx-chip">${p} ←</span>`)}
                    ${[...paths.writes].sort(compareNatural).map(p => html`<span class="rx-chip">${p} →</span>`)}
                    <span class="rx-small">← read · → written</span>
                </div>` : nothing}
            </div>
            <div class="rx-page-body">
                <section class="rx-card rx-card-xl" aria-labelledby="rx-xref-h" style="padding: 14px 20px 12px; gap: 6px">
                    <div class="rx-card-head">
                        <h2 class="rx-h2 rx-h2-lg" id="rx-xref-h">Who writes it, who reads it</h2>
                        <div class="rx-legend">
                            <span><svg width="24" height="8" viewBox="0 0 24 8" aria-hidden="true"><path d="M0 4H18" style="stroke: var(--rx-edge)"></path><path d="M17 1L23 4L17 7z" style="fill: var(--rx-edge)"></path></svg>known</span>
                            <span><svg width="24" height="8" viewBox="0 0 24 8" aria-hidden="true"><path d="M0 4H18" stroke-dasharray="4 3" style="stroke: var(--rx-warning)"></path><path d="M17 1L23 4L17 7z" style="fill: var(--rx-warning)"></path></svg>may write · compiled code</span>
                        </div>
                    </div>
                    ${xrefSvg(model, b, type)}
                </section>
                <div class="rx-grid-2">
                    ${card('Readers', list(model.readers, index), {class: 'rx-card-tight', action: html`<span class="rx-h2-count">${model.readers.length}</span>`})}
                    ${card('Writers', list([...model.writers, ...model.maybe], index, true), {class: 'rx-card-tight', action: html`<span class="rx-h2-count">${model.writers.length}${model.maybe.length ? ' + ' + model.maybe.length + ' possible' : ''}</span>`})}
                </div>
            </div>
        </div>`;
    }
}

function list(nodes, index, withNote = false) {
    if (!nodes.length) return html`<p class="rx-small" style="font-size: 12.5px">Nothing.</p>`;
    return html`<div class="rx-rows">${nodes.map(n => html`<a class="rx-row" href=${routes.artifact(n.artifact)} data-hover=${n.artifact.id}>
        ${glyph(n.artifact.type, {size: 11})}
        <span class="rx-name">${n.artifact.name}${n.sub ? html`<span class="rx-small">${n.sub}</span>` : nothing}</span>
        <span class="rx-kind-cell">${n.maybe ? html`<span style="color: var(--rx-warning); font-weight: 600">may write</span>` : withNote ? 'writes' : 'reads'}</span>
    </a>`)}</div>`;
}

/** The declared type, from a parameter or a bind command naming this binding. */
function bindingType(name, index) {
    for (const a of index.descriptor.artifacts) {
        const p = a.parameters.find(x => x.name === name);
        if (p && p.type) return p.type;
    }
    for (const flow of index.byType.ruleflow) {
        let found = null;
        walkCommands(flow.ruleFlow.commands, (c) => { if (!found && c.bind && c.bind.names) { const n = c.bind.names.find(x => x.name === name); if (n && n.type) found = n.type; } });
        if (found) return found;
    }
    return null;
}

/** What a writer does to the binding (plain text, so it fits in SVG too): "sets order.discount", "binds it as an input parameter". */
function writerSub(a, name, index) {
    const writes = new Set();
    for (const {expression} of expressionsOf(a)) for (const w of expression.writes || []) if (w === name || w.startsWith(name + '.')) writes.add(w);
    if (a.parameters.some(p => p.name === name)) return 'binds it as an input parameter';
    if (a.ruleFlow) {
        let how = null;
        walkCommands(a.ruleFlow.commands, (c, path, number) => {
            if (how) return;
            if (c.bind && c.bind.names && c.bind.names.some(n => n.name === name)) how = 'binds it in step ' + number + (c.bind.label ? ' from ' + c.bind.label : '');
            else if (c.as === name) how = 'keeps a result as it in step ' + number;
            else if (c.type === 'for-each' && c.item === name) how = 'loops over it in step ' + number;
        });
        if (how) return how;
    }
    const list = [...writes].filter(w => w !== name).sort(compareNatural);
    if (list.length) return 'sets ' + list.join(', ');
    if (writes.has(name)) return 'sets it';
    return null;
}

function xrefModel(b, index) {
    const node = (id) => index.byId.get(id);
    const writers = (b.writtenBy || []).map(node).filter(Boolean).map(a => ({artifact: a, sub: writerSub(a, b.name, index)}));
    const maybe = (b.unknownWriters || []).filter(id => !(b.writtenBy || []).includes(id)).map(node).filter(Boolean).map(a => ({artifact: a, maybe: true}));
    const readers = (b.readBy || []).map(node).filter(Boolean).map(a => ({artifact: a}));
    const order = {ruleflow: 0, ruleset: 1, rule: 2};
    readers.sort((x, y) => order[x.artifact.type] - order[y.artifact.type] || compareNatural(x.artifact.name, y.artifact.name));
    writers.sort((x, y) => order[x.artifact.type] - order[y.artifact.type] || compareNatural(x.artifact.name, y.artifact.name));
    return {writers, maybe, readers};
}

function xrefSvg(m, b, type) {
    const W = 1056;
    const leftH = 35 + m.writers.length * 40 + (m.maybe.length ? 12 + m.maybe.length * 26 : 0);
    const rightH = 35 + Math.max(m.readers.length, 1) * 24;
    const H = Math.max(leftH, rightH, 120) + 10;
    const midY = Math.round(H / 2) + 6;
    const parts = [];
    parts.push(svg`<g class="rx-xref-head"><text x="0" y="12">WRITES IT</text><text x="528" y="12" text-anchor="middle">BINDING</text><text x="760" y="12">READS IT</text></g>`);
    // writers
    let y = 35;
    const knownYs = [];
    m.writers.forEach(w => {
        const cy = y + 16.5;
        knownYs.push(cy);
        parts.push(svg`<a href=${routes.artifact(w.artifact)} class=${'rx-xref-node rx-xref-' + w.artifact.type} data-hover=${w.artifact.id}>
            <rect x="0.5" y=${y + 0.5} width="249" height="33" rx="8"></rect>
            ${glyphAt(w.artifact.type, 16, cy)}
            <text x="28" y=${w.sub ? cy - 3 : cy + 4.5}>${clip(w.artifact.name, 28)}</text>
            ${w.sub ? svg`<text class="rx-xref-sub" x="28" y=${cy + 10}>${w.sub}</text>` : nothing}</a>`);
        parts.push(svg`<path class="rx-xref-edge" d=${`M250 ${cy}H345`}></path>`);
        y += 40;
    });
    if (knownYs.length) {
        const top = knownYs[0], bottom = knownYs[knownYs.length - 1];
        parts.push(svg`<path class="rx-xref-edge" d=${`M345 ${Math.min(top, midY)}V${Math.max(bottom, midY)}`}></path><path class="rx-xref-edge" d=${`M345 ${midY}H438`} marker-end="url(#rx-xref-ah)"></path><circle cx="345" cy=${midY} r="3" class="rx-xref-arrow"></circle>`);
    }
    if (m.maybe.length) {
        y += 12;
        const maybeYs = [];
        m.maybe.forEach(w => {
            const cy = y + 10.5;
            maybeYs.push(cy);
            parts.push(svg`<a href=${routes.artifact(w.artifact)} class="rx-xref-node rx-xref-maybe" data-hover=${w.artifact.id}><rect x="0.5" y=${y + 0.5} width="249" height="21" rx="6"></rect>${glyphAt(w.artifact.type, 16, cy, 4)}<text x="28" y=${cy + 4}>${clip(w.artifact.name, 26)}</text><text class="rx-xref-maybe-label" x="240" y=${cy + 3.5} text-anchor="end">may write</text></a>`);
            parts.push(svg`<path class="rx-xref-edge-maybe" d=${`M250 ${cy}H372`}></path>`);
            y += 26;
        });
        parts.push(svg`<path class="rx-xref-edge-maybe" d=${`M372 ${maybeYs[maybeYs.length - 1]}V${midY + 12}H438`} marker-end="url(#rx-xref-ah-w)"></path>`);
    }
    if (!m.writers.length && !m.maybe.length) {
        parts.push(svg`<text class="rx-xref-sub" x="0" y=${midY + 4}>Nothing writes it; it comes from the caller.</text>`);
    }
    // pill
    parts.push(svg`<g class="rx-xref-pill"><rect x="440" y=${midY - 24} width="176" height="48" rx="24"></rect><text x="528" y=${midY - 3 + (type ? 0 : 5)} text-anchor="middle">${clip(b.name, 18)}</text>${type ? svg`<text class="rx-xref-type" x="528" y=${midY + 13} text-anchor="middle">${clip(shortType(type), 24)}</text>` : nothing}</g>`);
    // readers
    if (m.readers.length) {
        const ys = m.readers.map((r, i) => 40 + i * 24);
        parts.push(svg`<path class="rx-xref-edge" d=${`M616 ${midY}H690`}></path><circle cx="690" cy=${midY} r="3" class="rx-xref-arrow"></circle><path class="rx-xref-edge" d=${`M690 ${Math.min(ys[0], midY)}V${Math.max(ys[ys.length - 1], midY)}`}></path>`);
        m.readers.forEach((r, i) => {
            const cy = ys[i];
            parts.push(svg`<path class="rx-xref-edge" d=${`M690 ${cy}H756`} marker-end="url(#rx-xref-ah)"></path>`);
            parts.push(svg`<a href=${routes.artifact(r.artifact)} class="rx-xref-reader" data-hover=${r.artifact.id}>${glyphAt(r.artifact.type, 766, cy, 4.5)}<text x="780" y=${cy + 4}>${clip(r.artifact.name, 36)}</text></a>`);
        });
    } else {
        parts.push(svg`<text class="rx-xref-sub" x="760" y=${midY + 4}>Nothing reads it.</text>`);
    }
    return html`<svg class="rx-xref" viewBox=${`0 0 ${W} ${H}`} role="group" aria-label=${'Written by ' + (m.writers.map(w => w.artifact.name).join(', ') || 'nothing') + (m.maybe.length ? '; may be written by ' + m.maybe.map(w => w.artifact.name).join(', ') : '') + '. Read by ' + (m.readers.map(r => r.artifact.name).join(', ') || 'nothing') + '.'}>
        <defs>
            <marker id="rx-xref-ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 1L9 5L0 9z" class="rx-xref-arrow"></path></marker>
            <marker id="rx-xref-ah-w" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 1L9 5L0 9z" class="rx-xref-arrow-maybe"></path></marker>
        </defs>
        ${parts}
    </svg>`;
}


function glyphAt(type, cx, cy, r = 4.5) {
    if (type === 'ruleflow') return svg`<polygon points=${`${cx},${cy - 5} ${cx + 4.3},${cy - 2.5} ${cx + 4.3},${cy + 2.5} ${cx},${cy + 5} ${cx - 4.3},${cy + 2.5} ${cx - 4.3},${cy - 2.5}`} style="fill: var(--rx-flow)"></polygon>`;
    if (type === 'ruleset') return svg`<rect x=${cx - 4.5} y=${cy - 4.5} width="9" height="9" rx="2.5" style="fill: var(--rx-ruleset)"></rect>`;
    return svg`<circle cx=${cx} cy=${cy} r=${r} style="fill: var(--rx-rule)"></circle>`;
}

function clip(text, max) {
    return text.length > max ? text.slice(0, max - 1) + '…' : text;
}

customElements.define('rx-binding', RxBinding);
