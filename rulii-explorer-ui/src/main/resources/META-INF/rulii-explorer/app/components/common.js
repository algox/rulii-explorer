import {html, nothing} from 'lit';
import {glyph, icon} from './icons.js';
import {kindLabel, sourceText, typeLabel} from '../descriptor/format.js';
import {routes} from '../routing/router.js';

/**
 * Small shared templates: badges, links, cards, copy buttons. Every page composes these so the
 * same thing always looks the same (VQ-1).
 */

export function typeBadge(type, options = {}) {
    return html`<span class=${'rx-type-badge rx-type-' + type}>${glyph(type, {size: 10, undescribed: options.undescribed})}${options.label || typeLabel(type)}</span>`;
}

/** A link to an artifact with its glyph; hover shows the hover card. */
export function artifactLink(artifact, options = {}) {
    if (!artifact) return nothing;
    const cls = options.class || ('rx-art-link rx-art-link-' + artifact.type);
    return html`<a class=${cls} href=${routes.artifact(artifact)} data-hover=${artifact.id}>${options.glyph === false ? nothing : glyph(artifact.type, {size: options.glyphSize || 11})}${options.text || artifact.name}</a>`;
}

/** A binding chip linking to the binding page; `direction` is '←' for reads, '→' for writes. */
export function bindingChip(path, direction, index) {
    const root = path.split(/[.[(]/)[0];
    const known = index && index.bindings.has(root);
    const text = direction ? path + ' ' + direction : path;
    return known
        ? html`<a class="rx-chip" href=${routes.binding(root)} title=${'Binding ' + root}>${text}</a>`
        : html`<span class="rx-chip" title=${'Binding ' + root}>${text}</span>`;
}

export function kindCaption(artifact) {
    return html`<span class="rx-kind">${kindLabel(artifact)}</span>`;
}

export function sourceInline(artifact) {
    const text = sourceText(artifact.source);
    return text ? html`<span class="rx-sep-dot"></span><span class="rx-source-inline">${text}</span>` : nothing;
}

export function breadcrumb(items) {
    return html`<nav class="rx-breadcrumb" aria-label="Breadcrumb">${items.map((item, i) => html`${i ? icon('chevronRight', {size: 12}) : nothing}${item.href
        ? html`<a href=${item.href} class=${item.mono ? 'rx-mono' : ''}>${item.text}</a>`
        : html`<span aria-current="page">${item.text}</span>`}`)}</nav>`;
}

/** A card with an optional heading and right-hand action. */
export function card(title, body, options = {}) {
    const cls = 'rx-card' + (options.class ? ' ' + options.class : '');
    return html`<section class=${cls} aria-label=${options.ariaLabel || title || nothing}>
        ${title ? html`<div class="rx-card-head${options.headStart ? ' rx-card-head-start' : ''}"><h2 class=${'rx-h2' + (options.titleClass ? ' ' + options.titleClass : '')}>${title}</h2>${options.action || nothing}</div>` : nothing}
        ${body}
    </section>`;
}

let toastTimer = null;

/** Shows a short confirmation at the bottom of the screen. */
export function toast(message) {
    let el = document.querySelector('.rx-toast');
    if (!el) {
        el = document.createElement('div');
        el.className = 'rx-toast';
        el.setAttribute('role', 'status');
        document.body.appendChild(el);
    }
    el.textContent = message;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.remove(), 1800);
}

export async function copyText(text, done = 'Copied') {
    try {
        await navigator.clipboard.writeText(text);
        toast(done);
    } catch (e) {
        toast('Copy failed');
    }
}

export function copyButton(text, label, options = {}) {
    return html`<button type="button" class=${options.class || 'rx-icon-btn-bare rx-icon-btn'} aria-label=${label} title=${label} @click=${() => copyText(text)}>${icon('copy', {size: options.size || 13, width: 1.9})}</button>`;
}

export function copyLinkButton(label = 'Copy link to this page') {
    return html`<button type="button" class="rx-icon-btn" aria-label=${label} title=${label} @click=${() => copyText(location.href, 'Link copied')}>${icon('link', {size: 15, width: 1.9})}</button>`;
}

/** The "Show in graph" button, present but inert until the graphs arrive in M4. */
export function graphButton(label = 'Show in graph') {
    return html`<button type="button" class="rx-btn rx-btn-secondary" aria-disabled="true" title="Graphs arrive with the next milestone">${icon('graph', {size: 14, width: 1.9})}${label}</button>`;
}

export function severityDots(counts) {
    const parts = [];
    if (counts.error) parts.push(html`<span><span class="rx-dot rx-dot-lg rx-dot-error"></span>${counts.error} ${counts.error === 1 ? 'error' : 'errors'}</span>`);
    if (counts.warning) parts.push(html`<span><span class="rx-dot rx-dot-lg rx-dot-warning"></span>${counts.warning} ${counts.warning === 1 ? 'warning' : 'warnings'}</span>`);
    if (counts.info) parts.push(html`<span><span class="rx-dot rx-dot-lg rx-dot-info"></span>${counts.info} info</span>`);
    return parts;
}

export function requiredPill(parameter) {
    return parameter.required === false ? html`<span class="rx-pill">Optional</span>` : html`<span class="rx-pill">Required</span>`;
}
