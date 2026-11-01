import {expressionsOf, isCompiled, isScript, joinWords, numberWord, plainText, plural, resultOf, shortType, targetName, walkCommands} from './format.js';

/**
 * The one-sentence summaries shown in Newsreader italic on every detail page, and the problem
 * explanations. Everything here is derived from the descriptor; nothing is guessed. When the
 * data cannot support a sentence, the sentence says so.
 */

/** What a predefined validator checks, as the end of "passes when the value is …". */
const VALIDATOR_PHRASES = {
    email: 'a valid email address',
    url: 'a valid URL',
    pattern: 'a match for the required pattern',
    notNull: 'present',
    'null': 'absent',
    notEmpty: 'not empty',
    notBlank: 'not blank',
    empty: 'empty',
    min: 'at least the minimum',
    max: 'at most the maximum',
    range: 'within the allowed range',
    between: 'within the allowed range',
    size: 'of the allowed size',
    length: 'of the allowed length',
    digits: 'made of digits',
    numeric: 'numeric',
    alpha: 'made of letters',
    alphaNumeric: 'made of letters and digits',
    uppercase: 'all upper case',
    lowercase: 'all lower case',
    past: 'in the past',
    future: 'in the future',
    pastOrPresent: 'in the past or now',
    futureOrPresent: 'now or in the future',
    assertTrue: 'true',
    assertFalse: 'false',
    inList: 'one of the allowed values',
    notIn: 'none of the excluded values',
    startsWith: 'starting with the required prefix',
    endsWith: 'ending with the required suffix',
    contains: 'containing the required text',
    decimalMin: 'at least the minimum',
    decimalMax: 'at most the maximum',
    positive: 'positive',
    negative: 'negative',
    positiveOrZero: 'positive or zero',
    negativeOrZero: 'negative or zero'
};

export function validatorPhrase(validator) {
    return VALIDATOR_PHRASES[validator] || ('accepted by the ' + validator + ' validator');
}

/** The value a validator checks, as text: "customer.email" or the binding name. */
export function validatedValue(artifact) {
    const vs = artifact.validation && artifact.validation.valueSource;
    if (!vs) return 'the value';
    if (vs.kind === 'binding') return vs.name || 'the value';
    return vs.expression ? plainText(vs.expression) : 'the value';
}

/** The italic summary of an artifact. */
export function artifactSummary(artifact, index) {
    switch (artifact.type) {
        case 'rule': return ruleSummary(artifact);
        case 'ruleset': return ruleSetSummary(artifact);
        case 'ruleflow': return flowSummary(artifact, index);
        default: return '';
    }
}

function ruleSummary(a) {
    if (a.validation) {
        const v = a.validation;
        return 'Fails with ' + (v.errorCode || 'a violation') + ' when ' + validatedValue(a) + ' is not ' + validatorPhrase(v.validator) + '.';
    }
    const rule = a.rule || {};
    if (isCompiled(rule.given) || (!rule.given && rule.then.some(isCompiled))) {
        const names = a.parameters.map(p => 'the ' + p.name);
        const takes = names.length ? 'Takes ' + joinWords(names.slice(0, 3)) + (names.length > 3 ? ' and ' + (names.length - 3) + ' more' : '') + '.' : 'Takes no declared parameters.';
        return takes + ' Its logic is compiled Java code, so what it decides can’t be shown here, only what it takes in.';
    }
    const parts = [];
    if (rule.given) parts.push('Passes when ' + plainText(rule.given));
    else parts.push('Always passes');
    const actions = rule.then.filter(isScript).map(plainText).filter(Boolean);
    if (actions.length) parts.push('then ' + joinWords(actions.slice(0, 2)) + (actions.length > 2 ? ' and ' + (actions.length - 2) + ' more' : ''));
    else if (rule.then.some(isCompiled)) parts.push('then runs compiled code');
    let sentence = parts.join(', ') + '.';
    if (rule.otherwise) sentence += ' Otherwise ' + plainText(rule.otherwise) + '.';
    return sentence.charAt(0).toUpperCase() + sentence.slice(1);
}

function ruleSetSummary(a) {
    const s = a.ruleSet || {members: []};
    const n = s.members.length;
    let text = (s.validating ? 'Checks ' : 'Runs ') + plural(n, 'rule') + ' in order';
    if (s.stopCondition) text += ' and stops when ' + plainText(s.stopCondition);
    text += '.';
    if (s.preCondition) text += ' Runs only if ' + plainText(s.preCondition) + '.';
    return text;
}

function flowSummary(a, index) {
    const f = a.ruleFlow || {commands: []};
    let steps = 0;
    const runs = {ruleset: 0, rule: 0, unknown: 0};
    walkCommands(f.commands, (c) => {
        steps++;
        if ((c.type === 'run' || c.type === 'async-run' || c.type === 'apply') && c.target) {
            const target = c.target.kind === 'instance' && index ? index.byId.get(c.target.id) : null;
            if (target) runs[target.type] = (runs[target.type] || 0) + 1;
            else runs.unknown++;
        }
    });
    const what = [];
    if (runs.ruleset) what.push(plural(runs.ruleset, 'rule set'));
    if (runs.rule) what.push(plural(runs.rule, 'rule'));
    if (runs.unknown) what.push(plural(runs.unknown, 'step looked up by name', 'steps looked up by name'));
    let text = plural(steps, 'step') + (what.length ? ', running ' + joinWords(what) : '');
    if (f.returning) text += ', and returns ' + plainText(f.returning);
    return text.charAt(0).toUpperCase() + text.slice(1) + '.';
}

/** "Read by 9 artifacts and written by 3. Compiled code in 3 more may also change it." */
export function bindingSummary(usage) {
    const readers = usage.readBy ? usage.readBy.length : 0;
    const writers = usage.writtenBy ? usage.writtenBy.length : 0;
    const unknown = (usage.unknownWriters || []).filter(w => !(usage.writtenBy || []).includes(w)).length;
    let text = 'Read by ' + plural(readers, 'artifact') + ' and written by ' + writers + '.';
    if (unknown) text += ' Compiled code in ' + unknown + ' more may also change it.';
    return text;
}

/** The overview subtitle. */
export function overviewSubtitle(index) {
    const c = index.counts;
    return plural(c.rule, 'rule') + ', ' + plural(c.ruleset, 'rule set') + ' and ' + plural(c.ruleflow, 'rule flow')
        + ' across ' + plural(c.packages, 'package') + ', read straight from the running application.';
}

/** The problems page headline. */
export function problemsHeadline(counts) {
    if (!counts.total) return 'Nothing needs attention.';
    const failing = counts.error + counts.warning;
    const parts = [];
    if (failing) parts.push(numberWord(failing, true) + (failing === 1 ? ' step is' : ' steps are') + ' likely to fail.');
    if (counts.info) parts.push(numberWord(counts.info, true) + (counts.info === 1 ? ' thing is a suggestion.' : ' things are suggestions.'));
    return parts.join(' ');
}

/** The rules stat caption: "9 XML · 2 validators · 2 @Rule classes · 1 Java builder". */
export function kindCaption(type, kindCounts) {
    const counts = kindCounts.get(type) || {};
    const names = {
        'xml-script': ['XML', 'XML'], xml: ['XML', 'XML'], 'predefined-validator': ['validator', 'validators'],
        'rule-class': ['@Rule class', '@Rule classes'], 'java-builder': ['Java builder', 'Java builders'], unknown: ['unknown', 'unknown']
    };
    const entries = Object.entries(counts).sort((a, b) => b[1] - a[1]);
    if (entries.length === 1 && (entries[0][0] === 'xml' || entries[0][0] === 'xml-script')) return 'All defined in XML';
    if (entries.length === 1 && entries[0][0] === 'java-builder') return 'All built in Java';
    return entries.map(([kind, n]) => n + ' ' + (names[kind] || [kind, kind])[n === 1 ? 0 : 1]).join(' · ');
}

/** Why a problem matters and how to fix it, per code (FR-29 wording). */
export function problemExplanation(problem, index) {
    const a = problem.artifact && index.byId.get(problem.artifact);
    const bean = !a && problem.artifact && index.beans ? index.beans.byId.get(problem.artifact) : null;
    const name = a ? a.name : bean ? bean.name : problem.artifact;
    const quoted = /'([^']+)'/.exec(problem.message || '');
    const looked = quoted ? quoted[1] : null;
    if (bean) return beanProblemExplanation(problem, bean, looked);
    switch (problem.code) {
        case 'UNRESOLVED_TARGET':
            return {
                why: 'When the flow reaches this step it stops with an error, because the registry has nothing called ‘' + (looked || 'that') + '’ to run.',
                fix: 'Register a rule under the name ‘' + (looked || '…') + '’, or point the step at a rule that exists.'
            };
        case 'NAME_MISMATCH_LOOKUP': {
            const registry = /registry name of that artifact is '([^']+)'/.exec(problem.message || '');
            return {
                why: 'Lookups by name use registry (bean) names. ‘' + (looked || '…') + '’ is the rule’s own name, so the lookup won’t find it.',
                fix: registry ? 'Change the step to name="' + registry[1] + '".' : 'Use the registry name of the rule in the step.'
            };
        }
        case 'DUPLICATE_NAME':
            return {
                why: 'Two artifacts share the name ‘' + (looked || name) + '’, so lookups by name are ambiguous.',
                fix: 'Give each artifact a distinct name.'
            };
        case 'UNUSED_RULE':
            return {
                why: 'No rule set or flow references ' + name + ', so it only runs if application code calls it directly.',
                fix: 'Add it to a rule set or flow, or remove it if it is no longer needed.'
            };
        case 'MISSING_DESCRIPTION':
            return {
                why: 'Readers see only its name.',
                fix: a && a.kind === 'rule-class' ? 'Add @Description to the class to say what it checks.' : a && a.source && a.source.type === 'xml' ? 'Add a description attribute in the XML.' : 'Add a description in the builder.'
            };
        case 'UNCATEGORISED':
            return {
                why: 'This application files its rules by category, and ' + name + ' has none, so it sits under Uncategorised.',
                fix: a && a.kind === 'rule-class' ? 'Add @Category to the class, for example @Category("Pricing").'
                    : a && a.source && a.source.type === 'xml' ? 'Add a category attribute in the XML, or <r:defaults category="…"/> for the whole file.'
                    : 'Add .category("…") in the builder.'
            };
        case 'UNDESCRIBABLE':
            return {
                why: 'Reading this artifact’s definition failed, so only its name is shown. The rest of the application is unaffected.',
                fix: 'The message above comes from the application; fix the definition or report it to the rulii project.'
            };
        default:
            return {why: problem.message, fix: null};
    }
}

/** A short hint for the info rows on the problems page. */
export function problemHint(problem, index) {
    const e = problemExplanation(problem, index);
    return [e.why, e.fix].filter(Boolean).join(' ');
}

/** The sentence for a flow command in the outline, split into typed parts for rendering. */
export function commandParts(command, index, flowId) {
    const parts = [];
    const text = (t) => parts.push({t: 'text', text: t});
    const chip = (t) => parts.push({t: 'chip', text: t});
    const link = (target) => parts.push({t: 'target', target});
    const expr = (e) => parts.push({t: 'expr', expression: e});
    const strong = (t) => parts.push({t: 'strong', text: t});
    switch (command.type) {
        case 'bind': {
            const b = command.bind || {};
            const names = (b.names || []).map(n => n.name);
            if (b.kind === 'literal' || b.kind === 'expression') {
                text(names.length === 1 ? 'Set' : 'Set');
                names.forEach((n, i) => { if (i) text(i === names.length - 1 ? 'and' : ','); chip(n); });
                const first = (b.names || []).find(n => n.expression);
                if (first && first.expression) { text('to'); expr(first.expression); }
            } else {
                text('Make');
                names.slice(0, 4).forEach((n, i) => { if (i) text(i === Math.min(names.length, 4) - 1 ? 'and' : ','); chip(n); });
                if (names.length > 4) text('and ' + (names.length - 4) + ' more');
                text('available');
                if (b.label) {
                    const from = {map: 'from the map', bean: 'from the bean', bindings: 'from the bindings', loader: 'from', declarations: 'as declared in'}[b.kind] || 'from';
                    text(from); parts.push({t: 'mono', text: b.label});
                }
            }
            return {parts, caption: b.kind || 'bind'};
        }
        case 'run':
        case 'apply': {
            text(command.type === 'apply' ? 'Apply' : 'Run');
            link(command.target);
            if (command.as) { text('and keep the result as'); chip(command.as); }
            const bound = command.as ? boundResultType(command.target, index) : null;
            return {parts, caption: targetCaption(command.target, index) + (bound ? ' · ' + command.as + ': ' + bound : '')};
        }
        case 'async-run': {
            text('Start');
            link(command.target);
            text('in the background');
            if (command.as) { text('as'); chip(command.as); }
            return {parts, caption: 'async lane' + (command.mode ? ' · ' + command.mode : '')};
        }
        case 'await': {
            text(command.awaitKind === 'all' ? 'Wait for all of' : command.awaitKind === 'any' ? 'Wait for any of' : 'Wait for');
            (command.names || []).forEach((n, i) => { if (i) text(i === command.names.length - 1 ? 'and' : ','); chip(n); });
            const from = asyncStepFor(command, index, flowId);
            if (from) text('from step ' + from);
            return {parts, caption: 'joins here' + (command.timeout ? ' · timeout ' + durationWords(command.timeout) : '')};
        }
        case 'execute':
            if (command.expression) expr(command.expression); else text('Execute');
            return {parts, caption: 'action'};
        case 'when': {
            text('If');
            if (command.condition) expr(command.condition);
            const branches = (command.then && command.then.length ? 1 : 0) + (command.otherwise && command.otherwise.length ? 1 : 0);
            return {parts, caption: plural(branches, 'branch', 'branches')};
        }
        case 'for-each': {
            text('For each');
            if (command.item) chip(command.item);
            text('in');
            if (command.source) expr(command.source);
            if (command.stop) { text(', stopping when'); expr(command.stop); }
            return {parts, caption: 'loop'};
        }
        case 'scope':
            text('In a new scope' + (command.name ? ' named' : ''));
            if (command.name) chip(command.name);
            return {parts, caption: 'scope'};
        case 'exit':
            text('Stop here');
            if (command.expression) { text('and return'); expr(command.expression); }
            return {parts, caption: 'ends the flow'};
        case 'custom':
            text('Custom step');
            if (command.className) parts.push({t: 'mono', text: shortType(command.className)});
            return {parts, caption: 'custom'};
        default:
            text(command.type);
            return {parts, caption: ''};
    }
}

/** The short result type of the artifact a step runs, when the target resolves and its type is known; else null. */
export function boundResultType(target, index) {
    const a = target && target.kind === 'instance' && target.id ? index.byId.get(target.id) : null;
    const r = a ? resultOf(a) : null;
    return r && r.type ? r.type : null;
}

function targetCaption(target, index) {
    if (!target) return '';
    const a = target.kind === 'instance' && target.id ? index.byId.get(target.id) : null;
    const type = a ? (a.type === 'ruleset' ? 'rule set' : a.type === 'ruleflow' ? 'rule flow' : 'rule') : (target.kind === 'by-class' ? 'class' : 'name');
    const how = target.resolution === 'unresolved' ? 'unresolved' : target.resolution === 'direct' ? 'direct' : target.resolution === 'by-name' ? 'by name' : target.resolution === 'by-class' ? 'by class' : '';
    return a ? type + (how ? ' · ' + how : '') : (target.kind === 'by-class' ? 'by class' : 'by name') + (how === 'unresolved' ? ' · unresolved' : '');
}

function asyncStepFor(awaitCommand, index, flowId) {
    const map = flowId && index.commandsByPath.get(flowId);
    if (!map) return null;
    const names = awaitCommand.names || [];
    for (const {command, number} of map.values()) {
        if (command.type === 'async-run' && command.as && names.includes(command.as)) return number;
    }
    return null;
}

function durationWords(iso) {
    const m = /^PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?$/i.exec(iso || '');
    if (!m) return iso;
    const parts = [];
    if (m[1]) parts.push(Number(m[1]) + ' h');
    if (m[2]) parts.push(Number(m[2]) + ' min');
    if (m[3]) parts.push(Number(m[3]) + ' s');
    return parts.join(' ') || iso;
}

// ── Beans (descriptor 1.1) ─────────────────────────────────────────────────

/** Why a bean's problem matters and how to fix it. `looked` is the quoted name in the message, if any. */
function beanProblemExplanation(problem, bean, looked) {
    switch (problem.code) {
        case 'UNRESOLVED_TARGET':
            return {
                why: 'Validating a ' + bean.name + ' runs ‘' + (looked || 'that') + '’ by name, and the registry has nothing under it, so the validation fails with an error instead of a result.',
                fix: 'Register a rule under the name ‘' + (looked || '…') + '’, or change the @Validate to a rule that exists.'
            };
        case 'NAME_MISMATCH_LOOKUP': {
            const registry = /registry name of that artifact is '([^']+)'/.exec(problem.message || '');
            return {
                why: '@Validate names are looked up by registry (bean) name. ‘' + (looked || '…') + '’ is the rule’s own name, so the lookup won’t find it.',
                fix: registry ? 'Change the @Validate to "' + registry[1] + '".' : 'Use the registry name of the rule in the @Validate.'
            };
        }
        case 'BEAN_NOT_MATERIALIZED':
            return {
                why: 'rulii-bean-validation reads ' + bean.name + '’s declarations but cannot build its rules, so validating one fails. The bean is shown from its declarations alone.',
                fix: 'The message above comes from the library; fix the declaration it names.'
            };
        case 'UNDESCRIBABLE':
            return {
                why: problem.severity === 'warning'
                    ? bean.name + ' was configured as a bean to describe, but it declares nothing rulii-bean-validation reads, so there is nothing to show.'
                    : 'Reading this bean’s definition failed, so only its name is shown. The rest of the application is unaffected.',
                fix: problem.severity === 'warning' ? 'Remove it from rulii.explorer.beans.classes, or add the constraints it should carry.' : 'The message above comes from the library; fix the declaration or report it to the rulii project.'
            };
        default:
            return {why: problem.message, fix: null};
    }
}

/**
 * The italic summary of a bean: "8 properties carry 11 constraints, its items cascade into Item,
 * and ConsistentDatesRule runs on the whole order." The names are returned as parts so the page
 * can link them.
 *
 * @returns {Array<{text?: string, bean?: object, artifact?: object, missing?: string}>}
 */
export function beanSummary(bean, index) {
    const beans = index.beans;
    const props = bean.properties || [];
    const declarations = beanDeclarationsCount(bean);
    const parts = [];
    const constrained = props.filter(p => (p.constraints || []).length || (p.expressions || []).length || (p.validations || []).length || (p.containerElements || []).some(hasConstraints)).length;
    if (props.length) parts.push({text: plural(constrained || props.length, 'property', 'properties') + (declarations.members ? ' carry ' + plural(declarations.members, 'constraint') : ' carry no constraint')});
    else parts.push({text: 'no properties'});
    const cascades = (beans && beans.cascadesTo.get(bean.id)) || [];
    if (cascades.length) {
        const targets = [...new Set(cascades.map(r => r.to))].map(id => beans.byId.get(id)).filter(Boolean);
        parts.push({text: ', ' + (cascades.length === 1 ? 'its ' + memberOf(cascades[0].path) + ' cascade into ' : 'it cascades into ')});
        targets.forEach((t, i) => { if (i) parts.push({text: i === targets.length - 1 ? ' and ' : ', '}); parts.push({bean: t}); });
    }
    const whole = [];
    for (const v of bean.validations || []) for (const t of v.targets || []) whole.push(t.artifactId ? {artifact: index.byId.get(t.artifactId)} : {missing: t.name});
    if (whole.length) {
        parts.push({text: ', and '});
        whole.forEach((w, i) => { if (i) parts.push({text: i === whole.length - 1 ? ' and ' : ', '}); parts.push(w); });
        parts.push({text: (whole.length === 1 ? ' runs' : ' run') + ' on the whole ' + bean.name.toLowerCase()});
    } else if (bean.expressions && bean.expressions.length) {
        parts.push({text: ', and ' + plural(bean.expressions.length, 'expression') + ' check' + (bean.expressions.length === 1 ? 's' : '') + ' the whole ' + bean.name.toLowerCase()});
    }
    if (bean.executables && bean.executables.length) parts.push({text: '; ' + plural(bean.executables.length, 'method or constructor', 'methods and constructors') + ' validated too'});
    parts.push({text: '.'});
    if (parts.length && parts[0].text) parts[0].text = parts[0].text.charAt(0).toUpperCase() + parts[0].text.slice(1);
    return parts;
}

function memberOf(path) {
    const m = /^properties\.([^.[]+)/.exec(path || '');
    return m ? m[1] : 'member';
}

function hasConstraints(element) {
    return (element.constraints || []).length > 0 || (element.children || []).some(hasConstraints);
}

/** How many declarations a bean's members carry (constraints, @Expr, @Validate), and how many sit on the bean itself. */
function beanDeclarationsCount(bean) {
    const countElement = (el) => (el.constraints || []).length + (el.children || []).reduce((n, c) => n + countElement(c), 0);
    const count = (holder) => (holder.constraints || []).length + (holder.expressions || []).length + (holder.validations || []).length
        + (holder.containerElements || []).reduce((n, el) => n + countElement(el), 0);
    let members = 0;
    for (const p of bean.properties || []) members += count(p);
    for (const e of bean.executables || []) {
        for (const p of e.parameters || []) members += count(p);
        if (e.crossParameter) members += count(e.crossParameter);
        if (e.returnValue) members += count(e.returnValue);
    }
    return {members, own: count(bean)};
}

/** The Beans overview subtitle. */
export function beansOverviewSubtitle(beans) {
    const c = beans.counts;
    return plural(c.beans, 'bean') + ' carrying ' + plural(c.constraints, 'constraint') + ' in ' + plural(c.packages, 'package')
        + ', read through the application’s own validator. Rules the beans run live in the Rules section; nothing here is a registry artifact.';
}

/** The constraints stat caption: "15 Jakarta · 1 rulii · 2 @Expr · 1 @Validate". */
export function constraintKindsCaption(beans) {
    const k = beans.constraintKinds;
    const parts = [];
    if (k.jakarta) parts.push(k.jakarta + ' Jakarta');
    if (k.rulii) parts.push(k.rulii + ' rulii');
    if (k.other) parts.push(k.other + ' custom');
    if (k.expr) parts.push(k.expr + ' @Expr');
    if (k.validate) parts.push(k.validate + ' @Validate');
    return parts.length ? parts.join(' · ') : 'None declared';
}

/** The discovery stat caption: "2 scanned · 1 reached by cascade". */
export function discoveryCaption(beans) {
    const c = beans.counts;
    const parts = [];
    if (c.scan) parts.push(c.scan + ' scanned');
    if (c.configured) parts.push(c.configured + ' configured');
    if (c.validated) parts.push(c.validated + ' validated');
    if (c.cascade) parts.push(c.cascade + ' reached by cascade');
    return parts.join(' · ');
}

export {targetName};
