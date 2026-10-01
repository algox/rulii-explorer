import {html, nothing} from 'lit';
import {isCompiled, plainText, shortType} from '../descriptor/format.js';

/**
 * Expression rendering (SOLUTION §9.4, FR-14, FR-17): plain English from the descriptor's tokens,
 * or the raw text with syntax colouring. Nothing here interprets an expression; it only shows
 * what the descriptor carries.
 */

/** The tokens of an analysed expression as inline chips and words. */
export function plainTokens(expression, options = {}) {
    if (!expression) return nothing;
    if (isCompiled(expression)) return html`<span class="rx-plain rx-muted">${signatureText(expression)}</span>`;
    if (expression.kind === 'composite') {
        const op = expression.operator || 'and';
        return html`<span class="rx-plain">${(expression.operands || []).map((o, i) => html`${i ? html` <span class="rx-op">${op}</span> ` : nothing}${plainTokens(o, options)}`)}</span>`;
    }
    const plain = expression.plain;
    if (!plain || !plain.tokens || !plain.tokens.length) {
        return html`<span class="rx-plain"><span class="rx-raw">${expression.text}</span></span>`;
    }
    const parts = [];
    plain.tokens.forEach((t, i) => {
        if (i) parts.push(' ');
        parts.push(token(t, options));
    });
    return html`<span class="rx-plain">${parts}</span>${plain.complete === false ? html`<span class="rx-plain-partial">Partly translated; the raw view has the whole expression.</span>` : nothing}`;
}

function token(t, options) {
    const highlight = options.highlight;
    const text = highlight ? mark(t.text, highlight) : t.text;
    switch (t.t) {
        case 'binding':
            return html`<span class="rx-chip" title=${t.path ? t.path.join('.') : t.text}>${text}</span>`;
        case 'placeholder':
            return html`<span class="rx-ph-chip" title="Configuration placeholder; the resolved value is never shown"><span class="rx-ph-key">${t.key || t.text}</span>${t.defaultValue != null ? html`<span class="rx-ph-default">default ${t.defaultValue}</span>` : nothing}</span>`;
        case 'literal':
            return html`<span class="rx-lit">${text}</span>`;
        case 'raw':
            return html`<span class="rx-raw">${text}</span>`;
        case 'call':
        case 'keyword':
        case 'op':
        default:
            return html`<span class="rx-op">${text}</span>`;
    }
}

/** Wraps the parts of `text` matching any of `words` in <mark>. */
export function mark(text, words) {
    const list = (Array.isArray(words) ? words : [words]).filter(Boolean).map(w => w.toLowerCase());
    if (!list.length || !text) return text;
    const lower = text.toLowerCase();
    const ranges = [];
    for (const w of list) {
        let at = lower.indexOf(w);
        while (at >= 0) { ranges.push([at, at + w.length]); at = lower.indexOf(w, at + 1); }
    }
    if (!ranges.length) return text;
    ranges.sort((a, b) => a[0] - b[0]);
    const merged = [];
    for (const r of ranges) {
        const last = merged[merged.length - 1];
        if (last && r[0] <= last[1]) last[1] = Math.max(last[1], r[1]);
        else merged.push(r);
    }
    const out = [];
    let pos = 0;
    for (const [s, e] of merged) {
        if (s > pos) out.push(text.slice(pos, s));
        out.push(html`<mark>${text.slice(s, e)}</mark>`);
        pos = e;
    }
    if (pos < text.length) out.push(text.slice(pos));
    return out;
}

/** Wraps the character ranges in <mark>. */
export function markRanges(text, ranges) {
    if (!ranges || !ranges.length) return text;
    const out = [];
    let pos = 0;
    for (const [s, e] of ranges) {
        if (s > pos) out.push(text.slice(pos, s));
        out.push(html`<mark>${text.slice(s, e)}</mark>`);
        pos = e;
    }
    if (pos < text.length) out.push(text.slice(pos));
    return out;
}

const KEYWORDS = new Set(['and', 'or', 'not', 'null', 'true', 'false', 'matches', 'instanceof', 'between', 'new', 'eq', 'ne', 'lt', 'le', 'gt', 'ge', 'if', 'else', 'return', 'var', 'let', 'const', 'function', 'typeof', 'in', 'of']);

/**
 * Splits raw expression text into coloured spans: #variables, @beans, ${placeholders}, strings,
 * numbers, method calls, operators and keywords. Good enough for SpEL, JavaScript and Java.
 */
export function highlightCode(text) {
    if (!text) return nothing;
    const out = [];
    const re = /(\$\{[^}]*\})|('(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*")|(#[A-Za-z_][\w]*|@[A-Za-z_][\w]*)|(\b\d+(?:\.\d+)?[LlDdFf]?\b)|(\b[A-Za-z_][\w]*)(?=\s*\()|(\b[A-Za-z_][\w]*\b)|(==|!=|<=|>=|&&|\|\||\?:|\?\.|[+\-*/%<>=!?:])|(\/\/[^\n]*|\/\*[\s\S]*?\*\/)/g;
    let pos = 0;
    let m;
    while ((m = re.exec(text)) !== null) {
        if (m.index > pos) out.push(text.slice(pos, m.index));
        const s = m[0];
        if (m[1]) out.push(html`<span class="rx-c-ph">${s}</span>`);
        else if (m[2]) out.push(html`<span class="rx-c-str">${s}</span>`);
        else if (m[3]) out.push(html`<span class="rx-c-var">${s}</span>`);
        else if (m[4]) out.push(html`<span class="rx-c-num">${s}</span>`);
        else if (m[5]) out.push(s === 'T' ? html`<span class="rx-c-kw">${s}</span>` : html`<span class="rx-c-fn">${s}</span>`);
        else if (m[6]) out.push(KEYWORDS.has(s) ? html`<span class="rx-c-kw">${s}</span>` : s);
        else if (m[7]) out.push(html`<span class="rx-c-kw">${s}</span>`);
        else if (m[8]) out.push(html`<span class="rx-c-cm">${s}</span>`);
        pos = m.index + s.length;
    }
    if (pos < text.length) out.push(text.slice(pos));
    return out;
}

/** A raw code block for an expression. */
export function rawCode(expression, options = {}) {
    if (!expression) return nothing;
    if (isCompiled(expression)) return signatureBox(expression);
    if (expression.kind === 'composite') {
        return html`<code class=${'rx-code' + (options.inline ? ' rx-code-inline' : '')}>${(expression.operands || []).map((o, i) => html`${i ? html` <span class="rx-c-kw">${expression.operator || 'and'}</span> ` : nothing}${highlightCode(o.text || '')}`)}</code>`;
    }
    return html`<code class=${'rx-code' + (options.inline ? ' rx-code-inline' : '')}>${highlightCode(expression.text || '')}</code>`;
}

/** "boolean test(Order order, Customer customer)" with the return type coloured. */
export function signatureBox(expression) {
    const sig = expression.signature || 'compiled code';
    const space = sig.indexOf(' ');
    const ret = space > 0 ? sig.slice(0, space) : '';
    const rest = space > 0 ? sig.slice(space) : sig;
    return html`<div class="rx-signature"><code>${ret ? html`<span class="rx-ret">${ret}</span>` : nothing}${rest}</code><span class="rx-small">body not readable</span></div>`;
}

export function signatureText(expression) {
    return expression.signature || 'compiled code';
}

/** The Plain / Raw segmented control (DESIGN-SYSTEM §3). */
export function segmented(view, onChange, label = 'Expression view') {
    return html`<div class="rx-seg" role="group" aria-label=${label}>
        <button type="button" aria-pressed=${view === 'plain'} @click=${() => onChange('plain')}>Plain</button>
        <button type="button" aria-pressed=${view === 'raw'} @click=${() => onChange('raw')}>Raw</button>
    </div>`;
}

/** An expression in the current view, inline (for list rows and outline steps). */
export function inlineExpression(expression, view) {
    if (!expression) return nothing;
    if (view === 'raw' && !isCompiled(expression)) return rawCode(expression, {inline: true});
    return plainTokens(expression);
}

/** The language caption for a raw slot label, e.g. "given · el". */
export function slotCaption(slot, expression) {
    if (!expression) return slot;
    if (isCompiled(expression)) return slot + ' · compiled';
    return expression.language ? slot + ' · ' + expression.language : slot;
}

export {plainText, shortType};
