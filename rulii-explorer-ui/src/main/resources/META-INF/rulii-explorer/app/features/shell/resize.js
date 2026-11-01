import {html} from 'lit';
import {RxElement} from '../../components/base.js';
import {SIDEBAR_DEFAULT, SIDEBAR_MAX, SIDEBAR_MIN} from '../../state/store.js';

/**
 * The sidebar's right edge as a handle: drag it to make the sidebar wider or narrower, nudge it
 * with the arrow keys, double-click to put it back. The width lives in the store and is
 * remembered per browser; while dragging only the token moves, so nothing re-renders until the
 * pointer is released.
 */
class RxSidebarResize extends RxElement {

    render() {
        const width = this.state.sidebarWidth;
        return html`<div class="rx-sidebar-handle" role="separator" aria-orientation="vertical" aria-label="Resize the sidebar"
            aria-valuemin=${SIDEBAR_MIN} aria-valuemax=${SIDEBAR_MAX} aria-valuenow=${width} tabindex="0"
            title="Drag to resize the sidebar; double-click to put it back"
            @pointerdown=${this.down} @pointermove=${this.move} @pointerup=${this.up} @pointercancel=${this.up}
            @dblclick=${() => this.store.setSidebarWidth(SIDEBAR_DEFAULT)} @keydown=${this.key}></div>`;
    }

    /** The shell's left edge, so a width is the pointer's distance from it. */
    origin() {
        const shell = this.closest('.rx-shell');
        return shell ? shell.getBoundingClientRect().left : 0;
    }

    down(e) {
        if (e.button !== 0) return;
        e.preventDefault();
        e.currentTarget.setPointerCapture(e.pointerId);
        e.currentTarget.dataset.dragging = '';
        this.left = this.origin();
    }

    move(e) {
        if (!('dragging' in e.currentTarget.dataset)) return;
        this.store.applySidebarWidth(this.store.clampSidebarWidth(e.clientX - this.left));
    }

    up(e) {
        if (!('dragging' in e.currentTarget.dataset)) return;
        delete e.currentTarget.dataset.dragging;
        e.currentTarget.releasePointerCapture(e.pointerId);
        this.store.setSidebarWidth(e.clientX - this.left);
    }

    key(e) {
        const width = this.state.sidebarWidth;
        const step = e.shiftKey ? 48 : 16;
        const next = e.key === 'ArrowLeft' ? width - step
            : e.key === 'ArrowRight' ? width + step
            : e.key === 'Home' ? SIDEBAR_MIN
            : e.key === 'End' ? SIDEBAR_MAX
            : null;
        if (next === null) return;
        e.preventDefault();
        this.store.setSidebarWidth(next);
    }
}

customElements.define('rx-sidebar-resize', RxSidebarResize);
