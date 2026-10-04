import {html} from 'lit';

/**
 * Inline SVG icons and the artifact type glyphs (DESIGN-SYSTEM §3). Stroke icons on a 24-box,
 * 1.8 to 2.2 wide, round caps; glyphs are filled shapes on a 12-box. Always decorative: the
 * text next to them carries the meaning.
 */

const PATHS = {
    grid: 'M3.5 3.5h7v7h-7zM13.5 3.5h7v7h-7zM3.5 13.5h7v7h-7zM13.5 13.5h7v7h-7z',
    warning: 'M12 3.5l9 16H3zM12 10v4.5M12 17.2v.3',
    search: 'M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0M20 20l-3.5-3.5',
    moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z',
    sun: 'M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4',
    chevronRight: 'M9 6l6 6-6 6',
    chevronDown: 'M6 9l6 6 6-6',
    arrowRight: 'M5 12h14M13 6l6 6-6 6',
    close: 'M6 6l12 12M18 6L6 18',
    link: 'M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7',
    copy: 'M8.5 8.5h12v12h-12zM15.5 8.5V5.5a2 2 0 0 0-2-2h-8a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3',
    graph: 'M2.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M16.5 5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M16.5 19a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M7.5 12H12V5h4.5M12 12v7h4.5',
    lock: 'M4.5 10.5h15v10h-15zM8 10.5V7.5a4 4 0 0 1 8 0v3',
    info: 'M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M12 11v5.5M12 7.5v.5',
    error: 'M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M9 9l6 6M15 9l-6 6',
    check: 'M5 12.5l4.5 4.5L19 7.5',
    expand: 'M7 15l5 5 5-5M7 9l5-5 5 5',
    collapse: 'M7 20l5-5 5 5M7 4l5 5 5-5',
    reload: 'M20 12a8 8 0 1 1-2.6-5.9M20 4v5h-5',
    zoomOut: 'M5 12h14',
    zoomIn: 'M5 12h14M12 5v14',
    fit: 'M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5',
    minimap: 'M3.5 5h17v14h-17zM12 11h6v5h-6z',
    chevronUp: 'M6 15l6-6 6 6',
    help: 'M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M9.3 9.6a2.8 2.8 0 1 1 3.9 2.6c-.8.4-1.2 1-1.2 1.8M12 17v.3',
    globe: 'M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M3 12h18M12 3a13.5 13.5 0 0 1 0 18M12 3a13.5 13.5 0 0 0 0 18',
    file: 'M6.5 3h7.5l4.5 4.5V21h-12zM14 3v4.5h4.5',
    upload: 'M12 16V5M7.5 9.5L12 5l4.5 4.5M4.5 19.5h15'
};

/**
 * A stroke icon.
 * @param {keyof PATHS} name
 * @param {{size?: number, width?: number, class?: string}} [options]
 */
export function icon(name, options = {}) {
    const size = options.size ?? 16;
    const width = options.width ?? 2;
    return html`<svg class=${'rx-icon ' + (options.class || '')} width=${size} height=${size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width=${width} stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d=${PATHS[name]}></path></svg>`;
}

/**
 * The type glyph: circle (rule), rounded square (rule set), hexagon (rule flow). A dashed
 * outline marks an artifact that could not be described; a neutral dot marks a binding.
 * @param {'rule'|'ruleset'|'ruleflow'|'binding'} type
 * @param {{size?: number, undescribed?: boolean}} [options]
 */
export function glyph(type, options = {}) {
    const size = options.size ?? 12;
    const cls = 'rx-glyph rx-glyph-' + type;
    if (options.undescribed) {
        return html`<svg class=${cls} width=${size} height=${size} viewBox="0 0 12 12" aria-hidden="true"><circle cx="6" cy="6" r="4.5" fill="none" stroke-dasharray="2 1.6" style="stroke: var(--rx-rule); stroke-width: 1.4"></circle></svg>`;
    }
    switch (type) {
        case 'ruleset':
            return html`<svg class=${cls} width=${size} height=${size} viewBox="0 0 12 12" aria-hidden="true"><rect x="1.5" y="1.5" width="9" height="9" rx="2.5"></rect></svg>`;
        case 'ruleflow':
            return html`<svg class=${cls} width=${size} height=${size} viewBox="0 0 12 12" aria-hidden="true"><polygon points="6,0.8 10.5,3.4 10.5,8.6 6,11.2 1.5,8.6 1.5,3.4"></polygon></svg>`;
        case 'binding':
            return html`<svg class=${cls} width=${size} height=${size} viewBox="0 0 12 12" aria-hidden="true"><circle cx="6" cy="6" r="3.5"></circle></svg>`;
        default:
            return html`<svg class=${cls} width=${size} height=${size} viewBox="0 0 12 12" aria-hidden="true"><circle cx="6" cy="6" r="4.5"></circle></svg>`;
    }
}

/** The rulii wordmark with the gradient arcs (brand/rulii-logo.svg, inlined so the ink follows the theme). */
export function logo(height = 34) {
    return html`<svg class="rx-logo" viewBox="6 0 162 146" style=${'height: ' + height + 'px'} role="img" aria-label="rulii">
        <defs>
            <linearGradient id="rx-lg-o" gradientUnits="userSpaceOnUse" x1="14" y1="142" x2="116" y2="40"><stop offset="0.10" stop-color="#833EAE"></stop><stop offset="0.30" stop-color="#BB4683"></stop><stop offset="0.47" stop-color="#E1505F"></stop><stop offset="0.65" stop-color="#EB693F"></stop><stop offset="0.85" stop-color="#F5841D"></stop></linearGradient>
            <linearGradient id="rx-lg-m" gradientUnits="userSpaceOnUse" x1="30.5" y1="125" x2="106.25" y2="49.25"><stop offset="0" stop-color="#F0762F"></stop><stop offset="0.22" stop-color="#E55B52"></stop><stop offset="0.37" stop-color="#DA4A6A"></stop><stop offset="0.56" stop-color="#B24589"></stop><stop offset="0.72" stop-color="#9140A3"></stop><stop offset="0.85" stop-color="#833EAE"></stop></linearGradient>
            <linearGradient id="rx-lg-i" gradientUnits="userSpaceOnUse" x1="47.5" y1="105" x2="95.75" y2="56.75"><stop offset="0.03" stop-color="#B34588"></stop><stop offset="0.27" stop-color="#D4496F"></stop><stop offset="0.48" stop-color="#E2535C"></stop><stop offset="0.67" stop-color="#E7604C"></stop><stop offset="0.96" stop-color="#ED6F38"></stop></linearGradient>
        </defs>
        <g fill="none" stroke-width="10"><path d="M14 142V77.5A70 70 0 0 1 84 7.5" stroke="url(#rx-lg-o)"></path><path d="M30.5 125V79.5A53.5 53.5 0 0 1 84 26" stroke="url(#rx-lg-m)"></path><path d="M47.5 105V80.5A36.5 36.5 0 0 1 84 44" stroke="url(#rx-lg-i)"></path></g>
        <g transform="translate(63.5 44.5) scale(0.24)"><g fill="none" style="stroke: var(--rx-logo-ink)" stroke-width="35"><path d="M17.5 247V159.5A71 71 0 0 1 88.5 88.5H106"></path><path d="M88.5 129V157.5A71 71 0 0 0 230.5 157.5V71"></path><path d="M289.5 1V247"></path><path d="M346.5 71V247"></path><path d="M403.5 71V247"></path></g><circle cx="346.5" cy="17.5" r="17.5" fill="#F5841D"></circle><circle cx="403.5" cy="17.5" r="17.5" fill="#833EAE"></circle></g>
    </svg>`;
}

/** The three arcs alone, drawn in line style for empty states. */
export function arcs(height = 116) {
    return html`<svg class="rx-arcs" viewBox="8 2 78 141" style=${'height: ' + height + 'px'} aria-hidden="true"><g fill="none" stroke-width="10"><path d="M14 142V77.5A70 70 0 0 1 84 7.5"></path><path d="M30.5 125V79.5A53.5 53.5 0 0 1 84 26"></path><path d="M47.5 105V80.5A36.5 36.5 0 0 1 84 44"></path></g></svg>`;
}

/** A severity icon in its soft box: error (cross), warning (triangle), info (i). */
export function severityIcon(severity, size = 16) {
    const name = severity === 'error' ? 'error' : severity === 'warning' ? 'warning' : 'info';
    return icon(name, {size, width: 2.2});
}
