import {expressionsOf, identifierWords, plainText, typeLabel, compareNatural} from '../descriptor/format.js';

/**
 * The in-memory search index (SOLUTION §9.1): field-weighted, prefix and light fuzzy matching
 * over artifact names, descriptions, plain-English expressions, error codes and bindings. A
 * thousand artifacts need no library.
 *
 * @typedef {object} Hit
 * @property {'artifact'|'binding'} kind
 * @property {object} [artifact]
 * @property {object} [binding]
 * @property {number} score
 * @property {Array<[number, number]>} nameMatches   character ranges to highlight in the name
 * @property {{label: string, expression?: object, text?: string}|null} snippet
 */

/**
 * Splits a palette query into its text and its filters: `tag:vip` keeps artifacts carrying the
 * tag (several must all match), `in:Pricing` keeps a category and the ones below it (a level
 * prefix works too: `in:loyal` finds Pricing/Loyalty). A bare `tag:` or `in:` is ignored.
 *
 * @returns {{text: string, tags: string[], category: string|null}}
 */
export function parseQuery(raw) {
    const tags = [];
    let category = null;
    const text = [];
    for (const token of String(raw || '').split(/\s+/)) {
        if (!token) continue;
        const m = /^(tag|in|category):(.*)$/i.exec(token);
        if (!m) text.push(token);
        else if (m[2] && m[1].toLowerCase() === 'tag') tags.push(m[2].toLowerCase());
        else if (m[2]) category = m[2].toLowerCase();
    }
    return {text: text.join(' '), tags, category};
}

export function buildSearch(descriptor, index) {
    const docs = [];
    for (const a of descriptor.artifacts) docs.push(artifactDoc(a, index));
    for (const b of descriptor.bindings) docs.push(bindingDoc(b, index));
    return {
        /**
         * @param {string} query   the text (filters already split off by {@link parseQuery})
         * @param {{limit?: number, type?: string, tags?: string[], category?: string|null}} [options]
         * @returns {Array<{type: string, label: string, hits: Hit[]}>} groups in sidebar order
         */
        query(query, options = {}) {
            const terms = identifierWords(query).filter(Boolean);
            const filtered = (options.tags && options.tags.length > 0) || !!options.category;
            if (!terms.length && !filtered) return [];
            const hits = [];
            for (const doc of docs) {
                if (options.type && doc.type !== options.type) continue;
                if (filtered && (doc.kind !== 'artifact' || !matchesFilters(doc, options))) continue;
                const hit = terms.length ? score(doc, terms, query.trim().toLowerCase()) : listed(doc);
                if (hit) hits.push(hit);
            }
            hits.sort((a, b) => b.score - a.score || compareNatural(a.name, b.name));
            const limit = options.limit || (terms.length ? 24 : 60);
            const grouped = new Map();
            for (const hit of hits.slice(0, limit)) {
                const key = hit.type;
                if (!grouped.has(key)) grouped.set(key, {type: key, label: typeLabel(key, true), hits: []});
                grouped.get(key).hits.push(hit);
            }
            const order = ['rule', 'ruleset', 'ruleflow', 'binding'];
            return [...grouped.values()].sort((a, b) => order.indexOf(a.type) - order.indexOf(b.type));
        }
    };
}

function artifactDoc(a, index) {
    const expressions = expressionsOf(a)
        .filter(e => e.expression.kind !== 'compiled')
        .map(e => ({label: e.label, expression: e.expression, text: plainText(e.expression).toLowerCase(), raw: (e.expression.text || '').toLowerCase()}));
    const keywords = [];
    if (a.validation) {
        if (a.validation.errorCode) keywords.push(a.validation.errorCode.toLowerCase());
        if (a.validation.validator) keywords.push(a.validation.validator.toLowerCase(), 'r:' + a.validation.validator.toLowerCase());
    }
    if (a.id !== a.name) keywords.push(a.id.toLowerCase());
    if (a.className) keywords.push(a.className.toLowerCase());
    keywords.push(a.kind, a.type);
    const tags = (a.tags || []).map(t => t.toLowerCase());
    keywords.push(...tags);
    const category = index.categories && index.categories.of.get(a.id);
    const paths = new Set();
    for (const {expression} of expressionsOf(a)) {
        for (const p of expression.reads || []) paths.add(p.toLowerCase());
        for (const p of expression.writes || []) paths.add(p.toLowerCase());
    }
    for (const p of a.parameters) paths.add(p.name.toLowerCase());
    return {
        kind: 'artifact', type: a.type, artifact: a,
        name: a.name, nameLower: a.name.toLowerCase(), nameWords: identifierWords(a.name),
        description: (a.description || '').toLowerCase(),
        packageId: (a.packageId || '').toLowerCase(),
        category: category ? category.path.toLowerCase() : '',
        tags,
        expressions, keywords, paths: [...paths]
    };
}

function matchesFilters(doc, options) {
    for (const t of options.tags || []) if (!doc.tags.includes(t)) return false;
    const c = options.category;
    if (!c) return true;
    return doc.category === c || doc.category.startsWith(c + '/') || doc.category.split('/').some(level => level.startsWith(c));
}

/** A hit for a filter-only query: every match listed, in name order, with its description as the snippet. */
function listed(doc) {
    return {kind: doc.kind, type: doc.type, artifact: doc.artifact, binding: doc.binding, name: doc.name, score: 1, nameMatches: [],
        snippet: doc.description ? {label: null, text: doc.description} : null};
}

function bindingDoc(b, index) {
    const paths = index.bindingPaths.get(b.name);
    const people = [...(b.readBy || []), ...(b.writtenBy || [])].map(id => (index.byId.get(id) || {name: id}).name);
    return {
        kind: 'binding', type: 'binding', binding: b,
        name: b.name, nameLower: b.name.toLowerCase(), nameWords: identifierWords(b.name),
        description: '', packageId: '', expressions: [],
        keywords: people.map(n => n.toLowerCase()),
        paths: paths ? [...paths.reads, ...paths.writes].map(p => p.toLowerCase()) : []
    };
}

function score(doc, terms, whole) {
    let total = 0;
    let bestSnippet = null;
    let bestSnippetScore = 0;
    const nameMatches = [];
    for (const term of terms) {
        let best = 0;
        // Name
        const at = doc.nameLower.indexOf(term);
        if (doc.nameLower === term) best = 100;
        else if (doc.nameLower.startsWith(term)) best = 85;
        else if (doc.nameWords.some(w => w.startsWith(term))) best = 70;
        else if (at >= 0) best = 55;
        else if (term.length >= 4 && doc.nameWords.some(w => within1(w, term))) best = 22;
        if (at >= 0) nameMatches.push([at, at + term.length]);
        // Keywords: codes, validators, ids, class names
        for (const k of doc.keywords) {
            if (k === term) best = Math.max(best, 65);
            else if (k.includes(term)) best = Math.max(best, 45);
        }
        // Binding paths
        for (const p of doc.paths) {
            if (p === term) best = Math.max(best, 50);
            else if (p.split('.').some(seg => seg.startsWith(term))) best = Math.max(best, 40);
        }
        // Description
        if (doc.description) {
            const words = doc.description.split(/[^a-z0-9.]+/);
            if (words.some(w => w.startsWith(term))) {
                best = Math.max(best, 32);
                if (bestSnippetScore < 32) { bestSnippet = {label: null, text: doc.description}; bestSnippetScore = 32; }
            }
        }
        // Expressions (plain text and raw)
        for (const e of doc.expressions) {
            const words = e.text.split(/[^a-z0-9.]+/);
            let s = 0;
            if (words.some(w => w.startsWith(term))) s = 30;
            else if (e.raw.includes(term)) s = 26;
            if (s) {
                best = Math.max(best, s);
                if (s > bestSnippetScore) { bestSnippet = {label: e.label, expression: e.expression}; bestSnippetScore = s; }
            }
        }
        // Package
        if (doc.packageId && doc.packageId.includes(term)) best = Math.max(best, 12);
        if (!best) return null;
        total += best;
    }
    if (doc.nameLower === whole) total += 40;
    return {
        kind: doc.kind, type: doc.type, artifact: doc.artifact, binding: doc.binding, name: doc.name,
        score: total, nameMatches: merge(nameMatches),
        snippet: bestSnippet || (doc.description ? {label: null, text: doc.description} : null)
    };
}

/** Levenshtein distance of 1 (one typo), for words of 4+ letters. */
function within1(a, b) {
    if (Math.abs(a.length - b.length) > 1) return false;
    let i = 0, j = 0, edits = 0;
    while (i < a.length && j < b.length) {
        if (a[i] === b[j]) { i++; j++; continue; }
        if (++edits > 1) return false;
        if (a.length > b.length) i++;
        else if (a.length < b.length) j++;
        else { i++; j++; }
    }
    return edits + (a.length - i) + (b.length - j) <= 1;
}

function merge(ranges) {
    const sorted = [...ranges].sort((x, y) => x[0] - y[0]);
    const out = [];
    for (const r of sorted) {
        const last = out[out.length - 1];
        if (last && r[0] <= last[1]) last[1] = Math.max(last[1], r[1]);
        else out.push([r[0], r[1]]);
    }
    return out;
}
