/**
 * The application state: one plain object, replaced on every change, with a change event that
 * components subscribe to (SOLUTION §9.1). The descriptor itself is immutable once loaded;
 * everything else here is small UI state.
 *
 * @typedef {object} State
 * @property {'loading'|'ready'|'empty'|'not-exposed'|'unauthorized'|'forbidden'|'failed'|'unreachable'|'unsupported'} status
 * @property {object|null} descriptor   the parsed descriptor (see descriptor/types.js)
 * @property {object|null} index        indexes built from it (descriptor/indexes.js)
 * @property {object|null} error        details for the failure states: {status, message, detail}
 * @property {object} route             the parsed hash route (routing/router.js)
 * @property {'light'|'dark'|'system'} theme
 * @property {'plain'|'raw'} exprView   the global Plain / Raw choice (FR-17)
 * @property {Set<string>} expanded     expanded sidebar packages
 * @property {boolean} paletteOpen
 */

const THEME_KEY = 'rx.theme';
const EXPR_KEY = 'rx.exprView';

function remembered(key, allowed, fallback) {
    try {
        const value = localStorage.getItem(key);
        return allowed.includes(value) ? value : fallback;
    } catch (e) {
        return fallback;
    }
}

function remember(key, value) {
    try {
        localStorage.setItem(key, value);
    } catch (e) { /* private mode or blocked storage: the choice lasts for the session */ }
}

class Store extends EventTarget {

    /** @type {State} */
    #state = {
        status: 'loading',
        descriptor: null,
        index: null,
        error: null,
        route: {name: 'overview', query: {}},
        theme: remembered(THEME_KEY, ['light', 'dark'], 'system'),
        exprView: remembered(EXPR_KEY, ['plain', 'raw'], 'plain'),
        expanded: new Set(),
        paletteOpen: false
    };

    get state() {
        return this.#state;
    }

    /** Merges a patch into the state and notifies subscribers. */
    set(patch) {
        this.#state = {...this.#state, ...patch};
        this.dispatchEvent(new CustomEvent('change', {detail: this.#state}));
    }

    subscribe(listener) {
        this.addEventListener('change', listener);
        return () => this.removeEventListener('change', listener);
    }

    // ── Preferences ──────────────────────────────────────────────────────────

    /** The theme in effect, resolving 'system' against the OS preference. */
    effectiveTheme() {
        if (this.#state.theme !== 'system') return this.#state.theme;
        return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    toggleTheme() {
        const next = this.effectiveTheme() === 'dark' ? 'light' : 'dark';
        remember(THEME_KEY, next);
        this.applyTheme(next);
        this.set({theme: next});
    }

    applyTheme(theme) {
        const root = document.documentElement;
        if (theme === 'system') root.removeAttribute('data-theme');
        else root.setAttribute('data-theme', theme);
    }

    setExprView(view) {
        remember(EXPR_KEY, view);
        this.set({exprView: view});
    }

    toggleExpanded(packageId) {
        const expanded = new Set(this.#state.expanded);
        if (expanded.has(packageId)) expanded.delete(packageId);
        else expanded.add(packageId);
        this.set({expanded});
    }

    expand(packageId) {
        if (this.#state.expanded.has(packageId)) return;
        const expanded = new Set(this.#state.expanded);
        expanded.add(packageId);
        this.set({expanded});
    }
}

export const store = new Store();
