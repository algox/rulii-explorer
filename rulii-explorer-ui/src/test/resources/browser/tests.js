/**
 * In-browser unit tests for the explorer's modules (SOLUTION §10). A minimal runner: each test
 * is a name and an async function; results land in window.__rxResults for the JUnit driver.
 * The descriptor is the demo application's golden file, served at /actuator/rulii.
 */
import {render} from 'lit';
import {parseRoute, routes} from '/rulii-explorer/app/routing/router.js';
import {loadDescriptor} from '/rulii-explorer/app/descriptor/loader.js';
import {buildIndex, problemPath} from '/rulii-explorer/app/descriptor/indexes.js';
import {buildSearch} from '/rulii-explorer/app/search/search.js';
import {artifactSummary, bindingSummary, commandParts, problemsHeadline, kindCaption} from '/rulii-explorer/app/descriptor/summaries.js';
import {durationText, kindLabel, plainText, shortType, sourceText, identifierWords, compareNatural} from '/rulii-explorer/app/descriptor/format.js';
import {plainTokens, rawCode, highlightCode, mark} from '/rulii-explorer/app/components/expression.js';

const tests = [];
const test = (name, fn) => tests.push([name, fn]);
const eq = (actual, expected, what = '') => { if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error((what ? what + ': ' : '') + 'expected ' + JSON.stringify(expected) + ', got ' + JSON.stringify(actual)); };
const ok = (cond, what) => { if (!cond) throw new Error(what || 'expected truthy'); };
const has = (text, sub) => { if (!String(text).includes(sub)) throw new Error('expected "' + text + '" to include "' + sub + '"'); };
const scratch = document.getElementById('scratch');
const rendered = (template) => { render(template, scratch); return scratch; };

let descriptor, index, search;

test('loader reads the golden descriptor', async () => {
    const result = await loadDescriptor('/actuator/rulii');
    eq(result.status, 'ready');
    descriptor = result.descriptor;
    index = buildIndex(descriptor);
    search = buildSearch(descriptor, index);
    eq(descriptor.artifacts.length, 19);
});

test('loader maps failures to states', async () => {
    eq((await loadDescriptor('/case/notexposed/descriptor')).status, 'not-exposed');
    eq((await loadDescriptor('/case/unauthorized/descriptor')).status, 'unauthorized');
    eq((await loadDescriptor('/case/forbidden/descriptor')).status, 'forbidden');
    const failed = await loadDescriptor('/case/failed/descriptor');
    eq(failed.status, 'failed');
    has(failed.error.detail, 'Could not build the descriptor');
    eq((await loadDescriptor('/case/empty/descriptor')).status, 'empty');
});

test('routes parse and build', () => {
    eq(parseRoute(''), {name: 'overview', query: {}});
    eq(parseRoute('#/'), {name: 'overview', query: {}});
    eq(parseRoute('#/rule/MinTotalRule'), {name: 'artifact', type: 'rule', id: 'MinTotalRule', query: {}});
    eq(parseRoute(routes.package('rules/order')), {name: 'package', id: 'rules/order', query: {}});
    eq(parseRoute(routes.artifact('ruleflow', 'nightlyRepriceFlow', {step: 'commands[1].body[1]'})).query.step, 'commands[1].body[1]');
    eq(parseRoute('#/problems?severity=error'), {name: 'problems', query: {severity: 'error'}});
    eq(parseRoute('#/nope').name, 'missing');
    eq(routes.binding('order'), '#/binding/order');
});

test('index counts and groups', () => {
    eq(index.counts, {rule: 14, ruleset: 3, ruleflow: 2, packages: 4});
    eq(index.packages.map(p => p.pkg.id), ['com.acme.order.config', 'com.acme.order.rules', 'rules/order', 'rules/pricing']);
    eq(index.byPackage.get('rules/order').counts, {rule: 8, ruleset: 2, ruleflow: 1, total: 11});
    eq(index.byType.ruleflow.map(a => a.id), ['nightlyRepriceFlow', 'orderProcessingFlow']);
});

test('index back-links, bindings and problems', () => {
    const used = index.usedBy.get('MinTotalRule');
    eq(used.length, 1);
    eq(used[0].from, 'orderValidationRules');
    eq(used[0].path, 'members[3]');
    eq(index.uses.get('orderProcessingFlow').map(r => r.to), ['orderValidationRules', 'fraudScoreRule', 'pricingRules', 'approvalRules']);
    ok(index.bindingPaths.get('order').reads.has('order.total'), 'order.total read');
    ok(index.bindingPaths.get('order').writes.has('order.discount'), 'order.discount written');
    eq(index.problemCounts, {error: 1, warning: 1, info: 3, total: 5});
    eq(index.worstByArtifact.get('nightlyRepriceFlow'), 'error');
    eq(index.worstByArtifact.get('rangeCheckRule'), 'info');
    eq(index.undescribed.size, 0);
});

test('index numbers flow steps by path', () => {
    const steps = index.commandsByPath.get('orderProcessingFlow');
    eq(steps.get('commands[6].then[0]').number, '7.1');
    eq(steps.get('commands[6].otherwise[0]').number, '7.2');
    eq(steps.get('commands[3].handler.body[0]').number, '4.1');
    eq(steps.get('commands[3].handler.body[0]').parent.branch, 'on FraudServiceException');
    const chain = problemPath(index, descriptor.problems.find(p => p.code === 'UNRESOLVED_TARGET'));
    eq(chain.map(e => e.number), ['2', '2.2']);
});

test('search ranks names first and highlights the match', () => {
    const groups = search.query('total');
    eq(groups[0].type, 'rule');
    eq(groups[0].hits[0].name, 'MinTotalRule');
    eq(groups[0].hits[0].nameMatches, [[3, 8]]);
    ok(groups[0].hits.some(h => h.name === 'CreditLimitRule'), 'condition text matches');
    ok(groups.some(g => g.type === 'binding' && g.hits.some(h => h.name === 'order')), 'binding paths match');
    const flow = groups.find(g => g.type === 'ruleflow');
    ok(flow && flow.hits[0].snippet && flow.hits[0].snippet.label.startsWith('Step 7'), 'flow snippet names the step');
});

test('search finds error codes, camel-case prefixes and respects the type filter', () => {
    eq(search.query('customer.email.invalid')[0].hits[0].name, 'EmailFormatRule');
    eq(search.query('ordervalidation')[0].hits[0].name, 'orderValidationRules');
    eq(search.query('order', {type: 'ruleflow'}).map(g => g.type), ['ruleflow']);
    eq(search.query('zzzz'), []);
    eq(search.query('MinTotalRul')[0].hits[0].name, 'MinTotalRule', 'prefix');
});

test('summaries say what the data supports', () => {
    const a = (id) => index.byId.get(id);
    has(artifactSummary(a('MinTotalRule'), index), 'Passes when order total is at least order.minTotal (default 100).');
    eq(artifactSummary(a('EmailFormatRule'), index), 'Fails with customer.email.invalid when customer email is not a valid email address.');
    has(artifactSummary(a('fraudScoreRule'), index), 'Takes the order and the customer. Its logic is compiled Java code');
    eq(artifactSummary(a('orderValidationRules'), index), 'Checks 8 rules in order and stops when number of rule violations is at least 3. Runs only if order is present.');
    has(artifactSummary(a('orderProcessingFlow'), index), 'running 3 rule sets and 1 rule, and returns approved.');
    eq(bindingSummary(index.bindings.get('order')), 'Read by 12 artifacts and written by 2. Compiled code in 2 more may also change it.');
    eq(problemsHeadline(index.problemCounts), 'Two steps are likely to fail. Three things are suggestions.');
    eq(kindCaption('rule', index.kindCounts), '8 XML · 2 @Rule classes · 2 validators · 2 Java builders');
});

test('kind labels', () => {
    const a = (id) => index.byId.get(id);
    eq(kindLabel(a('MinTotalRule')), 'XML · script');
    eq(kindLabel(a('EmailFormatRule')), 'Validator · r:email');
    eq(kindLabel(a('ConsistentDatesRule')), '@Rule class');
    eq(kindLabel(a('fraudScoreRule')), 'Java builder · lambda');
    eq(kindLabel(a('rangeCheckRule')), 'Java builder · script');
    eq(kindLabel(a('orderValidationRules')), 'XML · validating');
    eq(kindLabel(a('approvalRules')), 'XML');
});

test('format helpers', () => {
    eq(shortType('java.time.Clock$SystemClock'), 'Clock.SystemClock');
    eq(shortType('java.util.List<com.acme.Item>'), 'List<Item>');
    eq(sourceText({type: 'xml', resource: 'classpath:rules/order/validation.xml', line: 34}), 'rules/order/validation.xml:34');
    eq(sourceText({type: 'java', className: 'com.acme.X'}), 'com.acme.X');
    eq(durationText('PT5S'), '5 s');
    eq(durationText('PT1M30S'), '1 min 30 s');
    eq(identifierWords('MinTotalRule'), ['min', 'total', 'rule']);
    eq(['commands[11]', 'commands[2]'].sort(compareNatural), ['commands[2]', 'commands[11]']);
    eq(plainText(index.byId.get('CreditLimitRule').rule.given), 'customer open balance plus order total is at most customer credit limit');
});

test('plain rendering: chips for bindings and placeholders', () => {
    const el = rendered(plainTokens(index.byId.get('MinTotalRule').rule.given));
    has(el.textContent, 'order total is at least');
    eq(el.querySelectorAll('.rx-chip').length, 1);
    eq(el.querySelector('.rx-ph-key').textContent, 'order.minTotal');
    eq(el.querySelector('.rx-ph-default').textContent, 'default 100');
});

test('raw rendering: syntax colours', () => {
    const el = rendered(rawCode(index.byId.get('MinTotalRule').rule.given));
    eq(el.querySelector('.rx-c-var').textContent, '#ctx');
    eq(el.querySelector('.rx-c-ph').textContent, '${order.minTotal:100}');
    eq(el.querySelector('.rx-c-kw').textContent, '>=');
    const code = rendered(highlightCode("#ctx.customer.tier == 'VIP' && #ctx.reviewQueue.add(#ctx.order) || T(java.lang.Math).max(1, 2.5)"));
    eq(code.querySelector('.rx-c-str').textContent, "'VIP'");
    ok([...code.querySelectorAll('.rx-c-fn')].some(e => e.textContent === 'add'), 'method call');
    ok([...code.querySelectorAll('.rx-c-num')].some(e => e.textContent === '2.5'), 'number');
    const marked = rendered(mark('MinTotalRule', ['total']));
    eq(marked.querySelector('mark').textContent, 'Total');
});

test('command sentences', () => {
    const flow = index.byId.get('orderProcessingFlow');
    const cmd = (i) => flow.ruleFlow.commands[i];
    const text = (c) => commandParts(c, index, flow.id).parts.map(p => p.text || (p.target && p.target.id) || (p.expression && 'expr') || '').join(' ').replace(/\s+/g, ' ').trim();
    has(text(cmd(0)), 'Make clock , inventory and reviewQueue available from the bindings orderServices');
    eq(text(cmd(1)), 'Run orderValidationRules and keep the result as validation');
    eq(commandParts(cmd(1), index, flow.id).caption, 'rule set · direct');
    eq(text(cmd(3)), 'Start fraudScoreRule in the background as fraudCheck');
    eq(text(cmd(5)), 'Wait for fraudCheck from step 4');
    eq(commandParts(cmd(5), index, flow.id).caption, 'joins here · timeout 5 s');
    eq(commandParts(cmd(6), index, flow.id).caption, '2 branches');
    const nightly = index.byId.get('nightlyRepriceFlow');
    eq(commandParts(nightly.ruleFlow.commands[1].body[1], index, nightly.id).caption, 'by name · unresolved');
});

async function run() {
    const results = {passed: 0, failed: 0, failures: []};
    const list = document.getElementById('results');
    for (const [name, fn] of tests) {
        const li = document.createElement('li');
        try {
            await fn();
            results.passed++;
            li.className = 'pass';
            li.textContent = '✓ ' + name;
        } catch (e) {
            results.failed++;
            results.failures.push(name + ': ' + (e && e.message || e));
            li.className = 'fail';
            li.textContent = '✗ ' + name + ': ' + (e && e.message || e);
            console.warn(e);
        }
        list.appendChild(li);
    }
    document.getElementById('summary').textContent = results.passed + ' passed, ' + results.failed + ' failed';
    window.__rxResults = results;
    window.__rxDone = true;
}

run();
