import {html, nothing} from 'lit';
import {RxElement} from '../../components/base.js';
import {glyph} from '../../components/icons.js';
import {breadcrumb, card} from '../../components/common.js';
import {plainTokens} from '../../components/expression.js';
import {routes} from '../../routing/router.js';
import {isCompiled, kindShort, plural, typeLabel} from '../../descriptor/format.js';
import {packageFiles} from '../../descriptor/indexes.js';

/** A package: its artifacts by type. Reached from breadcrumbs and the overview's package list. */
class RxPackage extends RxElement {

    render() {
        const {index, route} = this.state;
        const entry = index.byPackage.get(route.id);
        if (!entry) return html`<rx-states kind="missing"></rx-states>`;
        const files = packageFiles(entry);
        const caption = entry.pkg.kind === 'xml' ? 'XML · ' + (files.length ? plural(files.length, 'file') + ': ' + files.join(', ') : 'no files recorded') : 'Java package';
        const groups = ['ruleflow', 'ruleset', 'rule'].map(t => ({type: t, items: entry.artifacts.filter(a => a.type === t)})).filter(g => g.items.length);
        return html`<div class="rx-page">
            <div class="rx-page-head">
                ${breadcrumb([{text: 'Packages', href: routes.overview()}, {text: route.id}])}
                <div class="rx-meta-row"><span class="rx-overline">Package</span><span class="rx-kind">${caption}</span></div>
                <h1 class="rx-h1 rx-h1-mono">${entry.pkg.id}</h1>
                <p class="rx-subtitle">${groups.map(g => plural(g.items.length, typeLabel(g.type).toLowerCase(), typeLabel(g.type, true).toLowerCase())).join(', ')} defined here.</p>
            </div>
            <div class="rx-detail-grid">
                <div class="rx-col">
                    ${groups.map(g => card(typeLabel(g.type, true), html`<div class="rx-rows">${g.items.map(a => html`<a class="rx-row" href=${routes.artifact(a)} data-hover=${a.id}>
                        ${glyph(a.type, {size: 11, undescribed: index.undescribed.has(a.id)})}
                        <span class="rx-name">${a.name}${a.description ? html`<span class="rx-small">${a.description}</span>` : a.rule && a.rule.given && !isCompiled(a.rule.given) ? html`<span class="rx-small">${plainTokens(a.rule.given)}</span>` : nothing}</span>
                        <span class="rx-kind-cell">${kindShort(a)}</span>
                    </a>`)}</div>`, {class: 'rx-card-tight', action: html`<span class="rx-h2-count">${g.items.length}</span>`}))}
                </div>
            </div>
        </div>`;
    }
}

customElements.define('rx-package', RxPackage);
