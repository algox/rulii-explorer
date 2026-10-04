import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon} from '../../components/icons.js';
import {languageTag} from '../../components/common.js';
import {routes} from '../../routing/router.js';
import {typeLabel} from '../../descriptor/format.js';

/**
 * Sidebar: Overview and Problems; the category tree when the application uses categories; the
 * rule flows; the rule sets, each opening into its members in run order; and the rules as one
 * collapsed alphabetical list, plus the ones in no rule set when there are no categories to
 * find them by. Packages are a technical fact and stay off the sidebar: the Source card, the
 * breadcrumb and the package page have them.
 */
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
            ${ready && index.categories.has ? this.categories(index, current, expanded) : nothing}
            ${ready ? this.group('ruleflow', index.byType.ruleflow, current, index) : nothing}
            ${ready ? this.ruleSets(index, current, expanded) : nothing}
            ${ready ? this.rules(index, current, expanded) : nothing}
            <div class="rx-sidebar-foot">${descriptor ? html`Descriptor ${descriptor.descriptorVersion}${descriptor.application && descriptor.application.ruliiVersion && descriptor.application.ruliiVersion !== 'unknown' ? html` · rulii ${descriptor.application.ruliiVersion}` : nothing}` : 'Descriptor 1.1'}</div>
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

    /** The category tree: each category opens into its sub-categories and its artifacts; "Uncategorised" closes the list. */
    /** "Expand all" and "Collapse all" for a group of folders, in its header. */
    allButtons(keys, label, expanded) {
        const allOpen = keys.every(k => expanded.has(k));
        const noneOpen = !keys.some(k => expanded.has(k));
        return html`<span class="rx-nav-all">
            <button type="button" class="rx-nav-all-btn" aria-label=${'Expand all ' + label} title="Expand all" ?disabled=${allOpen} @click=${() => this.store.setExpanded(keys, true)}>${icon('expand', {size: 12, width: 2})}</button>
            <button type="button" class="rx-nav-all-btn" aria-label=${'Collapse all ' + label} title="Collapse all" ?disabled=${noneOpen} @click=${() => this.store.setExpanded(keys, false)}>${icon('collapse', {size: 12, width: 2})}</button>
        </span>`;
    }

    categories(index, current, expanded) {
        const c = index.categories;
        const keys = [...c.byPath.keys()].map(p => 'cat:' + p);
        if (c.uncategorised.length) keys.push('cat:');
        return html`<div class="rx-nav-group">
            <div class="rx-nav-head rx-overline"><span>Categories</span><span class="rx-nav-head-end">${this.allButtons(keys, 'categories', expanded)}${c.byPath.size}</span></div>
            ${c.roots.map(n => this.categoryNode(n, index, current, expanded))}
            ${c.uncategorised.length ? this.folder('cat:', 'Uncategorised', c.uncategorised, index, current, expanded) : nothing}
        </div>`;
    }

    categoryNode(n, index, current, expanded) {
        const key = 'cat:' + n.path;
        const open = expanded.has(key);
        const worst = open ? null : worstOf(n.artifacts, index);
        return html`
            <a class="rx-nav-row rx-nav-row-pkg rx-nav-row-cat" href=${routes.category(n.path)} aria-expanded=${open} @click=${(e) => { e.preventDefault(); this.store.toggleExpanded(key); }} title=${(open ? 'Collapse ' : 'Expand ') + n.path + '. Middle-click or the overview opens its page'}>
                ${icon(open ? 'chevronDown' : 'chevronRight', {size: 12, width: 2.2})}
                <span class="rx-name">${n.name}</span>
                ${worst ? html`<span class=${'rx-dot rx-dot-' + worst} aria-label=${worst}></span>` : nothing}
                <span class="rx-nav-count">${n.totals.total}</span>
            </a>
            ${open ? html`<div class="rx-nav-children">
                ${n.children.map(ch => this.categoryNode(ch, index, current, expanded))}
                ${n.artifacts.map(a => this.row(a, current, index, true))}
            </div>` : nothing}`;
    }

    /** A collapsible list of artifacts under one row: "Uncategorised", "All rules", "Not in any rule set". */
    folder(key, label, list, index, current, expanded) {
        const open = expanded.has(key);
        const worst = open ? null : worstOf(list, index);
        return html`
            <button type="button" class="rx-nav-row rx-nav-row-pkg rx-nav-row-folder" aria-expanded=${open} @click=${() => this.store.toggleExpanded(key)}>
                ${icon(open ? 'chevronDown' : 'chevronRight', {size: 12, width: 2.2})}
                <span class="rx-name">${label}</span>
                ${worst ? html`<span class=${'rx-dot rx-dot-' + worst} aria-label=${worst}></span>` : nothing}
                <span class="rx-nav-count">${list.length}</span>
            </button>
            ${open ? html`<div class="rx-nav-children">${list.map(a => this.row(a, current, index, true))}</div>` : nothing}`;
    }

    /** Rule sets, each with a toggle that opens its members in run order. */
    ruleSets(index, current, expanded) {
        const list = index.byType.ruleset;
        if (!list.length) return nothing;
        const keys = list.filter(s => s.ruleSet && s.ruleSet.members && s.ruleSet.members.length).map(s => 'set:' + s.id);
        return html`<div class="rx-nav-group">
            <div class="rx-nav-head rx-overline"><span>${typeLabel('ruleset', true)}</span><span class="rx-nav-head-end">${keys.length ? this.allButtons(keys, 'rule sets', expanded) : nothing}${list.length}</span></div>
            ${list.map(s => {
                const key = 'set:' + s.id;
                const open = expanded.has(key);
                const members = ((s.ruleSet && s.ruleSet.members) || []).map(id => index.byId.get(id)).filter(Boolean);
                return html`${this.row(s, current, index, false, members.length ? {key, open} : null)}
                    ${open ? html`<div class="rx-nav-children">${members.map(a => this.row(a, current, index, true))}</div>` : nothing}`;
            })}
        </div>`;
    }

    /** The rules: flat when nothing groups them, else one collapsed "All rules" list, and the loose ones when there are no categories. */
    rules(index, current, expanded) {
        const all = index.byType.rule;
        if (!all.length) return nothing;
        const grouped = index.categories.has || index.byType.ruleset.length > 0;
        const loose = index.categories.has ? [] : all.filter(a => !(index.usedBy.get(a.id) || []).some(r => r.type === 'contains'));
        return html`<div class="rx-nav-group">
            <div class="rx-nav-head rx-overline"><span>Rules</span><span>${all.length}</span></div>
            ${!grouped ? all.map(a => this.row(a, current, index)) : html`
                ${loose.length ? this.folder('loose-rules', 'Not in any rule set', loose, index, current, expanded) : nothing}
                ${this.folder('all-rules', 'All rules', all, index, current, expanded)}`}
        </div>`;
    }

    /**
     * One artifact row. `toggle` adds a chevron beside the row that opens a list under it (a rule
     * set's members); it sits next to the link, not inside it, so both stay keyboard-reachable.
     */
    row(a, current, index, child = false, toggle = null) {
        const worst = index.worstByArtifact.get(a.id);
        const undescribed = index.undescribed.has(a.id);
        const link = html`<a class=${'rx-nav-row' + (child ? ' rx-nav-row-child' : '')} href=${routes.artifact(a)} aria-current=${current === a.id ? 'page' : nothing} title=${a.description || a.name}>
            ${glyph(a.type, {size: child ? 10 : 12, undescribed})}
            <span class="rx-name">${a.name}</span>
            ${a.type === 'rule' ? languageTag(a, {nonDefaultOnly: true, small: true}) : nothing}
            ${undescribed ? html`<span class="rx-nav-undescribed">not described</span>` : worst ? html`<span class=${'rx-dot rx-dot-' + worst} aria-label=${worst} title=${worst}></span>` : nothing}
        </a>`;
        if (!toggle) return link;
        return html`<div class="rx-nav-row-wrap">${link}
            <button type="button" class="rx-nav-toggle" aria-expanded=${toggle.open} aria-label=${(toggle.open ? 'Hide' : 'Show') + ' the rules of ' + a.name} title=${toggle.open ? 'Hide its rules' : 'Show its rules'} @click=${() => this.store.toggleExpanded(toggle.key)}>${icon(toggle.open ? 'chevronDown' : 'chevronRight', {size: 11, width: 2.2})}</button>
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
