import {html, nothing, svg} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon} from '../../components/icons.js';
import {artifactLink, breadcrumb, card, copyButton, copyLinkButton, graphButton, kindCaption, sourceInline, typeBadge} from '../../components/common.js';
import {routes} from '../../routing/router.js';
import {hasCompiled, hasScript, isCompiled, plainText, scriptLanguage, sourceText, typeLabel} from '../../descriptor/format.js';
import {artifactSummary} from '../../descriptor/summaries.js';
import {ruleBody, validatorBody, compiledBody} from './rule.js';
import {ruleSetPage} from './ruleset.js';
import {ruleFlowPage} from './ruleflow.js';

/** Routes an artifact id to its detail page: rule, validator, compiled rule, rule set or rule flow. */
class RxArtifact extends RxElement {

    render() {
        const {route, index} = this.state;
        const a = index.byId.get(route.id);
        if (!a || a.type !== route.type) return html`<rx-states kind="missing"></rx-states>`;
        if (a.type === 'ruleset') return ruleSetPage(a, this);
        if (a.type === 'ruleflow') return ruleFlowPage(a, this);
        return this.rulePage(a);
    }

    rulePage(a) {
        const {index} = this.state;
        const undescribed = index.undescribed.has(a.id);
        const compiled = !a.validation && isRuleCompiled(a);
        const body = undescribed ? undescribedBody(a, index)
            : a.validation ? validatorBody(a, this)
            : compiled ? compiledBody(a, this)
            : ruleBody(a, this);
        return html`<div class="rx-page">
            ${pageHeader(a, index, {compiled})}
            <div class="rx-detail-grid">
                <div class="rx-col">
                    ${undescribed ? nothing : html`<div class="rx-summary">${artifactSummary(a, index)}</div>`}
                    ${body}
                </div>
                <aside class="rx-col" aria-label="Connections">
                    ${usedByCard(a, index)}
                    ${connectionsCard(a, index)}
                    ${sourceCard(a, index)}
                </aside>
            </div>
        </div>`;
    }
}

/** True for a rule whose logic is compiled code only. */
export function isRuleCompiled(a) {
    return !a.validation && hasCompiled(a) && !hasScript(a);
}

/** Breadcrumb, badge row, title, description and the page actions (S-Rule header). */
export function pageHeader(a, index, options = {}) {
    const group = typeLabel(a.type, true);
    const crumbs = [{text: group, href: routes.overview()}];
    if (a.packageId) crumbs.push({text: a.packageId, href: routes.package(a.packageId), mono: true});
    crumbs.push({text: a.name});
    const undescribed = index.undescribed.has(a.id);
    return html`<div class="rx-head-row">
        <div class="rx-page-head">
            ${breadcrumb(crumbs)}
            <div class="rx-meta-row">
                ${typeBadge(a.type, {undescribed})}
                ${kindCaption(a)}
                ${options.compiled ? html`<span class="rx-lock-chip">${icon('lock', {size: 11, width: 2.2})}compiled</span>` : nothing}
                ${sourceInline(a)}
            </div>
            <div class="rx-title-row">
                <h1 class="rx-h1">${a.name}</h1>
                ${a.registered !== false ? html`<span class="rx-bean">bean <span class="rx-chip">${a.id}</span></span>` : html`<span class="rx-bean">inline, not a bean</span>`}
            </div>
            ${a.description ? html`<p class="rx-subtitle">${a.description}</p>` : html`<p class="rx-subtitle rx-muted">No description.</p>`}
        </div>
        <div class="rx-actions">
            ${copyLinkButton('Copy link to this ' + typeLabel(a.type).toLowerCase())}
            ${graphButton(a)}
        </div>
    </div>`;
}

/** "Used by": the rule sets that contain this artifact and the flows that run it. */
export function usedByCard(a, index, options = {}) {
    const refs = index.usedBy.get(a.id) || [];
    const rows = refs.map(ref => {
        const from = index.byId.get(ref.from);
        if (!from) return nothing;
        let caption;
        if (ref.type === 'contains') {
            const n = from.ruleSet ? from.ruleSet.members.length : 0;
            const m = /members\[(\d+)\]/.exec(ref.path || '');
            caption = m ? html`rule ${Number(m[1]) + 1} of ${n}` : 'member';
        } else {
            const entry = index.commandsByPath.get(from.id) && index.commandsByPath.get(from.id).get(ref.path);
            const step = entry ? 'step ' + entry.number : 'runs it';
            const as = entry && entry.command.as ? html`, as <span class="rx-mono">${entry.command.as}</span>` : nothing;
            caption = ref.async ? html`async${as}` : html`${step}${as}`;
        }
        return html`<a class="rx-used-row" href=${routes.artifact(from)} data-hover=${from.id}>${glyph(from.type, {size: 12})}<span class="rx-name">${from.name}</span><span class="rx-small">${caption}</span></a>`;
    });
    const body = refs.length ? html`<div>${rows}</div>` : html`<p class="rx-small" style="font-size: 12.5px">${a.type === 'rule' ? 'No rule set or flow references this rule. It only runs if application code calls it directly.' : a.type === 'ruleset' ? 'No flow runs this rule set; the application runs it directly.' : 'Nothing runs this flow; the application starts it.'}</p>`;
    return card('Used by', body, {class: 'rx-card-tight', titleClass: options.overline ? 'rx-overline' : ''});
}

/** The mini-graph: the first flow that reaches this artifact, the containing rule set, the artifact, and what it reads. */
export function connectionsCard(a, index) {
    const nodes = [];
    const refs = index.usedBy.get(a.id) || [];
    const containing = refs.find(r => r.type === 'contains');
    const set = containing ? index.byId.get(containing.from) : null;
    const flowRef = set ? (index.usedBy.get(set.id) || []).find(r => r.type === 'runs') : refs.find(r => r.type === 'runs');
    const flow = flowRef ? index.byId.get(flowRef.from) : null;
    if (flow) nodes.push({artifact: flow, edge: 'runs', kind: 'edge'});
    if (set) {
        const m = /members\[(\d+)\]/.exec(containing.path || '');
        nodes.push({artifact: set, edge: m ? 'contains · ' + ordinal(Number(m[1]) + 1) : 'contains', kind: 'contains'});
    }
    nodes.push({artifact: a, self: true});
    const reads = new Set();
    for (const e of [a.rule && a.rule.given, ...(a.rule ? a.rule.then : []), a.validation && a.validation.valueSource && a.validation.valueSource.expression].filter(Boolean)) {
        for (const r of e.reads || []) reads.add(r);
    }
    const binding = [...reads].sort()[0];
    if (nodes.length === 1 && !binding) return nothing;
    const W = 280, STEP = 70;
    let y = 8;
    const parts = [];
    nodes.forEach((n, i) => {
        if (i) {
            const prev = nodes[i - 1];
            parts.push(svg`<path class=${prev.kind === 'contains' ? 'rx-conn-contains' : 'rx-conn-edge'} d=${`M140 ${y - STEP + 32}V${y - 2}`} marker-end=${prev.kind === 'contains' ? nothing : 'url(#rx-conn-ah)'}></path><text class="rx-conn-label" x="148" y=${y - STEP + 32 + 22}>${prev.edge}</text>`);
        }
        const w = n.self ? 136 : 200;
        const x = 140 - w / 2;
        const r = n.artifact.type === 'ruleflow' ? 16 : n.artifact.type === 'ruleset' ? 8 : 14;
        const h = n.self ? 28 : 32;
        const cy = y + h / 2;
        parts.push(svg`<a href=${routes.artifact(n.artifact)} class=${'rx-conn-' + n.artifact.type + (n.self ? ' rx-conn-self' : '')} data-hover=${n.self ? nothing : n.artifact.id}>
            <rect x=${x} y=${y} width=${w} height=${h} rx=${r}></rect>
            ${glyphAt(n.artifact.type, x + 18, cy)}
            <text x=${x + 32} y=${cy + 4.5}>${clip(n.artifact.name, n.self ? 14 : 22)}</text></a>`);
        y += STEP;
    }, 0);
    let height = y - STEP + 32 + 8;
    if (binding) {
        parts.push(svg`<path class="rx-conn-reads" d=${`M140 ${y - STEP + 28}V${y - STEP + 51}`}></path><text class="rx-conn-label" x="148" y=${y - STEP + 44}>reads</text>`);
        const bw = Math.min(200, Math.max(72, binding.length * 7.2 + 24));
        parts.push(svg`<a href=${routes.binding(binding.split('.')[0])} class="rx-conn-binding"><rect x=${140 - bw / 2} y=${y - STEP + 51} width=${bw} height="24" rx="5"></rect><text x="140" y=${y - STEP + 67} text-anchor="middle">${binding}</text></a>`);
        height = y - STEP + 51 + 24 + 4;
    }
    const body = html`<svg class="rx-conn" viewBox=${`0 0 ${W} ${height}`} role="group" aria-label=${nodes.map(n => n.artifact.name).join(' → ') + (binding ? ', reads ' + binding : '')}>
        <defs><marker id="rx-conn-ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 1L9 5L0 9z" class="rx-conn-arrow"></path></marker></defs>
        ${parts}
    </svg>`;
    return card('Connections', body, {class: 'rx-card-tight', action: html`<a class="rx-link-quiet" href=${routes.graph({focus: a.id})}>Open graph</a>`});
}

function glyphAt(type, cx, cy) {
    if (type === 'ruleflow') return svg`<polygon points=${`${cx},${cy - 6} ${cx + 5.2},${cy - 3} ${cx + 5.2},${cy + 3} ${cx},${cy + 6} ${cx - 5.2},${cy + 3} ${cx - 5.2},${cy - 3}`} style="fill: var(--rx-flow)"></polygon>`;
    if (type === 'ruleset') return svg`<rect x=${cx - 6} y=${cy - 6} width="12" height="12" rx="3" style="fill: var(--rx-ruleset)"></rect>`;
    return svg`<circle cx=${cx} cy=${cy} r="5" style="fill: var(--rx-rule)"></circle>`;
}

/** "Source": where it is defined, its language, package and class. */
export function sourceCard(a, index) {
    const text = sourceText(a.source);
    const lang = scriptLanguage(a);
    const rows = [];
    if (text) rows.push(html`<span class="rx-k">Defined in</span><span class="rx-v" style="flex-wrap: nowrap"><span class="rx-mono rx-ellipsis" title=${text}>${text}</span>${copyButton(text, 'Copy source location')}</span>`);
    if (lang) rows.push(html`<span class="rx-k">Language</span><span class="rx-v">${lang.name} <span class="rx-mono rx-muted">${lang.code}</span></span>`);
    if (a.packageId) rows.push(html`<span class="rx-k">Package</span><span class="rx-v"><a class="rx-mono" href=${routes.package(a.packageId)}>${a.packageId}</a></span>`);
    if (a.className || (a.source && a.source.className && a.source.className !== text)) rows.push(html`<span class="rx-k">Class</span><span class="rx-v"><span class="rx-mono rx-ellipsis" title=${a.className || a.source.className}>${a.className || a.source.className}</span></span>`);
    if (a.source && a.source.methodName) rows.push(html`<span class="rx-k">Method</span><span class="rx-v"><span class="rx-mono">${a.source.methodName}()</span></span>`);
    if (!rows.length) rows.push(html`<span class="rx-k">Defined in</span><span class="rx-v rx-muted">Not recorded${a.source ? '' : ' (sources are switched off)'}</span>`);
    return card('Source', html`<div class="rx-kv">${rows}</div>`, {class: 'rx-card-tight'});
}

/** The card shown instead of a body when describing the artifact failed. */
export function undescribedBody(a, index) {
    const problem = (index.problemsByArtifact.get(a.id) || []).find(p => p.code === 'UNDESCRIBABLE');
    return html`<section class="rx-card rx-undescribed-card">
        <div class="rx-card-head"><span style="display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 600">${glyph(a.type, {size: 11, undescribed: true})}${a.name}</span><span class="rx-code-tag" style="padding-top: 0">UNDESCRIBABLE</span></div>
        <p class="rx-small" style="font-size: 12.5px; color: var(--rx-ink-2)">Reading this ${typeLabel(a.type).toLowerCase()}’s definition failed, so only its name and bean are shown. The rest of the application is unaffected.</p>
        ${problem ? html`<code class="rx-code">${problem.message}</code>` : nothing}
    </section>`;
}

function ordinal(n) {
    const s = ['th', 'st', 'nd', 'rd'];
    const v = n % 100;
    return n + (s[(v - 20) % 10] || s[v] || s[0]);
}

function clip(text, max) {
    return text.length > max ? text.slice(0, max - 1) + '…' : text;
}

export {artifactLink, plainText, isCompiled};

customElements.define('rx-artifact', RxArtifact);
