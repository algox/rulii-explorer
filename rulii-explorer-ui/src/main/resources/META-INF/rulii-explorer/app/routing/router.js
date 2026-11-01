/**
 * Hash routes (FR-34, FR-54). Every screen has an address that survives a reload and can be
 * pasted into a chat:
 *
 *   #/                       overview
 *   #/problems?severity=error
 *   #/graph  #/graph?focus={id}&depth=1|2|all&selected={id}
 *   #/rule/{id}  #/ruleset/{id}  #/ruleflow/{id}?view=flowchart|outline&step=commands[1].body[0]
 *   #/binding/{name}
 *   #/package/{id}
 *   #/category/{path}        a category by its full path, e.g. #/category/Pricing/Loyalty
 *
 * The Beans section (descriptor 1.1, FR-70) has its own addresses, so a link opens in the right
 * section:
 *
 *   #/beans                  the beans overview
 *   #/beans/problems?severity=error
 *   #/beans/graph?focus={id}&depth=1|2|all&selected={id}
 *   #/bean/{id}?member=properties.total   a bean, scrolled to one member
 *
 * Ids are URI-encoded, so XML package ids with slashes work.
 *
 * @typedef {object} Route
 * @property {string} name   'overview' | 'problems' | 'graph' | 'artifact' | 'binding' | 'package' | 'category' | 'beans' | 'bean' | 'missing'
 * @property {'rules'|'beans'} section   which section of the UI the route belongs to
 * @property {string} [type] 'rule' | 'ruleset' | 'ruleflow' (artifact routes)
 * @property {string} [id]
 * @property {Record<string, string>} query
 */

const ARTIFACT_SEGMENTS = {rule: 'rule', ruleset: 'ruleset', ruleflow: 'ruleflow'};

/** @returns {Route} */
export function parseRoute(hash) {
    const raw = (hash || '').replace(/^#\/?/, '');
    const [path, queryString] = raw.split('?');
    const query = {};
    if (queryString) {
        for (const pair of queryString.split('&')) {
            if (!pair) continue;
            const [key, value = ''] = pair.split('=');
            query[decodeURIComponent(key)] = decodeURIComponent(value);
        }
    }
    const segments = path.split('/').filter(Boolean).map(decodeURIComponent);
    if (segments.length === 0) return {name: 'overview', section: 'rules', query};
    const [head, ...rest] = segments;
    const id = rest.join('/');
    if (head === 'problems') return {name: 'problems', section: 'rules', query};
    if (head === 'graph') return {name: 'graph', section: 'rules', query};
    if (ARTIFACT_SEGMENTS[head] && id) return {name: 'artifact', section: 'rules', type: head, id, query};
    if (head === 'binding' && id) return {name: 'binding', section: 'rules', id, query};
    if (head === 'package' && id) return {name: 'package', section: 'rules', id, query};
    if (head === 'category' && id) return {name: 'category', section: 'rules', id, query};
    if (head === 'beans' && !id) return {name: 'beans', section: 'beans', query};
    if (head === 'beans' && id === 'problems') return {name: 'problems', section: 'beans', query};
    if (head === 'beans' && id === 'graph') return {name: 'graph', section: 'beans', query};
    if (head === 'bean' && id) return {name: 'bean', section: 'beans', id, query};
    return {name: 'missing', section: head === 'beans' || head === 'bean' ? 'beans' : 'rules', id: path, query};
}

function encode(id) {
    return id.split('/').map(encodeURIComponent).join('/');
}

function withQuery(href, query) {
    const entries = Object.entries(query || {}).filter(([, v]) => v !== undefined && v !== null && v !== '');
    if (entries.length === 0) return href;
    return href + '?' + entries.map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v)).join('&');
}

/** Builders for hrefs, so no component spells a route by hand. */
export const routes = {
    overview: () => '#/',
    problems: (query) => withQuery('#/problems', query),
    /** `routes.artifact(artifact, query?)` or `routes.artifact(type, id, query?)`. */
    artifact: (artifactOrType, idOrQuery, query) => {
        const byObject = typeof artifactOrType !== 'string';
        const type = byObject ? artifactOrType.type : artifactOrType;
        const id = byObject ? artifactOrType.id : idOrQuery;
        return withQuery('#/' + (ARTIFACT_SEGMENTS[type] || 'rule') + '/' + encode(id), byObject ? idOrQuery : query);
    },
    binding: (name) => '#/binding/' + encode(name),
    /** The dependency graph: `{focus, depth, selected}` are all optional. */
    graph: (query) => withQuery('#/graph', query),
    package: (id) => '#/package/' + encode(id),
    /** A category by its full path: `#/category/Pricing/Loyalty`. */
    category: (path) => '#/category/' + encode(path),
    /** The Beans section: its overview, its problems, and a bean, optionally scrolled to a member (`properties.total`). */
    beans: () => '#/beans',
    beansProblems: (query) => withQuery('#/beans/problems', query),
    /** The Beans graph: `{focus, depth, selected}` are all optional. */
    beansGraph: (query) => withQuery('#/beans/graph', query),
    bean: (beanOrId, query) => withQuery('#/bean/' + encode(typeof beanOrId === 'string' ? beanOrId : beanOrId.id), query)
};

/** Starts listening to the hash and keeps the store's route current. */
export function startRouter(store) {
    const apply = () => {
        store.set({route: parseRoute(location.hash)});
        const main = document.querySelector('main.rx-main');
        if (main) main.scrollTop = 0;
    };
    addEventListener('hashchange', apply);
    apply();
}

export function navigate(href) {
    if (location.hash === href) return;
    location.hash = href;
}
