import {compareNatural, kindShort, plural, typeLabel, walkCommands} from '../../descriptor/format.js';
import {dependencyOptions} from '../../graph-engine/layout.js';
import {FONT_CAPTION, FONT_NAME, FONT_MONO, textWidth} from '../../graph-engine/vendor.js';

/**
 * The dependency graph as data (FR-50, FR-51): nodes for artifacts and missing targets, edges for
 * the descriptor's references plus the unresolved lookups, trimmed to the focus neighbourhood or
 * grouped by package for the whole application. Pure functions of the index, so the browser unit
 * tests cover them.
 *
 * @typedef {object} GraphNode
 * @property {string} id            artifact id or "missing:name"
 * @property {'rule'|'ruleset'|'ruleflow'|'missing'} type
 * @property {string} name
 * @property {string} caption       "8 rules · validating", "11 steps", "XML · script", "not registered"
 * @property {object} [artifact]
 * @property {string} [packageId]
 * @property {number} [order]       member order within the focused rule set (1-based)
 * @property {'error'|'warning'|'info'} [problem]
 * @property {number} width
 * @property {number} height
 *
 * @typedef {object} GraphEdge
 * @property {string} id
 * @property {string} from
 * @property {string} to
 * @property {'contains'|'runs'} type
 * @property {'direct'|'by-name'|'by-class'|'unresolved'} resolution
 * @property {boolean} async
 * @property {string} [path]
 */

export const DEPTHS = ['1', '2', 'all'];

/** Every node and edge of the application. */
export function fullGraph(index) {
    const nodes = new Map();
    const edges = [];
    for (const a of index.descriptor.artifacts) nodes.set(a.id, artifactNode(a, index));
    for (const ref of index.descriptor.references) {
        if (!nodes.has(ref.from) || !nodes.has(ref.to)) continue;
        edges.push({id: ref.from + '>' + ref.to + '#' + (ref.path || ''), from: ref.from, to: ref.to, type: ref.type, resolution: ref.resolution || 'direct', async: !!ref.async, path: ref.path});
    }
    // Unresolved lookups are not references; they come from the flows' commands.
    for (const flow of index.byType.ruleflow) {
        walkCommands(flow.ruleFlow.commands, (command, path) => {
            const t = command.target;
            if (!t || t.resolution !== 'unresolved') return;
            const name = t.name || t.className || '?';
            const id = 'missing:' + name;
            if (!nodes.has(id)) nodes.set(id, missingNode(id, name));
            edges.push({id: flow.id + '>' + id + '#' + path, from: flow.id, to: id, type: 'runs', resolution: 'unresolved', async: command.type === 'async-run', path});
        });
    }
    edges.sort((a, b) => compareNatural(a.from, b.from) || compareNatural(a.path || '', b.path || '') || compareNatural(a.to, b.to));
    return {nodes, edges};
}

function artifactNode(a, index) {
    const caption = a.type === 'ruleset' ? plural(a.ruleSet.members.length, 'rule') + (a.ruleSet.validating ? ' · validating' : '')
        : a.type === 'ruleflow' ? plural(countSteps(a), 'step')
        : kindShort(a).replace(/^Validator · r:/, 'validator · ');
    const category = index.categories && index.categories.of.get(a.id);
    const node = {id: a.id, type: a.type, name: a.name, caption, artifact: a, packageId: a.packageId || '', categoryId: category ? category.path : '', problem: index.worstByArtifact.get(a.id), undescribed: index.undescribed.has(a.id)};
    size(node);
    return node;
}

function missingNode(id, name) {
    const node = {id, type: 'missing', name, caption: 'not registered', packageId: null, categoryId: null};
    size(node);
    return node;
}

function countSteps(flow) {
    let n = 0;
    walkCommands(flow.ruleFlow.commands, () => n++);
    return n;
}

/** Node boxes, measured so ELK can place labels without overlaps (§9.3). */
function size(node) {
    const name = textWidth(node.name, FONT_NAME);
    const caption = textWidth(node.caption, FONT_CAPTION);
    if (node.type === 'rule') {
        node.width = Math.ceil(22 + Math.max(name, caption) + 14 + (node.problem ? 10 : 0));
        node.height = 34;
    } else if (node.type === 'missing') {
        node.width = Math.ceil(Math.max(name + 28, caption + 24) + 8);
        node.height = 30 + 16;
    } else {
        node.width = Math.ceil(Math.max(34 + name + 16, caption + 8, 120) + (node.problem ? 12 : 0));
        node.height = (node.type === 'ruleflow' ? 30 : 36) + 18;
    }
}

/**
 * The focus neighbourhood: the focused node plus everything within `depth` edges in either
 * direction ('all' = the whole connected component).
 */
export function focusGraph(full, focusId, depth = '1') {
    const limit = depth === 'all' ? Infinity : Number(depth) || 1;
    const adjacent = new Map();
    for (const e of full.edges) {
        if (!adjacent.has(e.from)) adjacent.set(e.from, new Set());
        if (!adjacent.has(e.to)) adjacent.set(e.to, new Set());
        adjacent.get(e.from).add(e.to);
        adjacent.get(e.to).add(e.from);
    }
    const seen = new Map([[focusId, 0]]);
    const queue = [focusId];
    while (queue.length) {
        const id = queue.shift();
        const d = seen.get(id);
        if (d >= limit) continue;
        for (const next of adjacent.get(id) || []) {
            if (!seen.has(next)) { seen.set(next, d + 1); queue.push(next); }
        }
    }
    const nodes = new Map();
    for (const id of seen.keys()) if (full.nodes.has(id)) nodes.set(id, {...full.nodes.get(id)});
    const edges = full.edges.filter(e => nodes.has(e.from) && nodes.has(e.to));
    // Member order within the focused rule set
    const focus = nodes.get(focusId);
    if (focus && focus.type === 'ruleset') {
        focus.artifact.ruleSet.members.forEach((id, i) => { const n = nodes.get(id); if (n && n.type === 'rule') { n.order = i + 1; n.width += i + 1 >= 10 ? 18 : 12; } });
    }
    return {nodes, edges, hidden: full.nodes.size - nodes.size};
}

/** Applies the toolbar filters: artifact types to show, and one package or one category (with its sub-categories) or all. */
export function filterGraph(graph, filters) {
    const types = filters.types;
    const pkg = filters.packageId;
    const cat = filters.categoryId;
    const nodes = new Map();
    for (const [id, n] of graph.nodes) {
        if (n.type !== 'missing' && !types.has(n.type)) continue;
        if (pkg && n.type !== 'missing' && n.packageId !== pkg) continue;
        if (cat && n.type !== 'missing' && !inCategory(n.categoryId, cat)) continue;
        if (n.type === 'missing' && !types.has('rule')) continue;
        nodes.set(id, n);
    }
    const edges = graph.edges.filter(e => nodes.has(e.from) && nodes.has(e.to));
    return {nodes, edges, hidden: (graph.hidden || 0) + (graph.nodes.size - nodes.size)};
}

/** True when a node's category is the filter or below it: "Pricing" keeps "Pricing/Loyalty"; "" (uncategorised) only itself. */
function inCategory(nodeCategory, filter) {
    if (filter === 'none') return !nodeCategory;
    return nodeCategory === filter || (!!nodeCategory && nodeCategory.startsWith(filter + '/'));
}

/**
 * The ELK input for a graph: flat, or grouped as compound nodes for the whole application, by
 * category when the application uses categories and by package otherwise (missing targets go
 * into a "not registered" group). Category groups are keyed `c:{path}`, `c:` for none.
 */
export function toElk(graph, index, grouped) {
    const nodes = [...graph.nodes.values()].sort((a, b) => typeRank(a) - typeRank(b) || (a.order || 0) - (b.order || 0) || compareNatural(a.name, b.name));
    const elkNode = (n) => ({id: n.id, width: n.width, height: n.height});
    const byCategory = !!(index.categories && index.categories.has);
    let children;
    if (grouped) {
        const groups = new Map();
        for (const n of nodes) {
            const key = n.type === 'missing' ? 'missing' : byCategory ? 'c:' + (n.categoryId || '') : (n.packageId || '');
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(elkNode(n));
        }
        children = [...groups.entries()].sort((a, b) => (a[0] === 'missing') - (b[0] === 'missing') || compareNatural(a[0], b[0]))
            .map(([key, list]) => ({id: 'group:' + key, layoutOptions: {'elk.padding': '[top=40,left=18,bottom=18,right=18]', 'elk.spacing.nodeNode': '12'}, children: list}));
    } else {
        children = nodes.map(elkNode);
    }
    return {
        id: 'root',
        layoutOptions: dependencyOptions(nodes.length, grouped),
        children,
        edges: graph.edges.map(e => ({id: e.id, sources: [e.from], targets: [e.to]}))
    };
}

function typeRank(n) {
    return {ruleflow: 0, ruleset: 1, rule: 2, missing: 3}[n.type] || 4;
}

/** The group label: "rules/order  XML · 11 artifacts", or "Pricing/Loyalty  5 artifacts" for a category. */
export function groupLabel(key, graph, index) {
    if (key === 'missing') return {name: 'NOT REGISTERED', caption: '', missing: true};
    if (key.startsWith('c:')) {
        const path = key.slice(2);
        const n = [...graph.nodes.values()].filter(x => (x.categoryId || '') === path && x.type !== 'missing').length;
        return {name: path || 'UNCATEGORISED', caption: plural(n, 'artifact'), missing: false};
    }
    const entry = index.byPackage.get(key);
    const n = [...graph.nodes.values()].filter(x => x.packageId === key).length;
    const kind = entry ? (entry.pkg.kind === 'xml' ? 'XML' : 'Java') : '';
    return {name: key || '(default package)', caption: kind + ' · ' + plural(n, 'artifact'), missing: false};
}

/** A sentence for the screen reader describing the graph. */
export function describeGraph(graph, index, focus) {
    const n = graph.nodes.size;
    const flows = [...graph.nodes.values()].filter(x => x.type === 'ruleflow').map(x => x.name);
    if (focus) return 'Focus graph on ' + focus.name + ': ' + n + ' artifacts, ' + graph.edges.length + ' connections.';
    return 'Whole-application graph: ' + plural(n, 'artifact') + ', ' + plural(graph.edges.length, 'connection') + (flows.length ? '; flows: ' + flows.join(', ') : '') + '.';
}

export {typeLabel, FONT_MONO};
