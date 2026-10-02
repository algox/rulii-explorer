import {html, nothing} from 'lit';
import {glyph, icon} from '../../components/icons.js';
import {breadcrumb, copyLinkButton} from '../../components/common.js';
import {inlineExpression, segmented} from '../../components/expression.js';
import {routes, navigate} from '../../routing/router.js';
import {commandChildren, plural, shortType, sourceText, walkCommands} from '../../descriptor/format.js';
import {commandParts} from '../../descriptor/summaries.js';
import {undescribedBody} from './artifact.js';
import {stepAside, renderParts} from '../flow/step-aside.js';

const TAGS = {bind: 'BIND', run: 'RUN', apply: 'APPLY', execute: 'EXECUTE', 'async-run': 'ASYNC', await: 'AWAIT', when: 'WHEN', 'for-each': 'FOR EACH', scope: 'SCOPE', exit: 'EXIT', custom: 'CUSTOM'};

/** Collapsed step paths per flow, kept while the page is open. */
const collapsed = new Map();

/**
 * The rule flow page: header with the Flowchart / Outline tabs and a Graph link, then the
 * flowchart (FR-53) or the outline (FR-55), with the selected step's panel on the right. The
 * selection is the `step` route query, so both views share it (FR-54).
 */
export function ruleFlowPage(a, host) {
    const {index, exprView: view, route} = host.state;
    const f = a.ruleFlow || {commands: []};
    const undescribed = index.undescribed.has(a.id);
    const outline = route.query.view === 'outline';
    let steps = 0;
    walkCommands(f.commands, () => steps++);
    const source = sourceText(a.source);
    const selected = route.query.step || '';
    const aside = selected ? stepAside(a, selected, index, host) : nothing;
    const tabHref = (v) => routes.artifact(a, {view: v, step: selected});

    return html`<div class=${'rx-canvas-page' + (selected ? ' rx-with-aside' : '')}>
        <div class="rx-canvas-main">
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
                        <a role="tab" class="rx-tab" aria-selected=${!outline} href=${tabHref('flowchart')}>Flowchart</a>
                        <a role="tab" class="rx-tab" aria-selected=${outline} href=${tabHref('outline')}>Outline</a>
                        <a role="tab" class="rx-tab" aria-selected="false" href=${routes.graph({focus: a.id})}>Graph</a>
                    </div>
                    ${outline ? outlineTools(a, f, host, view) : flowchartTools(host)}
                </div>
            </div>
            ${undescribed ? html`<div class="rx-flow-body">${undescribedBody(a, index)}</div>` : outline ? outlineBody(a, f, host, view, selected, index) : html`<rx-flowchart .artifact=${a}></rx-flowchart>`}
        </div>
        ${aside}
    </div>`;
}

function flowchartTools(host) {
    const stage = () => { const el = host.querySelector('rx-flowchart'); return el && el.stage; };
    const chart = () => host.querySelector('rx-flowchart');
    return html`<div class="rx-flow-tools">
        <div class="rx-zoom">
            <button type="button" aria-label="Zoom out" @click=${() => { const s = stage(); if (s) s.zoomBy(1 / 1.25); }}>${icon('zoomOut', {size: 14})}</button>
            <span class="rx-zoom-pct">100%</span>
            <button type="button" aria-label="Zoom in" @click=${() => { const s = stage(); if (s) s.zoomBy(1.25); }}>${icon('zoomIn', {size: 14})}</button>
        </div>
        <button type="button" class="rx-icon-btn rx-icon-btn-sm" aria-label="Fit to screen" title="Fit to screen (F)" @click=${() => { const s = stage(); if (s) s.fit(28, true); }}>${icon('fit', {size: 14})}</button>
        <button type="button" class="rx-icon-btn rx-icon-btn-sm" aria-label="Toggle minimap" @click=${() => { const c = chart(); if (c) c.minimapOn = !c.minimapOn; }}>${icon('minimap', {size: 14})}</button>
    </div>`;
}

function outlineTools(a, f, host, view) {
    const setAll = (open) => { const s = new Set(); if (!open) walkCommands(f.commands, (c, path) => { if (commandChildren(c).length) s.add(path); }); collapsed.set(a.id, s); host.requestUpdate(); };
    return html`<div class="rx-flow-tools">
        <button type="button" class="rx-btn rx-btn-ghost rx-btn-sm" @click=${() => setAll(true)}>${icon('expand', {size: 13})}Expand all</button>
        <button type="button" class="rx-btn rx-btn-ghost rx-btn-sm" @click=${() => setAll(false)}>${icon('collapse', {size: 13})}Collapse all</button>
        ${segmented(view, v => host.store.setExprView(v))}
    </div>`;
}

function outlineBody(a, f, host, view, selected, index) {
    const state = collapsed.get(a.id) || new Set();
    const toggle = (path) => { const s = new Set(collapsed.get(a.id) || []); if (s.has(path)) s.delete(path); else s.add(path); collapsed.set(a.id, s); host.requestUpdate(); };
    const problems = index.problemsByArtifact.get(a.id) || [];
    const problemAt = new Map(problems.filter(p => p.path).map(p => [p.path, p]));
    const select = (path) => navigate(routes.artifact(a, {view: 'outline', step: path}));
    requestAnimationFrame(() => { const el = host.querySelector('.rx-step[aria-selected="true"]'); if (el && selected && !el.dataset.scrolled) { el.dataset.scrolled = '1'; el.scrollIntoView({block: 'center'}); } });
    const ctx = {index, view, state, toggle, selected, problemAt, flowId: a.id, select};
    return html`<div class="rx-flow-body">
        <section class="rx-card rx-outline-card" aria-label=${'Steps of ' + a.name}>
            <ol class="rx-outline" role="tree" aria-label=${a.name + ' outline'}>
                ${startRow(a, f)}
                ${f.commands.map((c, i) => stepRows(c, 'commands[' + i + ']', String(i + 1), null, ctx))}
                ${f.finalizer ? row({number: '', tag: 'FINALLY', tagClass: 'rx-step-tag-flow', text: html`<span>Then always</span>${inlineExpression(f.finalizer, view)}`, caption: 'finalizer'}) : nothing}
                ${f.returning ? row({number: '', tag: 'RETURN', tagClass: 'rx-step-tag-flow', text: html`<span>Return</span>${inlineExpression(f.returning, view)}`, caption: f.resultType && f.resultType !== 'java.lang.Object' ? 'flow result · ' + shortType(f.resultType) : 'flow result'}) : nothing}
                ${!f.commands.length ? html`<li class="rx-step"><span></span><span></span><span></span><span class="rx-step-text rx-muted">This flow has no steps.</span><span></span></li>` : nothing}
            </ol>
            ${f.globalHandler ? html`<div class="rx-outline-foot">
                <span class="rx-overline" id="rx-outline-foot-h">Whenever a step fails</span>
                <ol class="rx-outline" role="tree" aria-labelledby="rx-outline-foot-h">
                ${(f.globalHandler.body || []).map((c, i) => stepRows(c, 'globalHandler.body[' + i + ']', '', null, {...ctx, branch: 'on ' + shortType(f.globalHandler.exceptionType || 'Exception'), handler: true, caption: 'global handler', selectPath: 'global'}))}
                ${!(f.globalHandler.body || []).length ? row({number: '', tag: 'HANDLER', text: html`<span class="rx-handler-chip">on ${shortType(f.globalHandler.exceptionType || 'Exception')}</span><span class="rx-muted">handled without further steps</span>`, caption: 'global handler', child: true}) : nothing}
                </ol>
            </div>` : nothing}
        </section>
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
    const text = html`${ctx.branch ? (ctx.handler ? html`<span class="rx-handler-chip">${ctx.branch}</span>` : html`<span class="rx-branch">${ctx.branch}</span>`) : nothing}${renderParts(parts, ctx)}`;
    const captionWithProblem = problem ? html`<span class=${'rx-dot rx-dot-' + problem.severity} title=${problem.message}></span>${caption}` : (command.type === 'await' ? html`<strong>joins here</strong>${caption.replace(/^joins here/, '')}` : caption);
    const selectPath = ctx.selectPath || path;
    const main = row({number, tag: TAGS[command.type] || command.type.toUpperCase(), tagClass, text, caption: ctx.caption || captionWithProblem, async, child: !!parent, selected: ctx.selected === selectPath, path,
        onSelect: () => ctx.select(selectPath),
        toggle: children.length ? html`<button type="button" class="rx-step-toggle" aria-label=${(open ? 'Collapse' : 'Expand') + ' step ' + number} aria-expanded=${open} @click=${(e) => { e.stopPropagation(); ctx.toggle(path); }}>${icon(open ? 'chevronDown' : 'chevronRight', {size: 12, width: 2.4})}</button>` : null});
    if (!children.length || !open) return main;
    let k = 0;
    return html`${main}${children.map(group => {
        const start = k;
        k += group.commands.length;
        return html`<li class=${'rx-step-children' + (async ? ' rx-step-children-async' : '')} role="group">
        ${group.commands.map((c, i) => stepRows(c, path + '.' + group.slot + '[' + i + ']', number + '.' + (start + i + 1), command, {...ctx, branch: group.branch, handler: group.slot === 'handler.body', caption: group.slot === 'handler.body' ? 'exception handler' : null, selectPath: group.slot === 'handler.body' ? 'handler:' + path : null}))}
    </li>`;
    })}`;
}

function row(o) {
    const cls = 'rx-step' + (o.child ? ' rx-step-child' : '') + (o.async ? ' rx-step-async' : '') + (o.onSelect ? ' rx-step-selectable' : '');
    return html`<li class=${cls} role="treeitem" aria-selected=${o.selected ? 'true' : nothing} data-path=${o.path || nothing} @click=${o.onSelect ? (e) => { if (!e.target.closest('a')) o.onSelect(); } : nothing}>
        ${o.toggle || html`<span></span>`}
        <span class="rx-step-num">${o.number}</span>
        <span class=${'rx-step-tag ' + (o.tagClass || '')}>${o.tag}</span>
        <span class="rx-step-text" style=${o.color ? 'color: ' + o.color : nothing}>${o.text}</span>
        <span class="rx-step-caption">${o.caption}</span>
    </li>`;
}
