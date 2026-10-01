import {loadElk} from './vendor.js';

/**
 * The layout interface (SOLUTION §9.3): a graph of nodes (possibly nested), edges and sizes
 * goes in; absolute positions and edge routes come out. ELK is the one implementation, run in
 * its worker; results are cached per key so switching back to a view is instant (FR-56).
 *
 * @typedef {object} LayoutNode
 * @property {string} id
 * @property {number} [width]
 * @property {number} [height]
 * @property {LayoutNode[]} [children]
 * @property {Record<string, string>} [layoutOptions]
 *
 * @typedef {object} LayoutEdge
 * @property {string} id
 * @property {string[]} sources
 * @property {string[]} targets
 *
 * @typedef {object} Layout
 * @property {number} width
 * @property {number} height
 * @property {Map<string, {x: number, y: number, width: number, height: number, depth: number, isGroup: boolean}>} nodes
 * @property {Map<string, Array<[number, number]>>} routes
 */

const cache = new Map();

/** The options the spike settled on (RESULTS.md) for the dependency graph, left to right. */
export function dependencyOptions(nodeCount, grouped) {
    if (nodeCount > 200) {
        // Exactly the spike's large-graph settings (RESULTS.md): 0.7 s for 1,000 nodes. Model-order
        // constraints and component separation multiply that by twenty, so they stay off here.
        return {
            'elk.algorithm': 'layered',
            'elk.direction': 'RIGHT',
            'elk.edgeRouting': 'POLYLINE',
            'elk.layered.nodePlacement.strategy': 'BRANDES_KOEPF',
            'elk.layered.spacing.nodeNodeBetweenLayers': '64',
            'elk.spacing.nodeNode': '20',
            'elk.spacing.edgeNode': '16',
            'elk.hierarchyHandling': grouped ? 'INCLUDE_CHILDREN' : 'INHERIT',
            'elk.padding': '[top=20,left=20,bottom=20,right=20]'
        };
    }
    return {
        'elk.algorithm': 'layered',
        'elk.direction': 'RIGHT',
        'elk.edgeRouting': 'ORTHOGONAL',
        'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX',
        'elk.layered.spacing.nodeNodeBetweenLayers': '56',
        'elk.spacing.nodeNode': '14',
        'elk.spacing.edgeNode': '14',
        'elk.spacing.edgeEdge': '10',
        'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
        'elk.layered.crossingMinimization.forceNodeModelOrder': 'true',
        'elk.hierarchyHandling': grouped ? 'INCLUDE_CHILDREN' : 'INHERIT',
        'elk.separateConnectedComponents': 'true',
        'elk.spacing.componentComponent': '40',
        'elk.padding': '[top=20,left=20,bottom=20,right=20]'
    };
}

/** Flowcharts are small, so they keep orthogonal routing (VQ-10). */
export function flowchartOptions() {
    return {
        'elk.algorithm': 'layered',
        'elk.direction': 'DOWN',
        'elk.edgeRouting': 'ORTHOGONAL',
        'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX',
        'elk.layered.nodePlacement.favorStraightEdges': 'true',
        'elk.layered.spacing.nodeNodeBetweenLayers': '34',
        'elk.spacing.nodeNode': '28',
        'elk.spacing.edgeNode': '18',
        'elk.spacing.edgeEdge': '12',
        'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
        'elk.layered.crossingMinimization.forceNodeModelOrder': 'true',
        'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
        'elk.separateConnectedComponents': 'true',
        'elk.spacing.componentComponent': '48',
        'elk.padding': '[top=20,left=20,bottom=20,right=20]'
    };
}

/**
 * Lays out the graph, from the cache when the same key was laid out before.
 * @param {string} key          identifies the view and its inputs
 * @param {LayoutNode & {edges: LayoutEdge[]}} graph  the ELK JSON graph (root)
 * @returns {Promise<Layout>}
 */
export async function layout(key, graph) {
    if (cache.has(key)) return cache.get(key);
    const promise = (async () => {
        const elk = await loadElk();
        const result = await elk.layout(graph);
        return absolutePositions(result);
    })();
    cache.set(key, promise);
    promise.catch(() => cache.delete(key));
    return promise;
}

export function clearLayoutCache() {
    cache.clear();
}

/**
 * ELK reports children relative to their parent and edge sections relative to the edge's
 * container node; this flattens everything to absolute coordinates.
 */
export function absolutePositions(root) {
    const offsets = new Map([[root.id, {x: 0, y: 0}]]);
    const nodes = new Map();
    const walk = (node, ox, oy, depth) => {
        for (const child of node.children || []) {
            const x = ox + (child.x || 0), y = oy + (child.y || 0);
            offsets.set(child.id, {x, y});
            nodes.set(child.id, {x, y, width: child.width || 0, height: child.height || 0, depth, isGroup: !!(child.children && child.children.length)});
            walk(child, x, y, depth + 1);
        }
    };
    walk(root, 0, 0, 0);
    const routes = new Map();
    const walkEdges = (node) => {
        for (const edge of node.edges || []) {
            const o = offsets.get(edge.container || node.id) || {x: 0, y: 0};
            const points = [];
            for (const section of edge.sections || []) {
                points.push(section.startPoint, ...(section.bendPoints || []), section.endPoint);
            }
            routes.set(edge.id, points.map(p => [p.x + o.x, p.y + o.y]));
        }
        for (const child of node.children || []) walkEdges(child);
    };
    walkEdges(root);
    return {width: root.width || 0, height: root.height || 0, nodes, routes};
}

/** An SVG path through the points with rounded corners at the bends (DESIGN-SYSTEM §4). */
export function roundedPath(points, radius = 8) {
    if (!points || points.length === 0) return '';
    if (points.length === 1) return 'M' + points[0][0] + ' ' + points[0][1];
    let d = 'M' + fmt(points[0][0]) + ' ' + fmt(points[0][1]);
    for (let i = 1; i < points.length - 1; i++) {
        const [px, py] = points[i - 1];
        const [cx, cy] = points[i];
        const [nx, ny] = points[i + 1];
        const inLen = Math.hypot(cx - px, cy - py);
        const outLen = Math.hypot(nx - cx, ny - cy);
        const r = Math.min(radius, inLen / 2, outLen / 2);
        if (r < 1) { d += 'L' + fmt(cx) + ' ' + fmt(cy); continue; }
        const ax = cx - (cx - px) / inLen * r, ay = cy - (cy - py) / inLen * r;
        const bx = cx + (nx - cx) / outLen * r, by = cy + (ny - cy) / outLen * r;
        d += 'L' + fmt(ax) + ' ' + fmt(ay) + 'Q' + fmt(cx) + ' ' + fmt(cy) + ' ' + fmt(bx) + ' ' + fmt(by);
    }
    const last = points[points.length - 1];
    d += 'L' + fmt(last[0]) + ' ' + fmt(last[1]);
    return d;
}

function fmt(n) {
    return Math.round(n * 10) / 10;
}
