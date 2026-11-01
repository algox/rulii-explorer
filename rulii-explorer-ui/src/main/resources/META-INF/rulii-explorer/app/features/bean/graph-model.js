import {compareNatural, kindShort, plural} from '../../descriptor/format.js';
import {beanDeclarations} from '../../descriptor/indexes.js';
import {dependencyOptions} from '../../graph-engine/layout.js';
import {FONT_CAPTION, FONT_NAME, textWidth} from '../../graph-engine/vendor.js';
import {focusGraph} from '../graph/graph-model.js';

/**
 * The Beans graph as data (FR-75): a node per bean, edges for the cascades between them, and
 * the registry rules the beans run as leaf nodes that link into the Rules section (a missing
 * name is a red dashed leaf). Pure functions of the index; the Rules dependency graph never
 * reads them.
 *
 * Nodes carry the shape the shared renderer expects: `type` ('bean', 'rule' or 'missing'),
 * `name`, `caption`, `width`, `height`, `problem`.
 */

/** Every bean, every cascade, every rule run. */
export function beansGraph(index) {
    const beans = index.beans;
    const nodes = new Map();
    const edges = [];
    for (const b of beans.list) nodes.set(b.id, beanNode(b, beans));
    for (const b of beans.list) {
        for (const ref of beans.cascadesTo.get(b.id) || []) {
            if (!nodes.has(ref.to)) continue;
            edges.push({id: b.id + '>' + ref.to + '#' + ref.path, from: b.id, to: ref.to, type: 'cascades', resolution: 'direct', async: false, path: ref.path, label: memberLabel(b, ref.path)});
        }
        for (const ref of beans.runs.get(b.id) || []) {
            const a = index.byId.get(ref.to);
            if (!a) continue;
            if (!nodes.has(a.id)) nodes.set(a.id, ruleNode(a, index));
            edges.push({id: b.id + '>' + a.id + '#' + ref.path, from: b.id, to: a.id, type: 'runs', resolution: 'by-name', async: false, path: ref.path});
        }
        for (const decl of beanDeclarations(b).filter(d => d.kind === 'validate')) {
            for (const t of decl.validation.targets || []) {
                if (t.artifactId) continue;
                const id = 'missing:' + t.name;
                if (!nodes.has(id)) nodes.set(id, missingNode(id, t.name));
                edges.push({id: b.id + '>' + id + '#' + decl.path, from: b.id, to: id, type: 'runs', resolution: 'unresolved', async: false, path: decl.path});
            }
        }
    }
    edges.sort((a, b) => compareNatural(a.from, b.from) || compareNatural(a.path || '', b.path || '') || compareNatural(a.to, b.to));
    return {nodes, edges};
}

function beanNode(b, beans) {
    const props = (b.properties || []).length;
    const declarations = beanDeclarations(b).length;
    const caption = plural(props, 'property', 'properties') + ' · ' + plural(declarations, 'constraint') + (b.discovery === 'cascade' ? ' · by cascade' : '');
    const node = {id: b.id, type: 'bean', name: b.name, caption, bean: b, packageId: b.packageId || '', problem: beans.worstByBean.get(b.id), undescribed: beans.undescribed.has(b.id)};
    node.width = Math.ceil(Math.max(34 + textWidth(node.name, FONT_NAME) + 16, textWidth(caption, FONT_CAPTION) + 8, 120) + (node.problem ? 12 : 0));
    node.height = 36 + 18;
    return node;
}

function ruleNode(a, index) {
    const node = {id: a.id, type: 'rule', name: a.name, caption: kindShort(a).replace(/^Validator · r:/, 'validator · '), artifact: a, packageId: a.packageId || '', problem: index.worstByArtifact.get(a.id), undescribed: index.undescribed.has(a.id)};
    node.width = Math.ceil(22 + Math.max(textWidth(node.name, FONT_NAME), textWidth(node.caption, FONT_CAPTION)) + 14 + (node.problem ? 10 : 0));
    node.height = 34;
    return node;
}

function missingNode(id, name) {
    const node = {id, type: 'missing', name, caption: 'not registered', packageId: null};
    node.width = Math.ceil(Math.max(textWidth(name, FONT_NAME) + 28, textWidth(node.caption, FONT_CAPTION) + 24) + 8);
    node.height = 30 + 16;
    return node;
}

function memberLabel(bean, path) {
    const m = /^properties\.([^.[]+)/.exec(path || '');
    if (m) return m[1];
    const e = /^executables\.([^.[]+)/.exec(path || '');
    if (e) { const x = (bean.executables || []).find(y => y.key === e[1]); return (x ? x.name : e[1]) + '()'; }
    return '';
}

/** The neighbourhood of one bean (or rule), as the dependency graph does it. */
export function focusBeansGraph(full, focusId, depth = '1') {
    const {nodes, edges, hidden} = focusGraph(full, focusId, depth);
    return {nodes, edges, hidden};
}

/** The ELK input: flat, beans on the left, rules on the right. */
export function toElkBeans(graph) {
    const rank = {bean: 0, rule: 1, missing: 2};
    const nodes = [...graph.nodes.values()].sort((a, b) => rank[a.type] - rank[b.type] || compareNatural(a.name, b.name));
    return {
        id: 'root',
        layoutOptions: dependencyOptions(nodes.length, false),
        children: nodes.map(n => ({id: n.id, width: n.width, height: n.height})),
        edges: graph.edges.map(e => ({id: e.id, sources: [e.from], targets: [e.to]}))
    };
}

/** A sentence for the screen reader describing the graph. */
export function describeBeansGraph(graph, focus) {
    const beans = [...graph.nodes.values()].filter(n => n.type === 'bean').length;
    const rules = [...graph.nodes.values()].filter(n => n.type === 'rule').length;
    const head = focus ? 'Beans graph around ' + focus.name + ': ' : 'Beans graph: ';
    return head + plural(beans, 'bean') + (rules ? ', ' + plural(rules, 'rule') : '') + ', ' + plural(graph.edges.length, 'connection') + '.';
}
