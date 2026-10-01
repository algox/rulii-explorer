import {html, nothing} from 'lit';
import {glyph, icon, severityIcon} from '../../components/icons.js';
import {typeBadge} from '../../components/common.js';
import {plainTokens, rawCode, segmented, slotCaption} from '../../components/expression.js';
import {routes} from '../../routing/router.js';
import {kindLabel, plural, sourceText, typeLabel, isCompiled} from '../../descriptor/format.js';
import {artifactSummary} from '../../descriptor/summaries.js';

/**
 * The detail panel of a selected graph node (S-GraphFocus aside): what it is, who runs it, what
 * it contains, its conditions, the problems in its neighbourhood, and where to go next.
 *
 * @param {object} node   a graph-model node
 * @param {{nodes: Map, edges: Array}} graph
 * @param {object} index
 * @param {object} host   the page element (store, requestUpdate)
 * @param {{onClose: () => void, onFocus?: (id: string) => void, focusId?: string}} actions
 */
export function graphAside(node, graph, index, host, actions) {
    if (!node) return nothing;
    if (node.type === 'missing') return missingAside(node, graph, index, actions);
    const a = node.artifact;
    const view = host.state.exprView;
    const runBy = graph.edges.filter(e => e.to === a.id && e.type === 'runs').map(e => index.byId.get(e.from)).filter(Boolean);
    const containedBy = graph.edges.filter(e => e.to === a.id && e.type === 'contains').map(e => index.byId.get(e.from)).filter(Boolean);
    const runs = graph.edges.filter(e => e.from === a.id && e.type === 'runs');
    const near = new Set([a.id, ...graph.edges.filter(e => e.from === a.id || e.to === a.id).flatMap(e => [e.from, e.to])]);
    const problems = (index.descriptor.problems || []).filter(p => near.has(p.artifact) && p.code !== 'UNUSED_RULE');
    const flowchart = flowchartLink(a, index);
    return html`<aside class="rx-gaside" aria-label="Selected artifact">
        <div class="rx-gaside-head"><span class="rx-overline">Selected · ${typeLabel(a.type)}</span><button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label="Close details" @click=${actions.onClose}>${icon('close', {size: 14})}</button></div>
        <div class="rx-gaside-title">
            <div class="rx-meta-row">${typeBadge(a.type, {undescribed: index.undescribed.has(a.id)})}<span class="rx-kind" style="font-size: 12px">${kindLabel(a)}</span></div>
            <h2>${a.name}</h2>
            ${a.description ? html`<p>${a.description}</p>` : html`<p class="rx-muted">No description.</p>`}
        </div>
        <div class="rx-summary rx-summary-sm">${artifactSummary(a, index)}</div>
        <div class="rx-kv rx-kv-84">
            ${runBy.length ? html`<span class="rx-k">Run by</span><span class="rx-v">${runBy.map(f => html`<a class="rx-art-link rx-art-link-${f.type}" style="font-family: var(--rx-font-ui); font-size: 12.5px; color: var(--rx-ink)" href=${routes.graph({focus: actions.focusId || a.id, selected: f.id})}>${glyph(f.type, {size: 11})}${f.name}</a>`)}</span>` : nothing}
            ${containedBy.length ? html`<span class="rx-k">Member of</span><span class="rx-v">${containedBy.map(s => html`<a class="rx-art-link rx-art-link-${s.type}" style="font-family: var(--rx-font-ui); font-size: 12.5px; color: var(--rx-ink)" href=${routes.graph({focus: actions.focusId || a.id, selected: s.id})}>${glyph(s.type, {size: 11})}${s.name}</a>`)}</span>` : nothing}
            ${a.type === 'ruleset' ? html`<span class="rx-k">Contains</span><span class="rx-v" style="color: var(--rx-ink)">${plural(a.ruleSet.members.length, 'rule')}${a.ruleSet.members.every(id => graph.nodes.has(id)) ? ', all shown' : ', ' + a.ruleSet.members.filter(id => graph.nodes.has(id)).length + ' shown'}</span>` : nothing}
            ${a.type === 'ruleflow' ? html`<span class="rx-k">Runs</span><span class="rx-v" style="color: var(--rx-ink)">${plural(runs.length, 'artifact')}${runs.some(e => e.resolution === 'unresolved') ? ', one missing' : ''}</span>` : nothing}
            ${a.packageId ? html`<span class="rx-k">Package</span><span class="rx-v"><a class="rx-mono" href=${routes.package(a.packageId)}>${a.packageId}</a></span>` : nothing}
            ${sourceText(a.source) ? html`<span class="rx-k">Defined in</span><span class="rx-v"><span class="rx-mono rx-ellipsis" title=${sourceText(a.source)}>${sourceText(a.source)}</span></span>` : nothing}
        </div>
        ${conditionsCard(a, view, host)}
        ${problems.length ? html`<div class="rx-gaside-section">
            <span class="rx-gaside-h">In this neighbourhood</span>
            ${problems.slice(0, 4).map(p => { const pa = index.byId.get(p.artifact); return html`<div class="rx-problem-row"><span class=${'rx-sev-icon rx-sev-icon-sm rx-sev-' + p.severity}>${severityIcon(p.severity, 11)}</span><p>${pa ? html`<a href=${routes.artifact(pa)} data-hover=${pa.id}>${pa.name}</a> ` : nothing}${lowerFirst(p.message)}</p></div>`; })}
        </div>` : nothing}
        <div class="rx-gaside-actions">
            <a class="rx-btn rx-btn-primary" href=${routes.artifact(a)}>Open ${typeLabel(a.type).toLowerCase()}${icon('arrowRight', {size: 14})}</a>
            ${flowchart ? html`<a class="rx-btn rx-btn-secondary" href=${flowchart}>Show in flowchart</a>` : actions.onFocus && actions.focusId !== a.id ? html`<button type="button" class="rx-btn rx-btn-secondary" @click=${() => actions.onFocus(a.id)}>Focus here</button>` : nothing}
        </div>
    </aside>`;
}

function missingAside(node, graph, index, actions) {
    const referrers = graph.edges.filter(e => e.to === node.id).map(e => ({flow: index.byId.get(e.from), path: e.path})).filter(r => r.flow);
    return html`<aside class="rx-gaside" aria-label="Selected target">
        <div class="rx-gaside-head"><span class="rx-overline">Selected · Missing target</span><button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label="Close details" @click=${actions.onClose}>${icon('close', {size: 14})}</button></div>
        <div class="rx-gaside-title">
            <div class="rx-meta-row"><span class="rx-type-badge" style="color: var(--rx-error); background: var(--rx-error-soft); border-color: var(--rx-error)">Not registered</span></div>
            <h2 class="rx-mono" style="font-size: 20px; font-weight: 600">${node.name}</h2>
            <p>Nothing with this name is in the registry, so the step that runs it fails when it is reached.</p>
        </div>
        <div class="rx-kv rx-kv-84">
            ${referrers.map(r => html`<span class="rx-k">Run by</span><span class="rx-v"><a class="rx-art-link rx-art-link-ruleflow" style="font-family: var(--rx-font-ui); font-size: 12.5px; color: var(--rx-ink)" href=${routes.artifact(r.flow, {view: 'flowchart', step: r.path})}>${glyph('ruleflow', {size: 11})}${r.flow.name}</a></span>`)}
        </div>
        <div class="rx-gaside-actions">
            <a class="rx-btn rx-btn-primary" href=${routes.problems({severity: 'error'})}>See the problem${icon('arrowRight', {size: 14})}</a>
        </div>
    </aside>`;
}

/** The conditions of a rule or rule set in the current Plain / Raw view. */
export function conditionsCard(a, view, host, options = {}) {
    const rows = [];
    if (a.rule) {
        if (a.rule.preCondition) rows.push(['Runs only if', 'pre-condition', a.rule.preCondition]);
        if (a.rule.given) rows.push(['Passes when', 'given', a.rule.given]);
        a.rule.then.forEach((e, i) => rows.push([a.rule.then.length > 1 ? 'Then ' + (i + 1) : 'Then', 'then', e]));
        if (a.rule.otherwise) rows.push(['Otherwise', 'otherwise', a.rule.otherwise]);
    }
    if (a.validation && a.validation.valueSource && a.validation.valueSource.expression) rows.push(['Checks', 'value', a.validation.valueSource.expression]);
    if (a.ruleSet) {
        if (a.ruleSet.preCondition) rows.push(['Runs only if', 'pre-condition', a.ruleSet.preCondition]);
        if (a.ruleSet.stopCondition) rows.push(['Stops when', 'stop-condition', a.ruleSet.stopCondition]);
    }
    if (a.ruleFlow && a.ruleFlow.returning) rows.push(['Returns', 'returning', a.ruleFlow.returning]);
    if (!rows.length) return nothing;
    return html`<div class="rx-card rx-card-tight rx-gaside-card">
        <div class="rx-card-head"><span class="rx-gaside-h">${options.title || 'Conditions'}</span>${segmented(view, v => host.store.setExprView(v))}</div>
        ${view === 'raw'
            ? html`<div class="rx-raw-grid rx-raw-grid-92">${rows.map(([, slot, e]) => html`<span class="rx-k">${slotCaption(slot, e)}</span><span class="rx-v">${rawCode(e)}</span>`)}</div>`
            : html`<div class="rx-plain-grid rx-plain-grid-92">${rows.map(([label, , e]) => html`<span class="rx-k">${label}</span><span class="rx-v">${isCompiled(e) ? html`<span class="rx-muted">${e.signature || 'compiled code'}</span>` : plainTokens(e)}</span>`)}</div>`}
    </div>`;
}

/** Where to show an artifact in a flowchart: its own, or the first flow that runs it, with the step selected. */
export function flowchartLink(a, index) {
    if (a.type === 'ruleflow') return routes.artifact(a, {view: 'flowchart'});
    const ref = (index.usedBy.get(a.id) || []).find(r => r.type === 'runs');
    if (ref) { const flow = index.byId.get(ref.from); if (flow) return routes.artifact(flow, {view: 'flowchart', step: ref.path}); }
    const contains = (index.usedBy.get(a.id) || []).find(r => r.type === 'contains');
    if (contains) {
        const set = index.byId.get(contains.from);
        const setRef = set && (index.usedBy.get(set.id) || []).find(r => r.type === 'runs');
        if (setRef) { const flow = index.byId.get(setRef.from); if (flow) return routes.artifact(flow, {view: 'flowchart', step: setRef.path}); }
    }
    return null;
}

function lowerFirst(text) {
    return text ? text.charAt(0).toLowerCase() + text.slice(1) : text;
}
