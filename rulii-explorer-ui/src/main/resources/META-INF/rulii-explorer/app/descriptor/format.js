/**
 * Labels and small formatting helpers shared by every view. Pure functions of descriptor data.
 */

export const TYPE_ORDER = ['ruleflow', 'ruleset', 'rule'];

const TYPE_LABELS = {
    rule: {one: 'Rule', many: 'Rules', group: 'Rules'},
    ruleset: {one: 'Rule set', many: 'Rule sets', group: 'Rule sets'},
    ruleflow: {one: 'Rule flow', many: 'Rule flows', group: 'Rule flows'},
    binding: {one: 'Binding', many: 'Bindings', group: 'Bindings'}
};

export function typeLabel(type, plural = false) {
    const t = TYPE_LABELS[type] || TYPE_LABELS.rule;
    return plural ? t.many : t.one;
}

/** The kind caption under a title: "XML · script", "Validator · r:email", "@Rule class", "Java builder · lambda". */
export function kindLabel(artifact) {
    const kind = artifact.kind;
    if (artifact.type === 'rule') {
        switch (kind) {
            case 'xml-script': return 'XML · script';
            case 'predefined-validator': return 'Validator · r:' + (artifact.validation && artifact.validation.validator || 'validator');
            case 'rule-class': return '@Rule class';
            case 'java-builder': return 'Java builder' + (hasScript(artifact) ? ' · script' : hasCompiled(artifact) ? ' · lambda' : '');
            default: return 'Unknown';
        }
    }
    const base = kind === 'xml' ? 'XML' : kind === 'java-builder' ? 'Java builder' : kind === 'unknown' ? 'Unknown' : kind;
    if (artifact.type === 'ruleset' && artifact.ruleSet && artifact.ruleSet.validating) return base + ' · validating';
    return base;
}

/** Short kind for list rows: "XML · script", "Validator · r:email", "@Rule class", "Java builder". */
export function kindShort(artifact) {
    const label = kindLabel(artifact);
    return label.replace(/ · (lambda|validating)$/, '');
}

export function isCompiled(expression) {
    return !!expression && expression.kind === 'compiled';
}

export function isScript(expression) {
    return !!expression && expression.kind === 'script';
}

/** Every expression an artifact carries, with the slot it sits in. */
export function expressionsOf(artifact) {
    const out = [];
    const add = (slot, label, expression) => { if (expression) out.push({slot, label, expression}); };
    if (artifact.rule) {
        add('preCondition', 'Runs only if', artifact.rule.preCondition);
        add('given', 'Condition', artifact.rule.given);
        (artifact.rule.then || []).forEach((e, i) => add('then', artifact.rule.then.length > 1 ? 'Action ' + (i + 1) : 'Action', e));
        add('otherwise', 'Otherwise', artifact.rule.otherwise);
    }
    if (artifact.validation && artifact.validation.valueSource && artifact.validation.valueSource.expression) {
        add('value', 'Value', artifact.validation.valueSource.expression);
    }
    if (artifact.ruleSet) {
        const s = artifact.ruleSet;
        add('preCondition', 'Runs only if', s.preCondition);
        add('initializer', 'Initializer', s.initializer);
        add('stopCondition', 'Stops when', s.stopCondition);
        add('finalizer', 'Finalizer', s.finalizer);
        add('resultExtractor', 'Result', s.resultExtractor);
        add('errorHandler', 'On error', s.errorHandler);
    }
    if (artifact.ruleFlow) {
        const f = artifact.ruleFlow;
        walkCommands(f.commands, (command, path, number) => {
            const label = 'Step ' + number;
            add('command', label + ' · ' + command.type, command.expression || command.condition || command.source);
            if (command.bind && command.bind.names) {
                for (const n of command.bind.names) if (n.expression) add('command', label + ' · bind', n.expression);
            }
        });
        if (f.globalHandler) walkCommands(f.globalHandler.body, (c, path, number) => add('handler', 'Error handler', c.expression || c.condition || c.source));
        add('finalizer', 'Finalizer', f.finalizer);
        add('returning', 'Returns', f.returning);
    }
    return out;
}

export function hasScript(artifact) {
    return expressionsOf(artifact).some(e => isScript(e.expression));
}

export function hasCompiled(artifact) {
    return expressionsOf(artifact).some(e => isCompiled(e.expression));
}

/**
 * Walks a flow's commands depth first, calling `visit(command, path, number, parent)` with the
 * descriptor path ("commands[1].body[0]") and the outline number ("2.1").
 */
export function walkCommands(commands, visit, prefix = 'commands', numberPrefix = '', parent = null, offset = 0) {
    (commands || []).forEach((command, i) => {
        const path = prefix + '[' + i + ']';
        const number = numberPrefix + (offset + i + 1);
        visit(command, path, number, parent);
        let k = 0;
        for (const {slot, commands: list, branch} of commandChildren(command)) {
            walkCommands(list, visit, path + '.' + slot, number + '.', {command, path, branch}, k);
            k += list.length;
        }
    });
}

/** The child command lists of a command, in display order, with the branch label shown before each. */
export function commandChildren(command) {
    const out = [];
    if (command.then && command.then.length) out.push({slot: 'then', commands: command.then, branch: 'then'});
    if (command.otherwise && command.otherwise.length) out.push({slot: 'otherwise', commands: command.otherwise, branch: 'otherwise'});
    if (command.body && command.body.length) out.push({slot: 'body', commands: command.body, branch: null});
    if (command.handler && command.handler.body && command.handler.body.length) out.push({slot: 'handler.body', commands: command.handler.body, branch: 'on ' + shortType(command.handler.exceptionType || 'Exception')});
    if (command.thenRun && command.thenRun.body && command.thenRun.body.length) out.push({slot: 'thenRun.body', commands: command.thenRun.body, branch: 'then'});
    return out;
}

/** "com.acme.order.model.Order" → "Order"; "java.time.Clock$SystemClock" → "Clock.SystemClock". */
export function shortType(fqcn) {
    if (!fqcn) return '';
    const generic = fqcn.indexOf('<');
    const head = generic >= 0 ? fqcn.slice(0, generic) : fqcn;
    const tail = generic >= 0 ? fqcn.slice(generic).replace(/[\w.]+\./g, '') : '';
    const simple = head.slice(head.lastIndexOf('.') + 1).replace(/\$/g, '.');
    return simple + tail;
}

/** "classpath:rules/order/validation.xml" line 34 → "rules/order/validation.xml:34". */
export function sourceText(source) {
    if (!source) return null;
    let text;
    if (source.resource) text = source.resource.replace(/^classpath\*?:/, '').replace(/^file:/, '');
    else if (source.className) text = source.className;
    else return null;
    if (source.line != null) text += ':' + source.line;
    return text;
}

/** The language of the first script expression, as a name and its descriptor code. */
export function scriptLanguage(artifact) {
    const first = expressionsOf(artifact).find(e => isScript(e.expression));
    if (!first) return null;
    const code = first.expression.language || 'el';
    const names = {el: 'SpEL', spel: 'SpEL', js: 'JavaScript', javascript: 'JavaScript', java: 'Java (Janino)'};
    return {name: names[code] || code, code};
}

/** The plain-English text of an expression, for list rows and search. */
export function plainText(expression) {
    if (!expression) return '';
    if (expression.plain && expression.plain.tokens && expression.plain.tokens.length) {
        return expression.plain.tokens.map(t => t.text).join(' ').replace(/\s+([,.)])/g, '$1');
    }
    if (expression.kind === 'compiled') return expression.signature || 'compiled code';
    if (expression.kind === 'composite') return (expression.operands || []).map(plainText).join(' ' + (expression.operator || 'and') + ' ');
    return expression.text || '';
}

const WORDS = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten', 'eleven', 'twelve'];

export function numberWord(n, capitalise = false) {
    const word = n >= 0 && n < WORDS.length ? WORDS[n] : String(n);
    return capitalise ? word.charAt(0).toUpperCase() + word.slice(1) : word;
}

export function plural(n, one, many = one + 's') {
    return n + ' ' + (n === 1 ? one : many);
}

/** Joins words the way a sentence does: "a", "a and b", "a, b and c". */
export function joinWords(items, conjunction = 'and') {
    const list = items.filter(Boolean);
    if (list.length <= 1) return list.join('');
    if (list.length === 2) return list[0] + ' ' + conjunction + ' ' + list[1];
    return list.slice(0, -1).join(', ') + ' ' + conjunction + ' ' + list[list.length - 1];
}

/** ISO-8601 duration → "5 s", "2 min", "1.5 s". */
export function durationText(iso) {
    if (!iso) return null;
    const m = /^PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?$/i.exec(iso);
    if (!m) return iso;
    const parts = [];
    if (m[1]) parts.push(Number(m[1]) + ' h');
    if (m[2]) parts.push(Number(m[2]) + ' min');
    if (m[3]) parts.push(Number(m[3]) + ' s');
    return parts.join(' ') || iso;
}

/** Splits a camelCase or snake_case identifier into lowercase words. */
export function identifierWords(name) {
    return String(name || '')
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
        .replace(/[_\-./]+/g, ' ')
        .toLowerCase()
        .split(/\s+/)
        .filter(Boolean);
}

/** Case-insensitive natural order: "commands[2]" before "commands[11]". */
export function compareNatural(a, b) {
    return String(a).localeCompare(String(b), 'en', {numeric: true, sensitivity: 'base'});
}

export const SEVERITY_ORDER = {error: 0, warning: 1, info: 2};

export function severityLabel(severity) {
    return severity === 'error' ? 'Error' : severity === 'warning' ? 'Warning' : 'Info';
}

/** The target of a run-like command, as text: the artifact name, or the looked-up name/class. */
export function targetName(target, index) {
    if (!target) return '';
    if (target.kind === 'instance' && target.id) {
        const a = index && index.byId.get(target.id);
        return a ? a.name : target.id;
    }
    if (target.kind === 'by-class') return shortType(target.className);
    return target.name || target.id || '';
}
