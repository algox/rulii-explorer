import {loadD3} from './vendor.js';

/**
 * The canvas behind every graph (SOLUTION §9.3, spike decisions in RESULTS.md): a stage element
 * takes the pointer events, the SVG inside it is sized to the drawing and moved as one composited
 * layer with a CSS transform, which keeps panning and zooming at 60 fps. When a gesture ends the
 * layer is re-rasterised sharp. The stage also owns the dotted background, fit/zoom controls and
 * the minimap.
 */
export class GraphStage {

    /**
     * @param {HTMLElement} stage      the element that fills the canvas area (position: relative)
     * @param {{onZoom?: (k: number) => void, minScale?: number, maxScale?: number}} [options]
     */
    constructor(stage, options = {}) {
        this.stage = stage;
        this.options = options;
        this.svg = null;
        this.extent = {width: 0, height: 0};
        this.transform = null;
        this.minimap = null;
        this.minimapNodes = [];
        this.zoom = null;
        this.d3 = null;
        this.ready = loadD3().then(d3 => { this.d3 = d3; this.#install(); });
    }

    #install() {
        const d3 = this.d3;
        this.zoom = d3.zoom()
            .scaleExtent([this.options.minScale || 0.15, this.options.maxScale || 3])
            .filter(event => !event.ctrlKey || event.type === 'wheel')
            .on('start', () => { if (this.svg) this.svg.style.willChange = 'transform'; })
            .on('zoom', event => { this.transform = event.transform; this.#apply(); })
            .on('end', () => { if (this.svg) this.svg.style.willChange = 'auto'; });
        d3.select(this.stage).call(this.zoom).on('dblclick.zoom', null);
        this.transform = d3.zoomIdentity;
    }

    /** Adopts the drawn SVG (sized to the drawing) and fits it into view. */
    async setContent(svg, extent, minimapNodes = []) {
        await this.ready;
        if (this.svg && this.svg !== svg) this.svg.remove();
        this.svg = svg;
        this.extent = extent;
        this.minimapNodes = minimapNodes;
        svg.setAttribute('width', Math.ceil(extent.width));
        svg.setAttribute('height', Math.ceil(extent.height));
        svg.style.position = 'absolute';
        svg.style.left = '0';
        svg.style.top = '0';
        svg.style.overflow = 'visible';
        svg.style.transformOrigin = '0 0';
        if (!svg.parentNode) this.stage.insertBefore(svg, this.stage.firstChild);
        this.#drawMinimap();
    }

    fit(padding = 32, animate = false) {
        if (!this.svg || !this.d3) return;
        const w = this.stage.clientWidth, h = this.stage.clientHeight;
        if (!w || !h || !this.extent.width || !this.extent.height) return;
        const k = Math.min((w - padding * 2) / this.extent.width, (h - padding * 2) / this.extent.height, 1.25);
        const x = (w - this.extent.width * k) / 2, y = Math.max(padding, (h - this.extent.height * k) / 2);
        const t = this.d3.zoomIdentity.translate(x, y).scale(k);
        const sel = this.d3.select(this.stage);
        if (animate) sel.transition().duration(360).call(this.zoom.transform, t);
        else sel.call(this.zoom.transform, t);
    }

    zoomBy(factor) {
        if (!this.d3) return;
        this.d3.select(this.stage).transition().duration(200).call(this.zoom.scaleBy, factor);
    }

    /** Centres the view on a point of the drawing (in SVG coordinates), keeping the scale. */
    centerOn(x, y, animate = true) {
        if (!this.d3 || !this.transform) return;
        const w = this.stage.clientWidth, h = this.stage.clientHeight;
        const k = this.transform.k;
        const t = this.d3.zoomIdentity.translate(w / 2 - x * k, h / 2 - y * k).scale(k);
        const sel = this.d3.select(this.stage);
        if (animate) sel.transition().duration(360).call(this.zoom.transform, t);
        else sel.call(this.zoom.transform, t);
    }

    get scale() {
        return this.transform ? this.transform.k : 1;
    }

    #apply() {
        if (!this.svg || !this.transform) return;
        const t = this.transform;
        this.svg.style.transform = 'translate(' + t.x + 'px, ' + t.y + 'px) scale(' + t.k + ')';
        if (this.options.onZoom) this.options.onZoom(t.k);
        this.#updateMinimapViewport();
    }

    /** The minimap: the drawing's nodes as blocks, the viewport as an accent frame; click to pan there. */
    setMinimap(element) {
        this.minimap = element;
        if (element) {
            element.addEventListener('click', (e) => {
                const rect = element.getBoundingClientRect();
                const s = this.#minimapScale();
                if (!s) return;
                this.centerOn((e.clientX - rect.left - s.ox) / s.k, (e.clientY - rect.top - s.oy) / s.k);
            });
        }
        this.#drawMinimap();
    }

    #minimapScale() {
        if (!this.minimap || !this.extent.width) return null;
        const W = this.minimap.clientWidth || 140, H = this.minimap.clientHeight || 96;
        const k = Math.min((W - 12) / this.extent.width, (H - 12) / this.extent.height);
        return {k, ox: (W - this.extent.width * k) / 2, oy: (H - this.extent.height * k) / 2, W, H};
    }

    #drawMinimap() {
        if (!this.minimap) return;
        const s = this.#minimapScale();
        if (!s) { this.minimap.innerHTML = ''; return; }
        const ns = 'http://www.w3.org/2000/svg';
        this.minimap.innerHTML = '';
        const svg = document.createElementNS(ns, 'svg');
        svg.setAttribute('viewBox', '0 0 ' + s.W + ' ' + s.H);
        svg.setAttribute('width', s.W);
        svg.setAttribute('height', s.H);
        svg.setAttribute('aria-hidden', 'true');
        for (const n of this.minimapNodes) {
            const r = document.createElementNS(ns, 'rect');
            r.setAttribute('x', s.ox + n.x * s.k);
            r.setAttribute('y', s.oy + n.y * s.k);
            r.setAttribute('width', Math.max(2, n.width * s.k));
            r.setAttribute('height', Math.max(2, n.height * s.k));
            r.setAttribute('rx', n.isGroup ? 2 : 1.2);
            r.setAttribute('class', 'rx-mm-' + (n.kind || 'node') + (n.selected ? ' rx-mm-selected' : ''));
            svg.appendChild(r);
        }
        const view = document.createElementNS(ns, 'rect');
        view.setAttribute('class', 'rx-mm-view');
        view.setAttribute('rx', 3);
        svg.appendChild(view);
        this.minimap.appendChild(svg);
        this.minimapView = view;
        this.#updateMinimapViewport();
    }

    #updateMinimapViewport() {
        if (!this.minimapView || !this.transform) return;
        const s = this.#minimapScale();
        if (!s) return;
        const t = this.transform;
        const w = this.stage.clientWidth, h = this.stage.clientHeight;
        const x = s.ox + (-t.x / t.k) * s.k, y = s.oy + (-t.y / t.k) * s.k;
        this.minimapView.setAttribute('x', Math.max(0, x));
        this.minimapView.setAttribute('y', Math.max(0, y));
        this.minimapView.setAttribute('width', Math.min(s.W, (w / t.k) * s.k));
        this.minimapView.setAttribute('height', Math.min(s.H, (h / t.k) * s.k));
    }

    destroy() {
        if (this.d3 && this.zoom) this.d3.select(this.stage).on('.zoom', null);
        if (this.svg) this.svg.remove();
        this.svg = null;
    }
}
