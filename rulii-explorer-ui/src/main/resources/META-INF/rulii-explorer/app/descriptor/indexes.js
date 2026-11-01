import {TYPE_ORDER, compareNatural, expressionsOf, walkCommands, sourceText, beanExpressionsOf} from './format.js';

/**
 * Indexes built once when the descriptor loads (SOLUTION §9.2). Every view reads these; nothing
 * re-scans the descriptor.
 *
 * @typedef {object} Index
 * @property {Map<string, object>} byId
 * @property {{rule: object[], ruleset: object[], ruleflow: object[]}} byType
 * @property {Map<string, {pkg: object, artifacts: object[], counts: object, files: Set<string>}>} byPackage
 * @property {object[]} packages           the descriptor's packages in natural order, each with artifact counts
 * @property {Map<string, object[]>} usedBy   references pointing at an artifact (its "used by"), beans' included
 * @property {Map<string, object[]>} uses     references leaving an artifact
 * @property {Map<string, object>} bindings   binding usages by name
 * @property {Map<string, {reads: Set<string>, writes: Set<string>}>} bindingPaths  dotted paths read and written per binding
 * @property {Map<string, object[]>} problemsByArtifact
 * @property {Map<string, 'error'|'warning'|'info'>} worstByArtifact
 * @property {{error: number, warning: number, info: number, total: number}} problemCounts   the Rules section's problems only
 * @property {object[]} problems             the Rules section's problems: everything that is not about a bean
 * @property {Set<string>} undescribed       artifacts with an UNDESCRIBABLE problem
 * @property {Map<string, object>} kindCounts  per type: kind → count
 * @property {Map<string, Map<string, {command: object, number: string, parent: object}>>} commandsByPath  per flow id
 * @property {Categories} categories   the category tree, tags, and which artifact sits where
 * @property {Beans|null} beans        the Beans section (descriptor 1.1); null when the descriptor has no beans
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
 *
 * @typedef {object} Beans
 * @property {object[]} list               the beans, sorted by id
 * @property {Map<string, object>} byId
 * @property {Array<{id: string, beans: object[]}>} packages   beans grouped by package, packages in natural order
 * @property {Map<string, object[]>} cascadesTo   bean id → the cascade references leaving it
 * @property {Map<string, object[]>} cascadedFrom bean id → the cascade references reaching it
 * @property {Map<string, object[]>} runs         bean id → the runs references leaving it (its @Validate targets)
 * @property {Map<string, object[]>} problemsByBean
 * @property {Map<string, 'error'|'warning'|'info'>} worstByBean
 * @property {{error: number, warning: number, info: number, total: number}} problemCounts
 * @property {object[]} problems           the problems about beans
 * @property {Set<string>} undescribed     beans with an UNDESCRIBABLE problem
 * @property {{beans: number, constraints: number, cascades: number, executables: number, packages: number, scan: number, configured: number, validated: number, cascade: number}} counts
 * @property {{jakarta: number, rulii: number, other: number, expr: number, validate: number}} constraintKinds
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

    // Beans (descriptor 1.1): their problems are theirs, not the Rules section's.
    const beanIds = new Set((d.beans || []).map(b => b.id));

    // Problems
    const problemsByArtifact = new Map();
    const worstByArtifact = new Map();
    const problemCounts = {error: 0, warning: 0, info: 0, total: 0};
    const undescribed = new Set();
    const problems = [];
    const rank = {error: 0, warning: 1, info: 2};
    for (const p of d.problems) {
        if (p.artifact && beanIds.has(p.artifact)) continue;
        problems.push(p);
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

    const beans = d.beans ? buildBeans(d, usedBy, uses) : null;

    return {
        descriptor: d, byId, byType, byPackage, packages, usedBy, uses, bindings, bindingPaths,
        problemsByArtifact, worstByArtifact, problemCounts, problems, undescribed, kindCounts, commandsByPath, categories, beans,
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

/**
 * The Beans section (descriptor 1.1): the beans by id and by package, the cascades between them,
 * the registry rules they run, and their own problems. Nothing here is read by the Rules section.
 */
function buildBeans(d, usedBy, uses) {
    const list = [...d.beans].sort((a, b) => compareNatural(a.id, b.id));
    const byId = new Map(list.map(b => [b.id, b]));
    const byPackage = new Map();
    for (const b of list) push(byPackage, b.packageId || '', b);
    const packages = [...byPackage.entries()].sort((x, y) => compareNatural(x[0], y[0])).map(([id, beans]) => ({id, beans: beans.sort((a, b) => compareNatural(a.name, b.name) || compareNatural(a.id, b.id))}));

    const cascadesTo = new Map();
    const cascadedFrom = new Map();
    const runs = new Map();
    for (const b of list) {
        for (const ref of uses.get(b.id) || []) {
            if (ref.type === 'cascades') { push(cascadesTo, b.id, ref); push(cascadedFrom, ref.to, ref); }
            else if (ref.type === 'runs') push(runs, b.id, ref);
        }
    }

    const problemsByBean = new Map();
    const worstByBean = new Map();
    const problemCounts = {error: 0, warning: 0, info: 0, total: 0};
    const undescribed = new Set();
    const problems = [];
    const rank = {error: 0, warning: 1, info: 2};
    for (const p of d.problems) {
        if (!p.artifact || !byId.has(p.artifact)) continue;
        problems.push(p);
        problemCounts[p.severity] = (problemCounts[p.severity] || 0) + 1;
        problemCounts.total++;
        push(problemsByBean, p.artifact, p);
        const current = worstByBean.get(p.artifact);
        if (current === undefined || rank[p.severity] < rank[current]) worstByBean.set(p.artifact, p.severity);
        if (p.code === 'UNDESCRIBABLE') undescribed.add(p.artifact);
    }

    const counts = {beans: list.length, constraints: 0, cascades: 0, executables: 0, packages: packages.length, scan: 0, configured: 0, validated: 0, cascade: 0};
    const constraintKinds = {jakarta: 0, rulii: 0, other: 0, expr: 0, validate: 0};
    for (const b of list) {
        counts[b.discovery] = (counts[b.discovery] || 0) + 1;
        counts.executables += (b.executables || []).length;
        for (const {kind, constraint} of beanDeclarations(b)) {
            counts.constraints++;
            if (kind === 'constraint') constraintKinds[constraintFamily(constraint.annotation)]++;
            else constraintKinds[kind]++;
        }
    }
    for (const refs of cascadesTo.values()) counts.cascades += refs.length;

    return {list, byId, packages, cascadesTo, cascadedFrom, runs, problemsByBean, worstByBean, problemCounts, problems, undescribed, counts, constraintKinds};
}

/**
 * Every declaration a bean carries, flattened: constraints (composed ones count once, as the
 * reader wrote them), `@Expr` and `@Validate`, on the bean, its properties, their container
 * elements, and its executables' parameters, cross-parameter slots and return values.
 *
 * @returns {Array<{kind: 'constraint'|'expr'|'validate', constraint?: object, expr?: object, validation?: object, path: string, owner: string}>}
 */
export function beanDeclarations(bean) {
    const out = [];
    const slot = (holder, path, owner) => {
        for (const c of holder.constraints || []) out.push({kind: 'constraint', constraint: c, path, owner});
        for (const e of holder.expressions || []) out.push({kind: 'expr', expr: e, path, owner});
        for (const v of holder.validations || []) out.push({kind: 'validate', validation: v, path, owner});
        (holder.containerElements || []).forEach((el, i) => elements(el, path + '.containerElements[' + i + ']', owner));
    };
    const elements = (el, path, owner) => {
        for (const c of el.constraints || []) out.push({kind: 'constraint', constraint: c, path, owner});
        (el.children || []).forEach((ch, i) => elements(ch, path + '.children[' + i + ']', owner));
    };
    slot(bean, '', bean.name);
    for (const p of bean.properties || []) slot(p, 'properties.' + p.name, p.name);
    for (const e of bean.executables || []) {
        const base = 'executables.' + e.key;
        for (const p of e.parameters || []) slot(p, base + '.parameters[' + p.index + ']', e.name + '(' + p.name + ')');
        if (e.crossParameter) slot(e.crossParameter, base + '.crossParameter', e.name + '()');
        if (e.returnValue) slot(e.returnValue, base + '.returnValue', e.name + '() result');
    }
    return out;
}

/** Where a constraint annotation comes from: Jakarta's built-ins, rulii's own, or the application's. */
export function constraintFamily(annotation) {
    if (!annotation) return 'other';
    if (annotation.startsWith('jakarta.validation.constraints.')) return 'jakarta';
    if (annotation.startsWith('org.rulii.validation.beans.constraints.')) return 'rulii';
    return 'other';
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

/**
 * The member a bean problem path points at, as the reader would name it: "property tier",
 * "placeOrder(order)", "placeOrder() result", or null for the bean itself.
 *
 * @returns {{label: string, member: string}|null}  `member` is the `?member=` value that scrolls to it
 */
export function beanMember(bean, path) {
    if (!bean || !path) return null;
    let m = /^properties\.([^.[]+)/.exec(path);
    if (m) return {label: 'property ' + m[1], member: 'properties.' + m[1]};
    m = /^executables\.([^.[]+)(?:\.parameters\[(\d+)\]|\.(crossParameter|returnValue))?/.exec(path);
    if (m) {
        const e = (bean.executables || []).find(x => x.key === m[1]);
        const name = e ? e.name : m[1];
        if (m[2] !== undefined) {
            const p = e && e.parameters.find(x => String(x.index) === m[2]);
            return {label: name + '(' + (p ? p.name : m[2]) + ')', member: 'executables.' + m[1] + '.parameters[' + m[2] + ']'};
        }
        if (m[3] === 'returnValue') return {label: name + '() result', member: 'executables.' + m[1] + '.returnValue'};
        return {label: name + '()', member: 'executables.' + m[1]};
    }
    return null;
}

/** The source files of a package (XML) as short names. */
export function packageFiles(entry) {
    return [...entry.files].map(r => sourceText({resource: r}).split('/').pop()).sort(compareNatural);
}

export {beanExpressionsOf};
