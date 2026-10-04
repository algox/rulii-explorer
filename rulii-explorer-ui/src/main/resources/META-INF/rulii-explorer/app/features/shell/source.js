import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {icon} from '../../components/icons.js';
import {absoluteUrl, externalSourcesAllowed, sourceLabel} from '../../descriptor/source.js';
import {openApplication, openFile, openUrl} from '../../boot.js';

/**
 * The "Open a descriptor" dialog: another application's Actuator endpoint or a descriptor JSON by
 * address, or a file from this machine. Opened from the top bar chip, the palette footer and the
 * failure states. Store: `sourceOpen`.
 */
class RxSource extends RxElement {

    static properties = {problem: {state: true}, dragging: {state: true}};

    constructor() {
        super();
        this.problem = null;
        this.dragging = false;
    }

    updated() {
        const dialog = this.querySelector('dialog');
        if (!dialog) return;
        if (this.state.sourceOpen && !dialog.open) {
            this.problem = null;
            dialog.showModal();
            const input = dialog.querySelector('input[type="url"]');
            if (input) { input.value = ''; input.focus(); }
        } else if (!this.state.sourceOpen && dialog.open) {
            if (dialog.contains(document.activeElement)) document.activeElement.blur();
            dialog.close();
        }
    }

    close() {
        if (this.state.sourceOpen) this.store.set({sourceOpen: false});
    }

    submit(e) {
        e.preventDefault();
        const input = this.querySelector('input[type="url"]');
        const value = (input && input.value || '').trim();
        if (!value) { this.problem = 'Enter the address of a descriptor.'; return; }
        // Typed here, an address is absolute or starts at this server's root; "nope" is not a page on this server.
        const url = /^(https?:\/\/|\/)/i.test(value) ? absoluteUrl(value) : null;
        if (!url) { this.problem = 'That is not a web address. It should start with http:// or https://.'; return; }
        this.problem = null;
        openUrl(url);
    }

    file(file) {
        if (!file) return;
        if (!/\.json$/i.test(file.name) && file.type && file.type !== 'application/json') {
            this.problem = '“' + file.name + '” is not a JSON file.';
            return;
        }
        this.problem = null;
        openFile(file);
    }

    render() {
        if (!externalSourcesAllowed()) return nothing;
        const source = this.state.source || {kind: 'application'};
        const origin = location.origin;
        return html`<dialog class="rx-source" aria-labelledby="rx-source-h" @cancel=${(e) => { e.preventDefault(); this.close(); }} @close=${() => this.close()} @click=${(e) => { if (e.target === e.currentTarget) this.close(); }}>
            <div class="rx-source-box">
                <div class="rx-source-head">
                    <h2 class="rx-h2 rx-h2-lg" id="rx-source-h">Open a descriptor</h2>
                    <button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label="Close" @click=${() => this.close()}>${icon('close', {size: 14})}</button>
                </div>
                <div class="rx-source-body">
                    <p class="rx-source-intro">Browse another application’s rules, or a descriptor saved as a file. The explorer reads it in this browser; nothing is sent to this application.</p>
                    <form class="rx-source-section" @submit=${(e) => this.submit(e)} novalidate>
                        <label class="rx-overline" for="rx-source-url">From an address</label>
                        <div class="rx-source-row">
                            <input id="rx-source-url" class="rx-input" type="url" placeholder="https://staging.example.com/actuator/rulii" autocomplete="off" spellcheck="false" @input=${() => { this.problem = null; }}>
                            <button type="submit" class="rx-btn rx-btn-primary">Open</button>
                        </div>
                        <p class="rx-small">An Actuator endpoint on another origin must allow this one: <span class="rx-mono">management.endpoints.web.cors.allowed-origins=${origin}</span>. A descriptor JSON works from any address that allows cross-origin reads. No sign-in travels with the request.</p>
                    </form>
                    <div class="rx-source-section">
                        <span class="rx-overline" id="rx-source-file-h">From a file</span>
                        <label class=${'rx-dropzone' + (this.dragging ? ' rx-dropzone-over' : '')} aria-labelledby="rx-source-file-h"
                               @dragover=${(e) => { e.preventDefault(); this.dragging = true; }} @dragleave=${() => { this.dragging = false; }}
                               @drop=${(e) => { e.preventDefault(); this.dragging = false; this.file(e.dataTransfer.files && e.dataTransfer.files[0]); }}>
                            ${icon('upload', {size: 20, width: 1.8})}
                            <span class="rx-dropzone-text"><strong>Choose a file</strong> or drop it here</span>
                            <span class="rx-small">A descriptor written with <span class="rx-mono">RuliiDescriptors.write</span> or saved from <span class="rx-mono">/actuator/rulii</span>. It stays in this tab: a reload asks for it again.</span>
                            <input type="file" class="rx-sr" accept=".json,application/json" aria-label="Choose a descriptor file" @change=${(e) => { this.file(e.target.files && e.target.files[0]); e.target.value = ''; }}>
                        </label>
                    </div>
                    ${this.problem ? html`<div class="rx-note rx-note-info rx-source-problem" role="alert">${icon('info', {size: 16, width: 2.2})}<span>${this.problem}</span></div>` : nothing}
                </div>
                <div class="rx-source-foot">
                    <span>Showing <strong>${source.kind === 'application' ? 'this application' : sourceLabel(source)}</strong>${source.kind === 'url' ? html` <span class="rx-mono rx-source-foot-url">${source.url}</span>` : nothing}</span>
                    <span class="rx-spacer"></span>
                    ${source.kind !== 'application' ? html`<button type="button" class="rx-btn rx-btn-secondary rx-btn-sm" @click=${() => openApplication()}>Back to this application</button>` : nothing}
                </div>
            </div>
        </dialog>`;
    }
}

customElements.define('rx-source', RxSource);
