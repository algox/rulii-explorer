import {html, nothing} from 'lit';
import {icon} from '../../components/icons.js';
import {bindingChip, card, languageTag, requiredPill} from '../../components/common.js';
import {plainTokens, rawCode, segmented, signatureBox, slotCaption, highlightCode, placeholdersOf} from '../../components/expression.js';
import {expressionsOf, isCompiled, isScript, shortType, compareNatural} from '../../descriptor/format.js';
import {validatorPhrase, validatedValue} from '../../descriptor/summaries.js';

/**
 * The bodies of the three rule pages: a script rule (S-Rule), a predefined validator
 * (S-Validator) and a compiled rule (S-Compiled). Each returns the left column's cards.
 *
 */

export function ruleBody(a, host) {
    const view = host.state.exprView;
    const index = host.state.index;
    const rule = a.rule || {then: []};
    const placeholders = expressionsOf(a).flatMap(e => placeholdersOf(e.expression));
    const hasPlaceholder = placeholders.length > 0;
    const placeholderKeys = [...new Set(placeholders.map(p => p.key))];
    const hiddenKeys = [...new Set(placeholders.filter(p => p.hidden).map(p => p.key))];
    const valuesShared = !!(host.state.descriptor && host.state.descriptor.application && host.state.descriptor.application.placeholderValues);
    const plain = html`<div class="rx-plain-grid">
        ${rule.preCondition ? html`<span class="rx-k">Runs only if</span><span class="rx-v">${plainTokens(rule.preCondition)}</span>` : nothing}
        <span class="rx-k">Passes when</span>
        ${rule.given ? html`<span class="rx-v">${isCompiled(rule.given) ? signatureBox(rule.given) : plainTokens(rule.given)}</span>` : html`<span class="rx-v rx-muted">Always. This rule has no condition.</span>`}
        <span class="rx-k">Then</span>
        ${rule.then.length ? rule.then.map((e, i) => html`${i ? html`<span class="rx-k"></span>` : nothing}<span class="rx-v">${isCompiled(e) ? signatureBox(e) : plainTokens(e)}</span>`) : html`<span class="rx-v rx-muted">No actions. This rule only checks.</span>`}
        <span class="rx-k">Otherwise</span>
        ${rule.otherwise ? html`<span class="rx-v">${isCompiled(rule.otherwise) ? signatureBox(rule.otherwise) : plainTokens(rule.otherwise)}</span>` : html`<span class="rx-v rx-muted">No actions.</span>`}
    </div>`;
    const raw = html`<div class="rx-raw-grid">
        ${rule.preCondition ? html`<span class="rx-k">${slotCaption('pre-condition', rule.preCondition)}</span><span class="rx-v">${rawCode(rule.preCondition)}</span>` : nothing}
        <span class="rx-k">${slotCaption('given', rule.given)}</span>
        <span class="rx-v">${rule.given ? rawCode(rule.given) : html`<span class="rx-none">none</span>`}</span>
        <span class="rx-k">${rule.then.length ? slotCaption('then', rule.then[0]) : 'then'}</span>
        ${rule.then.length ? rule.then.map((e, i) => html`${i ? html`<span class="rx-k"></span>` : nothing}<span class="rx-v">${rawCode(e)}</span>`) : html`<span class="rx-v"><span class="rx-none">none</span></span>`}
        <span class="rx-k">${slotCaption('otherwise', rule.otherwise)}</span>
        <span class="rx-v">${rule.otherwise ? rawCode(rule.otherwise) : html`<span class="rx-none">none</span>`}</span>
    </div>`;
    return html`
        ${card('What it checks', view === 'raw' ? raw : plain, {action: html`<span class="rx-card-tools">${languageTag(a, {full: true})}${segmented(view, v => host.store.setExprView(v))}</span>`})}
        <div class="rx-grid-2">
            ${parametersCard(a)}
            ${bindingsCard(a, index, {})}
        </div>
        ${hasPlaceholder ? placeholderNote(placeholderKeys, hiddenKeys, valuesShared) : nothing}
        ${rule.then.some(isCompiled) || (rule.given && isCompiled(rule.given)) ? compiledNote() : nothing}`;
}

const keyList = (keys) => keys.map((k, i) => html`${i ? ', ' : ''}<span class="rx-mono">${k}</span>`);

/**
 * The note under a rule with `${key:default}` placeholders. Values off: the explorer shows keys
 * and defaults only. Values on: they are the ones the rules compiled with, and the note names any
 * key the application keeps hidden.
 */
function placeholderNote(keys, hiddenKeys, valuesShared) {
    const one = keys.length === 1;
    if (!valuesShared) {
        return html`<div class="rx-note">${icon('lock', {size: 16, width: 1.9})}<p>The explorer shows the placeholder${one ? ' ' : 's '}${keyList(keys)} and ${one ? 'its' : 'their'} default${one ? '' : 's'}, never the value the application resolved. Configuration values stay private.</p></div>`;
    }
    const oneHidden = hiddenKeys.length === 1;
    return html`<div class="rx-note rx-note-info">${icon('info', {size: 16, width: 1.9})}<p>Placeholder values are the ones ${one ? 'this rule' : 'these expressions'} compiled with, read from the running application. ${hiddenKeys.length ? html`The application keeps ${oneHidden ? 'the value of ' : 'the values of '}${keyList(hiddenKeys)} hidden.` : nothing}</p></div>`;
}

export function validatorBody(a, host) {
    const view = host.state.exprView;
    const index = host.state.index;
    const v = a.validation;
    const vs = v.valueSource || {};
    const value = vs.kind === 'binding' ? html`<span class="rx-chip">${vs.name}</span>` : vs.expression ? plainTokens(vs.expression) : html`<span class="rx-muted">the value</span>`;
    const settings = Object.entries(v.settings || {}).sort((x, y) => compareNatural(x[0], y[0]));
    const plain = html`<div class="rx-plain-grid">
        <span class="rx-k">Passes when</span><span class="rx-v">${value} is ${validatorPhrase(v.validator)}</span>
        <span class="rx-k">Otherwise</span><span class="rx-v">records a violation${v.errorCode ? html`, <span class="rx-chip rx-chip-error">${v.errorCode}</span>` : nothing}</span>
        ${v.failOnNull === false ? html`<span class="rx-k">When absent</span><span class="rx-v rx-muted">passes; a missing value is not an error</span>` : nothing}
    </div>`;
    const declared = ['r:' + v.validator + (vs.kind === 'binding' ? ' binding="' + vs.name + '"' : vs.expression ? ' value="' + (vs.expression.text || '') + '"' : ''),
        [v.errorCode ? 'errorCode="' + v.errorCode + '"' : null, v.severity ? 'severity="' + v.severity + '"' : null].filter(Boolean).join(' ')].filter(Boolean).join('\n');
    const raw = html`<div class="rx-raw-grid">
        <span class="rx-k">declared as</span><span class="rx-v"><code class="rx-code">${highlightCode(declared)}</code></span>
        ${vs.expression ? html`<span class="rx-k">${slotCaption('value', vs.expression)}</span><span class="rx-v">${rawCode(vs.expression)}</span>` : nothing}
        <span class="rx-k">validator</span><span class="rx-v"><span class="rx-mono" style="font-size: 12px; color: var(--rx-ink-2)">${v.validator}</span></span>
        ${settings.length ? html`<span class="rx-k">settings</span><span class="rx-v"><code class="rx-code">${settings.map(([k, val], i) => html`${i ? '\n' : ''}<span class="rx-c-fn">${k}</span><span class="rx-c-kw"> = </span>${highlightCode(JSON.stringify(val))}`)}</code></span>` : nothing}
    </div>`;
    return html`
        ${card('What it checks', view === 'raw' ? raw : plain, {action: html`<span class="rx-card-tools">${languageTag(a, {full: true})}${segmented(view, x => host.store.setExprView(x))}</span>`})}
        <div class="rx-grid-2">
            ${card('When it fails', html`
                <div class="rx-kv rx-kv-66">
                    <span class="rx-k">Error code</span><span class="rx-v">${v.errorCode ? html`<span class="rx-chip">${v.errorCode}</span>` : html`<span class="rx-muted">none</span>`}</span>
                    <span class="rx-k">Severity</span><span class="rx-v">${severityBadge(v.severity)}</span>
                    <span class="rx-k">Message</span><span class="rx-v" style="color: var(--rx-ink)">${message(v.errorMessage || v.defaultMessage)}</span>
                </div>
                <span class="rx-small"><span class="rx-mono">{0}</span> is replaced with the value that was checked.${v.errorMessage && v.defaultMessage && v.errorMessage !== v.defaultMessage ? ' The validator’s own default message is “' + v.defaultMessage + '”.' : ''}</span>`, {class: 'rx-card-tight'})}
            ${bindingsCard(a, index, {note: a.parameters.length ? null : 'The value comes straight from the binding; there are no declared parameters.'})}
        </div>
        ${settings.length ? card('Validator settings', html`<div class="rx-inputs">${settings.map(([k, val]) => html`<div><span class="rx-mono">${k}</span><span class="rx-mono" style="color: var(--rx-ink-2); font-weight: 400">${JSON.stringify(val)}</span></div>`)}</div>`, {class: 'rx-card-tight'}) : nothing}
        ${a.parameters.length ? parametersCard(a) : nothing}`;
}

export function compiledBody(a, host) {
    const index = host.state.index;
    const rule = a.rule || {then: []};
    const rows = [];
    if (rule.preCondition) rows.push(['Runs only if', rule.preCondition]);
    if (rule.given) rows.push(['Condition', rule.given]);
    rule.then.forEach((e, i) => rows.push([rule.then.length > 1 ? 'Action ' + (i + 1) : 'Action', e]));
    if (rule.otherwise) rows.push(['Otherwise', rule.otherwise]);
    return html`
        ${card('What it checks', html`<div class="rx-plain-grid" style="align-items: center">
            ${rows.map(([label, e]) => html`<span class="rx-k">${label}</span><span class="rx-v">${isCompiled(e) ? signatureBox(e) : plainTokens(e)}</span>`)}
            ${!rows.length ? html`<span class="rx-k">Condition</span><span class="rx-v rx-muted">Nothing readable; the rule class declares no when or then method the explorer can see.</span>` : nothing}
        </div>`, {action: html`<span class="rx-small" style="display: inline-flex; align-items: center; gap: 6px; font-size: 12px">${icon('lock', {size: 13})}Compiled code · no expression to show</span>`})}
        <div class="rx-grid-2">
            ${parametersCard(a)}
            ${bindingsCard(a, index, {compiled: true, note: 'Reads come from the declared parameters.'})}
        </div>
        ${compiledNote(a.kind === 'rule-class')}`;
}

function compiledNote(ruleClass = false) {
    return html`<div class="rx-note rx-note-info">${icon('info', {size: 16})}<p>The body of ${ruleClass ? 'a method' : 'a lambda'} can’t be read while the application runs. The explorer shows what rulii knows for certain (its name, description, signature and parameters) and never guesses the rest. To show the logic here, define the rule in XML with a script expression.</p></div>`;
}

export function parametersCard(a) {
    const params = a.parameters || [];
    const body = params.length ? html`<div class="rx-params">${params.map((p, i) => {
        const last = i === params.length - 1;
        return html`
            <span class="rx-p-name">${p.name}</span>${requiredPill(p)}
            <span class=${'rx-p-type' + (last || p.description ? '' : ' rx-p-end')} title=${p.type || ''}>${p.type ? shortType(p.type) : ''}${p.defaultValue != null ? html` <span class="rx-muted">= ${p.defaultValue}</span>` : nothing}</span>
            <span class=${'rx-p-note' + (last || p.description ? '' : ' rx-p-end')}>${p.matchStrategy ? matchLabel(p.matchStrategy) : 'matched by name'}</span>
            ${p.description ? html`<span class=${'rx-p-desc' + (last ? '' : ' rx-p-end')}>${p.description}</span>` : nothing}`;
    })}</div>` : html`<p class="rx-small" style="font-size: 12.5px">No declared parameters. Values come from the bindings the expressions name.</p>`;
    return card('Parameters', body, {class: 'rx-card-tight', action: html`<span class="rx-h2-count">${params.length}</span>`});
}

function matchLabel(strategy) {
    const s = String(strategy).toLowerCase();
    if (s.includes('type')) return 'matched by type';
    if (s.includes('name')) return 'matched by name';
    return 'matched ' + s.replace(/_/g, ' ');
}

/** Reads and writes across every expression of the artifact, as chips linking to the binding pages. */
export function bindingsCard(a, index, options = {}) {
    const reads = new Set();
    const writes = new Set();
    let unknownWrites = false;
    for (const {expression} of expressionsOf(a)) {
        for (const r of expression.reads || []) reads.add(r);
        for (const w of expression.writes || []) writes.add(w);
        if (isCompiled(expression) && (expression.writes || []).length === 0) unknownWrites = true;
    }
    for (const p of a.parameters || []) if (!reads.has(p.name) && ![...reads].some(r => r.startsWith(p.name + '.'))) reads.add(p.name);
    const readList = [...reads].sort(compareNatural);
    const writeList = [...writes].sort(compareNatural);
    const compiled = options.compiled || unknownWrites;
    return card('Bindings', html`
        <div class="rx-kv rx-kv-58" style="row-gap: 10px">
            <span class="rx-k">Reads</span>
            <span class="rx-v">${readList.length ? readList.map(r => bindingChip(r, '←', index)) : html`<span class="rx-muted">Nothing</span>`}</span>
            <span class="rx-k">Writes</span>
            <span class="rx-v">${writeList.length ? writeList.map(w => bindingChip(w, '→', index)) : compiled ? html`<span class="rx-chip rx-chip-muted">unknown</span><span class="rx-small">compiled code may write</span>` : html`<span class="rx-muted">Nothing</span>`}</span>
        </div>
        ${options.note ? html`<span class="rx-small">${options.note}</span>` : nothing}`, {class: 'rx-card-tight'});
}

function severityBadge(severity) {
    const s = String(severity || 'ERROR').toUpperCase();
    const cls = s === 'ERROR' || s === 'FATAL' ? 'error' : s === 'WARNING' ? 'warning' : 'info';
    const label = s.charAt(0) + s.slice(1).toLowerCase();
    return html`<span class=${'rx-status-badge rx-status-' + cls}>${cls === 'error' ? icon('close', {size: 10, width: 3}) : cls === 'warning' ? icon('warning', {size: 10, width: 2.6}) : icon('info', {size: 10, width: 2.6})}${label}</span>`;
}

/** Renders "{0} is not a valid email address." with the placeholders as dashed chips. */
function message(text) {
    if (!text) return html`<span class="rx-muted">none</span>`;
    const parts = [];
    const re = /\{(\d+)\}/g;
    let pos = 0, m;
    while ((m = re.exec(text)) !== null) {
        if (m.index > pos) parts.push(text.slice(pos, m.index));
        parts.push(html`<span class="rx-ph-chip" style="padding: 0 4px"><span class="rx-ph-key" style="font-size: 11.5px">${m[0]}</span></span>`);
        pos = m.index + m[0].length;
    }
    if (pos < text.length) parts.push(text.slice(pos));
    return parts;
}

export {isScript, validatedValue};
