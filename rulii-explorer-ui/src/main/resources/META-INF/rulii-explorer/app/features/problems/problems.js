import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon, severityIcon} from '../../components/icons.js';
import {artifactLink, card} from '../../components/common.js';
import {routes, navigate} from '../../routing/router.js';
import {compareNatural, SEVERITY_ORDER, severityLabel, targetName} from '../../descriptor/format.js';
import {problemExplanation, problemHint, problemsHeadline} from '../../descriptor/summaries.js';
import {problemPath} from '../../descriptor/indexes.js';

const GROUPS = [
    {severity: 'error', title: 'Error', sub: '· will fail when it runs'},
    {severity: 'warning', title: 'Warning', sub: '· likely to fail'},
    {severity: 'info', title: 'Info', sub: '· suggestions'}
];

/** The problems list (FR-29, S-Problems): filters by severity, grouped, each with why and how to fix. */
class RxProblems extends RxElement {

    render() {
        const {descriptor, index, route} = this.state;
        const filter = ['error', 'warning', 'info'].includes(route.query.severity) ? route.query.severity : null;
        const c = index.problemCounts;
        const all = [...descriptor.problems].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] || compareNatural(a.artifact || '', b.artifact || '') || compareNatural(a.path || '', b.path || ''));
        return html`<div class="rx-page" style="padding-top: 30px">
            <div class="rx-problems-grid">
                <div class="rx-col" style="gap: 18px">
                    <div class="rx-page-head" style="gap: 6px">
                        <div class="rx-overline rx-overline-accent">Problems</div>
                        <h1 class="rx-h1 rx-h1-md" style="line-height: 1.2; letter-spacing: -0.01em">${problemsHeadline(c)}</h1>
                        <p class="rx-subtitle rx-subtitle-sm">Checked when the rule descriptor was read from ${descriptor.application.name || 'the application'}.</p>
                    </div>
                    ${c.total ? html`<div class="rx-filters" role="group" aria-label="Filter by severity">
                        ${this.filter(null, 'All', c.total, filter)}
                        ${this.filter('error', 'Errors', c.error, filter)}
                        ${this.filter('warning', 'Warnings', c.warning, filter)}
                        ${this.filter('info', 'Info', c.info, filter)}
                        <span class="rx-filters-note">Grouped by severity</span>
                    </div>` : nothing}
                    ${c.total ? GROUPS.filter(g => !filter || g.severity === filter).map(g => this.group(g, all.filter(p => p.severity === g.severity), index)) : html`<div class="rx-card"><p class="rx-subtitle rx-subtitle-sm">Every step resolves, every name matches and every artifact describes itself. Problems appear here when the descriptor is read again after a change.</p></div>`}
                </div>
                <div class="rx-col">
                    ${this.byArtifact(all, index)}
                    ${this.byCode(all)}
                </div>
            </div>
        </div>`;
    }

    filter(severity, label, count, current) {
        if (severity && !count) return nothing;
        return html`<button type="button" class="rx-filter-btn" aria-pressed=${current === severity} @click=${() => navigate(routes.problems(severity ? {severity} : {}))}>
            ${severity ? html`<span class=${'rx-dot rx-dot-lg rx-dot-' + severity}></span>` : nothing}${label}<span class="rx-count">${count}</span>
        </button>`;
    }

    group(g, problems, index) {
        if (!problems.length) return nothing;
        return html`<section class="rx-problem-group" aria-labelledby=${'rx-pg-' + g.severity}>
            <h2 class=${'rx-overline rx-overline-' + g.severity} id=${'rx-pg-' + g.severity}>${g.title}<span>${g.sub}</span></h2>
            ${g.severity === 'info' ? html`<div class="rx-info-list">${problems.map(p => this.infoItem(p, index))}</div>` : problems.map(p => this.article(p, index))}
        </section>`;
    }

    article(p, index) {
        const a = index.byId.get(p.artifact);
        const chain = problemPath(index, p);
        const e = problemExplanation(p, index);
        const mismatch = p.code === 'NAME_MISMATCH_LOOKUP' ? this.mismatchTarget(p, index) : null;
        return html`<article class="rx-article">
            <span class=${'rx-sev-icon rx-sev-' + p.severity}>${severityIcon(p.severity, 16)}</span>
            <div class="rx-article-body">
                <div class="rx-article-head"><p>${a ? a.name + ' ' : ''}${a ? lowerFirst(p.message) : p.message}</p><span class="rx-code-tag">${p.code}</span></div>
                ${a ? html`<div class="rx-article-path">
                    ${artifactLink(a)}
                    ${chain.map(entry => html`${icon('chevronRight', {size: 12, width: 2.2})}<span>${stepShort(entry.command, index, p)}</span>`)}
                    ${mismatch ? html`<span class="rx-vbar"></span>${artifactLink(mismatch)}` : nothing}
                    <span class="rx-spacer"></span>
                    ${a.type === 'ruleflow' && p.path ? html`<a class="rx-link rx-link-arrow" style="font-size: 12.5px" href=${routes.artifact(a, {view: 'flowchart', step: p.path})}>Show in flowchart${icon('arrowRight', {size: 13})}</a>` : nothing}
                </div>` : nothing}
                <div class="rx-whyfix">
                    <div><span class="rx-overline">Why it matters</span><span>${e.why}</span></div>
                    <div><span class="rx-overline">How to fix</span><span>${e.fix || '—'}</span></div>
                </div>
            </div>
        </article>`;
    }

    mismatchTarget(p, index) {
        const m = /registry name of that artifact is '([^']+)'/.exec(p.message || '');
        return m ? index.byId.get(m[1]) : null;
    }

    infoItem(p, index) {
        const a = index.byId.get(p.artifact);
        return html`<article class="rx-info-item">
            <span class="rx-sev-icon rx-sev-info">${severityIcon('info', 16)}</span>
            <div>
                <p>${a ? artifactLink(a, {glyph: false, class: 'rx-art-link rx-art-link-' + a.type}) : p.artifact}${a ? ' ' + lowerFirst(p.message) : p.message}</p>
                <span class="rx-small">${problemHint(p, index)}</span>
            </div>
            <span class="rx-code-tag">${p.code}</span>
        </article>`;
    }

    byArtifact(all, index) {
        const map = new Map();
        for (const p of all) {
            if (!p.artifact) continue;
            if (!map.has(p.artifact)) map.set(p.artifact, {error: 0, warning: 0, info: 0});
            map.get(p.artifact)[p.severity]++;
        }
        if (!map.size) return nothing;
        const body = html`<div class="rx-by-list">${[...map.entries()].map(([id, counts]) => {
            const a = index.byId.get(id);
            return html`<a href=${a ? routes.artifact(a) : '#'} data-hover=${a ? a.id : nothing}>${a ? glyph(a.type, {size: 11, undescribed: index.undescribed.has(a.id)}) : nothing}<span class="rx-name">${a ? a.name : id}</span><span class="rx-dots">${['error', 'warning', 'info'].filter(s => counts[s]).map(s => html`<span class=${'rx-dot rx-dot-lg rx-dot-' + s} title=${counts[s] + ' ' + s}></span>`)}</span></a>`;
        })}</div>`;
        return card('By artifact', body, {class: 'rx-card-tight', titleClass: 'rx-h2-md'});
    }

    byCode(all) {
        const map = new Map();
        for (const p of all) map.set(p.code, (map.get(p.code) || 0) + 1);
        if (!map.size) return nothing;
        const body = html`<div class="rx-by-list">${[...map.entries()].sort((a, b) => b[1] - a[1]).map(([code, n]) => html`<div><span class="rx-name rx-mono" style="font-size: 11.5px; font-weight: 500">${code}</span><span class="rx-small">${n}</span></div>`)}</div>`;
        return card('By code', body, {class: 'rx-card-tight', titleClass: 'rx-h2-md'});
    }
}

/** A short label for a flow step in the problem's path: "for each item", "run ‘prefixRule’ · by name". */
function stepShort(command, index, problem) {
    switch (command.type) {
        case 'for-each': return 'for each ' + (command.item || 'item');
        case 'when': return 'if …';
        case 'scope': return 'scope';
        case 'run': case 'apply': case 'async-run': {
            const t = command.target || {};
            const name = targetName(t, index);
            const bad = t.resolution === 'unresolved';
            return html`${command.type === 'async-run' ? 'start ' : 'run '}<span class=${'rx-mono' + (bad ? ' rx-chip-error' : '')}>‘${name}’</span>${t.kind === 'by-name' ? ' · by name' : t.kind === 'by-class' ? ' · by class' : ''}`;
        }
        default: return command.type;
    }
}

function lowerFirst(text) {
    return text ? text.charAt(0).toLowerCase() + text.slice(1) : text;
}

customElements.define('rx-problems', RxProblems);
