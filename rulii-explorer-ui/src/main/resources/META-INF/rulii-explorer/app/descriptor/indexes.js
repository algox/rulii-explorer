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

    return {
        descriptor: d, byId, byType, byPackage, packages, usedBy, uses, bindings, bindingPaths,
        problemsByArtifact, worstByArtifact, problemCounts, undescribed, kindCounts, commandsByPath,
        counts: {rule: byType.rule.length, ruleset: byType.ruleset.length, ruleflow: byType.ruleflow.length, packages: packages.length}
    };
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
