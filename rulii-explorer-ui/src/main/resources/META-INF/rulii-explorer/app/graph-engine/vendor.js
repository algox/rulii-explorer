/**
 * Lazy loading of the UMD libraries the graphs need (SOLUTION §9.1): d3 and ELK ship as classic
 * scripts, so they are added to the page the first time a graph opens and never before.
 * Both resolve relative to this module, which works under the starter's versioned path and
 * from a plain static server alike.
 */

const loading = new Map();

function loadScript(url) {
    if (loading.has(url)) return loading.get(url);
    const promise = new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = url;
        script.async = true;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error('Could not load ' + url));
        document.head.appendChild(script);
    });
    loading.set(url, promise);
    return promise;
}

/** @returns {Promise<typeof d3>} */
export async function loadD3() {
    if (!globalThis.d3) await loadScript(new URL('../../vendor/d3/d3.v7.min.js', import.meta.url).href);
    return globalThis.d3;
}

let elkInstance = null;

/** The ELK layout engine, running in its Web Worker. */
export async function loadElk() {
    if (elkInstance) return elkInstance;
    if (!globalThis.ELK) await loadScript(new URL('../../vendor/elk/elk-api.js', import.meta.url).href);
    elkInstance = new globalThis.ELK({workerUrl: new URL('../../vendor/elk/elk-worker.min.js', import.meta.url).href});
    return elkInstance;
}

/** Text width in CSS pixels for the given font, measured once per string and font. */
const measureCache = new Map();
let context = null;

export function textWidth(text, font = '600 12.5px "Hanken Grotesk", system-ui, sans-serif') {
    const key = font + '|' + text;
    const cached = measureCache.get(key);
    if (cached !== undefined) return cached;
    if (!context) context = document.createElement('canvas').getContext('2d');
    context.font = font;
    const width = context.measureText(text).width;
    measureCache.set(key, width);
    return width;
}

/** Shortens text with an ellipsis until it fits `maxWidth` in `font`. */
export function clipText(text, maxWidth, font) {
    if (textWidth(text, font) <= maxWidth) return text;
    let lo = 0, hi = text.length;
    while (lo < hi) {
        const mid = (lo + hi + 1) >> 1;
        if (textWidth(text.slice(0, mid) + '…', font) <= maxWidth) lo = mid;
        else hi = mid - 1;
    }
    return text.slice(0, Math.max(1, lo)) + '…';
}

export const FONT_NAME = '600 12.5px "Hanken Grotesk", system-ui, sans-serif';
export const FONT_CAPTION = '400 11px "Hanken Grotesk", system-ui, sans-serif';
export const FONT_MONO = '500 12px "JetBrains Mono", Consolas, monospace';
export const FONT_OVERLINE = '600 9.5px "Hanken Grotesk", system-ui, sans-serif';
