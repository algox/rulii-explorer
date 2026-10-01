import {html, nothing} from 'lit';
import {glyph, icon} from '../../components/icons.js';
import {typeBadge} from '../../components/common.js';
import {inlineExpression, plainTokens, rawCode, segmented, slotCaption} from '../../components/expression.js';
import {routes, navigate} from '../../routing/router.js';
import {durationText, kindLabel, kindShort, shortType, sourceText, targetName, typeLabel} from '../../descriptor/format.js';
import {artifactSummary, commandParts} from '../../descriptor/summaries.js';
import {conditionsCard} from '../graph/graph-aside.js';

const TAGS = {bind: 'Bind', run: 'Run', apply: 'Apply', execute: 'Execute', 'async-run': 'Async run', await: 'Await', when: 'When', 'for-each': 'For each', scope: 'Scope', exit: 'Exit', custom: 'Custom'};

/**
 * The selected-step panel shared by the flowchart and the outline (Lavender-Flow aside): for a
 * run step, what it runs; for anything else, the step itself with its expression.
 *
 * @param {object} flow      the rule flow artifact
 * @param {string} path      the selected step: a command path, "handler:<path>" or "global"
 */
export function stepAside(flow, path, index, host) {
    const resolved = resolveStep(flow, path, index);
    if (!resolved) return nothing;
    const view = host.state.exprView;
    const otherView = host.state.route.query.view === 'outline' ? 'flowchart' : 'outline';
    const close = () => navigate(routes.artifact(flow, {...host.state.route.query, step: ''}));
    const switchLink = html`<a class="rx-btn rx-btn-secondary" href=${routes.artifact(flow, {...host.state.route.query, view: otherView, step: path})}>Show in ${otherView}</a>`;

    if (resolved.kind === 'handler') {
        const h = resolved.handler;
        return html`<aside class="rx-gaside" aria-label="Selected step">
            <div class="rx-gaside-head"><span class="rx-overline">Selected · ${resolved.global ? 'Global handler' : 'Exception handler'}</span>${closeButton(close)}</div>
            <div class="rx-gaside-title">
                <div class="rx-meta-row"><span class="rx-handler-chip">on ${shortType(h.exceptionType || 'Exception')}</span>${resolved.number ? html`<span class="rx-kind">step ${resolved.number}</span>` : nothing}</div>
                <h2>${resolved.global ? 'Whenever a step fails' : 'If step ' + resolved.number + ' fails'}</h2>
                <p>${(h.body || []).length ? 'These steps run instead, then the flow continues.' : 'The failure is handled and the flow continues.'}</p>
            </div>
            ${(h.body || []).length ? html`<div class="rx-card rx-card-tight rx-gaside-card"><div class="rx-card-head"><span class="rx-gaside-h">Then</span>${segmented(view, v => host.store.setExprView(v))}</div>
                <ol class="rx-gaside-steps">${h.body.map((c, i) => { const {parts, caption} = commandParts(c, index, flow.id); return html`<li><span class="rx-step-num">${i + 1}</span><span class="rx-step-text" style="white-space: normal">${renderParts(parts, {index, view})}</span><span class="rx-step-caption">${caption}</span></li>`; })}</ol></div>` : nothing}
            <div class="rx-gaside-actions">${switchLink}</div>
        </aside>`;
    }

    const {command, number} = resolved;
    const t = command.target;
    const target = t && t.kind === 'instance' ? index.byId.get(t.id) : null;
    if (target) return targetAside(flow, command, number, target, index, host, close, switchLink);

    const {parts, caption} = commandParts(command, index, flow.id);
    const expression = command.condition || command.expression || command.source;
    const unresolved = t && t.resolution === 'unresolved';
    return html`<aside class="rx-gaside" aria-label="Selected step">
        <div class="rx-gaside-head"><span class="rx-overline">Selected step · ${TAGS[command.type] || command.type}</span>${closeButton(close)}</div>
        <div class="rx-gaside-title">
            <div class="rx-meta-row">${unresolved ? html`<span class="rx-type-badge" style="color: var(--rx-error); background: var(--rx-error-soft); border-color: var(--rx-error)">Not registered</span>` : html`<span class="rx-step-tag">${(TAGS[command.type] || command.type).toUpperCase()}</span>`}<span class="rx-kind">step ${number}${caption ? ' · ' + caption : ''}</span></div>
            <h2 style="font-size: 20px; line-height: 1.3; font-family: var(--rx-font-ui); font-weight: 600"><span class="rx-step-text" style="white-space: normal; font-size: 15px">${renderParts(parts, {index, view: 'plain'})}</span></h2>
            ${unresolved ? html`<p>Nothing named <span class="rx-mono">${targetName(t, index)}</span> is in the registry, so the flow stops with an error when it reaches this step.</p>` : nothing}
        </div>
        ${expression ? html`<div class="rx-card rx-card-tight rx-gaside-card">
            <div class="rx-card-head"><span class="rx-gaside-h">${command.type === 'when' ? 'Condition' : command.type === 'for-each' ? 'Items' : 'Expression'}</span>${segmented(view, v => host.store.setExprView(v))}</div>
            ${view === 'raw' ? html`<div class="rx-raw-grid rx-raw-grid-92"><span class="rx-k">${slotCaption(command.type, expression)}</span><span class="rx-v">${rawCode(expression)}</span></div>` : html`<div class="rx-plain" style="font-size: 13.5px">${plainTokens(expression)}</div>`}
        </div>` : nothing}
        ${command.type === 'bind' && command.bind && command.bind.names && command.bind.names.length ? html`<div class="rx-card rx-card-tight rx-gaside-card"><span class="rx-gaside-h">Makes available</span>
            <div class="rx-inputs">${command.bind.names.map(n => html`<div><span class="rx-mono">${n.name}${n.type ? html`<span>: ${shortType(n.type)}</span>` : nothing}</span>${n.expression ? html`<span class="rx-small">${inlineExpression(n.expression, view)}</span>` : nothing}</div>`)}</div></div>` : nothing}
        ${command.type === 'await' ? html`<div class="rx-kv rx-kv-84"><span class="rx-k">Waits for</span><span class="rx-v">${(command.names || []).map(n => html`<span class="rx-chip">${n}</span>`)}</span>${command.timeout ? html`<span class="rx-k">Timeout</span><span class="rx-v">${durationText(command.timeout)}</span>` : nothing}</div>` : nothing}
        ${command.handler ? html`<div class="rx-kv rx-kv-84"><span class="rx-k">On failure</span><span class="rx-v"><a class="rx-handler-chip" href=${routes.artifact(flow, {...host.state.route.query, step: 'handler:' + resolved.path})}>on ${shortType(command.handler.exceptionType || 'Exception')}</a></span></div>` : nothing}
        <div class="rx-gaside-actions">${unresolved ? html`<a class="rx-btn rx-btn-primary" href=${routes.problems({severity: 'error'})}>See the problem${icon('arrowRight', {size: 14})}</a>` : nothing}${switchLink}</div>
    </aside>`;
}

function targetAside(flow, command, number, target, index, host, close, switchLink) {
    const view = host.state.exprView;
    const usedBy = (index.usedBy.get(target.id) || []).filter(r => r.type === 'runs').map(r => index.byId.get(r.from)).filter(Boolean);
    return html`<aside class="rx-gaside" aria-label="Selected step">
        <div class="rx-gaside-head"><span class="rx-overline">Selected step · ${command.type === 'async-run' ? 'Async run' : 'Run'}</span>${closeButton(close)}</div>
        <div class="rx-gaside-title">
            <div class="rx-meta-row">${typeBadge(target.type, {undescribed: index.undescribed.has(target.id)})}<span class="rx-kind" style="font-size: 12px">${kindLabel(target)}</span></div>
            <h2>${target.name}</h2>
            ${target.description ? html`<p>${target.description}</p>` : html`<p class="rx-muted">No description.</p>`}
        </div>
        <div class="rx-summary rx-summary-sm">${artifactSummary(target, index)}</div>
        <div class="rx-kv rx-kv-84">
            <span class="rx-k">Step</span><span class="rx-v">${number}${command.type === 'async-run' ? ' · in the background' : ''}</span>
            ${command.as ? html`<span class="rx-k">Runs as</span><span class="rx-v"><span class="rx-chip">${command.as}</span></span>` : nothing}
            ${usedBy.length ? html`<span class="rx-k">Used by</span><span class="rx-v">${usedBy.map(f => html`<a class="rx-art-link rx-art-link-${f.type}" style="font-family: var(--rx-font-ui); font-size: 12.5px; color: var(--rx-ink)" href=${routes.artifact(f)} data-hover=${f.id}>${glyph(f.type, {size: 11})}${f.name}</a>`)}</span>` : nothing}
            ${sourceText(target.source) ? html`<span class="rx-k">Defined in</span><span class="rx-v"><span class="rx-mono rx-ellipsis" title=${sourceText(target.source)}>${sourceText(target.source)}</span></span>` : nothing}
        </div>
        ${conditionsCard(target, view, host)}
        ${target.type === 'ruleset' ? html`<div class="rx-gaside-section">
            <div class="rx-card-head"><span class="rx-gaside-h">Rules, in order</span><span class="rx-h2-count">${target.ruleSet.members.length}</span></div>
            <ol class="rx-gaside-members">${target.ruleSet.members.map((id, i) => { const m = index.byId.get(id); return m ? html`<li><a href=${routes.artifact(m)} data-hover=${m.id}><span class="rx-step-num">${i + 1}</span>${glyph(m.type, {size: 9, undescribed: index.undescribed.has(m.id)})}<span class="rx-name">${m.name}</span><span class="rx-small">${kindShort(m)}</span></a></li>` : html`<li><span><span class="rx-step-num">${i + 1}</span><span class="rx-name" style="color: var(--rx-error)">${id}</span><span class="rx-small">missing</span></span></li>`; })}</ol>
        </div>` : nothing}
        <div class="rx-gaside-actions">
            <a class="rx-btn rx-btn-primary" href=${routes.artifact(target)}>Open ${typeLabel(target.type).toLowerCase()}${icon('arrowRight', {size: 14})}</a>
            ${switchLink}
        </div>
    </aside>`;
}

function closeButton(close) {
    return html`<button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label="Close details" @click=${close}>${icon('close', {size: 14})}</button>`;
}

/** Finds what a step path points at. */
export function resolveStep(flow, path, index) {
    if (!path) return null;
    if (path === 'global') return flow.ruleFlow.globalHandler ? {kind: 'handler', handler: flow.ruleFlow.globalHandler, global: true} : null;
    const map = index.commandsByPath.get(flow.id);
    if (!map) return null;
    if (path.startsWith('handler:')) {
        const entry = map.get(path.slice('handler:'.length));
        return entry && entry.command.handler ? {kind: 'handler', handler: entry.command.handler, number: entry.number, path: path.slice('handler:'.length)} : null;
    }
    const entry = map.get(path);
    return entry ? {kind: 'command', command: entry.command, number: entry.number, path} : null;
}

/** Renders the typed parts of a command sentence (summaries.commandParts). */
export function renderParts(parts, ctx) {
    return parts.map(p => {
        switch (p.t) {
            case 'chip': return html`<span class="rx-chip">${p.text}</span>`;
            case 'mono': return html`<span class="rx-mono">${p.text}</span>`;
            case 'strong': return html`<span class="rx-lit">${p.text}</span>`;
            case 'expr': return inlineExpression(p.expression, ctx.view);
            case 'target': {
                const t = p.target || {};
                const a = t.kind === 'instance' ? ctx.index.byId.get(t.id) : null;
                if (a) return html`<a class=${'rx-target rx-target-' + a.type} href=${routes.artifact(a)} data-hover=${a.id}>${glyph(a.type, {size: 10, undescribed: ctx.index.undescribed.has(a.id)})}${a.name}</a>`;
                const name = targetName(t, ctx.index);
                const byName = ctx.index.byType.rule.concat(ctx.index.byType.ruleset, ctx.index.byType.ruleflow).find(x => x.id === t.name);
                if (byName && t.resolution !== 'unresolved') return html`<a class=${'rx-target rx-target-' + byName.type} href=${routes.artifact(byName)} data-hover=${byName.id}>${glyph(byName.type, {size: 10})}${byName.name}</a>`;
                return html`<span class="rx-target rx-target-missing" title=${t.resolution === 'unresolved' ? 'Nothing with this name is registered' : 'Resolved at run time'}>‘${name}’</span>`;
            }
            default: return html`<span>${p.text}</span>`;
        }
    });
}
