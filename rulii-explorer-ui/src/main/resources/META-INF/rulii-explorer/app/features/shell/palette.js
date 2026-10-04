import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon} from '../../components/icons.js';
import {routes, navigate} from '../../routing/router.js';
import {markRanges, plainTokens, mark} from '../../components/expression.js';
import {identifierWords, joinWords} from '../../descriptor/format.js';
import {externalSourcesAllowed} from '../../descriptor/source.js';

const TYPE_CYCLE = [null, 'rule', 'ruleset', 'ruleflow', 'binding'];
const TYPE_NAMES = {rule: 'rules', ruleset: 'rule sets', ruleflow: 'rule flows', binding: 'bindings'};

/** The command palette (Ctrl/⌘ K): search over everything, grouped by type (S-Search). */
class RxPalette extends RxElement {

    static properties = {query: {state: true}, selected: {state: true}, typeFilter: {state: true}};

    constructor() {
        super();
        this.query = '';
        this.selected = 0;
        this.typeFilter = null;
    }

    updated() {
        const dialog = this.querySelector('dialog');
        if (!dialog) return;
        if (this.state.paletteOpen && !dialog.open) {
            this.query = '';
            this.selected = 0;
            this.typeFilter = null;
            dialog.showModal();
            const input = dialog.querySelector('input');
            if (input) { input.value = ''; input.focus(); }
        } else if (!this.state.paletteOpen && dialog.open) {
            if (dialog.contains(document.activeElement)) document.activeElement.blur(); // focus must not linger in a hidden field
            dialog.close();
        }
    }

    close() {
        if (this.state.paletteOpen) this.store.set({paletteOpen: false});
    }

    results() {
        const search = this.state.search;
        if (!search || !this.query.trim()) return [];
        return search.query(this.query, {type: this.typeFilter || undefined});
    }

    flat(groups) {
        return groups.flatMap(g => g.hits);
    }

    onKey(e, groups) {
        const hits = this.flat(groups);
        if (e.key === 'ArrowDown') { e.preventDefault(); this.selected = hits.length ? (this.selected + 1) % hits.length : 0; }
        else if (e.key === 'ArrowUp') { e.preventDefault(); this.selected = hits.length ? (this.selected - 1 + hits.length) % hits.length : 0; }
        else if (e.key === 'Enter') { e.preventDefault(); const hit = hits[this.selected]; if (hit) this.open(hit); }
        else if (e.key === 'Tab') {
            e.preventDefault();
            const i = TYPE_CYCLE.indexOf(this.typeFilter);
            this.typeFilter = TYPE_CYCLE[(i + (e.shiftKey ? TYPE_CYCLE.length - 1 : 1)) % TYPE_CYCLE.length];
            this.selected = 0;
        }
    }

    open(hit) {
        navigate(hit.kind === 'binding' ? routes.binding(hit.binding.name) : routes.artifact(hit.artifact));
        this.close();
    }

    render() {
        const groups = this.results();
        const hits = this.flat(groups);
        const words = identifierWords(this.query);
        let i = 0;
        return html`<dialog class="rx-palette" aria-label="Search everything" @cancel=${(e) => { e.preventDefault(); this.close(); }} @close=${() => this.close()} @click=${(e) => { if (e.target === e.currentTarget) this.close(); }}>
            <div class="rx-palette-box">
                <label class="rx-palette-field">
                    ${icon('search', {size: 18})}
                    <span class="rx-sr">Search rules, flows, bindings and error codes</span>
                    <input type="search" placeholder=${this.typeFilter ? 'Search ' + TYPE_NAMES[this.typeFilter] + '…' : 'Search rules, flows, error codes…'} autocomplete="off" spellcheck="false"
                           @input=${(e) => { this.query = e.target.value; this.selected = 0; }} @keydown=${(e) => this.onKey(e, groups)}
                           role="combobox" aria-expanded=${hits.length > 0} aria-controls="rx-palette-results" aria-activedescendant=${hits.length ? 'rx-opt-' + this.selected : nothing}>
                    ${this.typeFilter ? html`<span class="rx-pill">${TYPE_NAMES[this.typeFilter]}</span>` : nothing}
                    ${this.query.trim() ? html`<span class="rx-small">${hits.length} ${hits.length === 1 ? 'result' : 'results'}</span>` : nothing}
                    <kbd class="rx-kbd">esc</kbd>
                </label>
                <div class="rx-palette-list" role="listbox" id="rx-palette-results" aria-label="Results">
                    ${groups.map(g => html`
                        <div class="rx-palette-group rx-overline"><span>${g.label}</span><span>${g.hits.length}</span></div>
                        ${g.hits.map(hit => { const idx = i++; return this.option(hit, idx, words); })}`)}
                    ${this.query.trim() && !hits.length ? html`<div class="rx-palette-empty">Nothing matches “${this.query}”${this.typeFilter ? ' among ' + TYPE_NAMES[this.typeFilter] : ''}.</div>` : nothing}
                </div>
                <div class="rx-palette-foot">
                    <span><kbd class="rx-kbd">↑</kbd><kbd class="rx-kbd">↓</kbd>move</span>
                    <span><kbd class="rx-kbd">↵</kbd>open</span>
                    <span><kbd class="rx-kbd">tab</kbd>filter by type</span>
                    <span><kbd class="rx-kbd">esc</kbd>close</span>
                    <button type="button" class="rx-palette-help" @click=${() => this.store.set({paletteOpen: false, helpOpen: true})}><kbd class="rx-kbd">?</kbd>help</button>
                    ${externalSourcesAllowed() ? html`<button type="button" class="rx-palette-help" @click=${() => this.store.set({paletteOpen: false, sourceOpen: true})}>${icon('globe', {size: 12, width: 2})}open a descriptor</button>` : nothing}
                    <span class="rx-spacer"></span>
                    <span>Names, conditions, error codes and bindings</span>
                </div>
            </div>
        </dialog>`;
    }

    option(hit, idx, words) {
        const selected = idx === this.selected;
        const index = this.state.index;
        if (hit.kind === 'binding') {
            const b = hit.binding;
            const readers = (b.readBy || []).map(id => (index.byId.get(id) || {name: id}).name);
            return html`<div class="rx-option" role="option" id=${'rx-opt-' + idx} aria-selected=${selected} @click=${() => this.open(hit)} @mousemove=${() => { if (this.selected !== idx) this.selected = idx; }}>
                ${glyph('binding', {size: 12})}
                <div>
                    <span class="rx-opt-name rx-mono">${markRanges(b.name, hit.nameMatches)}</span>
                    <span class="rx-opt-snip">${readers.length ? 'Read by ' + joinWords(readers.slice(0, 4)) + (readers.length > 4 ? ' and ' + (readers.length - 4) + ' more' : '') : 'Written but never read'}</span>
                </div>
                <span class="rx-opt-meta">binding</span>
                ${selected ? html`<kbd class="rx-kbd">↵</kbd>` : html`<span></span>`}
            </div>`;
        }
        const a = hit.artifact;
        return html`<div class="rx-option" role="option" id=${'rx-opt-' + idx} aria-selected=${selected} @click=${() => this.open(hit)} @mousemove=${() => { if (this.selected !== idx) this.selected = idx; }}>
            ${glyph(a.type, {size: 12, undescribed: index.undescribed.has(a.id)})}
            <div>
                <span class="rx-opt-name">${markRanges(a.name, hit.nameMatches)}</span>
                ${this.snippet(hit, words)}
            </div>
            <span class="rx-opt-meta">${a.packageId || ''}</span>
            ${selected ? html`<kbd class="rx-kbd">↵</kbd>` : html`<span></span>`}
        </div>`;
    }

    snippet(hit, words) {
        const s = hit.snippet;
        if (!s) return nothing;
        if (s.expression) return html`<span class="rx-opt-snip">${s.label ? s.label + ': ' : ''}${plainTokens(s.expression, {highlight: words})}</span>`;
        return html`<span class="rx-opt-snip">${mark(s.text, words)}</span>`;
    }
}

customElements.define('rx-palette', RxPalette);
