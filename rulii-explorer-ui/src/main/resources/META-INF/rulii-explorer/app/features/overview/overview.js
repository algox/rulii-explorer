import {html, nothing, svg} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon, severityIcon} from '../../components/icons.js';
import {card, severityDots} from '../../components/common.js';
import {routes} from '../../routing/router.js';
import {compareNatural, SEVERITY_ORDER, typeLabel} from '../../descriptor/format.js';
import {kindCaption, overviewSubtitle} from '../../descriptor/summaries.js';
import {packageFiles} from '../../descriptor/indexes.js';

/** The landing page (FR-33, B-Overview): counts, application map, problems and packages. */
class RxOverview extends RxElement {

    render() {
        const {descriptor, index} = this.state;
        const name = descriptor.application.name || 'this application';
        const c = index.counts;
        const p = index.problemCounts;
        const attention = p.error + p.warning;
        return html`<div class="rx-page rx-page-overview">
            <div class="rx-page-head" style="gap: 8px">
                <div class="rx-overline rx-overline-accent">Overview</div>
                <h1 class="rx-h1">Everything ${name} decides, in one place.</h1>
                <p class="rx-subtitle">${overviewSubtitle(index)}</p>
            </div>

            ${index.undescribed.size ? html`<div class="rx-note rx-note-warning" role="status">${icon('warning', {size: 16, width: 2.2})}<span><span class="rx-strong">${index.undescribed.size === 1 ? '1 artifact couldn’t be described.' : index.undescribed.size + ' artifacts couldn’t be described.'}</span> The other ${descriptor.artifacts.length - index.undescribed.size} are shown as usual.</span><span class="rx-spacer"></span><a href=${routes.problems()}>See details</a></div>` : nothing}

            <div class="rx-stats">
                ${this.stat('rule', c.rule, kindCaption('rule', index.kindCounts))}
                ${this.stat('ruleset', c.ruleset, kindCaption('ruleset', index.kindCounts))}
                ${this.stat('ruleflow', c.ruleflow, kindCaption('ruleflow', index.kindCounts))}
                <a class=${'rx-card rx-stat rx-stat-problems' + (p.total ? '' : ' rx-stat-ok')} href=${routes.problems()}>
                    <div class="rx-stat-label">${p.total ? icon('warning', {size: 13, width: 2.2}) : icon('check', {size: 13, width: 2.4})}${p.total ? 'Needs attention' : 'All clear'}</div>
                    <div class="rx-stat-num">${attention}</div>
                    <div class="rx-stat-caption">${p.total ? severityDots(p) : 'No problems found'}</div>
                </a>
            </div>

            <div class="rx-overview-grid">
                ${this.mapCard(index)}
                <div class="rx-col">
                    ${this.problemsCard(index)}
                    ${index.categories.has ? this.categoriesCard(index) : this.packagesCard(index)}
                </div>
            </div>
        </div>`;
    }

    stat(type, n, caption) {
        return html`<div class=${'rx-card rx-stat rx-stat-' + type}>
            <div class="rx-stat-label">${glyph(type, {size: 12})}${typeLabel(type, true)}</div>
            <div class="rx-stat-num">${n}</div>
            <div class="rx-stat-caption">${n ? caption : 'None defined'}</div>
        </div>`;
    }

    problemsCard(index) {
        const problems = [...index.descriptor.problems].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] || compareNatural(a.artifact, b.artifact)).slice(0, 4);
        const body = problems.length ? html`<div class="rx-problem-list">${problems.map(p => {
            const a = index.byId.get(p.artifact);
            return html`<div class="rx-problem-row">
                <span class=${'rx-sev-icon rx-sev-icon-sm rx-sev-' + p.severity}>${severityIcon(p.severity, 11)}</span>
                <p>${a ? html`<a href=${routes.artifact(a)} data-hover=${a.id}>${a.name}</a> ` : nothing}${lowerFirst(p.message)}</p>
            </div>`;
        })}</div>` : html`<p class="rx-small" style="font-size: 12.5px">Nothing needs attention. Every step resolves, names match, and every artifact describes itself.</p>`;
        return card('Problems', body, {class: 'rx-card-xl', titleClass: 'rx-h2-lg', action: index.problemCounts.total ? html`<a class="rx-link-quiet" href=${routes.problems()}>View all</a>` : nothing});
    }

    packagesCard(index) {
        const packages = [...index.packages].sort((a, b) => b.counts.total - a.counts.total || compareNatural(a.pkg.id, b.pkg.id));
        const max = Math.max(1, ...packages.map(p => p.counts.total));
        const body = html`<div class="rx-pkg-grid">${packages.map(p => html`
            <div class="rx-pkg-name"><a href=${routes.package(p.pkg.id)}>${p.pkg.id}</a><span>${packageCaption(p)}</span></div>
            <div class="rx-pkg-bar" aria-hidden="true">${['rule', 'ruleset', 'ruleflow'].filter(t => p.counts[t]).map(t => html`<span class=${'rx-seg-' + t} style=${'width: ' + Math.max(6, Math.round(120 * p.counts[t] / max) - 2) + 'px'}></span>`)}</div>
            <span class="rx-pkg-count">${p.counts.total}</span>`)}</div>`;
        return card('Where they’re defined', body, {class: 'rx-card-xl rx-card-tight', titleClass: 'rx-h2-lg'});
    }

    /** The category tree as an indented list with a bar per category; the uncategorised rest closes it. */
    categoriesCard(index) {
        const c = index.categories;
        const rows = [];
        const walk = (n, depth) => { rows.push({n, depth}); n.children.forEach(ch => walk(ch, depth + 1)); };
        c.roots.forEach(r => walk(r, 0));
        const none = countsOf(c.uncategorised);
        const max = Math.max(1, none.total, ...rows.map(r => r.n.totals.total));
        const bar = (counts) => html`<div class="rx-pkg-bar" aria-hidden="true">${['rule', 'ruleset', 'ruleflow'].filter(t => counts[t]).map(t => html`<span class=${'rx-seg-' + t} style=${'width: ' + Math.max(6, Math.round(120 * counts[t] / max) - 2) + 'px'}></span>`)}</div>`;
        const body = html`<div class="rx-pkg-grid rx-cat-grid">${rows.map(({n, depth}) => html`
            <div class="rx-pkg-name" style=${depth ? 'padding-left: ' + depth * 14 + 'px' : nothing}><a href=${routes.category(n.path)}>${n.name}</a><span>${categoryCaption(n.totals)}</span></div>
            ${bar(n.totals)}
            <span class="rx-pkg-count">${n.totals.total}</span>`)}
            ${c.uncategorised.length ? html`<div class="rx-pkg-name"><span class="rx-cat-none">Uncategorised</span><span>${categoryCaption(none)}</span></div>${bar(none)}<span class="rx-pkg-count">${none.total}</span>` : nothing}
        </div>`;
        return card('Categories', body, {class: 'rx-card-xl rx-card-tight', titleClass: 'rx-h2-lg', action: html`<span class="rx-h2-count">${c.byPath.size}</span>`});
    }

    mapCard(index) {
        const model = buildMap(index);
        return html`<section class="rx-card rx-card-xl rx-map-card" aria-labelledby="rx-map-h">
            <div class="rx-card-head rx-card-head-start">
                <div style="display: flex; flex-direction: column; gap: 2px">
                    <h2 class="rx-h2 rx-h2-xl" id="rx-map-h">Application map</h2>
                    <span class="rx-card-sub">Which flows run which rule sets and rules</span>
                </div>
                <a class="rx-btn rx-btn-secondary" href=${routes.graph()}>Open graph${icon('arrowRight', {size: 14})}</a>
            </div>
            ${renderMap(model)}
            <div class="rx-legend">
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" style="stroke: var(--rx-edge)"></path><path d="M19 1L25 4L19 7z" style="fill: var(--rx-edge)"></path></svg>runs</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H26" style="stroke: var(--rx-line-strong); stroke-width: 1.2"></path></svg>contains</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" stroke-dasharray="4 4" style="stroke: var(--rx-edge)"></path><path d="M19 1L25 4L19 7z" style="fill: var(--rx-edge)"></path></svg>dynamic lookup (by name)</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" stroke-dasharray="4 4" style="stroke: var(--rx-error)"></path><path d="M19 1L25 4L19 7z" style="fill: var(--rx-error)"></path></svg>missing target</span>
            </div>
        </section>`;
    }
}

function lowerFirst(text) {
    return text ? text.charAt(0).toLowerCase() + text.slice(1) : text;
}

function countsOf(list) {
    const counts = {rule: 0, ruleset: 0, ruleflow: 0, total: 0};
    for (const a of list) { counts[a.type]++; counts.total++; }
    return counts;
}

/** "3 rules · 1 rule set" */
function categoryCaption(counts) {
    return ['ruleflow', 'ruleset', 'rule'].filter(t => counts[t]).map(t => counts[t] + ' ' + typeLabel(t, counts[t] !== 1).toLowerCase()).join(' · ');
}

function packageCaption(entry) {
    if (entry.pkg.kind === 'xml') {
        const files = packageFiles(entry);
        return 'XML · ' + (files.length === 1 ? files[0] : files.length + ' files');
    }
    const kinds = new Set(entry.artifacts.map(a => a.kind));
    if (kinds.size === 1 && kinds.has('rule-class')) return 'Java · @Rule classes';
    if (kinds.size === 1 && kinds.has('java-builder')) return 'Java · @Bean';
    return 'Java';
}

/**
 * Lays out the application map: flows, rule sets and rules in three columns, rules in the
 * order their rule sets list them, then rules run directly, missing targets, and unused rules.
 * Above 40 rule rows the rules collapse to one row per rule set.
 */
export function buildMap(index) {
    const flows = index.byType.ruleflow;
    const sets = index.byType.ruleset;
    const rows = [];           // {kind: 'rule'|'group'|'missing', id, label, artifact?, count?}
    const rowOf = new Map();   // artifact id or missing name → row index
    const placed = new Set();
    const dense = index.byType.rule.length > 40;
    const MAX_SETS = 24;  // beyond this the map lists the first sets and counts the rest; the graph has them all

    const setRows = new Map();  // set id → [first, last] row indexes
    let hiddenSets = 0;
    for (const s of sets) {
        const members = s.ruleSet.members;
        const first = rows.length;
        if (dense && setRows.size >= MAX_SETS) { hiddenSets++; for (const id of members) placed.add(id); continue; }
        if (dense) {
            rows.push({kind: 'group', id: s.id, label: members.length + (members.length === 1 ? ' rule' : ' rules'), set: s});
            for (const id of members) placed.add(id);
        } else {
            for (const id of members) {
                if (rowOf.has(id)) continue;
                const a = index.byId.get(id);
                rows.push({kind: 'rule', id, label: a ? a.name : id, artifact: a, missing: !a});
                rowOf.set(id, rows.length - 1);
                placed.add(id);
            }
        }
        setRows.set(s.id, [first, Math.max(first, rows.length - 1)]);
    }
    // Rules run directly by flows, and missing targets
    const flowTargets = new Map(); // flow id → [{row, set?, lookup, missing}]
    for (const f of flows) {
        const targets = [];
        for (const ref of index.uses.get(f.id) || []) {
            const target = index.byId.get(ref.to);
            if (target && target.type === 'ruleset') { if (setRows.has(target.id)) targets.push({set: target.id, lookup: ref.resolution !== 'direct'}); }
            else if (target && target.type === 'rule') {
                if (!rowOf.has(target.id)) { rows.push({kind: 'rule', id: target.id, label: target.name, artifact: target}); rowOf.set(target.id, rows.length - 1); placed.add(target.id); }
                targets.push({row: rowOf.get(target.id), lookup: ref.resolution !== 'direct'});
            }
        }
        // Unresolved targets are not references; read them from the flow's commands
        for (const {command} of (index.commandsByPath.get(f.id) || new Map()).values()) {
            const t = command.target;
            if (t && t.resolution === 'unresolved') {
                const key = 'missing:' + (t.name || t.className || '?');
                if (!rowOf.has(key)) { rows.push({kind: 'missing', id: key, label: t.name || t.className || '?'}); rowOf.set(key, rows.length - 1); }
                targets.push({row: rowOf.get(key), lookup: true, missing: true});
            }
        }
        flowTargets.set(f.id, targets);
    }
    // Rules nothing runs
    let unused = 0;
    for (const a of index.byType.rule) {
        if (placed.has(a.id)) continue;
        if (dense) { unused++; continue; }
        rows.push({kind: 'rule', id: a.id, label: a.name, artifact: a, unused: true});
        rowOf.set(a.id, rows.length - 1);
    }
    if (dense && unused) rows.push({kind: 'group', id: 'unused', label: unused + ' unused ' + (unused === 1 ? 'rule' : 'rules'), unused: true});
    if (hiddenSets) rows.push({kind: 'group', id: 'more-sets', label: 'and ' + hiddenSets + ' more rule sets, with their rules', unused: true});

    const ROW = 24, TOP = 18;
    const y = (i) => TOP + i * ROW;
    const setNodes = sets.filter(s => setRows.has(s.id)).map(s => { const [a, b] = setRows.get(s.id); return {artifact: s, y: (y(a) + y(b)) / 2, fromRow: a, toRow: b}; });
    for (let i = 1; i < setNodes.length; i++) setNodes[i].y = Math.max(setNodes[i].y, setNodes[i - 1].y + 40);
    const setY = new Map(setNodes.map(n => [n.artifact.id, n.y]));
    const flowNodes = flows.map(f => {
        const ts = flowTargets.get(f.id);
        const ys = ts.map(t => t.set ? setY.get(t.set) : y(t.row)).filter(v => v !== undefined);
        return {artifact: f, y: ys.length ? ys.reduce((a, b) => a + b, 0) / ys.length : 0, targets: ts};
    });
    flowNodes.sort((a, b) => a.y - b.y);
    for (let i = 0; i < flowNodes.length; i++) if (i && flowNodes[i].y < flowNodes[i - 1].y + 44) flowNodes[i].y = flowNodes[i - 1].y + 44;
    const height = Math.max(y(rows.length - 1) + 24, (setNodes.length ? setNodes[setNodes.length - 1].y : 0) + 30, (flowNodes.length ? flowNodes[flowNodes.length - 1].y : 0) + 30, 80);
    return {rows, setNodes, flowNodes, rowY: y, height, dense};
}

function renderMap(m) {
    const W = 652;
    const parts = [];
    // contains edges: set → rule rows
    for (const s of m.setNodes) {
        if (m.dense) {
            parts.push(svg`<path class="rx-map-contains" d=${`M416 ${s.y}H452V${m.rowY(s.fromRow)}H480`}></path>`);
            continue;
        }
        for (let i = s.fromRow; i <= s.toRow; i++) {
            const ry = m.rowY(i);
            parts.push(svg`<path class="rx-map-contains" d=${i === s.fromRow ? `M416 ${s.y}H452V${ry}H480` : `M452 ${ry}H480`}></path>`);
            if (i === s.toRow && s.toRow > s.fromRow) parts.push(svg`<path class="rx-map-contains" d=${`M452 ${Math.min(s.y, m.rowY(s.fromRow))}V${Math.max(s.y, ry)}`}></path>`);
        }
    }
    // runs edges: flow → set or rule
    for (const f of m.flowNodes) {
        const jx = 210;
        const ys = f.targets.map(t => t.set ? m.setNodes.find(n => n.artifact.id === t.set).y : m.rowY(t.row));
        if (ys.length) parts.push(svg`<circle cx=${jx} cy=${f.y} r="3" class="rx-map-arrow"></circle>`);
        f.targets.forEach((t, k) => {
            const ty = ys[k];
            const toX = t.set ? 246 : 480;
            const cls = 'rx-map-edge' + (t.lookup ? ' rx-map-lookup' : '') + (t.missing ? ' rx-map-error' : '');
            const d = t.set ? `M176 ${f.y}H${jx}V${ty}H${toX}` : `M176 ${f.y}H${jx}V${ty}H${toX}`;
            parts.push(svg`<path class=${cls} d=${d} marker-end=${t.missing ? 'url(#rx-map-ah-err)' : 'url(#rx-map-ah)'}></path>`);
        });
    }
    // nodes
    for (const f of m.flowNodes) {
        parts.push(svg`<a href=${routes.artifact(f.artifact)} class="rx-map-flow" data-hover=${f.artifact.id}><rect x="0" y=${f.y - 17} width="176" height="34" rx="17"></rect><polygon points=${hex(18, f.y)} style="fill: var(--rx-flow)"></polygon><text x="32" y=${f.y + 4.5}>${clip(f.artifact.name, 19)}</text></a>`);
    }
    for (const s of m.setNodes) {
        parts.push(svg`<a href=${routes.artifact(s.artifact)} class="rx-map-set" data-hover=${s.artifact.id}><rect x="246" y=${s.y - 15} width="170" height="30" rx="8"></rect><rect x="258" y=${s.y - 6} width="12" height="12" rx="3" style="fill: var(--rx-ruleset)"></rect><text x="278" y=${s.y + 4.5}>${clip(s.artifact.name, 19)}</text></a>`);
    }
    m.rows.forEach((row, i) => {
        const ry = m.rowY(i);
        if (row.kind === 'group') {
            parts.push(svg`<g class="rx-map-rule"><circle cx="486" cy=${ry} r="5" style="fill: var(--rx-rule)"></circle><text class="rx-map-more" x="498" y=${ry + 4}>${row.label}</text></g>`);
        } else if (row.kind === 'missing' || row.missing) {
            parts.push(svg`<g class="rx-map-rule rx-map-missing"><circle cx="486" cy=${ry} r="5"></circle><text x="498" y=${ry + 4}>${clip(row.label, 22)} · missing</text></g>`);
        } else {
            parts.push(svg`<a href=${routes.artifact(row.artifact)} class="rx-map-rule" data-hover=${row.artifact.id}><circle cx="486" cy=${ry} r="5" style="fill: var(--rx-rule)"></circle><text x="498" y=${ry + 4} style=${row.unused ? 'fill: var(--rx-ink-3)' : ''}>${clip(row.label, 22)}${row.unused ? ' · unused' : ''}</text></a>`);
        }
    });
    return html`<svg class="rx-map" viewBox=${`0 0 ${W} ${m.height}`} role="group" aria-label="Application map: which flows run which rule sets and rules">
        <defs>
            <marker id="rx-map-ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 1L9 5L0 9z" class="rx-map-arrow"></path></marker>
            <marker id="rx-map-ah-err" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 1L9 5L0 9z" class="rx-map-arrow-error"></path></marker>
        </defs>
        ${parts}
    </svg>`;
}

function hex(cx, cy) {
    return `${cx},${cy - 6} ${cx + 5.2},${cy - 3} ${cx + 5.2},${cy + 3} ${cx},${cy + 6} ${cx - 5.2},${cy + 3} ${cx - 5.2},${cy - 3}`;
}

function clip(text, max) {
    return text.length > max ? text.slice(0, max - 1) + '…' : text;
}

customElements.define('rx-overview', RxOverview);
