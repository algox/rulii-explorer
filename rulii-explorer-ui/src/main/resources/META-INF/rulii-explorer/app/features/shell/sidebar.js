import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon} from '../../components/icons.js';
import {languageTag} from '../../components/common.js';
import {routes} from '../../routing/router.js';
import {typeLabel} from '../../descriptor/format.js';

/** Sidebar: Overview and Problems, then every artifact by type, rules grouped by package. */
class RxSidebar extends RxElement {

    render() {
        const {status, index, route, descriptor, expanded} = this.state;
        const current = route.name === 'artifact' ? route.id : null;
        const loading = status === 'loading';
        const ready = !!index;
        return html`<nav class="rx-sidebar" aria-label="Artifacts">
            <div class="rx-nav-group">
                <a class="rx-nav-link" href=${routes.overview()} aria-current=${route.name === 'overview' ? 'page' : nothing}>${icon('grid', {size: 16, width: 1.8})}<span class="rx-name">Overview</span></a>
                <a class="rx-nav-link" href=${routes.problems()} aria-current=${route.name === 'problems' ? 'page' : nothing}>${icon('warning', {size: 16, width: 1.8})}<span class="rx-name">Problems</span>${this.problemCount(loading, index)}</a>
            </div>
            ${loading ? this.skeleton() : nothing}
            ${ready ? this.group('ruleflow', index.byType.ruleflow, current, index) : nothing}
            ${ready ? this.group('ruleset', index.byType.ruleset, current, index) : nothing}
            ${ready ? this.rules(index, current, expanded) : nothing}
            <div class="rx-sidebar-foot">${descriptor ? html`Descriptor ${descriptor.descriptorVersion}${descriptor.application && descriptor.application.ruliiVersion && descriptor.application.ruliiVersion !== 'unknown' ? html` · rulii ${descriptor.application.ruliiVersion}` : nothing}` : 'Descriptor 1.0'}</div>
        </nav>`;
    }

    problemCount(loading, index) {
        if (loading) return html`<span class="rx-sk" aria-hidden="true" style="width: 18px; height: 10px"></span>`;
        if (!index || !index.problemCounts.total) return nothing;
        const c = index.problemCounts;
        const worst = c.error ? 'error' : c.warning ? 'warning' : 'info';
        const n = c.error + c.warning || c.info;
        return html`<span class=${'rx-nav-problems rx-nav-problems-' + worst} aria-label=${n + ' problems'}><span class=${'rx-dot rx-dot-' + worst}></span>${n}</span>`;
    }

    group(type, list, current, index) {
        if (!list.length) return nothing;
        return html`<div class="rx-nav-group">
            <div class="rx-nav-head rx-overline"><span>${typeLabel(type, true)}</span><span>${list.length}</span></div>
            ${list.map(a => this.row(a, current, index))}
        </div>`;
    }

    row(a, current, index, child = false) {
        const worst = index.worstByArtifact.get(a.id);
        const undescribed = index.undescribed.has(a.id);
        return html`<a class=${'rx-nav-row' + (child ? ' rx-nav-row-child' : '')} href=${routes.artifact(a)} aria-current=${current === a.id ? 'page' : nothing} title=${a.description || a.name}>
            ${glyph(a.type, {size: child ? 10 : 12, undescribed})}
            <span class="rx-name">${a.name}</span>
            ${a.type === 'rule' ? languageTag(a, {nonDefaultOnly: true, small: true}) : nothing}
            ${undescribed ? html`<span class="rx-nav-undescribed">not described</span>` : worst ? html`<span class=${'rx-dot rx-dot-' + worst} aria-label=${worst} title=${worst}></span>` : nothing}
        </a>`;
    }

    rules(index, current, expanded) {
        const packages = index.packages.filter(p => p.counts.rule > 0);
        if (!index.byType.rule.length) return nothing;
        return html`<div class="rx-nav-group">
            <div class="rx-nav-head rx-overline"><span>Rules</span><span>${index.byType.rule.length}</span></div>
            ${packages.map(p => {
                const open = expanded.has(p.pkg.id);
                const rules = p.artifacts.filter(a => a.type === 'rule');
                const worst = worstOf(rules, index);
                return html`
                    <a class="rx-nav-row rx-nav-row-pkg" href=${routes.package(p.pkg.id)} aria-expanded=${open} @click=${(e) => { e.preventDefault(); this.store.toggleExpanded(p.pkg.id); }} title=${(open ? 'Collapse ' : 'Expand ') + p.pkg.id}>
                        ${icon(open ? 'chevronDown' : 'chevronRight', {size: 12, width: 2.2})}
                        <span class="rx-name rx-mono">${p.pkg.id}</span>
                        ${!open && worst ? html`<span class=${'rx-dot rx-dot-' + worst} aria-label=${worst}></span>` : nothing}
                        <span class="rx-nav-count">${rules.length}</span>
                    </a>
                    ${open ? html`<div class="rx-nav-children">${rules.map(a => this.row(a, current, index, true))}</div>` : nothing}`;
            })}
        </div>`;
    }

    skeleton() {
        const line = (w) => html`<div class="rx-nav-row" aria-hidden="true"><span class="rx-sk" style="width: 12px; height: 10px"></span><span class="rx-sk" style=${'width: ' + w + 'px; height: 10px'}></span></div>`;
        return html`
            <div class="rx-nav-group" aria-hidden="true"><div class="rx-nav-head rx-overline"><span>Rule flows</span></div>${line(136)}${line(118)}</div>
            <div class="rx-nav-group" aria-hidden="true"><div class="rx-nav-head rx-overline"><span>Rule sets</span></div>${line(96)}${line(140)}${line(84)}</div>
            <div class="rx-nav-group" aria-hidden="true"><div class="rx-nav-head rx-overline"><span>Rules</span></div>${line(92)}${line(104)}${line(150)}${line(158)}</div>`;
    }
}

function worstOf(list, index) {
    const rank = {error: 0, warning: 1, info: 2};
    let worst = null;
    for (const a of list) {
        const w = index.worstByArtifact.get(a.id);
        if (w && (worst === null || rank[w] < rank[worst])) worst = w;
    }
    return worst;
}

customElements.define('rx-sidebar', RxSidebar);
