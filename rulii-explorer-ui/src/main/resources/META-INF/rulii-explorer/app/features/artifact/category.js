import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph, icon} from '../../components/icons.js';
import {breadcrumb, card, tagChips} from '../../components/common.js';
import {plainTokens} from '../../components/expression.js';
import {routes} from '../../routing/router.js';
import {isCompiled, kindShort, plural, typeLabel} from '../../descriptor/format.js';

/**
 * A category: its sub-categories, the artifacts in it (a rule shown here through its rule set
 * says so), and the tags they carry. Reached from the sidebar tree, the breadcrumbs and the
 * overview's categories card.
 */
class RxCategory extends RxElement {

    render() {
        const {index, route} = this.state;
        const node = index.categories.byPath.get(route.id);
        if (!node) return html`<rx-states kind="missing"></rx-states>`;
        const levels = node.path.split('/');
        const crumbs = [{text: 'Categories', href: routes.overview()}];
        levels.forEach((name, i) => crumbs.push(i === levels.length - 1 ? {text: name} : {text: name, href: routes.category(levels.slice(0, i + 1).join('/'))}));
        const groups = ['ruleflow', 'ruleset', 'rule'].map(t => ({type: t, items: node.artifacts.filter(a => a.type === t)})).filter(g => g.items.length);
        const tags = tagsOf(node);
        return html`<div class="rx-page">
            <div class="rx-page-head">
                ${breadcrumb(crumbs)}
                <div class="rx-meta-row"><span class="rx-overline">Category</span><span class="rx-kind">${node.path}</span></div>
                <h1 class="rx-h1">${node.name}</h1>
                <p class="rx-subtitle">${subtitle(node)}</p>
            </div>
            <div class="rx-detail-grid">
                <div class="rx-col">
                    ${node.children.length ? card('Sub-categories', html`<div class="rx-rows">${node.children.map(ch => html`<a class="rx-row" href=${routes.category(ch.path)}>
                        ${icon('chevronRight', {size: 12, width: 2.2})}
                        <span class="rx-name">${ch.name}<span class="rx-small">${counted(ch.totals)}</span></span>
                        <span class="rx-kind-cell">${ch.totals.total}</span>
                    </a>`)}</div>`, {class: 'rx-card-tight', action: html`<span class="rx-h2-count">${node.children.length}</span>`}) : nothing}
                    ${groups.map(g => card(typeLabel(g.type, true), html`<div class="rx-rows">${g.items.map(a => {
                        const c = index.categories.of.get(a.id);
                        const via = c && c.inherited ? index.byId.get(c.from) : null;
                        return html`<a class="rx-row" href=${routes.artifact(a)} data-hover=${a.id}>
                            ${glyph(a.type, {size: 11, undescribed: index.undescribed.has(a.id)})}
                            <span class="rx-name">${a.name}${via ? html`<span class="rx-via">via ${via.name}</span>` : nothing}${a.description ? html`<span class="rx-small">${a.description}</span>` : a.rule && a.rule.given && !isCompiled(a.rule.given) ? html`<span class="rx-small">${plainTokens(a.rule.given)}</span>` : nothing}</span>
                            <span class="rx-kind-cell">${kindShort(a)}</span>
                        </a>`;
                    })}</div>`, {class: 'rx-card-tight', action: html`<span class="rx-h2-count">${g.items.length}</span>`}))}
                    ${!groups.length && !node.children.length ? html`<p class="rx-small">Nothing is in this category.</p>` : nothing}
                </div>
                ${tags.length ? html`<div class="rx-col">
                    ${card('Tags', html`<div class="rx-tag-list">${tags.map(([tag, n]) => html`<div class="rx-tag-row">${tagChips({tags: [tag]})}<span class="rx-small">${plural(n, 'artifact')}</span></div>`)}</div>`, {class: 'rx-card-tight'})}
                </div>` : nothing}
            </div>
        </div>`;
    }
}

function subtitle(node) {
    const parts = counted(node.totals);
    const subs = node.children.length ? ', in ' + plural(node.children.length, 'sub-category', 'sub-categories') : '';
    return (parts || 'Nothing') + subs + '.';
}

function counted(totals) {
    return ['ruleflow', 'ruleset', 'rule'].filter(t => totals[t]).map(t => plural(totals[t], typeLabel(t).toLowerCase(), typeLabel(t, true).toLowerCase())).join(', ');
}

/** The tags in a category and below it, with how many artifacts carry each, most first. */
function tagsOf(node) {
    const counts = new Map();
    const walk = (n) => { for (const a of n.artifacts) for (const t of a.tags || []) counts.set(t, (counts.get(t) || 0) + 1); n.children.forEach(walk); };
    walk(node);
    return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
}

customElements.define('rx-category', RxCategory);
