import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon} from '../../components/icons.js';
import {artifactLink, beanLink, breadcrumb, card, copyButton, copyLinkButton, typeBadge} from '../../components/common.js';
import {highlightCode, plainTokens, rawCode, segmented} from '../../components/expression.js';
import {routes} from '../../routing/router.js';
import {beanKindLabel, compareNatural, discoveryLabel, kindLabel, plural, shortType, sourceText} from '../../descriptor/format.js';
import {beanSummary, validatorPhrase} from '../../descriptor/summaries.js';
import {constraintFamily} from '../../descriptor/indexes.js';

/**
 * The bean page (FR-72, S-Bean): what the bean declares, member by member, each constraint
 * opening into the rule rulii-bean-validation materialized from it; the beans it cascades into
 * and the rules it runs; its flow and source. `?member=properties.total` scrolls to a member.
 */
class RxBean extends RxElement {

    static properties = {open: {state: true}};

    constructor() {
        super();
        this.open = new Set();
        this.scrolledTo = null;
    }

    updated() {
        const member = this.state.route.query.member;
        const key = this.state.route.id + '?' + (member || '');
        if (this.scrolledTo === key) return;
        this.scrolledTo = key;
        if (!member) return;
        const target = this.querySelector('[data-member="' + member.replace(/"/g, '') + '"]');
        // After the fonts, so the centre is computed on the final layout
        if (target) document.fonts.ready.then(() => { if (target.isConnected) target.scrollIntoView({block: 'center'}); });
    }

    toggle(key) {
        const open = new Set(this.open);
        if (open.has(key)) open.delete(key);
        else open.add(key);
        this.open = open;
    }

    render() {
        const {route, index} = this.state;
        const bean = index.beans && index.beans.byId.get(route.id);
        if (!bean) return html`<rx-states kind="missing"></rx-states>`;
        const beans = index.beans;
        const undescribed = beans.undescribed.has(bean.id);
        const notMaterialized = !bean.materialized && !undescribed;
        const rules = ruleIndex(bean);
        const ctx = {bean, index, beans, rules, host: this, member: route.query.member || null, view: this.state.exprView};
        const source = sourceText(bean.source);
        return html`<div class="rx-page">
            <div class="rx-head-row">
                <div class="rx-page-head">
                    ${breadcrumb([{text: 'Beans', href: routes.beans()}, ...(bean.packageId ? [{text: bean.packageId, mono: true}] : []), {text: bean.name}])}
                    <div class="rx-meta-row">
                        ${typeBadge('bean', {undescribed})}
                        <span class="rx-kind">${beanKindLabel(bean)}</span>
                        ${source ? html`<span class="rx-sep-dot"></span><span class="rx-source-inline">${source}</span>` : nothing}
                        ${bean.groupSequence ? html`<span class="rx-sep-dot"></span><span class="rx-kind">group sequence ${bean.groupSequence.map((g, i) => html`${i ? ' → ' : ''}<span class="rx-mono">${g}</span>`)}</span>` : nothing}
                    </div>
                    <div class="rx-title-row">
                        <h1 class="rx-h1">${bean.name}</h1>
                        ${bean.flow ? html`<span class="rx-bean">flow <span class="rx-chip">${bean.flow.name}</span></span>` : nothing}
                    </div>
                    ${undescribed ? html`<p class="rx-subtitle rx-muted">Could not be described.</p>` : html`<p class="rx-subtitle">${summaryParts(beanSummary(bean, index))}</p>`}
                </div>
                <div class="rx-actions">
                    <a class="rx-btn rx-btn-secondary" href=${routes.beansGraph({focus: bean.id})}>${icon('graph', {size: 14, width: 1.9})}Show in graph</a>
                    ${copyLinkButton('Copy link to this bean')}
                </div>
            </div>
            ${notMaterialized ? this.notMaterializedNote(bean, beans) : nothing}
            ${undescribed ? this.undescribedNote(bean, beans) : nothing}
            <div class="rx-detail-grid">
                <div class="rx-col">
                    ${wholeBeanCard(ctx)}
                    ${propertiesCard(ctx)}
                    ${executablesCard(ctx)}
                </div>
                <aside class="rx-col" aria-label="Connections">
                    ${usedByCard(ctx)}
                    ${reachesCard(ctx)}
                    ${flowCard(ctx)}
                    ${sourceCard(ctx)}
                </aside>
            </div>
        </div>`;
    }

    notMaterializedNote(bean, beans) {
        const problem = (beans.problemsByBean.get(bean.id) || []).find(p => p.code === 'BEAN_NOT_MATERIALIZED');
        return html`<div class="rx-note rx-note-warning" role="status">${icon('warning', {size: 16, width: 2.2})}<span><span class="rx-strong">The library could not build this bean’s rules.</span> It is shown from its declarations alone; validating a ${bean.name} fails.${problem ? html` <span class="rx-mono">${problem.message}</span>` : nothing}</span><span class="rx-spacer"></span><a href=${routes.beansProblems()}>See details</a></div>`;
    }

    undescribedNote(bean, beans) {
        const problem = (beans.problemsByBean.get(bean.id) || []).find(p => p.code === 'UNDESCRIBABLE');
        return html`<section class="rx-card rx-undescribed-card">
            <div class="rx-card-head"><span style="display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 600">${glyph('bean', {size: 11, undescribed: true})}${bean.name}</span><span class="rx-code-tag" style="padding-top: 0">UNDESCRIBABLE</span></div>
            <p class="rx-small" style="font-size: 12.5px; color: var(--rx-ink-2)">${problem && problem.severity === 'warning' ? 'This class was configured as a bean, but declares nothing rulii-bean-validation reads.' : 'Reading this bean’s definition failed, so only its name is shown. The rest of the application is unaffected.'}</p>
            ${problem ? html`<code class="rx-code">${problem.message}</code>` : nothing}
        </section>`;
    }
}

/** The materialized rules of a bean and its executables, by id, hosted ones included. */
function ruleIndex(bean) {
    const map = new Map();
    const add = (list) => { for (const r of list || []) { map.set(r.id, r); add(r.hosted); } };
    add(bean.rules);
    for (const e of bean.executables || []) add(e.rules);
    return map;
}

/** The summary sentence with its names as links. */
function summaryParts(parts) {
    return parts.map(p => p.text !== undefined ? p.text
        : p.bean ? beanLink(p.bean, {glyph: false, class: 'rx-via-link'})
        : p.artifact ? artifactLink(p.artifact, {glyph: false, class: 'rx-via-link'})
        : html`<span class="rx-chip-error rx-mono" style="font-size: 13px">${p.missing}</span>`);
}

// ── Cards ────────────────────────────────────────────────────────────────────

/** Class-level declarations: @Validate, @Expr and @Bind on the bean itself. */
function wholeBeanCard(ctx) {
    const {bean} = ctx;
    const n = (bean.constraints || []).length + (bean.validations || []).length + (bean.expressions || []).length + (bean.binds || []).length;
    if (!n) return nothing;
    return card('Whole bean', html`
        <div class="rx-decls" data-member="">
            ${declarations(ctx, bean, '', bean.name)}
            ${binds(bean.binds, ctx)}
        </div>`, {class: 'rx-card-tight', action: html`<span class="rx-h2-count">runs after the properties</span>`});
}

/** The property tree: each property with its type and declarations; container elements nested under it. */
function propertiesCard(ctx) {
    const {bean, host, view} = ctx;
    const props = bean.properties || [];
    const constraints = props.reduce((n, p) => n + declarationCount(p), 0);
    const body = props.length ? html`<div class="rx-props">${props.map(p => propertyRow(ctx, p))}</div>`
        : html`<p class="rx-small" style="font-size: 12.5px">No properties.</p>`;
    return card('Properties', body, {class: 'rx-card-tight', action: html`<span class="rx-card-tools"><span class="rx-h2-count">${plural(props.length, 'property', 'properties')} · ${plural(constraints, 'constraint')}</span>${segmented(view, v => host.store.setExprView(v), 'Constraint view')}</span>`});
}

function declarationCount(holder) {
    const el = (e) => (e.constraints || []).length + (e.children || []).reduce((n, c) => n + el(c), 0);
    return (holder.constraints || []).length + (holder.expressions || []).length + (holder.validations || []).length + (holder.containerElements || []).reduce((n, e) => n + el(e), 0);
}

function propertyRow(ctx, p) {
    const path = 'properties.' + p.name;
    const current = ctx.member === path;
    const cascade = p.cascade ? cascadeLink(ctx, p.cascade, path) : nothing;
    const empty = !declarationCount(p) && !p.cascade;
    return html`<div class=${'rx-prop' + (current ? ' rx-prop-current' : '') + (empty ? ' rx-prop-empty' : '')} data-member=${path} id=${'rx-m-' + path}>
        <span class="rx-prop-name rx-mono">${p.name}</span>
        <span class="rx-prop-type rx-mono" title=${p.genericType || p.type}>${shortType(p.genericType || p.type)}${p.declaredOn && p.declaredOn !== 'field' ? html` <span class="rx-prop-on">${p.declaredOn === 'getter' ? 'getter' : 'component'}</span>` : nothing}${p.declaringClass ? html` <span class="rx-prop-on" title=${'Declared on ' + p.declaringClass}>from ${shortType(p.declaringClass)}</span>` : nothing}</span>
        <div class="rx-cons">
            ${empty ? html`<span class="rx-small">no declarations · bound for expressions</span>` : nothing}
            ${declarations(ctx, p, path, p.name)}
            ${cascade}
        </div>
        ${(p.containerElements || []).map((el, i) => elementRow(ctx, el, path + '.containerElements[' + i + ']', 1))}
    </div>`;
}

function elementRow(ctx, el, path, depth) {
    const cascade = el.cascade ? cascadeLink(ctx, el.cascade, path) : nothing;
    return html`<div class="rx-prop rx-prop-element" data-member=${path} style=${'--rx-depth: ' + depth}>
        <span class="rx-prop-name rx-mono rx-prop-element-name">${icon('arrowRight', {size: 11, width: 2.2})}${el.name}</span>
        <span class="rx-prop-type rx-mono" title=${el.type}>${shortType(el.type)}</span>
        <div class="rx-cons">
            ${(el.constraints || []).length || el.cascade ? nothing : html`<span class="rx-small">no declarations</span>`}
            ${declarations(ctx, el, path, el.name)}
            ${cascade}
        </div>
        ${(el.children || []).map((ch, i) => elementRow(ctx, ch, path + '.children[' + i + ']', depth + 1))}
    </div>`;
}

function cascadeLink(ctx, cascade, path) {
    const target = cascade.beanId ? ctx.beans.byId.get(cascade.beanId) : null;
    const conversions = (cascade.groupConversions || []).map(c => c.from + ' → ' + c.to).join(', ');
    return html`<span class="rx-cascade">${target
        ? html`<a class="rx-cascade-link" href=${routes.bean(target)} data-hover=${target.id}>${icon('arrowRight', {size: 13, width: 2.2})}cascades into ${target.name}</a>`
        : html`<span class="rx-cascade-link rx-cascade-plain">${icon('arrowRight', {size: 13, width: 2.2})}cascades into ${shortType(cascade.targetType)}<span class="rx-small"> · not described</span></span>`}
        ${conversions ? html`<span class="rx-pill" title="Group conversions">${conversions}</span>` : nothing}
    </span>`;
}

/** The constraint, @Expr and @Validate chips of a member, each opening its rule. */
function declarations(ctx, holder, path, where) {
    const out = [];
    (holder.constraints || []).forEach((c, i) => out.push(constraintChip(ctx, c, path + '#c' + i)));
    (holder.expressions || []).forEach((e, i) => out.push(exprChip(ctx, e, path + '#e' + i)));
    (holder.validations || []).forEach((v, i) => out.push(validateChip(ctx, v, path + '#v' + i)));
    return out;
}

function constraintChip(ctx, c, key) {
    const open = ctx.host.open.has(key);
    const rule = c.ruleId ? ctx.rules.get(c.ruleId) : null;
    const name = c.annotation.slice(c.annotation.lastIndexOf('.') + 1).replace(/\$/g, '.');
    return html`<span class="rx-con-wrap">
        <button type="button" class=${'rx-con' + (c.reportAsSingleViolation ? ' rx-con-composed' : '')} aria-expanded=${open} @click=${() => ctx.host.toggle(key)} title=${(rule ? 'Rule ' + rule.name + ' · ' : '') + c.annotation}>
            <span>${c.plain}</span><span class="rx-con-an">@${name}</span>${(c.groups || []).length ? html`<span class="rx-con-groups">${c.groups.join(', ')}</span>` : nothing}
        </button>
        ${open ? constraintDetail(ctx, c, rule) : nothing}
    </span>`;
}

function constraintDetail(ctx, c, rule) {
    const raw = ctx.view === 'raw';
    const attributes = Object.entries(c.attributes || {}).sort((x, y) => compareNatural(x[0], y[0]));
    const declared = '@' + c.annotation.slice(c.annotation.lastIndexOf('.') + 1).replace(/\$/g, '.') + (attributes.length ? '(' + attributes.map(([k, v]) => k + ' = ' + JSON.stringify(v)).join(', ') + ')' : '');
    return html`<div class="rx-con-detail">
        <div class="rx-kv rx-kv-84">
            ${rule ? html`<span class="rx-k">Rule</span><span class="rx-v"><span class="rx-chip">${rule.name}</span><span class="rx-kind">${ruleKindLabel(rule)}</span>${placementText(rule)}</span>` : html`<span class="rx-k">Rule</span><span class="rx-v rx-muted">${ctx.bean.materialized ? (c.composing || []).length ? 'none of its own: its composing constraints become the rules' : 'none' : 'not built: the bean did not materialize'}</span>`}
            ${rule ? passesWhen(ctx, c, rule) : nothing}
            ${rule && rule.validation ? html`<span class="rx-k">Otherwise</span><span class="rx-v">records a violation${rule.validation.errorCode ? html`, <span class="rx-chip rx-chip-error">${rule.validation.errorCode}</span>` : nothing}${c.severity ? html` · ${severityWord(c.severity)}` : nothing}</span>` : nothing}
            ${rule && rule.validation && rule.validation.settings && Object.keys(rule.validation.settings).length ? html`<span class="rx-k">Settings</span><span class="rx-v">${Object.entries(rule.validation.settings).map(([k, v]) => html`<span class="rx-mono rx-setting">${k} = ${JSON.stringify(v)}</span>`)}</span>` : nothing}
            ${raw ? html`<span class="rx-k">Declared as</span><span class="rx-v"><code class="rx-code rx-code-inline">${highlightCode(declared)}</code></span>` : nothing}
            ${c.message ? html`<span class="rx-k">Message</span><span class="rx-v"><span class="rx-mono rx-message">${c.message}</span>${rule && rule.validation && rule.validation.failOnNull === false ? html`<span class="rx-small">· null passes; @NotNull says when a value is required</span>` : nothing}</span>` : nothing}
            ${(c.payload || []).length ? html`<span class="rx-k">Payload</span><span class="rx-v">${c.payload.map(p => html`<span class="rx-pill">${p}</span>`)}</span>` : nothing}
            ${(c.composing || []).length ? html`<span class="rx-k">${c.reportAsSingleViolation ? 'Reports as one' : 'Composed of'}</span><span class="rx-v rx-composing">${c.composing.map((cc, i) => constraintChip(ctx, cc, (rule ? rule.id : c.annotation) + '#cc' + i))}</span>` : nothing}
        </div>
    </div>`;
}

/** The "Passes when" row of a materialized rule: the validator's phrase, the script, or the hosted rules. */
function passesWhen(ctx, c, rule) {
    const v = rule.validation;
    if (rule.hosted && rule.hosted.length) return html`<span class="rx-k">Passes when</span><span class="rx-v">every composing constraint passes; one violation is recorded for all of them</span>`;
    if (rule.rule && rule.rule.given) {
        return html`<span class="rx-k">Passes when</span><span class="rx-v">${ctx.view === 'raw' ? rawCode(rule.rule.given, {inline: true}) : plainTokens(rule.rule.given)}</span>`;
    }
    if (v && v.validator) {
        const value = v.valueSource && v.valueSource.kind === 'binding' ? html`<span class="rx-chip">${v.valueSource.name}</span>` : html`<span class="rx-muted">the value</span>`;
        return html`<span class="rx-k">Passes when</span><span class="rx-v">${value} is ${validatorPhrase(v.validator)}</span>`;
    }
    return html`<span class="rx-k">Passes when</span><span class="rx-v">${c.plain}</span>`;
}

function ruleKindLabel(rule) {
    return kindLabel({type: 'rule', kind: rule.kind, validation: rule.validation, rule: rule.rule, parameters: rule.parameters || []});
}

function placementText(rule) {
    const p = rule.placement;
    if (!p) return nothing;
    const where = p.kind === 'property' ? 'property ' + p.name : p.kind === 'parameter' ? 'parameter ' + p.name : p.kind === 'return-value' ? 'the return value' : p.kind === 'cross-parameter' ? 'the parameters as a whole' : 'the bean';
    return html`<span class="rx-small">reports on ${where}</span>`;
}

function severityWord(severity) {
    const s = String(severity).toLowerCase();
    return s.charAt(0).toUpperCase() + s.slice(1);
}

function exprChip(ctx, e, key) {
    const open = ctx.host.open.has(key);
    const rule = e.ruleId ? ctx.rules.get(e.ruleId) : null;
    const lang = e.expression && e.expression.language ? e.expression.language : null;
    return html`<span class="rx-con-wrap">
        <button type="button" class="rx-con rx-con-expr" aria-expanded=${open} @click=${() => ctx.host.toggle(key)} title=${'@Expr' + (e.errorCode ? ' · ' + e.errorCode : '')}>
            <span>${ctx.view === 'raw' && e.expression ? rawCode(e.expression, {inline: true}) : plainTokens(e.expression)}</span><span class="rx-con-an">@Expr${lang ? ' · ' + lang : ''}</span>${(e.groups || []).length ? html`<span class="rx-con-groups">${e.groups.join(', ')}</span>` : nothing}
        </button>
        ${open ? html`<div class="rx-con-detail"><div class="rx-kv rx-kv-84">
            ${rule ? html`<span class="rx-k">Rule</span><span class="rx-v"><span class="rx-chip">${rule.name}</span><span class="rx-kind">${ruleKindLabel(rule)}</span>${placementText(rule)}</span>` : nothing}
            <span class="rx-k">Passes when</span><span class="rx-v">${plainTokens(e.expression)}</span>
            ${ctx.view !== 'raw' && e.expression && e.expression.text ? html`<span class="rx-k">Script</span><span class="rx-v">${rawCode(e.expression, {inline: true})}</span>` : nothing}
            <span class="rx-k">Otherwise</span><span class="rx-v">records a violation, <span class="rx-chip rx-chip-error">${e.errorCode}</span>${e.severity ? ' · ' + severityWord(e.severity) : ''}</span>
            ${e.message ? html`<span class="rx-k">Message</span><span class="rx-v"><span class="rx-mono rx-message">${e.message}</span></span>` : nothing}
            <span class="rx-k">When absent</span><span class="rx-v">${e.skipNull ? 'passes; a null value is not checked' : 'checked like any value'}</span>
            ${e.applyTo && e.applyTo !== 'IMPLICIT' ? html`<span class="rx-k">Applies to</span><span class="rx-v">${e.applyTo === 'PARAMETERS' ? 'the parameters' : 'the return value'}</span>` : nothing}
        </div></div>` : nothing}
    </span>`;
}

function validateChip(ctx, v, key) {
    const open = ctx.host.open.has(key);
    const targets = v.targets || [];
    const missing = targets.filter(t => !t.artifactId);
    return html`<span class="rx-con-wrap">
        <button type="button" class=${'rx-con rx-con-validate' + (missing.length ? ' rx-con-missing' : '')} aria-expanded=${open} @click=${() => ctx.host.toggle(key)} title="@Validate: runs registered rules by name">
            ${glyph('rule', {size: 10})}<span>runs ${targets.map((t, i) => html`${i ? ', ' : ''}${t.name}`)}</span><span class="rx-con-an">@Validate</span>${(v.groups || []).length ? html`<span class="rx-con-groups">${v.groups.join(', ')}</span>` : nothing}
        </button>
        ${open ? html`<div class="rx-con-detail"><div class="rx-kv rx-kv-84">
            <span class="rx-k">Runs</span><span class="rx-v">${targets.map(t => {
                const a = t.artifactId ? ctx.index.byId.get(t.artifactId) : null;
                return a ? artifactLink(a) : html`<span class="rx-chip-error rx-mono">${t.name}</span><span class="rx-small">nothing registered under this name</span>`;
            })}</span>
            <span class="rx-k">Looked up</span><span class="rx-v">by registry name, when the bean is validated${v.applyTo && v.applyTo !== 'IMPLICIT' ? ', on ' + (v.applyTo === 'PARAMETERS' ? 'the parameters' : 'the return value') : ''}</span>
            <span class="rx-k">Groups</span><span class="rx-v">${(v.groups || []).length ? v.groups.map(g => html`<span class="rx-pill">${g}</span>`) : html`<span class="rx-pill">Default</span>`}</span>
        </div></div>` : nothing}
    </span>`;
}

function binds(list, ctx) {
    if (!list || !list.length) return nothing;
    return html`<div class="rx-binds">${list.map(b => html`<span class="rx-bind" title=${'@Bind ' + b.name + (b.declaredOn ? ' · declared on ' + b.declaredOn : '')}>
        <span class="rx-chip">${b.name}</span><span class="rx-bind-eq">=</span>
        ${b.kind === 'literal' ? html`<span class="rx-mono rx-bind-lit">${JSON.stringify(b.literal)}</span>` : (ctx.view === 'raw' ? rawCode(b.expression, {inline: true}) : plainTokens(b.expression))}
        <span class="rx-con-an">@Bind · ${shortType(b.type)}${b.applyTo && b.applyTo !== 'IMPLICIT' ? ' · ' + b.applyTo.toLowerCase().replace('_', ' ') : ''}</span>
    </span>`)}</div>`;
}

/** Methods and constructors: parameters, cross-parameter declarations and return values, with their flows. */
function executablesCard(ctx) {
    const list = ctx.bean.executables || [];
    const body = list.length ? list.map(e => executableBlock(ctx, e))
        : html`<p class="rx-small" style="font-size: 12.5px">No constrained method or constructor. Parameter, cross-parameter and return-value declarations would be listed here, each with its own rules and flows.</p>`;
    return card('Methods and constructors', body, {class: 'rx-card-tight', action: html`<span class="rx-h2-count">${list.length}</span>`});
}

function executableBlock(ctx, e) {
    const path = 'executables.' + e.key;
    const current = ctx.member === path;
    return html`<div class=${'rx-exec' + (current ? ' rx-prop-current' : '')} data-member=${path}>
        <div class="rx-exec-head">
            <span class="rx-pill">${e.kind}</span>
            <span class="rx-mono rx-exec-sig" title=${e.signature}>${shortSignature(e.signature)}</span>
            <span class="rx-spacer"></span>
            ${e.parametersFlow ? html`<span class="rx-bean">params <span class="rx-chip">${e.parametersFlow.name}</span></span>` : nothing}
            ${e.returnValueFlow ? html`<span class="rx-bean">result <span class="rx-chip">${e.returnValueFlow.name}</span></span>` : nothing}
        </div>
        ${(e.parameters || []).map(p => {
            const ppath = path + '.parameters[' + p.index + ']';
            const empty = !declarationCount(p) && !p.cascade;
            return html`<div class=${'rx-prop' + (ctx.member === ppath ? ' rx-prop-current' : '') + (empty ? ' rx-prop-empty' : '')} data-member=${ppath}>
                <span class="rx-prop-name rx-mono"><span class="rx-prop-idx">${p.index}</span>${p.name}</span>
                <span class="rx-prop-type rx-mono" title=${p.genericType || p.type}>${shortType(p.genericType || p.type)}</span>
                <div class="rx-cons">${empty ? html`<span class="rx-small">no declarations</span>` : nothing}${declarations(ctx, p, ppath, p.name)}${p.cascade ? cascadeLink(ctx, p.cascade, ppath) : nothing}</div>
                ${(p.containerElements || []).map((el, i) => elementRow(ctx, el, ppath + '.containerElements[' + i + ']', 1))}
            </div>`;
        })}
        ${e.crossParameter ? html`<div class=${'rx-prop' + (ctx.member === path + '.crossParameter' ? ' rx-prop-current' : '')} data-member=${path + '.crossParameter'}>
            <span class="rx-prop-name">all parameters</span><span class="rx-prop-type rx-mono">cross-parameter</span>
            <div class="rx-cons">${declarations(ctx, e.crossParameter, path + '.crossParameter', e.name)}</div>
        </div>` : nothing}
        ${e.returnValue ? html`<div class=${'rx-prop' + (ctx.member === path + '.returnValue' ? ' rx-prop-current' : '')} data-member=${path + '.returnValue'}>
            <span class="rx-prop-name">${e.kind === 'constructor' ? 'the new object' : 'returns'}</span><span class="rx-prop-type rx-mono" title=${e.returnValue.genericType || e.returnValue.type}>${shortType(e.returnValue.genericType || e.returnValue.type)}</span>
            <div class="rx-cons">${declarations(ctx, e.returnValue, path + '.returnValue', e.name)}${e.returnValue.cascade ? cascadeLink(ctx, e.returnValue.cascade, path + '.returnValue') : nothing}${binds(e.returnValue.binds, ctx)}</div>
            ${(e.returnValue.containerElements || []).map((el, i) => elementRow(ctx, el, path + '.returnValue.containerElements[' + i + ']', 1))}
        </div>` : nothing}
        ${(e.binds || []).length ? html`<div class="rx-prop" data-member=${path + '.binds'}><span class="rx-prop-name">bindings</span><span class="rx-prop-type rx-mono">@Bind</span><div class="rx-cons">${binds(e.binds, ctx)}</div></div>` : nothing}
    </div>`;
}

function shortSignature(signature) {
    return String(signature || '').replace(/[\w.$]+\.(\w+)/g, '$1');
}

/** Beans that cascade into this one. */
function usedByCard(ctx) {
    const {bean, beans} = ctx;
    const refs = beans.cascadedFrom.get(bean.id) || [];
    const rows = refs.map(ref => {
        const from = beans.byId.get(ref.from);
        if (!from) return nothing;
        return html`<a class="rx-used-row" href=${routes.bean(from, {member: memberPath(ref.path)})} data-hover=${from.id}>${glyph('bean', {size: 12})}<span class="rx-name">${from.name}</span><span class="rx-small">cascade · ${memberLabel(from, ref.path)}</span></a>`;
    });
    const body = refs.length ? html`<div>${rows}</div>`
        : html`<p class="rx-small" style="font-size: 12.5px">No bean cascades into ${bean.name}; the application validates it directly${bean.flow ? html`, or through a flow that embeds <span class="rx-chip">${bean.flow.name}</span>` : nothing}.</p>`;
    return card('Used by', body, {class: 'rx-card-tight'});
}

/** The beans this one cascades into, and the registry rules it runs. */
function reachesCard(ctx) {
    const {bean, beans, index} = ctx;
    const rows = [];
    for (const ref of beans.cascadesTo.get(bean.id) || []) {
        const to = beans.byId.get(ref.to);
        if (to) rows.push(html`<a class="rx-used-row" href=${routes.bean(to)} data-hover=${to.id}>${glyph('bean', {size: 12})}<span class="rx-name">${to.name}</span><span class="rx-small">cascade · ${memberLabel(bean, ref.path)}</span></a>`);
    }
    const seen = new Set();
    for (const ref of beans.runs.get(bean.id) || []) {
        const a = index.byId.get(ref.to);
        if (!a || seen.has(a.id)) continue;
        seen.add(a.id);
        rows.push(html`<a class="rx-used-row" href=${routes.artifact(a)} data-hover=${a.id}>${glyph(a.type, {size: 12})}<span class="rx-name">${a.name}</span><span class="rx-small">runs · by name</span></a>`);
    }
    for (const t of missingTargets(bean)) rows.push(html`<div class="rx-used-row">${glyph('rule', {size: 12, undescribed: true})}<span class="rx-name rx-chip-error">${t}</span><span class="rx-small">missing</span></div>`);
    if (!rows.length) return nothing;
    return card('Reaches', html`<div>${rows}</div>`, {class: 'rx-card-tight'});
}

function missingTargets(bean) {
    const out = new Set();
    const walk = (holder) => {
        for (const v of holder.validations || []) for (const t of v.targets || []) if (!t.artifactId) out.add(t.name);
    };
    walk(bean);
    for (const p of bean.properties || []) walk(p);
    for (const e of bean.executables || []) { if (e.crossParameter) walk(e.crossParameter); if (e.returnValue) walk(e.returnValue); }
    return [...out].sort(compareNatural);
}

function memberPath(path) {
    const m = /^(properties\.[^.[]+|executables\.[^.[]+(?:\.parameters\[\d+\]|\.returnValue)?)/.exec(path || '');
    return m ? m[1] : undefined;
}

function memberLabel(bean, path) {
    const m = /^properties\.([^.[]+)/.exec(path || '');
    if (m) return m[1];
    const e = /^executables\.([^.[]+)/.exec(path || '');
    if (e) { const x = (bean.executables || []).find(y => y.key === e[1]); return (x ? x.name : e[1]) + '()'; }
    return path;
}

/** The flow the library runs for the bean, and how to embed it. */
function flowCard(ctx) {
    const {bean} = ctx;
    if (!bean.flow) return nothing;
    const rules = (bean.rules || []).length;
    const snippet = 'validator.getRuleFlow(' + bean.name + '.class)';
    return card('Flow', html`
        <p class="rx-small" style="font-size: 12.5px; line-height: 1.5; color: var(--rx-ink-2)">Validating ${aOrAn(bean.name)} runs <span class="rx-chip">${bean.flow.name}</span>: bind the properties, run ${bean.materialized ? 'the ' + plural(rules, 'rule') + ' above' : 'its rules'}, cascade. Embed it in a flow of your own with the object the validator gives you:</p>
        <div class="rx-code-block"><code class="rx-code">${highlightCode(snippet)}</code><button type="button" class="rx-copy-btn" aria-label="Copy snippet" @click=${() => copySnippet(snippet)}>${icon('copy', {size: 12})}Copy</button></div>`,
        {class: 'rx-card-tight', action: html`<span class="rx-h2-count">not registered</span>`});
}

async function copySnippet(text) {
    const {copyText} = await import('../../components/common.js');
    copyText(text, 'Snippet copied');
}

function aOrAn(name) {
    return (/^[aeiou]/i.test(name) ? 'an ' : 'a ') + name;
}

function sourceCard(ctx) {
    const {bean} = ctx;
    const text = sourceText(bean.source);
    const rows = [];
    if (text) rows.push(html`<span class="rx-k">Defined in</span><span class="rx-v" style="flex-wrap: nowrap"><span class="rx-mono rx-ellipsis" title=${text}>${text}</span>${copyButton(text, 'Copy source location')}</span>`);
    else rows.push(html`<span class="rx-k">Defined in</span><span class="rx-v rx-muted">Not recorded (sources are switched off)</span>`);
    if (bean.packageId) rows.push(html`<span class="rx-k">Package</span><span class="rx-v"><span class="rx-mono">${bean.packageId}</span></span>`);
    rows.push(html`<span class="rx-k">Class</span><span class="rx-v"><span class="rx-mono rx-ellipsis" title=${bean.id}>${bean.id}</span></span>`);
    rows.push(html`<span class="rx-k">Found</span><span class="rx-v">${discoveryLabel(bean.discovery)}</span>`);
    return card('Source', html`<div class="rx-kv">${rows}</div>`, {class: 'rx-card-tight'});
}

export {constraintFamily};

customElements.define('rx-bean', RxBean);
