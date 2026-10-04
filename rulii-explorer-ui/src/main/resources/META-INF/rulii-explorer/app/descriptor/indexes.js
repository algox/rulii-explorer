import {TYPE_ORDER, compareNatural, expressionsOf, walkCommands, sourceText} from './format.js';

/**
 * Indexes built once when the descriptor loads (SOLUTION §9.2). Every view reads these; nothing
 * re-scans the descriptor.
 *
 * @typedef {object} Index
 * @property {Map<string, object>} byId
 * @property {{rule: object[], ruleset: object[], ruleflow: object[]}} byType
 * @property {Map<string, {pkg: object, artifacts: object[], counts: object, files: Set<string>}>} byPackage
 * @property {object[]} packages           the descriptor's packages in natural order, each with artifact counts
 * @property {Map<string, object[]>} usedBy   references pointing at an artifact (its "used by")
 * @property {Map<string, object[]>} uses     references leaving an artifact
 * @property {Map<string, object>} bindings   binding usages by name
 * @property {Map<string, {reads: Set<string>, writes: Set<string>}>} bindingPaths  dotted paths read and written per binding
 * @property {Map<string, object[]>} problemsByArtifact
 * @property {Map<string, 'error'|'warning'|'info'>} worstByArtifact
 * @property {{error: number, warning: number, info: number, total: number}} problemCounts
 * @property {Set<string>} undescribed       artifacts with an UNDESCRIBABLE problem
 * @property {Map<string, object>} kindCounts  per type: kind → count
 * @property {Map<string, Map<string, {command: object, number: string, parent: object}>>} commandsByPath  per flow id
 * @property {Categories} categories   the category tree, tags, and which artifact sits where (descriptor 1.1)
 *
 * @typedef {object} Categories
 * @property {boolean} has                 whether any artifact declares a category; the sidebar and graph group by it when true
 * @property {CategoryNode[]} roots        top-level categories in natural order
 * @property {Map<string, CategoryNode>} byPath   every category, by its full path ("Pricing/Loyalty")
 * @property {Map<string, {path: string, inherited: boolean, from?: string}>} of   artifact id → its category: its own, or
 *           for a rule in exactly one rule set that has one, that set's (inherited, with the set's id)
 * @property {object[]} uncategorised      registered artifacts with no category, own or inherited
 * @property {Map<string, object[]>} tags  tag → artifacts, tags in natural order
 *
 * @typedef {object} CategoryNode
 * @property {string} path
 * @property {string} name                 the last level
 * @property {CategoryNode[]} children
 * @property {object[]} artifacts          directly in this category, flows then sets then rules
 * @property {{rule: number, ruleset: number, ruleflow: number, total: number}} counts   direct
 * @property {{rule: number, ruleset: number, ruleflow: number, total: number}} totals   including sub-categories
 */

export function buildIndex(d) {
    const byId = new Map();
    const byType = {rule: [], ruleset: [], ruleflow: []};
    for (const a of d.artifacts) {
        byId.set(a.id, a);
        (byType[a.type] || (byType[a.type] = [])).push(a);
    }
    for (const list of Object.values(byType)) list.sort((a, b) => compareNatural(a.name, b.name) || compareNatural(a.id, b.id));

    // Packages
    const byPackage = new Map();
    for (const pkg of d.packages) byPackage.set(pkg.id, {pkg, artifacts: [], counts: {rule: 0, ruleset: 0, ruleflow: 0, total: 0}, files: new Set()});
    for (const a of d.artifacts) {
        const key = a.packageId || '';
        if (!byPackage.has(key)) byPackage.set(key, {pkg: {id: key, kind: a.source && a.source.type === 'xml' ? 'xml' : 'java'}, artifacts: [], counts: {rule: 0, ruleset: 0, ruleflow: 0, total: 0}, files: new Set()});
        const entry = byPackage.get(key);
        entry.artifacts.push(a);
        entry.counts[a.type] = (entry.counts[a.type] || 0) + 1;
        entry.counts.total++;
        if (a.source && a.source.resource) entry.files.add(a.source.resource);
    }
    for (const entry of byPackage.values()) {
        entry.artifacts.sort((a, b) => TYPE_ORDER.indexOf(a.type) - TYPE_ORDER.indexOf(b.type) || compareNatural(a.name, b.name));
    }
    const packages = [...byPackage.values()].sort((a, b) => compareNatural(a.pkg.id, b.pkg.id));

    // References
    const usedBy = new Map();
    const uses = new Map();
    for (const ref of d.references) {
        push(usedBy, ref.to, ref);
        push(uses, ref.from, ref);
    }
    for (const list of usedBy.values()) list.sort((a, b) => compareNatural(a.from, b.from) || compareNatural(a.path, b.path));
    for (const list of uses.values()) list.sort((a, b) => compareNatural(a.path, b.path));

    // Bindings
    const bindings = new Map();
    for (const b of d.bindings) bindings.set(b.name, b);
    const bindingPaths = new Map();
    for (const a of d.artifacts) {
        for (const {expression} of expressionsOf(a)) {
            for (const path of expression.reads || []) notePath(bindingPaths, path, 'reads');
            for (const path of expression.writes || []) notePath(bindingPaths, path, 'writes');
        }
    }

    // Problems
    const problemsByArtifact = new Map();
    const worstByArtifact = new Map();
    const problemCounts = {error: 0, warning: 0, info: 0, total: 0};
    const undescribed = new Set();
    const rank = {error: 0, warning: 1, info: 2};
    for (const p of d.problems) {
        problemCounts[p.severity] = (problemCounts[p.severity] || 0) + 1;
        problemCounts.total++;
        if (p.artifact) {
            push(problemsByArtifact, p.artifact, p);
            const current = worstByArtifact.get(p.artifact);
            if (current === undefined || rank[p.severity] < rank[current]) worstByArtifact.set(p.artifact, p.severity);
            if (p.code === 'UNDESCRIBABLE') undescribed.add(p.artifact);
        }
    }

    // Kinds
    const kindCounts = new Map();
    for (const type of Object.keys(byType)) {
        const counts = {};
        for (const a of byType[type]) counts[a.kind] = (counts[a.kind] || 0) + 1;
        kindCounts.set(type, counts);
    }

    // Flow commands by path, with outline numbers
    const commandsByPath = new Map();
    for (const flow of byType.ruleflow) {
        const map = new Map();
        walkCommands(flow.ruleFlow.commands, (command, path, number, parent) => map.set(path, {command, number, parent}));
        commandsByPath.set(flow.id, map);
    }

    // Categories and tags
    const categories = buildCategories(d.artifacts, usedBy);

    return {
        descriptor: d, byId, byType, byPackage, packages, usedBy, uses, bindings, bindingPaths,
        problemsByArtifact, worstByArtifact, problemCounts, undescribed, kindCounts, commandsByPath, categories,
        counts: {rule: byType.rule.length, ruleset: byType.ruleset.length, ruleflow: byType.ruleflow.length, packages: packages.length}
    };
}

/**
 * The category tree. An artifact's own category wins; a rule without one that belongs to exactly
 * one rule set with an own category is shown under that set's, marked inherited, because a member
 * of a Pricing set is a pricing rule in every practical sense. Nothing is inferred from packages.
 */
function buildCategories(artifacts, usedBy) {
    const of = new Map();
    let own = 0;
    for (const a of artifacts) if (a.category) { of.set(a.id, {path: a.category, inherited: false}); own++; }
    for (const a of artifacts) {
        if (of.has(a.id) || a.type !== 'rule') continue;
        const sets = [...new Set((usedBy.get(a.id) || []).filter(r => r.type === 'contains').map(r => r.from))];
        if (sets.length !== 1) continue;
        const parent = of.get(sets[0]);
        if (parent && !parent.inherited) of.set(a.id, {path: parent.path, inherited: true, from: sets[0]});
    }
    const byPath = new Map();
    const roots = [];
    const zero = () => ({rule: 0, ruleset: 0, ruleflow: 0, total: 0});
    const node = (path) => {
        let n = byPath.get(path);
        if (n) return n;
        const cut = path.lastIndexOf('/');
        n = {path, name: cut >= 0 ? path.slice(cut + 1) : path, children: [], artifacts: [], counts: zero(), totals: zero()};
        byPath.set(path, n);
        if (cut >= 0) node(path.slice(0, cut)).children.push(n);
        else roots.push(n);
        return n;
    };
    for (const a of artifacts) {
        const c = of.get(a.id);
        if (c) node(c.path).artifacts.push(a);
    }
    const sortArtifacts = (list) => list.sort((a, b) => TYPE_ORDER.indexOf(a.type) - TYPE_ORDER.indexOf(b.type) || compareNatural(a.name, b.name));
    const finish = (n) => {
        n.children.sort((x, y) => compareNatural(x.name, y.name));
        sortArtifacts(n.artifacts);
        for (const a of n.artifacts) { n.counts[a.type]++; n.counts.total++; }
        for (const t of ['rule', 'ruleset', 'ruleflow', 'total']) n.totals[t] = n.counts[t];
        for (const c of n.children) { finish(c); for (const t of ['rule', 'ruleset', 'ruleflow', 'total']) n.totals[t] += c.totals[t]; }
    };
    roots.sort((x, y) => compareNatural(x.name, y.name));
    for (const r of roots) finish(r);
    // "Uncategorised" only means something once categories are in use; before that, nothing is missing one.
    const uncategorised = own > 0 ? sortArtifacts(artifacts.filter(a => !of.has(a.id) && a.registered !== false)) : [];
    const tags = new Map();
    for (const a of artifacts) for (const t of a.tags || []) push(tags, t, a);
    for (const list of tags.values()) sortArtifacts(list);
    return {has: own > 0, roots, byPath, of, uncategorised, tags: new Map([...tags.entries()].sort((x, y) => compareNatural(x[0], y[0])))};
}

/** The parents of a category path, shortest first: "Pricing/Loyalty/Gold" → ["Pricing", "Pricing/Loyalty"]. */
export function categoryParents(path) {
    const levels = path.split('/');
    return levels.slice(0, -1).map((_, i) => levels.slice(0, i + 1).join('/'));
}

function push(map, key, value) {
    if (key == null) return;
    const list = map.get(key);
    if (list) list.push(value);
    else map.set(key, [value]);
}

function notePath(map, path, kind) {
    const root = path.split(/[.[(]/)[0];
    if (!root) return;
    let entry = map.get(root);
    if (!entry) map.set(root, entry = {reads: new Set(), writes: new Set()});
    if (path !== root) entry[kind].add(path);
}

/** The first flow command path label chain for a problem path, e.g. ["for each item", "run 'prefixRule' · by name"]. */
export function problemPath(index, problem) {
    if (!problem.artifact || !problem.path) return [];
    const flowCommands = index.commandsByPath.get(problem.artifact);
    if (!flowCommands) return [];
    const chain = [];
    let path = problem.path;
    while (path) {
        const entry = flowCommands.get(path);
        if (entry) chain.unshift(entry);
        const cut = path.lastIndexOf('.', path.lastIndexOf('['));
        path = cut > 0 ? path.slice(0, cut) : '';
    }
    return chain;
}

/** The source files of a package (XML) as short names. */
export function packageFiles(entry) {
    return [...entry.files].map(r => sourceText({resource: r}).split('/').pop()).sort(compareNatural);
}
