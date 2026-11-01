/**
 * The application state: one plain object, replaced on every change, with a change event that
 * components subscribe to (SOLUTION §9.1). The descriptor itself is immutable once loaded;
 * everything else here is small UI state.
 *
 * @typedef {object} State
 * @property {'loading'|'ready'|'empty'|'not-exposed'|'unauthorized'|'forbidden'|'failed'|'unreachable'|'unsupported'|'pick-source'} status
 * @property {import('../descriptor/source.js').Source} source  where the descriptor comes from
 * @property {object|null} descriptor   the parsed descriptor (see descriptor/types.js)
 * @property {object|null} index        indexes built from it (descriptor/indexes.js)
 * @property {object|null} error        details for the failure states: {status, message, detail}
 * @property {object} route             the parsed hash route (routing/router.js)
 * @property {'light'|'dark'|'system'} theme
 * @property {'plain'|'raw'} exprView   the global Plain / Raw choice (FR-17)
 * @property {number} sidebarWidth      the sidebar's width in pixels, remembered per browser
 * @property {Set<string>} expanded     expanded sidebar packages
 * @property {boolean} paletteOpen
 * @property {string|null} paletteQuery  a query to open the palette with, e.g. "tag:vip"; consumed on open
 * @property {boolean} helpOpen       the help sheet (?)
 * @property {boolean} sourceOpen     the "Open a descriptor" dialog
 */

const THEME_KEY = 'rx.theme';
const EXPR_KEY = 'rx.exprView';
const SIDEBAR_KEY = 'rx.sidebarWidth';

/** The sidebar's width in pixels: the token's default, and the limits a user can drag it to. */
export const SIDEBAR_DEFAULT = 264;
export const SIDEBAR_MIN = 220;
export const SIDEBAR_MAX = 480;

function rememberedWidth() {
    try {
        const value = Number(localStorage.getItem(SIDEBAR_KEY));
        return value >= SIDEBAR_MIN && value <= SIDEBAR_MAX ? Math.round(value) : SIDEBAR_DEFAULT;
    } catch (e) {
        return SIDEBAR_DEFAULT;
    }
}

function forget(key) {
    try {
        localStorage.removeItem(key);
    } catch (e) { /* as above */ }
}

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
        source: {kind: 'application'},
        descriptor: null,
        index: null,
        error: null,
        route: {name: 'overview', section: 'rules', query: {}},
        theme: remembered(THEME_KEY, ['light', 'dark'], 'system'),
        exprView: remembered(EXPR_KEY, ['plain', 'raw'], 'plain'),
        sidebarWidth: rememberedWidth(),
        expanded: new Set(),
        paletteOpen: false,
        paletteQuery: null,
        helpOpen: false,
        sourceOpen: false
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

    // ── Sidebar width (per browser) ──────────────────────────────────────────

    clampSidebarWidth(width) {
        return Math.round(Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, width)));
    }

    /** Puts the width on the shell's grid through the token; the default removes the override so the token rules. */
    applySidebarWidth(width) {
        const root = document.documentElement;
        if (width === SIDEBAR_DEFAULT) root.style.removeProperty('--rx-sidebar-w');
        else root.style.setProperty('--rx-sidebar-w', width + 'px');
    }

    setSidebarWidth(width) {
        const next = this.clampSidebarWidth(width);
        if (next === SIDEBAR_DEFAULT) forget(SIDEBAR_KEY);
        else remember(SIDEBAR_KEY, next);
        this.applySidebarWidth(next);
        this.set({sidebarWidth: next});
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

    /** Opens or closes several folders at once ("Expand all" / "Collapse all" on a sidebar group). */
    setExpanded(keys, open) {
        const expanded = new Set(this.#state.expanded);
        for (const key of keys) {
            if (open) expanded.add(key);
            else expanded.delete(key);
        }
        this.set({expanded});
    }
}

export const store = new Store();
