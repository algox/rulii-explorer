import {html, nothing, svg} from 'lit';
import {RxElement} from '../../components/base.js';
import {arcs, icon} from '../../components/icons.js';
import {copyText} from '../../components/common.js';
import {routes} from '../../routing/router.js';
import {descriptorUrl} from '../../descriptor/loader.js';

const EXPOSURE = '# application.properties\nmanagement.endpoints.web.exposure.include=rulii';

/** Loading, empty and failure states (S-Loading, S-States). The shell stays; the content area explains and offers one next step. */
class RxStates extends RxElement {

    static properties = {kind: {type: String}};

    render() {
        switch (this.kind) {
            case 'loading': return this.loading();
            case 'empty': return this.empty();
            case 'not-exposed': return this.notExposed();
            case 'unauthorized': return this.signIn(false);
            case 'forbidden': return this.signIn(true);
            case 'failed': return this.failed();
            case 'unreachable': return this.unreachable();
            case 'unsupported': return this.unsupported();
            default: return this.missing();
        }
    }

    loading() {
        const sk = (w, h, r = 4) => html`<div class="rx-sk" style=${`width: ${w}; height: ${h}px; border-radius: ${r}px`}></div>`;
        const statCard = (w) => html`<div class="rx-sk-card" style="height: 110px; padding: 16px 20px 18px; display: flex; flex-direction: column; gap: 10px">
            <div style="display: flex; align-items: center; gap: 8px">${sk('12px', 12, 6)}${sk(w + 'px', 10)}</div>${sk('44px', 32, 6)}${sk('180px', 10)}</div>`;
        return html`<div class="rx-page rx-page-overview" aria-busy="true">
            <div class="rx-progress" role="progressbar" aria-label="Loading the rule descriptor"><div></div></div>
            <div class="rx-page-head" style="gap: 10px">
                <div class="rx-overline rx-overline-accent rx-status-line" role="status" aria-live="polite">Reading the rule descriptor</div>
                ${sk('620px', 34, 8)}${sk('440px', 14)}
            </div>
            <div class="rx-stats" aria-hidden="true">${statCard(48)}${statCard(64)}${statCard(68)}${statCard(96)}</div>
            <div class="rx-overview-grid" aria-hidden="true">
                <section class="rx-card rx-card-xl rx-map-card">
                    <div class="rx-card-head rx-card-head-start">
                        <div style="display: flex; flex-direction: column; gap: 6px"><h2 class="rx-h2 rx-h2-xl rx-muted">Application map</h2>${sk('240px', 10)}</div>
                        <div style="width: 108px; height: 34px; border: 1px solid var(--rx-line); border-radius: 9px"></div>
                    </div>
                    <svg viewBox="0 0 652 330" class="rx-map" style="opacity: .9">
                        <g fill="none" style="stroke: var(--rx-sunken); stroke-width: 1.5"><path d="M176 164H210V70H246"></path><path d="M210 164H246"></path><path d="M210 164V258H246"></path><path d="M176 290H222V290H246"></path><path d="M416 70H452V22H480M452 46H480M452 70H480M452 94H480M452 118H480"></path><path d="M416 164H452V152H480M452 164V176H480"></path><path d="M416 258H452V246H480M452 258V270H480"></path></g>
                        <g style="fill: var(--rx-sunken)"><rect x="0" y="147" width="176" height="34" rx="17"></rect><rect x="0" y="273" width="176" height="34" rx="17"></rect><rect x="246" y="55" width="170" height="30" rx="8"></rect><rect x="246" y="149" width="170" height="30" rx="8"></rect><rect x="246" y="243" width="170" height="30" rx="8"></rect>
                        ${[22, 46, 70, 94, 118, 152, 176, 246, 270].map((y, i) => svg`<circle cx="486" cy=${y} r="5"></circle><rect x="498" y=${y - 5} width=${[96, 110, 84, 120, 100, 112, 104, 96, 108][i]} height="10" rx="4"></rect>`)}</g>
                    </svg>
                </section>
                <div class="rx-col">
                    <section class="rx-card rx-card-xl"><h2 class="rx-h2 rx-h2-lg rx-muted">Problems</h2>
                        ${[88, 74, 62].map(w => html`<div style="display: flex; gap: 10px; align-items: center">${sk('18px', 18, 6)}${sk(w + '%', 10)}</div>`)}</section>
                    <section class="rx-card rx-card-xl"><h2 class="rx-h2 rx-h2-lg rx-muted">Packages</h2>
                        <div style="display: grid; grid-template-columns: minmax(0, 1fr) 150px; align-items: center; column-gap: 12px; row-gap: 14px">
                            ${[[96, '100%'], [108, '64px'], [150, '28px'], [156, '14px']].map(([a, b]) => html`${sk(a + 'px', 10)}${sk(b, 8)}`)}</div></section>
                    <p class="rx-small" style="padding: 0 4px; font-size: 12.5px; line-height: 1.5">The explorer builds this page from the application’s rule descriptor. It usually takes a moment the first time; after that it’s cached.</p>
                </div>
            </div>
        </div>`;
    }

    empty() {
        const d = this.state.descriptor;
        const name = d && d.application && d.application.name ? d.application.name : 'this application';
        return html`<div class="rx-state rx-state-center">
            ${arcs(116)}
            <div class="rx-state-body">
                <h2>No rules in ${name} yet</h2>
                <p>The explorer is connected, but the rule registry is empty. Rules, rule sets and flows you define with <span class="rx-chip">@Rule</span>, the rulii builders or rulii XML appear here as soon as the application starts.</p>
                <p class="rx-small">Using XML files? Check that <span class="rx-mono">@RuleScan(xmlLocations = …)</span> points at them.</p>
                <div class="rx-state-actions">
                    <a class="rx-link rx-link-arrow" href="https://www.rulii.org" target="_blank" rel="noopener">Read the getting-started guide${icon('arrowRight', {size: 14})}</a>
                    <button type="button" class="rx-btn rx-btn-secondary" @click=${() => location.reload()}>Reload</button>
                </div>
            </div>
        </div>`;
    }

    notExposed() {
        const url = pathOf(descriptorUrl());
        return html`<div class="rx-state rx-state-center" style="align-items: flex-start">
            <span class="rx-state-icon rx-state-icon-warning">${icon('warning', {size: 19})}</span>
            <div class="rx-state-body">
                <h2 class="rx-h-sm">The rule descriptor isn’t exposed</h2>
                <p>The explorer reads everything from <span class="rx-mono">${url}</span>, and this application doesn’t expose that endpoint over HTTP yet. Add it to your configuration:</p>
                <div class="rx-code-block"><code class="rx-code"><span class="rx-c-cm"># application.properties</span>\n<span class="rx-c-fn">management.endpoints.web.exposure.include</span><span class="rx-c-kw">=</span><span class="rx-c-str">rulii</span></code>
                    <button type="button" class="rx-copy-btn" aria-label="Copy configuration" @click=${() => copyText(EXPOSURE, 'Configuration copied')}>${icon('copy', {size: 12})}Copy</button></div>
                <p class="rx-small">Already exposing other endpoints? Add <span class="rx-mono">rulii</span> to the list, for example <span class="rx-mono">health,info,rulii</span>.</p>
                <div class="rx-state-actions">
                    <button type="button" class="rx-btn rx-btn-secondary" @click=${() => location.reload()}>Reload</button>
                    <a class="rx-link" href="https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.exposing" target="_blank" rel="noopener">How endpoint exposure works</a>
                </div>
            </div>
        </div>`;
    }

    signIn(forbidden) {
        const url = pathOf(descriptorUrl());
        return html`<div class="rx-state rx-state-center" style="align-items: flex-start">
            <span class="rx-state-icon rx-state-icon-lock">${icon('lock', {size: 18})}</span>
            <div class="rx-state-body">
                <h2 class="rx-h-sm">Sign in to see the rules</h2>
                <p>This application protects its actuator endpoints, which is the recommended setup for production. Sign in to the application in this browser, then reload this page.</p>
                ${forbidden ? html`<div class="rx-note rx-note-info" style="width: 100%">${icon('info', {size: 16, width: 2.2})}<span><span class="rx-strong">Already signed in?</span> Your account can’t open <span class="rx-mono">${url}</span> (HTTP 403). Ask whoever manages the application for access to its actuator endpoints.</span></div>` : nothing}
                <div class="rx-state-actions">
                    <button type="button" class="rx-btn rx-btn-primary" @click=${() => location.reload()}>Reload</button>
                    <a class="rx-link" href="https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.security" target="_blank" rel="noopener">Securing the explorer</a>
                </div>
            </div>
        </div>`;
    }

    failed() {
        const e = this.state.error || {};
        return html`<div class="rx-state rx-state-center" style="align-items: flex-start">
            <span class="rx-state-icon rx-state-icon-error">${icon('error', {size: 19})}</span>
            <div class="rx-state-body">
                <h2 class="rx-h-sm">The rule descriptor couldn’t be built</h2>
                <p>The application answered${e.httpStatus ? ' with HTTP ' + e.httpStatus : ''}, but describing its rules failed. The application itself is unaffected; the message below comes from it.</p>
                ${e.detail ? html`<div class="rx-code-block"><code class="rx-code">${e.detail}</code></div>` : nothing}
                <div class="rx-state-actions"><button type="button" class="rx-btn rx-btn-secondary" @click=${() => location.reload()}>Reload</button></div>
            </div>
        </div>`;
    }

    unreachable() {
        const e = this.state.error || {};
        return html`<div class="rx-state rx-state-center" style="align-items: flex-start">
            <span class="rx-state-icon rx-state-icon-error">${icon('error', {size: 19})}</span>
            <div class="rx-state-body">
                <h2 class="rx-h-sm">The application didn’t answer</h2>
                <p>The explorer could not reach <span class="rx-mono">${pathOf(descriptorUrl())}</span>. The application may be starting, stopped, or behind a proxy that blocks the request.</p>
                ${e.detail ? html`<p class="rx-small rx-mono">${e.detail}</p>` : nothing}
                <div class="rx-state-actions"><button type="button" class="rx-btn rx-btn-secondary" @click=${() => location.reload()}>Reload</button></div>
            </div>
        </div>`;
    }

    unsupported() {
        const e = this.state.error || {};
        return html`<div class="rx-state rx-state-center" style="align-items: flex-start">
            <span class="rx-state-icon rx-state-icon-warning">${icon('warning', {size: 19})}</span>
            <div class="rx-state-body">
                <h2 class="rx-h-sm">This descriptor is newer than the explorer</h2>
                <p>${e.message || 'The descriptor version is not supported.'} Upgrade the explorer UI to match the application.</p>
                <div class="rx-state-actions"><button type="button" class="rx-btn rx-btn-secondary" @click=${() => location.reload()}>Reload</button></div>
            </div>
        </div>`;
    }

    missing() {
        return html`<div class="rx-state rx-state-center">
            ${arcs(96)}
            <div class="rx-state-body">
                <h2>Nothing here</h2>
                <p>This address doesn’t match anything in the current descriptor. The artifact may have been renamed or removed since the link was made.</p>
                <div class="rx-state-actions"><a class="rx-btn rx-btn-secondary" href=${routes.overview()}>Back to the overview</a></div>
            </div>
        </div>`;
    }
}

function pathOf(url) {
    try { return new URL(url).pathname; } catch (e) { return url; }
}

customElements.define('rx-states', RxStates);
