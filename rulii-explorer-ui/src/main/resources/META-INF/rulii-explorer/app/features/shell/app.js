import {html} from 'lit';
import {RxElement} from '../../components/base.js';

/**
 * The shell: top bar, sidebar and the content area, which shows the screen for the current
 * route, or one of the designed states while the descriptor is loading or unavailable.
 */
class RxApp extends RxElement {

    connectedCallback() {
        super.connectedCallback();
        this.onKey = (e) => {
            const target = e.target;
            const typing = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable);
            if ((e.key === 'k' || e.key === 'K') && (e.metaKey || e.ctrlKey)) {
                e.preventDefault();
                this.store.set({paletteOpen: !this.state.paletteOpen});
            } else if (e.key === '/' && !typing && !this.state.paletteOpen) {
                e.preventDefault();
                this.store.set({paletteOpen: true});
            }
        };
        addEventListener('keydown', this.onKey);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        removeEventListener('keydown', this.onKey);
    }

    updated() {
        this.syncTitle();
        this.syncSidebar();
    }

    syncTitle() {
        const {descriptor, route, index} = this.state;
        const app = descriptor && descriptor.application && descriptor.application.name;
        let page = 'Overview';
        if (route.name === 'problems') page = 'Problems';
        else if (route.name === 'graph') page = 'Dependency graph';
        else if (route.name === 'artifact' && index) { const a = index.byId.get(route.id); page = a ? a.name : route.id; }
        else if (route.name === 'binding') page = route.id;
        else if (route.name === 'package') page = route.id;
        document.title = [page, app, 'rulii explorer'].filter(Boolean).join(' · ');
    }

    /** Keeps the sidebar package of the current rule open. */
    syncSidebar() {
        const {route, index} = this.state;
        if (route.name !== 'artifact' || !index) return;
        const a = index.byId.get(route.id);
        if (a && a.type === 'rule' && a.packageId) this.store.expand(a.packageId);
    }

    render() {
        return html`
            <div class="rx-shell">
                <rx-topbar></rx-topbar>
                <rx-sidebar></rx-sidebar>
                <main class="rx-main" id="rx-main" tabindex="-1">${this.content()}</main>
            </div>
            <rx-palette></rx-palette>
            <rx-hovercard></rx-hovercard>`;
    }

    content() {
        const {status, route} = this.state;
        if (status === 'loading') return html`<rx-states kind="loading"></rx-states>`;
        if (status !== 'ready' && status !== 'empty') return html`<rx-states kind=${status}></rx-states>`;
        if (status === 'empty' && route.name === 'overview') return html`<rx-states kind="empty"></rx-states>`;
        switch (route.name) {
            case 'overview': return html`<rx-overview></rx-overview>`;
            case 'problems': return html`<rx-problems></rx-problems>`;
            case 'graph': return html`<rx-graph></rx-graph>`;
            case 'artifact': return html`<rx-artifact></rx-artifact>`;
            case 'binding': return html`<rx-binding></rx-binding>`;
            case 'package': return html`<rx-package></rx-package>`;
            default: return html`<rx-states kind="missing"></rx-states>`;
        }
    }
}

customElements.define('rx-app', RxApp);
