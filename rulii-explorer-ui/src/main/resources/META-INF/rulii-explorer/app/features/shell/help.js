import {html, svg} from 'lit';
import {RxElement} from '../../components/base.js';
import {icon} from '../../components/icons.js';

const IS_MAC = /Mac|iPhone|iPad/.test(navigator.platform || '');
const GUIDE_URL = 'https://github.com/algox/rulii-explorer/blob/main/GUIDE.md';

/**
 * The help sheet (?): the keyboard, the addresses every screen has, and how to read a flowchart.
 * One screen, no prose; the full guide is GUIDE.md in the repository.
 *
 */
class RxHelp extends RxElement {

    updated() {
        const dialog = this.querySelector('dialog');
        if (!dialog) return;
        if (this.state.helpOpen && !dialog.open) dialog.showModal();
        else if (!this.state.helpOpen && dialog.open) {
            if (dialog.contains(document.activeElement)) document.activeElement.blur();
            dialog.close();
        }
    }

    close() {
        if (this.state.helpOpen) this.store.set({helpOpen: false});
    }

    render() {
        return html`<dialog class="rx-help" aria-labelledby="rx-help-h" @cancel=${(e) => { e.preventDefault(); this.close(); }} @close=${() => this.close()} @click=${(e) => { if (e.target === e.currentTarget) this.close(); }}>
            <div class="rx-help-box">
                <div class="rx-help-head">
                    <h2 class="rx-h2 rx-h2-lg" id="rx-help-h">Using the explorer</h2>
                    <span class="rx-small">Press <kbd class="rx-kbd">?</kbd> on any screen</span>
                    <button type="button" class="rx-icon-btn-bare rx-icon-btn" aria-label="Close help" @click=${() => this.close()}>${icon('close', {size: 14})}</button>
                </div>
                <div class="rx-help-grid">
                    ${this.keyboard()}
                    ${this.addresses()}
                    ${this.legend()}
                </div>
                <div class="rx-help-foot">
                    <span>Everything on screen comes from the application’s rule descriptor. The explorer never guesses: compiled code says so, and placeholders show their keys and defaults, with the compiled value only when the application chose to share it.</span>
                    <span class="rx-spacer"></span>
                    <a class="rx-link rx-link-arrow" href=${GUIDE_URL} target="_blank" rel="noopener">Full guide${icon('arrowRight', {size: 13})}</a>
                </div>
            </div>
        </dialog>`;
    }

    keyboard() {
        const k = (text) => html`<kbd class="rx-kbd">${text}</kbd>`;
        const row = (keys, text) => html`<div class="rx-help-row"><dt>${keys}</dt><dd>${text}</dd></div>`;
        return html`<section class="rx-help-section" aria-labelledby="rx-help-keys">
            <h3 class="rx-overline" id="rx-help-keys">Keyboard</h3>
            <dl class="rx-help-rows">
                ${row(html`${k(IS_MAC ? '⌘ K' : 'Ctrl K')}<span class="rx-help-or">or</span>${k('/')}`, 'Search rules, flows, bindings and error codes')}
                ${row(html`${k('Tab')}${k('⇧ Tab')}`, 'In the search: cycle the type filter')}
                ${row(html`${k('↑')}${k('↓')}${k('↵')}`, 'Move through the results and open one')}
                ${row(html`${k('Esc')}`, 'Close the search, a panel or this sheet')}
                ${row(html`${k('?')}`, 'This sheet')}
                ${row(html`${k('F')}`, 'Fit a flowchart or graph to the screen')}
                ${row(html`${k('Tab')}<span class="rx-help-or">then</span>${k('↵')}`, 'Select a flowchart step or a graph node; its panel opens on the right')}
                ${row(html`<span class="rx-help-word">Drag</span><span class="rx-help-word">Wheel</span>`, 'Pan and zoom a flowchart or graph')}
                ${row(html`<span class="rx-help-word">Double-click</span>`, 'A graph node: focus the graph on it')}
                ${row(html`<span class="rx-help-word">Hover</span>`, 'Any artifact link: a card with its summary')}
            </dl>
        </section>`;
    }

    addresses() {
        const row = (addr, text) => html`<div class="rx-help-row"><dt><code>${addr}</code></dt><dd>${text}</dd></div>`;
        return html`<section class="rx-help-section rx-help-addr" aria-labelledby="rx-help-addrs">
            <h3 class="rx-overline" id="rx-help-addrs">Addresses</h3>
            <p class="rx-help-note">Every screen has one, including the selected step or node. <strong>Copy link</strong> on a page puts it on the clipboard; paste it into a review or a chat.</p>
            <dl class="rx-help-rows">
                ${row('#/', 'Overview')}
                ${row('#/problems?severity=error', 'Problems, filtered by severity')}
                ${row('#/rule/{id}', 'A rule, by its registry name')}
                ${row('#/ruleset/{id}', 'A rule set')}
                ${row('#/ruleflow/{id}?step=commands[1]', 'A flow with step 2 selected; add view=outline for the text view')}
                ${row('#/graph?focus={id}&depth=2', 'The graph around an artifact: 1, 2 or all steps away')}
                ${row('#/graph?selected={id}', 'The whole application, one artifact selected')}
                ${row('?descriptor={url}#/', 'Another application’s descriptor, or a saved JSON, by address; before the #')}
                ${row('#/binding/{name}', 'Who writes and reads a binding')}
                ${row('#/package/{id}', 'A package and what it defines')}
            </dl>
        </section>`;
    }

    legend() {
        const sw = (inner, w = 64, h = 28) => html`<svg class="rx-help-sw" width=${w} height=${h} viewBox=${'0 0 ' + w + ' ' + h} aria-hidden="true">${inner}</svg>`;
        const row = (swatch, name, text) => html`<div class="rx-help-row"><dt>${swatch}</dt><dd><strong>${name}</strong> ${text}</dd></div>`;
        const lines = (cls) => svg`<rect x="26" y="8" width="24" height="4" rx="2" class=${cls}></rect><rect x="26" y="16" width="30" height="4" rx="2" class="rx-help-sw-line"></rect>`;
        return html`<section class="rx-help-section rx-help-legend" aria-labelledby="rx-help-flow">
            <h3 class="rx-overline" id="rx-help-flow">Reading a flowchart</h3>
            <dl class="rx-help-rows">
                ${row(sw(svg`<g class="rx-fnode rx-fnode-step rx-fnode-ruleset"><rect class="rx-fshape" x="0.5" y="0.5" width="63" height="27" rx="7"></rect><rect class="rx-fglyph" x="8" y="8" width="12" height="12" rx="3"></rect>${lines('rx-help-sw-over')}</g>`), 'Step.', 'Runs a rule, rule set or flow, coloured by type. The caption says how: RUN, APPLY, ASYNC, AWAIT, BIND, EXECUTE. “→ name” keeps the result under that name.')}
                ${row(sw(svg`<g class="rx-fnode rx-fnode-decision"><polygon class="rx-fshape" points="0.5,14 10,0.5 54,0.5 63.5,14 54,27.5 10,27.5"></polygon>${lines('rx-help-sw-line')}</g>`), 'Decision.', 'WHEN leaves along yes or no. FOR EACH runs its box for each item, then leaves along done.')}
                ${row(sw(svg`<g class="rx-fcontainer"><rect x="0.5" y="0.5" width="63" height="27" rx="6"></rect><text x="7" y="11">SCOPE</text></g>`), 'Box.', 'A scope or the body of a loop. Steps inside share its bindings.')}
                ${row(sw(svg`<g class="rx-flane"><rect x="0.5" y="0.5" width="63" height="27" rx="6"></rect></g><path class="rx-fedge rx-fedge-async" d="M8 14H56"></path>`), 'Async lane.', 'Steps started in the background. The dashed result edge joins the AWAIT that waits for them.')}
                ${row(sw(svg`<path class="rx-fedge rx-fedge-handler" d="M2 14H14"></path><g class="rx-fnode rx-fnode-handler"><rect class="rx-fshape" x="14.5" y="2.5" width="49" height="23" rx="6"></rect></g>`), 'Handler.', 'Hangs off its step, labelled with the exception it catches. The global handler sits on its own.')}
                ${row(sw(svg`<g class="rx-fnode rx-fnode-exit"><rect class="rx-fshape" x="0.5" y="0.5" width="63" height="27" rx="13.5"></rect>${lines('rx-help-sw-line')}</g>`), 'Exit.', 'Stops the flow here, with or without a value. Start, Return and End are pills too: Start lists the parameters, Return the expression.')}
                ${row(sw(svg`<g class="rx-fnode rx-fnode-unresolved"><rect class="rx-fshape" x="0.5" y="0.5" width="63" height="27" rx="7"></rect><circle class="rx-fglyph" cx="14" cy="14" r="5.5"></circle></g>`), 'Not registered.', 'Nothing with that name exists, so the step fails when it runs. Problems says how to fix it.')}
                ${row(sw(svg`<g class="rx-fnode rx-fnode-step rx-fnode-rule rx-fnode-lookup"><rect class="rx-fshape" x="0.5" y="0.5" width="63" height="27" rx="7"></rect><circle class="rx-fglyph" cx="14" cy="14" r="5.5"></circle>${lines('rx-help-sw-over')}</g>`), 'Dashed outline.', 'Looked up by name or class from the registry when the flow runs.')}
                ${row(sw(svg`<path class="rx-flock" d="M8 13h10v8H8zM10 13v-3a3 3 0 0 1 6 0v3"></path><circle class="rx-gproblem rx-gproblem-warning" cx="44" cy="14" r="3.5"></circle>`), 'Lock, dot.', 'A lock means compiled code whose logic cannot be read. A dot marks a step with a problem, coloured by severity.')}
            </dl>
        </section>`;
    }
}

customElements.define('rx-help', RxHelp);
