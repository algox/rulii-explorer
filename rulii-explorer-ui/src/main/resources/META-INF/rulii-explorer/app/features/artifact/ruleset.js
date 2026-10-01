import {html, nothing} from 'lit';
import {glyph, icon} from '../../components/icons.js';
import {breadcrumb, card, copyButton, copyLinkButton, graphButton, kindCaption, typeBadge} from '../../components/common.js';
import {plainTokens, rawCode, segmented, slotCaption} from '../../components/expression.js';
import {routes} from '../../routing/router.js';
import {isCompiled, kindShort, plural, shortType, sourceText, typeLabel} from '../../descriptor/format.js';
import {artifactSummary, validatorPhrase, validatedValue} from '../../descriptor/summaries.js';
import {sourceCard, usedByCard, undescribedBody, isRuleCompiled} from './artifact.js';

/** The rule set page (S-RuleSet): the members in order, when it runs, its inputs, who uses it. */
export function ruleSetPage(a, host) {
    const {index, exprView: view} = host.state;
    const s = a.ruleSet || {members: []};
    const undescribed = index.undescribed.has(a.id);
    const source = sourceText(a.source);
    const hooks = [['initializer', s.initializer], ['finalizer', s.finalizer], ['result extractor', s.resultExtractor], ['error handler', s.errorHandler]];
    const present = hooks.filter(h => h[1]).map(h => h[0]);
    const absent = hooks.filter(h => !h[1]).map(h => h[0]);
    return html`<div class="rx-page rx-page-split">
        <div class="rx-page-head">
            ${breadcrumb([{text: 'Rule sets', href: routes.overview()}, ...(a.packageId ? [{text: a.packageId, href: routes.package(a.packageId), mono: true}] : []), {text: a.name}])}
            <div class="rx-head-row">
                <div class="rx-page-head" style="gap: 8px">
                    <div class="rx-meta-row">${typeBadge('ruleset', {undescribed})}${kindCaption(a)}</div>
                    <div class="rx-title-row"><h1 class="rx-h1 rx-h1-md">${a.name}</h1>${a.registered !== false ? html`<span class="rx-bean">bean <span class="rx-mono" style="font-size: 12px; font-weight: 500; color: var(--rx-ink-2)">${a.id}</span></span>` : nothing}</div>
                    ${a.description ? html`<p class="rx-subtitle rx-subtitle-sm">${a.description}</p>` : html`<p class="rx-subtitle rx-subtitle-sm rx-muted">No description.</p>`}
                    ${undescribed ? nothing : html`<p class="rx-lede">${artifactSummary(a, index)}</p>`}
                </div>
                <div class="rx-actions" style="padding-top: 22px">${graphButton('Open in graph')}${copyLinkButton('Copy link to this rule set')}</div>
            </div>
            <div class="rx-facts">
                ${a.packageId ? html`<span>Package <a class="rx-mono" href=${routes.package(a.packageId)}>${a.packageId}</a></span>` : nothing}
                ${source ? html`<span>Defined in <span class="rx-mono">${source}</span>${copyButton(source, 'Copy source location')}</span>` : nothing}
                <span>Validating <strong>${s.validating ? 'yes' : 'no'}</strong></span>
            </div>
        </div>
        <div class="rx-page-body">
            ${undescribed ? undescribedBody(a, index) : nothing}
            <div class="rx-detail-grid rx-detail-grid-wide">
                <div class="rx-col">
                    ${card('Rules, in order', membersList(s, index), {class: 'rx-card-tight', titleClass: 'rx-h2-lg', action: html`<span class="rx-h2-count">${plural(s.members.length, 'rule')} · run top to bottom</span>`, ariaLabel: 'Members'})}
                    <div class="rx-grid-2 rx-grid-2-uneven">
                        ${card('When it runs', whenItRuns(s, view), {titleClass: 'rx-h2-md', action: segmented(view, v => host.store.setExprView(v))})}
                        ${card('Inputs', html`
                            ${a.parameters.length ? html`<div class="rx-inputs">${a.parameters.map(p => html`<div><span class="rx-mono" title=${p.description || p.type || ''}>${p.name}<span>: ${shortType(p.type)}</span></span><span class="rx-req">${p.required === false ? 'optional' : 'required'}</span></div>`)}</div>` : html`<p class="rx-small" style="font-size: 12.5px">No declared inputs. The rules read whatever the bindings hold.</p>`}
                            <p class="rx-small" style="font-size: 12px">${present.length ? 'Has ' + joinAnd(present) + (absent.length ? '; no ' + joinOr(absent) + '.' : '.') : 'No initializer, finalizer, result extractor or error handler.'}</p>`, {class: 'rx-card-tight', titleClass: 'rx-h2-md'})}
                    </div>
                </div>
                <aside class="rx-col" aria-label="Connections">
                    ${usedByCard(a, index, {overline: true})}
                    ${sourceCard(a, index)}
                </aside>
            </div>
        </div>
    </div>`;
}

function membersList(s, index) {
    if (!s.members.length) return html`<p class="rx-small" style="font-size: 12.5px; padding-bottom: 6px">This rule set has no rules.</p>`;
    return html`<ol class="rx-members">${s.members.map((id, i) => {
        const m = index.byId.get(id);
        if (!m) return html`<li><div class="rx-member"><span class="rx-idx">${i + 1}</span>${glyph('rule', {size: 10, undescribed: true})}<span class="rx-name" style="color: var(--rx-error)">${id}</span><span class="rx-what rx-muted">not in the registry</span><span class="rx-kind-cell">missing</span></div></li>`;
        return html`<li><a class="rx-member" href=${routes.artifact(m)} data-hover=${m.id}>
            <span class="rx-idx">${i + 1}</span>
            ${glyph(m.type, {size: 10, undescribed: index.undescribed.has(m.id)})}
            <span class="rx-name">${m.name}</span>
            ${memberWhat(m, index)}
            <span class="rx-kind-cell">${index.undescribed.has(m.id) ? 'not described' : kindShort(m)}</span>
        </a></li>`;
    })}</ol>`;
}

function memberWhat(m, index) {
    if (index.undescribed.has(m.id)) return html`<span class="rx-what rx-muted">could not be described</span>`;
    if (m.validation) return html`<span class="rx-what">${validatedValue(m)} is ${validatorPhrase(m.validation.validator)}</span>`;
    if (m.type === 'rule' && isRuleCompiled(m)) return html`<span class="rx-what rx-muted">${icon('lock', {size: 12})}compiled code · parameters shown on the rule</span>`;
    if (m.rule && m.rule.given && !isCompiled(m.rule.given)) return html`<span class="rx-what">${plainTokens(m.rule.given)}</span>`;
    if (m.type !== 'rule') return html`<span class="rx-what rx-muted">${typeLabel(m.type).toLowerCase()}</span>`;
    return html`<span class="rx-what rx-muted">${m.description || 'always passes'}</span>`;
}

function whenItRuns(s, view) {
    const rows = [['Runs only if', 'pre-condition', s.preCondition], ['Stops when', 'stop-condition', s.stopCondition],
        ['First', 'initializer', s.initializer], ['Last', 'finalizer', s.finalizer], ['Result', 'result-extractor', s.resultExtractor], ['On error', 'error-handler', s.errorHandler]].filter(r => r[2]);
    if (!rows.length) return html`<p class="rx-small" style="font-size: 13px">Runs every rule, every time. There is no pre-condition and no stop condition.</p>`;
    if (view === 'raw') return html`<div class="rx-raw-grid rx-raw-grid-92">${rows.map(([, slot, e]) => html`<span class="rx-k">${slotCaption(slot, e)}</span><span class="rx-v">${rawCode(e)}</span>`)}</div>`;
    return html`<div class="rx-plain-grid rx-plain-grid-92">${rows.map(([label, , e]) => html`<span class="rx-k">${label}</span><span class="rx-v">${plainTokens(e)}</span>`)}</div>`;
}

function joinAnd(list) {
    return list.length <= 1 ? list.join('') : list.slice(0, -1).join(', ') + ' and ' + list[list.length - 1];
}

function joinOr(list) {
    return list.length <= 1 ? list.join('') : list.slice(0, -1).join(', ') + ' or ' + list[list.length - 1];
}
