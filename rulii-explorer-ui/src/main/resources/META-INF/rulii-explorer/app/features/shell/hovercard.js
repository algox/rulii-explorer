import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {icon} from '../../components/icons.js';
import {typeBadge} from '../../components/common.js';
import {routes} from '../../routing/router.js';
import {kindLabel, typeLabel} from '../../descriptor/format.js';
import {artifactSummary} from '../../descriptor/summaries.js';

const DELAY = 350;

/**
 * The hover card (VQ-23): any link with data-hover="artifactId" shows the artifact's badge, name
 * and plain summary after a short pause. One instance serves the whole page.
 */
class RxHovercard extends RxElement {

    static properties = {artifactId: {state: true}, anchor: {state: true}};

    constructor() {
        super();
        this.artifactId = null;
        this.anchor = null;
        this.timer = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.onOver = (e) => {
            const link = e.target.closest && e.target.closest('[data-hover]');
            if (!link) return;
            clearTimeout(this.timer);
            this.timer = setTimeout(() => this.show(link), DELAY);
        };
        this.onOut = (e) => {
            const link = e.target.closest && e.target.closest('[data-hover]');
            if (!link) return;
            clearTimeout(this.timer);
            if (e.relatedTarget && this.contains(e.relatedTarget)) return;
            this.hide();
        };
        this.onFocus = (e) => {
            const link = e.target.closest && e.target.closest('[data-hover]');
            if (link) this.show(link);
        };
        this.onHide = (e) => { if (!e || e.type !== 'keydown' || e.key === 'Escape') this.hide(); };
        document.addEventListener('mouseover', this.onOver);
        document.addEventListener('mouseout', this.onOut);
        document.addEventListener('focusin', this.onFocus);
        document.addEventListener('focusout', this.onHide);
        document.addEventListener('scroll', this.onHide, true);
        document.addEventListener('keydown', this.onHide);
        this.addEventListener('mouseleave', () => this.hide());
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        document.removeEventListener('mouseover', this.onOver);
        document.removeEventListener('mouseout', this.onOut);
        document.removeEventListener('focusin', this.onFocus);
        document.removeEventListener('focusout', this.onHide);
        document.removeEventListener('scroll', this.onHide, true);
        document.removeEventListener('keydown', this.onHide);
    }

    show(link) {
        const id = link.getAttribute('data-hover');
        const rect = link.getBoundingClientRect();
        this.artifactId = id;
        this.anchor = {left: rect.left, top: rect.top, bottom: rect.bottom};
    }

    hide() {
        clearTimeout(this.timer);
        if (this.artifactId) { this.artifactId = null; this.anchor = null; }
    }

    render() {
        const index = this.state.index;
        const a = this.artifactId && index ? index.byId.get(this.artifactId) : null;
        if (!a || !this.anchor) return nothing;
        const width = 320;
        const left = Math.max(12, Math.min(this.anchor.left, innerWidth - width - 12));
        const below = this.anchor.bottom + 8;
        const style = below + 160 < innerHeight ? `left: ${left}px; top: ${below}px` : `left: ${left}px; bottom: ${innerHeight - this.anchor.top + 8}px`;
        return html`<div class="rx-hovercard" role="tooltip" style=${style}>
            <div class="rx-meta-row">${typeBadge(a.type, {undescribed: index.undescribed.has(a.id)})}<span class="rx-small">${[a.packageId, kindLabel(a)].filter(Boolean).join(' · ')}</span></div>
            <span class="rx-hc-name">${a.name}</span>
            <span class="rx-hc-sum">${a.description || artifactSummary(a, index)}</span>
            <a class="rx-link rx-link-arrow" href=${routes.artifact(a)}>Open ${typeLabel(a.type).toLowerCase()}${icon('arrowRight', {size: 13, width: 2.2})}</a>
        </div>`;
    }
}

customElements.define('rx-hovercard', RxHovercard);
