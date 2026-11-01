import {html, nothing, svg} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon, severityIcon} from '../../components/icons.js';
import {card, severityDots} from '../../components/common.js';
import {routes} from '../../routing/router.js';
import {compareNatural, SEVERITY_ORDER, typeLabel} from '../../descriptor/format.js';
import {beansOverviewSubtitle, constraintKindsCaption, discoveryCaption} from '../../descriptor/summaries.js';
import {beanDeclarations} from '../../descriptor/indexes.js';

/**
 * The Beans overview (FR-74, S-BeansOverview): counts, the validation map (which beans cascade
 * into which, and the registry rules they run), the beans' problems and their packages. Reads
 * only the Beans index; the Rules section never reads it.
 */
class RxBeansOverview extends RxElement {

    render() {
        const {descriptor, index} = this.state;
        const beans = index.beans;
        const name = descriptor.application.name || 'this application';
        const c = beans.counts;
        const p = beans.problemCounts;
        return html`<div class="rx-page rx-page-overview">
            <div class="rx-page-head" style="gap: 8px">
                <div class="rx-overline rx-overline-accent">Beans</div>
                <h1 class="rx-h1">Everything ${name} validates, in one place.</h1>
                <p class="rx-subtitle">${beansOverviewSubtitle(beans)}</p>
            </div>

            ${beans.undescribed.size ? html`<div class="rx-note rx-note-warning" role="status">${icon('warning', {size: 16, width: 2.2})}<span><span class="rx-strong">${beans.undescribed.size === 1 ? '1 bean couldn’t be described.' : beans.undescribed.size + ' beans couldn’t be described.'}</span> The others are shown as usual.</span><span class="rx-spacer"></span><a href=${routes.beansProblems()}>See details</a></div>` : nothing}

            <div class="rx-stats">
                <div class="rx-card rx-stat rx-stat-bean">
                    <div class="rx-stat-label">${glyph('bean', {size: 12})}Beans</div>
                    <div class="rx-stat-num">${c.beans}</div>
                    <div class="rx-stat-caption">${c.beans ? discoveryCaption(beans) : 'None found'}</div>
                </div>
                <div class="rx-card rx-stat rx-stat-rule">
                    <div class="rx-stat-label">${glyph('rule', {size: 12})}Constraints</div>
                    <div class="rx-stat-num">${c.constraints}</div>
                    <div class="rx-stat-caption">${constraintKindsCaption(beans)}</div>
                </div>
                <div class="rx-card rx-stat rx-stat-cascades">
                    <div class="rx-stat-label">${icon('arrowRight', {size: 14, width: 2.2})}Cascades</div>
                    <div class="rx-stat-num">${c.cascades}</div>
                    <div class="rx-stat-caption">${this.cascadesCaption(beans)}</div>
                </div>
                <a class=${'rx-card rx-stat rx-stat-problems' + (p.total ? '' : ' rx-stat-ok')} href=${routes.beansProblems()}>
                    <div class="rx-stat-label">${p.total ? icon('warning', {size: 13, width: 2.2}) : icon('check', {size: 13, width: 2.4})}${p.total ? 'Needs attention' : 'All clear'}</div>
                    <div class="rx-stat-num">${p.error + p.warning}</div>
                    <div class="rx-stat-caption">${p.total ? severityDots(p) : 'No problems found'}</div>
                </a>
            </div>

            <div class="rx-overview-grid">
                ${this.mapCard(index)}
                <div class="rx-col">
                    ${this.problemsCard(index)}
                    ${this.packagesCard(beans)}
                    ${card('How this was read', html`<p class="rx-small" style="font-size: 12.5px; line-height: 1.5; color: var(--rx-ink-2)">The application’s packages were scanned for classes that declare constraints, <span class="rx-mono">@Valid</span>, <span class="rx-mono">@Validate</span>, <span class="rx-mono">@Expr</span> or <span class="rx-mono">@Bind</span>, then each was read through the application’s validator: what is shown is what validating the bean runs.</p>`, {class: 'rx-card-tight'})}
                </div>
            </div>
        </div>`;
    }

    cascadesCaption(beans) {
        const pairs = [];
        for (const [from, refs] of beans.cascadesTo) {
            for (const to of new Set(refs.map(r => r.to))) {
                const a = beans.byId.get(from);
                const b = beans.byId.get(to);
                if (a && b) pairs.push(a.name + ' → ' + b.name);
            }
        }
        if (!pairs.length) return 'No bean cascades into another';
        return pairs.slice(0, 2).join(' · ') + (pairs.length > 2 ? ' · and ' + (pairs.length - 2) + ' more' : '');
    }

    problemsCard(index) {
        const beans = index.beans;
        const problems = [...beans.problems].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] || compareNatural(a.artifact, b.artifact)).slice(0, 4);
        const body = problems.length ? html`<div class="rx-problem-list">${problems.map(p => {
            const b = beans.byId.get(p.artifact);
            return html`<div class="rx-problem-row">
                <span class=${'rx-sev-icon rx-sev-icon-sm rx-sev-' + p.severity}>${severityIcon(p.severity, 11)}</span>
                <p>${b ? html`<a href=${routes.bean(b)} data-hover=${b.id}>${b.name}</a> ` : nothing}${lowerFirst(p.message)}</p>
            </div>`;
        })}</div>` : html`<p class="rx-small" style="font-size: 12.5px">Nothing needs attention. Every bean materialized and every @Validate resolves.</p>`;
        return card('Problems', body, {class: 'rx-card-xl', titleClass: 'rx-h2-lg', action: beans.problemCounts.total ? html`<a class="rx-link-quiet" href=${routes.beansProblems()}>View all</a>` : nothing});
    }

    packagesCard(beans) {
        const packages = [...beans.packages].sort((a, b) => b.beans.length - a.beans.length || compareNatural(a.id, b.id));
        const max = Math.max(1, ...packages.map(p => p.beans.length));
        const body = html`<div class="rx-pkg-grid">${packages.map(p => html`
            <div class="rx-pkg-name"><span class="rx-mono rx-pkg-plain">${p.id || '(default package)'}</span><span>${packageCaption(p.beans)}</span></div>
            <div class="rx-pkg-bar" aria-hidden="true"><span class="rx-seg-bean" style=${'width: ' + Math.max(6, Math.round(120 * p.beans.length / max) - 2) + 'px'}></span></div>
            <span class="rx-pkg-count">${p.beans.length}</span>`)}</div>`;
        return card('Where they’re defined', body, {class: 'rx-card-xl rx-card-tight', titleClass: 'rx-h2-lg', action: html`<span class="rx-h2-count">${packages.length === 1 ? '1 package' : packages.length + ' packages'}</span>`});
    }

    /** The validation map: beans in a column, the beans they cascade into, and the rules they run. */
    mapCard(index) {
        const m = buildBeansMap(index);
        return html`<section class="rx-card rx-card-xl rx-map-card" aria-labelledby="rx-bmap-h">
            <div class="rx-card-head rx-card-head-start">
                <div style="display: flex; flex-direction: column; gap: 2px">
                    <h2 class="rx-h2 rx-h2-xl" id="rx-bmap-h">Validation map</h2>
                    <span class="rx-card-sub">Which beans cascade into which, and the rules they run</span>
                </div>
                <a class="rx-btn rx-btn-secondary" href=${routes.beansGraph()}>Open graph${icon('arrowRight', {size: 14})}</a>
            </div>
            ${renderBeansMap(m, index)}
            <div class="rx-legend">
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" style="stroke: var(--rx-edge)"></path><path d="M19 1L25 4L19 7z" style="fill: var(--rx-edge)"></path></svg>cascades (@Valid)</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" stroke-dasharray="4 4" style="stroke: var(--rx-edge)"></path><path d="M19 1L25 4L19 7z" style="fill: var(--rx-edge)"></path></svg>runs a registry rule (@Validate, by name)</span>
                <span><svg width="26" height="8" viewBox="0 0 26 8" aria-hidden="true"><path d="M0 4H20" stroke-dasharray="4 4" style="stroke: var(--rx-error)"></path><path d="M19 1L25 4L19 7z" style="fill: var(--rx-error)"></path></svg>missing target</span>
            </div>
        </section>`;
    }
}

function lowerFirst(text) {
    return text ? text.charAt(0).toLowerCase() + text.slice(1) : text;
}

function packageCaption(beans) {
    const kinds = new Map();
    for (const b of beans) kinds.set(b.discovery, (kinds.get(b.discovery) || 0) + 1);
    const words = {scan: 'scanned', configured: 'configured', validated: 'validated', cascade: 'by cascade'};
    return 'Java · ' + [...kinds.entries()].map(([k, n]) => n + ' ' + (words[k] || k)).join(', ');
}

/**
 * Lays out the validation map: every bean in the left column (sources first, then the ones only
 * reached by cascade), cascade targets in the middle, rules on the right. Above 30 beans the map
 * lists the beans with the most declarations and counts the rest.
 */
export function buildBeansMap(index) {
    const beans = index.beans;
    const MAX = 30;
    const sorted = [...beans.list].sort((a, b) => (a.discovery === 'cascade') - (b.discovery === 'cascade') || compareNatural(a.name, b.name));
    const shown = sorted.slice(0, MAX);
    const hidden = sorted.length - shown.length;
    const ROW = 44, TOP = 24;
    const rows = shown.map((b, i) => ({bean: b, y: TOP + 18 + i * ROW, count: beanDeclarations(b).length}));
    const rowOf = new Map(rows.map(r => [r.bean.id, r]));
    const targets = [];  // middle column: beans cascaded into, one row each, aligned with their first source when free
    const targetOf = new Map();
    const rules = [];    // right column: rule targets and missing names
    const ruleOf = new Map();
    const edges = [];
    let ty = TOP + 18;
    let ry = TOP + 18;
    for (const r of rows) {
        for (const ref of beans.cascadesTo.get(r.bean.id) || []) {
            const target = beans.byId.get(ref.to);
            if (!target) continue;
            if (!targetOf.has(target.id)) { const t = {bean: target, y: Math.max(ty, r.y)}; targets.push(t); targetOf.set(target.id, t); ty = t.y + ROW; }
            edges.push({from: r, to: targetOf.get(target.id), kind: 'cascade', label: memberLabel(ref.path)});
        }
        for (const decl of beanDeclarations(r.bean).filter(d => d.kind === 'validate')) {
            for (const t of decl.validation.targets || []) {
                const key = t.artifactId ? 'a:' + t.artifactId : 'missing:' + t.name;
                if (!ruleOf.has(key)) {
                    const artifact = t.artifactId ? index.byId.get(t.artifactId) : null;
                    const node = {key, artifact, name: artifact ? artifact.name : t.name, missing: !artifact, y: Math.max(ry, r.y)};
                    rules.push(node); ruleOf.set(key, node); ry = node.y + ROW;
                }
                edges.push({from: r, to: ruleOf.get(key), kind: 'runs', missing: !t.artifactId});
            }
        }
    }
    const height = Math.max(rows.length ? rows[rows.length - 1].y + 24 : 0, ty, ry, 80) + (hidden ? ROW : 0);
    return {rows, targets, rules, edges, hidden, height};
}

function memberLabel(path) {
    const m = /^properties\.([^.[]+)/.exec(path || '');
    if (m) return m[1];
    const e = /^executables\.([^.[]+)/.exec(path || '');
    return e ? e[1] : '';
}

function renderBeansMap(m, index) {
    const W = 652;
    if (!m.rows.length) return html`<p class="rx-small" style="font-size: 12.5px">No beans to map.</p>`;
    const parts = [];
    const X0 = 0, W0 = 176, X1 = 300, W1 = 150, X2 = 480;
    parts.push(svg`<text class="rx-map-head" x=${X0} y="12">BEANS</text>`);
    if (m.targets.length) parts.push(svg`<text class="rx-map-head" x=${X1} y="12">CASCADES INTO</text>`);
    if (m.rules.length) parts.push(svg`<text class="rx-map-head" x=${X2} y="12">RUNS · RULES SECTION</text>`);
    for (const e of m.edges) {
        const x1 = X0 + W0;
        if (e.kind === 'cascade') {
            const x2 = X1;
            const mid = x1 + 40;
            parts.push(svg`<path class="rx-map-edge" d=${`M${x1} ${e.from.y}H${mid}V${e.to.y}H${x2}`} marker-end="url(#rx-bmap-ah)"></path>`);
            if (e.label) parts.push(svg`<text class="rx-map-label" x=${x1 + 24} y=${Math.min(e.from.y, e.to.y) - 6}>${e.label}</text>`);
        } else {
            const mid = x1 + 70 + (m.targets.length ? 190 : 0);
            const x2 = X2;
            parts.push(svg`<path class=${'rx-map-edge rx-map-lookup' + (e.missing ? ' rx-map-error' : '')} d=${`M${x1} ${e.from.y}H${mid}V${e.to.y}H${x2}`} marker-end=${e.missing ? 'url(#rx-bmap-ah-err)' : 'url(#rx-bmap-ah)'}></path>`);
        }
    }
    for (const r of m.rows) {
        const worst = index.beans.worstByBean.get(r.bean.id);
        parts.push(svg`<a href=${routes.bean(r.bean)} class="rx-map-bean" data-hover=${r.bean.id}><rect x=${X0} y=${r.y - 18} width=${W0} height="36" rx="10"></rect><polygon points=${diamond(X0 + 18, r.y)} style="fill: var(--rx-bean)"></polygon><text x=${X0 + 34} y=${r.y + 4.5}>${clip(r.bean.name, 14)}</text><text class="rx-map-sub" x=${X0 + W0 - 10} y=${r.y + 4.5} text-anchor="end">${r.count}</text>${worst ? svg`<circle cx=${X0 + W0 - 6} cy=${r.y - 12} r="3.5" class=${'rx-map-dot-' + worst}></circle>` : nothing}</a>`);
    }
    for (const t of m.targets) {
        parts.push(svg`<a href=${routes.bean(t.bean)} class="rx-map-bean" data-hover=${t.bean.id}><rect x=${X1} y=${t.y - 18} width=${W1} height="36" rx="10"></rect><polygon points=${diamond(X1 + 18, t.y)} style="fill: var(--rx-bean)"></polygon><text x=${X1 + 34} y=${t.y + 4.5}>${clip(t.bean.name, 12)}</text></a>`);
    }
    for (const r of m.rules) {
        if (r.missing) parts.push(svg`<g class="rx-map-rule rx-map-missing"><circle cx=${X2 + 6} cy=${r.y} r="5"></circle><text x=${X2 + 18} y=${r.y + 4}>${clip(r.name, 20)} · missing</text></g>`);
        else parts.push(svg`<a href=${routes.artifact(r.artifact)} class="rx-map-rule" data-hover=${r.artifact.id}><circle cx=${X2 + 6} cy=${r.y} r="5" style="fill: var(--rx-rule)"></circle><text x=${X2 + 18} y=${r.y + 4}>${clip(r.name, 22)}</text></a>`);
    }
    if (m.hidden) parts.push(svg`<text class="rx-map-more" x=${X0} y=${m.height - 14}>and ${m.hidden} more ${m.hidden === 1 ? 'bean' : 'beans'}; the sidebar has them all</text>`);
    return html`<svg class="rx-map rx-bmap" viewBox=${`0 0 ${W} ${m.height}`} role="group" aria-label="Validation map: which beans cascade into which, and the rules they run">
        <defs>
            <marker id="rx-bmap-ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 1L9 5L0 9z" class="rx-map-arrow"></path></marker>
            <marker id="rx-bmap-ah-err" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 1L9 5L0 9z" class="rx-map-arrow-error"></path></marker>
        </defs>
        ${parts}
    </svg>`;
}

function diamond(cx, cy) {
    return `${cx},${cy - 6} ${cx + 6},${cy} ${cx},${cy + 6} ${cx - 6},${cy}`;
}

function clip(text, max) {
    return text.length > max ? text.slice(0, max - 1) + '…' : text;
}

export {typeLabel};

customElements.define('rx-beans-overview', RxBeansOverview);
