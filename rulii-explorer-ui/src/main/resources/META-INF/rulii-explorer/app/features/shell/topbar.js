import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {icon, logo} from '../../components/icons.js';

const IS_MAC = /Mac|iPhone|iPad/.test(navigator.platform || '');

/** Top bar: brand, application name, the search field that opens the palette, the theme switch. */
class RxTopbar extends RxElement {

    render() {
        const {descriptor, status} = this.state;
        const name = descriptor && descriptor.application ? descriptor.application.name : null;
        const dark = this.store.effectiveTheme() === 'dark';
        const searchable = status === 'ready';
        return html`<header class="rx-topbar">
            <div class="rx-brand">
                ${logo(34)}
                <div class="rx-brand-divider"></div>
                <span class="rx-overline">Explorer</span>
            </div>
            <div class="rx-app-name">
                ${name ? html`<span>${name}</span>` : status === 'loading' ? html`<span class="rx-sk" style="width: 120px; height: 14px"></span>` : nothing}
            </div>
            <button type="button" class="rx-search" @click=${() => this.store.set({paletteOpen: true})} ?disabled=${!searchable} aria-label="Search rules, flows, bindings and error codes" aria-keyshortcuts=${IS_MAC ? 'Meta+K' : 'Control+K'}>
                ${icon('search', {size: 16})}
                <span class="rx-search-hint">Search rules, flows, error codes…</span>
                <kbd class="rx-kbd">${IS_MAC ? '⌘K' : 'Ctrl K'}</kbd>
            </button>
            <div class="rx-spacer"></div>
            <button type="button" class="rx-icon-btn rx-icon-btn-lg" @click=${() => this.store.set({helpOpen: true})} aria-label="Help: keyboard, addresses and the flowchart legend" title="Help (?)" aria-keyshortcuts="?">
                ${icon('help', {size: 17, width: 1.8})}
            </button>
            <button type="button" class="rx-icon-btn rx-icon-btn-lg" @click=${() => this.store.toggleTheme()} aria-label=${dark ? 'Switch to light theme' : 'Switch to dark theme'} title=${dark ? 'Switch to light theme' : 'Switch to dark theme'}>
                ${icon(dark ? 'sun' : 'moon', {size: 17, width: 1.8})}
            </button>
        </header>`;
    }
}

customElements.define('rx-topbar', RxTopbar);
