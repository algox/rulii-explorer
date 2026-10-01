import {html, nothing} from 'lit';
import {glyph, icon} from '../../components/icons.js';
import {breadcrumb, copyLinkButton} from '../../components/common.js';
import {inlineExpression, segmented} from '../../components/expression.js';
import {routes} from '../../routing/router.js';
import {commandChildren, plural, shortType, sourceText, targetName, walkCommands} from '../../descriptor/format.js';
import {commandParts} from '../../descriptor/summaries.js';
import {undescribedBody} from './artifact.js';

const TAGS = {bind: 'BIND', run: 'RUN', apply: 'APPLY', execute: 'EXECUTE', 'async-run': 'ASYNC', await: 'AWAIT', when: 'WHEN', 'for-each': 'FOR EACH', scope: 'SCOPE', exit: 'EXIT', custom: 'CUSTOM'};

/** Collapsed step paths per flow, kept while the page is open. */
const collapsed = new Map();

/** The rule flow page: header with tabs, then the outline (S-FlowOutline, FR-55). The flowchart tab arrives with the graphs. */
export function ruleFlowPage(a, host) {
    const {index, exprView: view, route} = host.state;
    const f = a.ruleFlow || {commands: []};
    const undescribed = index.undescribed.has(a.id);
    let steps = 0;
    walkCommands(f.commands, () => steps++);
    const source = sourceText(a.source);
    const state = collapsed.get(a.id) || new Set();
    const toggle = (path) => { const s = new Set(collapsed.get(a.id) || []); if (s.has(path)) s.delete(path); else s.add(path); collapsed.set(a.id, s); host.requestUpdate(); };
    const setAll = (open) => { const s = new Set(); if (!open) walkCommands(f.commands, (c, path) => { if (commandChildren(c).length) s.add(path); }); collapsed.set(a.id, s); host.requestUpdate(); };
    const selected = route.query.step || null;
    const problems = index.problemsByArtifact.get(a.id) || [];
    const problemAt = new Map(problems.filter(p => p.path).map(p => [p.path, p]));
    requestAnimationFrame(() => { const el = host.querySelector('.rx-step[aria-selected="true"]'); if (el && selected && !el.dataset.scrolled) { el.dataset.scrolled = '1'; el.scrollIntoView({block: 'center'}); } });

    return html`<div class="rx-page rx-page-split">
        <div class="rx-flow-head">
            ${breadcrumb([{text: 'Rule flows', href: routes.overview()}, ...(a.packageId ? [{text: a.packageId, href: routes.package(a.packageId), mono: true}] : []), {text: a.name}])}
            <div class="rx-head-row" style="align-items: center">
                <div class="rx-flow-title">${glyph('ruleflow', {size: 22, undescribed})}<h1 class="rx-h1 rx-h1-md">${a.name}</h1>${a.registered !== false && a.id !== a.name ? html`<span class="rx-bean">bean <span class="rx-chip">${a.id}</span></span>` : nothing}</div>
                <div class="rx-actions" style="padding-top: 0">${copyLinkButton('Copy link to this rule flow')}</div>
            </div>
            <div class="rx-flow-desc">
                <p>${a.description || html`<span class="rx-muted">No description.</span>`}</p>
                <span>${plural(steps, 'step')}${source ? html` · <span class="rx-mono">${source}</span>` : nothing}</span>
            </div>
            <div class="rx-flow-bar">
                <div class="rx-tabs" role="tablist" aria-label="Views">
                    <button type="button" role="tab" class="rx-tab" aria-selected="false" aria-disabled="true" title="The flowchart arrives with the next milestone">Flowchart</button>
                    <button type="button" role="tab" class="rx-tab" aria-selected="true">Outline</button>
                    <button type="button" role="tab" class="rx-tab" aria-selected="false" aria-disabled="true" title="The graph arrives with the next milestone">Graph</button>
                </div>
                <div class="rx-flow-tools">
                    <button type="button" class="rx-btn rx-btn-ghost rx-btn-sm" @click=${() => setAll(true)}>${icon('expand', {size: 13})}Expand all</button>
                    <button type="button" class="rx-btn rx-btn-ghost rx-btn-sm" @click=${() => setAll(false)}>${icon('collapse', {size: 13})}Collapse all</button>
                    ${segmented(view, v => host.store.setExprView(v))}
                </div>
            </div>
        </div>
        <div class="rx-flow-body">
            ${undescribed ? undescribedBody(a, index) : nothing}
            <section class="rx-card rx-outline-card" aria-label=${'Steps of ' + a.name}>
                <ol class="rx-outline" role="tree" aria-label=${a.name + ' outline'}>
                    ${startRow(a, f)}
                    ${f.commands.map((c, i) => stepRows(c, 'commands[' + i + ']', String(i + 1), null, {index, view, state, toggle, selected, problemAt, flowId: a.id}))}
                    ${f.finalizer ? row({number: '', tag: 'FINALLY', tagClass: 'rx-step-tag-flow', text: html`<span>Then always</span>${inlineExpression(f.finalizer, view)}`, caption: 'finalizer'}) : nothing}
                    ${f.returning ? row({number: '', tag: 'RETURN', tagClass: 'rx-step-tag-flow', text: html`<span>Return</span>${inlineExpression(f.returning, view)}`, caption: f.resultType && f.resultType !== 'java.lang.Object' ? 'flow result · ' + shortType(f.resultType) : 'flow result'}) : nothing}
                    ${!f.commands.length ? html`<li class="rx-step"><span></span><span></span><span></span><span class="rx-step-text rx-muted">This flow has no steps.</span><span></span></li>` : nothing}
                </ol>
                ${f.globalHandler ? html`<div class="rx-outline-foot">
                    <span class="rx-overline">Whenever a step fails</span>
                    ${(f.globalHandler.body || []).map((c, i) => stepRows(c, 'globalHandler.body[' + i + ']', '', null, {index, view, state, toggle, selected, problemAt, flowId: a.id, branch: 'on ' + shortType(f.globalHandler.exceptionType || 'Exception'), handler: true, caption: 'global handler'}))}
                    ${!(f.globalHandler.body || []).length ? row({number: '', tag: 'HANDLER', text: html`<span class="rx-handler-chip">on ${shortType(f.globalHandler.exceptionType || 'Exception')}</span><span class="rx-muted">handled without further steps</span>`, caption: 'global handler', child: true}) : nothing}
                </div>` : nothing}
            </section>
        </div>
    </div>`;
}

function startRow(a, f) {
    const params = a.parameters || [];
    const required = params.filter(p => p.required !== false).length;
    const text = params.length
        ? html`<span>Starts with</span>${params.map((p, i) => html`${i ? html`<span>${i === params.length - 1 ? 'and' : ','}</span>` : nothing}<span class="rx-chip" title=${p.description || p.type || ''}>${p.name}: ${shortType(p.type)}</span>`)}`
        : html`<span>Starts with ${f.context ? html`the bindings from <span class="rx-mono">${f.context}</span>` : 'whatever the caller binds'}</span>`;
    const caption = params.length ? (required === params.length ? (params.length === 1 ? 'required' : params.length === 2 ? 'both required' : 'all required') : required + ' of ' + params.length + ' required') : '';
    return row({number: '', tag: 'START', tagClass: 'rx-step-tag-flow', text, caption, color: 'var(--rx-ink-2)'});
}

function stepRows(command, path, number, parent, ctx) {
    const children = commandChildren(command);
    const open = !ctx.state.has(path);
    const {parts, caption} = commandParts(command, ctx.index, ctx.flowId);
    const problem = ctx.problemAt.get(path);
    const target = command.target && command.target.kind === 'instance' ? ctx.index.byId.get(command.target.id) : null;
    const tagClass = command.type === 'async-run' ? 'rx-step-tag-rule' : (command.type === 'run' || command.type === 'apply') && target ? 'rx-step-tag-' + target.type : '';
    const async = command.type === 'async-run' || command.type === 'await';
    const text = html`${ctx.branch ? (ctx.handler ? html`<span class="rx-handler-chip">${ctx.branch}</span>` : html`<span class="rx-branch">${ctx.branch}</span>`) : nothing}${parts.map(p => part(p, ctx))}`;
    const captionWithProblem = problem ? html`<span class=${'rx-dot rx-dot-' + problem.severity} title=${problem.message}></span>${caption}` : (command.type === 'await' ? html`<strong>joins here</strong>${caption.replace(/^joins here/, '')}` : caption);
    const main = row({number, tag: TAGS[command.type] || command.type.toUpperCase(), tagClass, text, caption: ctx.caption || captionWithProblem, async, child: !!parent, selected: ctx.selected === path, path,
        toggle: children.length ? html`<button type="button" class="rx-step-toggle" aria-label=${(open ? 'Collapse' : 'Expand') + ' step ' + number} aria-expanded=${open} @click=${() => ctx.toggle(path)}>${icon(open ? 'chevronDown' : 'chevronRight', {size: 12, width: 2.4})}</button>` : null});
    if (!children.length || !open) return main;
    let k = 0;
    return html`${main}${children.map(group => {
        const start = k;
        k += group.commands.length;
        return html`<li class=${'rx-step-children' + (async ? ' rx-step-children-async' : '')} role="group">
        ${group.commands.map((c, i) => stepRows(c, path + '.' + group.slot + '[' + i + ']', number + '.' + (start + i + 1), command, {...ctx, branch: group.branch, handler: group.slot === 'handler.body', caption: group.slot === 'handler.body' ? 'exception handler' : null}))}
    </li>`;
    })}`;
}

function part(p, ctx) {
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
}

function row(o) {
    const cls = 'rx-step' + (o.child ? ' rx-step-child' : '') + (o.async ? ' rx-step-async' : '');
    return html`<li class=${cls} role="treeitem" aria-selected=${o.selected ? 'true' : nothing} data-path=${o.path || nothing}>
        ${o.toggle || html`<span></span>`}
        <span class="rx-step-num">${o.number}</span>
        <span class=${'rx-step-tag ' + (o.tagClass || '')}>${o.tag}</span>
        <span class="rx-step-text" style=${o.color ? 'color: ' + o.color : nothing}>${o.text}</span>
        <span class="rx-step-caption">${o.caption}</span>
    </li>`;
}
