import {LitElement} from 'lit';
import {store} from '../state/store.js';

/** True when a key press is not typing into a field: shortcuts may act on it. */
export function keyIsFree(event) {
    const t = event.target;
    return !(t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable));
}

/** True when no dialog (the palette or the help sheet) is open. */
export function noDialogOpen() {
    return !document.querySelector('dialog[open]');
}

/**
 * The base for every explorer element. Renders into the light DOM, so the one global
 * stylesheet (app/design/app.css) styles everything and the design tokens need no plumbing.
 * Re-renders whenever the store changes.
 */
export class RxElement extends LitElement {

    #unsubscribe = null;

    createRenderRoot() {
        return this;
    }

    connectedCallback() {
        super.connectedCallback();
        this.#unsubscribe = store.subscribe(() => this.requestUpdate());
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this.#unsubscribe) this.#unsubscribe();
        this.#unsubscribe = null;
    }

    /**
     * A store change reaches every element at once; the shell may swap the page out before an
     * element on the way out gets its turn, so a detached element never renders (a descriptor
     * switch empties the store while the old page's elements are still queued).
     */
    shouldUpdate() {
        return this.isConnected;
    }

    /** @returns {import('../state/store.js').State} */
    get state() {
        return store.state;
    }

    get store() {
        return store;
    }
}
