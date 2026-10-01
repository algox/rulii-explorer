import {commandChildren, durationText, plainText, shortType, targetName, walkCommands, isCompiled} from '../../descriptor/format.js';
import {flowchartOptions} from '../../graph-engine/layout.js';
import {FONT_NAME, textWidth} from '../../graph-engine/vendor.js';

/**
 * A rule flow as a flowchart (FR-53, DESIGN-SYSTEM §4): one node per command, decisions for
 * when and for-each, containers for scopes and loop bodies, async steps in a lane that joins
 * back at their await, exception handlers hanging off their step. Pure data, laid out by ELK.
 *
 * @typedef {object} FlowNode
 * @property {string} id         the command path ("commands[2]"), or "start", "return", "end", "finalizer", "global", "handler:<path>"
 * @property {'start'|'step'|'decision'|'exit'|'return'|'end'|'handler'|'global'|'finalizer'} kind
 * @property {string} overline   "RUN · RULE SET"
 * @property {string} text
 * @property {boolean} [mono]
 * @property {string} [as]
 * @property {'rule'|'ruleset'|'ruleflow'} [targetType]
 * @property {string} [resolution]
 * @property {boolean} [async]
 * @property {boolean} [compiled]
 * @property {object} [command]
 * @property {object} [target]
 * @property {string} [parent]   container id
 * @property {number} width
 * @property {number} height
 */

export const STEP_W = 240, STEP_H = 44, HANDLER_W = 184, HANDLER_H = 40;

export function buildFlowchart(flow, index) {
    const f = flow.ruleFlow || {commands: []};
    const nodes = [];
    const containers = [];
    const edges = [];
    const asyncByAs = new Map();
    const byPath = new Map();
    let edgeSeq = 0;
    const addNode = (n) => { nodes.push(n); if (n.command) byPath.set(n.id, n); return n; };
    const edge = (from, to, label, cls) => { edges.push({id: 'e' + (edgeSeq++), from, to, label: label || null, cls: cls || null}); };

    const params = flow.parameters || [];
    const startText = 'Start' + (params.length ? ' · ' + params.map(p => p.name).join(', ') : '');
    const start = addNode({id: 'start', kind: 'start', overline: '', text: startText, width: Math.max(160, Math.ceil(textWidth(startText, FONT_NAME)) + 36), height: 34});

    const sequence = (commands, prefix, parent, entries) => {
        // entries: [{id, label}] open ends that connect to the next node
        let open = entries;
        let first = null;
        commands.forEach((command, i) => {
            const path = prefix + '[' + i + ']';
            const result = build(command, path, parent, open);
            if (!first) first = result.entry;
            open = result.exits;
        });
        return {entry: first, exits: open};
    };

    const connect = (open, to) => { for (const o of open) edge(o.id, to, o.label, o.cls); };

    const build = (command, path, parent, open) => {
        const t = command.target;
        const target = t && t.kind === 'instance' ? index.byId.get(t.id) : (t && t.name ? [...index.byId.values()].find(a => a.id === t.name && t.resolution !== 'unresolved') : null);
        const targetType = target ? target.type : null;
        const resolution = t ? t.resolution : null;
        switch (command.type) {
            case 'run': case 'apply': {
                const n = addNode({id: path, kind: 'step', parent, command, target, targetType, resolution, as: command.as,
                    overline: (command.type === 'apply' ? 'APPLY' : 'RUN') + (targetType ? ' · ' + typeWord(targetType) : t && t.kind === 'by-class' ? ' · BY CLASS' : ' · BY NAME'),
                    text: target ? target.name : targetName(t, index), mono: !target, width: STEP_W, height: STEP_H});
                connect(open, n.id);
                return {entry: n.id, exits: [{id: n.id}]};
            }
            case 'async-run': {
                const n = addNode({id: path, kind: 'step', parent, command, target, targetType, resolution, as: command.as, async: true,
                    overline: 'ASYNC' + (targetType ? ' · ' + typeWord(targetType) : ''), text: target ? target.name : targetName(t, index), mono: !target, width: STEP_W, height: STEP_H});
                const before = edges.length;
                connect(open, n.id);
                for (let k = before; k < edges.length; k++) if (!edges[k].label) edges[k].label = 'async';
                if (command.as) asyncByAs.set(command.as, n.id);
                if (command.handler) handler(command.handler, path, n.id, parent);
                return {entry: n.id, exits: open};
            }
            case 'await': {
                const names = command.names || [];
                const n = addNode({id: path, kind: 'step', parent, command, overline: 'AWAIT' + (command.timeout ? ' · ' + durationText(command.timeout).toUpperCase() + ' TIMEOUT' : ''),
                    text: names.join(', '), mono: true, width: STEP_W, height: STEP_H});
                connect(open, n.id);
                for (const name of names) { const from = asyncByAs.get(name); if (from) edge(from, n.id, 'result', 'rx-fedge-async'); }
                return {entry: n.id, exits: [{id: n.id}]};
            }
            case 'bind': {
                const b = command.bind || {};
                const names = (b.names || []).map(x => x.name);
                const n = addNode({id: path, kind: 'step', parent, command, overline: 'BIND' + (b.kind ? ' · ' + bindWord(b.kind) : ''), text: names.join(', ') || (b.label || 'bindings'), mono: true, width: STEP_W, height: STEP_H});
                connect(open, n.id);
                return {entry: n.id, exits: [{id: n.id}]};
            }
            case 'execute': {
                const compiled = isCompiled(command.expression);
                const n = addNode({id: path, kind: 'step', parent, command, compiled, overline: 'EXECUTE' + (compiled ? ' · COMPILED' : ''), text: compiled ? 'compiled code' : plainText(command.expression), mono: compiled, width: STEP_W, height: STEP_H});
                connect(open, n.id);
                return {entry: n.id, exits: [{id: n.id}]};
            }
            case 'when': {
                const n = addNode({id: path, kind: 'decision', parent, command, overline: 'WHEN', text: plainText(command.condition) + '?', width: STEP_W, height: STEP_H});
                connect(open, n.id);
                const exits = [];
                if (command.then && command.then.length) {
                    const r = sequence(command.then, path + '.then', parent, [{id: n.id, label: 'yes'}]);
                    exits.push(...r.exits);
                } else exits.push({id: n.id, label: 'yes'});
                if (command.otherwise && command.otherwise.length) {
                    const r = sequence(command.otherwise, path + '.otherwise', parent, [{id: n.id, label: 'no'}]);
                    exits.push(...r.exits);
                } else exits.push({id: n.id, label: 'no'});
                return {entry: n.id, exits};
            }
            case 'for-each': {
                const n = addNode({id: path, kind: 'decision', parent, command, overline: 'FOR EACH', text: (command.item || 'item') + ' in ' + plainText(command.source), width: STEP_W, height: STEP_H});
                connect(open, n.id);
                const exits = [];
                if (command.body && command.body.length) {
                    const cid = 'container:' + path;
                    containers.push({id: cid, parent, title: 'FOR EACH · ' + (command.item || 'item'), children: []});
                    const r = sequence(command.body, path + '.body', cid, [{id: n.id, label: 'each item'}]);
                    exits.push(...r.exits);
                }
                exits.push({id: n.id, label: 'done'});
                return {entry: n.id, exits};
            }
            case 'scope': {
                const cid = 'container:' + path;
                containers.push({id: cid, parent, title: 'SCOPE' + (command.name ? ' · ' + command.name : ''), children: []});
                if (command.body && command.body.length) {
                    const r = sequence(command.body, path + '.body', cid, open);
                    return {entry: r.entry, exits: r.exits};
                }
                const n = addNode({id: path, kind: 'step', parent: cid, command, overline: 'SCOPE', text: 'empty scope', width: STEP_W, height: STEP_H});
                connect(open, n.id);
                return {entry: n.id, exits: [{id: n.id}]};
            }
            case 'exit': {
                const n = addNode({id: path, kind: 'exit', parent, command, overline: 'EXIT', text: command.expression ? 'Stop and return ' + plainText(command.expression) : 'Stop here', width: STEP_W, height: STEP_H});
                connect(open, n.id);
                return {entry: n.id, exits: []};
            }
            default: {
                const n = addNode({id: path, kind: 'step', parent, command, overline: 'CUSTOM', text: command.className ? shortType(command.className) : command.type, mono: true, width: STEP_W, height: STEP_H});
                connect(open, n.id);
                return {entry: n.id, exits: [{id: n.id}]};
            }
        }
    };

    const handler = (h, path, ownerId, parent) => {
        const body = h.body || [];
        const firstText = body.length ? firstCommandText(body[0], index) + (body.length > 1 ? ' +' + (body.length - 1) : '') : 'handled';
        const n = addNode({id: 'handler:' + path, kind: 'handler', parent, handler: h, overline: 'on ' + shortType(h.exceptionType || 'Exception'), text: firstText, mono: true, width: HANDLER_W, height: HANDLER_H});
        edge(ownerId, n.id, null, 'rx-fedge-handler');
    };

    const main = sequence(f.commands, 'commands', null, [{id: 'start'}]);
    let open = main.exits;
    if (f.finalizer) {
        const n = addNode({id: 'finalizer', kind: 'finalizer', overline: 'FINALLY', text: isCompiled(f.finalizer) ? 'compiled code' : plainText(f.finalizer), width: STEP_W, height: STEP_H});
        connect(open, n.id);
        open = [{id: n.id}];
    }
    if (f.returning) {
        const text = plainText(f.returning);
        const n = addNode({id: 'return', kind: 'return', overline: 'RETURN', text, mono: true, width: Math.max(160, Math.ceil(textWidth('RETURN  ' + text, FONT_NAME)) + 40), height: 34});
        connect(open, n.id);
    } else if (open.length) {
        const n = addNode({id: 'end', kind: 'end', overline: '', text: 'End', width: 120, height: 34});
        connect(open, n.id);
    }
    if (f.globalHandler) {
        const body = f.globalHandler.body || [];
        addNode({id: 'global', kind: 'global', handler: f.globalHandler, overline: 'GLOBAL HANDLER · ON ' + shortType(f.globalHandler.exceptionType || 'Exception').toUpperCase(),
            text: body.length ? firstCommandText(body[0], index) + (body.length > 1 ? ' +' + (body.length - 1) : '') : 'handled', width: STEP_W, height: STEP_H});
    }
    // containers' children
    for (const n of nodes) if (n.parent) { const c = containers.find(x => x.id === n.parent); if (c) c.children.push(n.id); }
    for (const c of containers) if (c.parent) { const p = containers.find(x => x.id === c.parent); if (p) p.children.push(c.id); }
    return {nodes, containers, edges, byPath, asyncIds: new Set(nodes.filter(n => n.async || n.kind === 'handler').map(n => n.id))};
}

function firstCommandText(command, index) {
    switch (command.type) {
        case 'bind': return 'bind ' + ((command.bind && command.bind.names) || []).map(n => n.name).join(', ');
        case 'execute': return isCompiled(command.expression) ? 'compiled code' : plainText(command.expression);
        case 'run': case 'async-run': case 'apply': return 'run ' + targetName(command.target, index);
        case 'exit': return 'stop';
        default: return command.type;
    }
}

function typeWord(type) {
    return type === 'ruleset' ? 'RULE SET' : type === 'ruleflow' ? 'RULE FLOW' : 'RULE';
}

function bindWord(kind) {
    return {bean: 'FROM BEAN', map: 'FROM MAP', bindings: 'FROM BINDINGS', literal: 'LITERAL', declarations: 'DECLARED', loader: 'LOADED', expression: 'EXPRESSION'}[kind] || kind.toUpperCase();
}

/** The ELK input: containers as compound nodes, everything else flat. */
export function toElkFlow(model) {
    const nodeOf = (n) => ({id: n.id, width: n.width, height: n.height});
    const containerOf = (c) => ({
        id: c.id,
        layoutOptions: {'elk.padding': '[top=36,left=20,bottom=20,right=20]'},
        children: c.children.map(id => { const n = model.nodes.find(x => x.id === id); if (n) return nodeOf(n); const sub = model.containers.find(x => x.id === id); return containerOf(sub); })
    });
    const children = [];
    for (const n of model.nodes) if (!n.parent) children.push(nodeOf(n));
    for (const c of model.containers) if (!c.parent) children.push(containerOf(c));
    return {
        id: 'root',
        layoutOptions: flowchartOptions(),
        children,
        edges: model.edges.map(e => ({id: e.id, sources: [e.from], targets: [e.to]}))
    };
}

export {commandChildren, walkCommands};
