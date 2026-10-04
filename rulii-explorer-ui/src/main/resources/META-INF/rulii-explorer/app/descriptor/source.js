/**
 * Where the descriptor comes from. By default the page's `rulii-descriptor` meta tag names the
 * application's own Actuator endpoint. The address can also name another one:
 *
 *   /rulii?descriptor=https://staging:8080/actuator/rulii#/     another application's endpoint
 *   /rulii?descriptor=https://ci.example.com/rules.json#/        a descriptor saved as a file
 *
 * and a file from the user's machine can be opened in the browser; it then lives in memory only,
 * so after a reload the explorer asks for it again (its name is kept for the session). The fetch
 * runs in the browser: nothing is proxied through the application. The page's `rulii-sources`
 * meta tag set to `application` turns all of this off.
 *
 * @typedef {object} Source
 * @property {'application'|'url'|'file'} kind
 * @property {string} [url]    application and url: the absolute address of the descriptor
 * @property {string} [name]   file: the file name
 * @property {string} [text]   file: the content, only while opening it
 * @property {boolean} [invalid] url: the address is not a web address
 */

export const PARAM = 'descriptor';
const FILE_KEY = 'rx.source.file';

/** The application's own descriptor URL, from the meta tag, resolved against the page. */
export function applicationUrl() {
    const meta = document.querySelector('meta[name="rulii-descriptor"]');
    return new URL(meta && meta.content ? meta.content : '/actuator/rulii', document.baseURI).toString();
}

/** False when the page says `<meta name="rulii-sources" content="application">`. */
export function externalSourcesAllowed() {
    const meta = document.querySelector('meta[name="rulii-sources"]');
    return !(meta && meta.content === 'application');
}

/**
 * Resolves the source from its inputs; pure, so it can be tested without a page.
 *
 * @param {{search: string, allowed: boolean, stored: string|null, applicationUrl: string, base?: string}} inputs
 * @returns {Source}
 */
export function resolveSource({search, allowed, stored, applicationUrl: app, base}) {
    if (allowed) {
        const value = new URLSearchParams(search || '').get(PARAM);
        if (value && value.trim()) {
            const url = absoluteUrl(value.trim(), base);
            return url ? {kind: 'url', url} : {kind: 'url', url: value.trim(), invalid: true};
        }
        if (stored) {
            try {
                const file = JSON.parse(stored);
                if (file && typeof file.name === 'string') return {kind: 'file', name: file.name};
            } catch (e) { /* not ours */ }
        }
    }
    return {kind: 'application', url: app};
}

/** The source the page is showing, from the address, the session and the meta tags. */
export function currentSource() {
    return resolveSource({search: location.search, allowed: externalSourcesAllowed(), stored: storedFile(), applicationUrl: applicationUrl(), base: document.baseURI});
}

/** An absolute http(s) URL for the value, or null when it is not one. */
export function absoluteUrl(value, base = document.baseURI) {
    try {
        const url = new URL(value, base);
        return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
    } catch (e) {
        return null;
    }
}

/** True when the URL is on another origin than the page: the fetch then needs CORS and carries no credentials. */
export function isCrossOrigin(url) {
    try {
        return new URL(url, document.baseURI).origin !== location.origin;
    } catch (e) {
        return false;
    }
}

/** True when a secure page would read a plain-http address, which browsers refuse. */
export function isMixedContent(url) {
    return location.protocol === 'https:' && String(url).startsWith('http:');
}

/** What the top bar calls the source: "Live", the remote host, or the file name. */
export function sourceLabel(source) {
    if (!source || source.kind === 'application') return 'Live';
    if (source.kind === 'file') return source.name;
    try {
        return new URL(source.url).host;
    } catch (e) {
        return source.url;
    }
}

/** The page address for a source: the same path and hash, with `?descriptor=` set or removed. */
export function pageUrlFor(url, hash = '#/') {
    const params = new URLSearchParams(location.search);
    if (url) params.set(PARAM, url);
    else params.delete(PARAM);
    const query = params.toString();
    return location.pathname + (query ? '?' + query : '') + hash;
}

export function storedFile() {
    try {
        return sessionStorage.getItem(FILE_KEY);
    } catch (e) {
        return null;
    }
}

export function rememberFile(name) {
    try {
        sessionStorage.setItem(FILE_KEY, JSON.stringify({name}));
    } catch (e) { /* no session storage: the file is forgotten on reload */ }
}

export function forgetFile() {
    try {
        sessionStorage.removeItem(FILE_KEY);
    } catch (e) { /* nothing to forget */ }
}
