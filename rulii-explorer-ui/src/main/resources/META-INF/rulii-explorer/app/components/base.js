import {LitElement} from 'lit';
import {store} from '../state/store.js';

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

    /** @returns {import('../state/store.js').State} */
    get state() {
        return store.state;
    }

    get store() {
        return store;
    }
}
