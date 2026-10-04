/**
 * In-browser unit tests for the explorer's modules (SOLUTION §10). A minimal runner: each test
 * is a name and an async function; results land in window.__rxResults for the JUnit driver.
 * The descriptor is the demo application's golden file, served at /actuator/rulii.
 *
 */
import {render} from 'lit';
import {parseRoute, routes} from '/rulii-explorer/app/routing/router.js';
import {loadDescriptor, fromText} from '/rulii-explorer/app/descriptor/loader.js';
import {resolveSource, absoluteUrl, sourceLabel} from '/rulii-explorer/app/descriptor/source.js';
import {buildIndex, problemPath, categoryParents} from '/rulii-explorer/app/descriptor/indexes.js';
import {buildSearch, parseQuery} from '/rulii-explorer/app/search/search.js';
import {artifactSummary, bindingSummary, commandParts, problemsHeadline, kindCaption} from '/rulii-explorer/app/descriptor/summaries.js';
import {durationText, kindLabel, kindShort, scriptLanguage, languageName, plainText, shortType, sourceText, identifierWords, compareNatural} from '/rulii-explorer/app/descriptor/format.js';
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
    eq(descriptor.artifacts.length, 30);
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

test('loader reads a file and says when it is not a descriptor', async () => {
    const golden = await (await fetch('/actuator/rulii')).text();
    const file = await loadDescriptor({kind: 'file', name: 'rules.json', text: golden});
    eq(file.status, 'ready');
    eq(file.descriptor.artifacts.length, 30);
    eq(fromText('not json', {file: true}).status, 'failed');
    eq(fromText('not json', {file: true}).error.message, 'The file is not a rule descriptor.');
    eq(fromText('{"hello": 1}', {httpStatus: 200, ok: true}).error.message, 'The response is not a rule descriptor.');
    eq(fromText('{"descriptorVersion": "2.0", "artifacts": []}').status, 'unsupported');
    eq(fromText('{"descriptorVersion": "1.3", "artifacts": []}').status, 'empty');
    eq(fromText('{"descriptorVersion": "1.0", "error": {"message": "boom"}}', {httpStatus: 500, ok: false}).error.detail, 'boom');
    const invalid = await loadDescriptor({kind: 'url', url: 'ftp://x', invalid: true});
    eq(invalid.status, 'unreachable');
    eq(invalid.error.crossOrigin, true);
});

test('the source comes from the address, then the session, then the page', () => {
    const app = 'http://127.0.0.1:1/actuator/rulii';
    const base = 'http://127.0.0.1:1/rulii';
    eq(resolveSource({search: '', allowed: true, stored: null, applicationUrl: app}), {kind: 'application', url: app});
    eq(resolveSource({search: '?descriptor=https%3A%2F%2Fstaging%3A8080%2Factuator%2Frulii', allowed: true, stored: null, applicationUrl: app, base}), {kind: 'url', url: 'https://staging:8080/actuator/rulii'});
    eq(resolveSource({search: '?descriptor=/files/rules.json', allowed: true, stored: null, applicationUrl: app, base}), {kind: 'url', url: 'http://127.0.0.1:1/files/rules.json'}, 'relative addresses resolve against the page');
    eq(resolveSource({search: '?descriptor=javascript:alert(1)', allowed: true, stored: null, applicationUrl: app, base}), {kind: 'url', url: 'javascript:alert(1)', invalid: true}, 'only http(s)');
    eq(resolveSource({search: '?descriptor=', allowed: true, stored: null, applicationUrl: app, base}), {kind: 'application', url: app}, 'an empty parameter is ignored');
    eq(resolveSource({search: '', allowed: true, stored: '{"name":"rules.json"}', applicationUrl: app}), {kind: 'file', name: 'rules.json'});
    eq(resolveSource({search: '?descriptor=https://a/b', allowed: true, stored: '{"name":"rules.json"}', applicationUrl: app, base}).kind, 'url', 'the address wins over the session');
    eq(resolveSource({search: '?descriptor=https://a/b', allowed: false, stored: '{"name":"rules.json"}', applicationUrl: app, base}), {kind: 'application', url: app}, 'locked pages ignore both');
    eq(absoluteUrl('ftp://x/y'), null);
    eq(absoluteUrl('https://a:8443/actuator/rulii'), 'https://a:8443/actuator/rulii');
    eq(sourceLabel({kind: 'application'}), 'Live');
    eq(sourceLabel({kind: 'url', url: 'https://staging:8080/actuator/rulii'}), 'staging:8080');
    eq(sourceLabel({kind: 'file', name: 'rules.json'}), 'rules.json');
});

test('routes parse and build', () => {
    eq(parseRoute(''), {name: 'overview', query: {}});
    eq(parseRoute('#/'), {name: 'overview', query: {}});
    eq(parseRoute('#/rule/MinTotalRule'), {name: 'artifact', type: 'rule', id: 'MinTotalRule', query: {}});
    eq(parseRoute(routes.package('rules/order')), {name: 'package', id: 'rules/order', query: {}});
    eq(parseRoute(routes.category('Pricing/Loyalty')), {name: 'category', id: 'Pricing/Loyalty', query: {}});
    eq(parseRoute(routes.artifact('ruleflow', 'nightlyRepriceFlow', {step: 'commands[1].body[1]'})).query.step, 'commands[1].body[1]');
    eq(parseRoute('#/problems?severity=error'), {name: 'problems', query: {severity: 'error'}});
    eq(parseRoute('#/nope').name, 'missing');
    eq(routes.binding('order'), '#/binding/order');
});

test('index counts and groups', () => {
    eq(index.counts, {rule: 22, ruleset: 5, ruleflow: 3, packages: 4});
    eq(index.packages.map(p => p.pkg.id), ['com.acme.order.config', 'com.acme.order.rules', 'rules/order', 'rules/pricing']);
    eq(index.byPackage.get('rules/order').counts, {rule: 11, ruleset: 3, ruleflow: 1, total: 15});
    eq(index.byType.ruleflow.map(a => a.id), ['loyaltyFlow', 'nightlyRepriceFlow', 'orderProcessingFlow']);
});

test('index categories: tree, inheritance, uncategorised and tags', () => {
    const c = index.categories;
    ok(c.has, 'the demo uses categories');
    eq(c.roots.map(n => n.name), ['Fulfilment', 'Orders', 'Pricing', 'Risk']);
    eq(c.byPath.get('Pricing').children.map(n => n.path), ['Pricing/Catalogue', 'Pricing/Loyalty']);
    eq(c.byPath.get('Pricing/Loyalty').artifacts.map(a => a.id), ['loyaltyFlow', 'loyaltyRules', 'BulkOrderRule', 'LoyaltyPointsRule', 'SeniorDiscountRule', 'tierUpgradeRule'], 'flows, then sets, then rules');
    eq(c.byPath.get('Pricing').counts.total, 3, 'direct: pricingRules, FreeShippingRule, VipDiscountRule');
    eq(c.byPath.get('Pricing').totals.total, 3 + c.byPath.get('Pricing/Catalogue').totals.total + c.byPath.get('Pricing/Loyalty').totals.total);
    eq(c.of.get('ConsistentDatesRule'), {path: 'Orders/Validation', inherited: false});
    // StockAvailableRule declares no category (the UNCATEGORISED problem says so) but sits in one rule set that has one
    eq(c.of.get('StockAvailableRule'), {path: 'Orders/Validation', inherited: true, from: 'orderValidationRules'});
    eq(c.uncategorised.map(a => a.id), [], 'so nothing in the demo is left without a home');
    eq([...c.tags.keys()].slice(0, 4), ['dates', 'fraud', 'loyalty', 'nightly']);
    eq(c.tags.get('vip').map(a => a.id), ['LoyaltyPointsRule', 'tierUpgradeRule', 'VipDiscountRule'], 'by name: TierUpgradeRule before VipDiscountRule');
    eq(categoryParents('Pricing/Loyalty/Gold'), ['Pricing', 'Pricing/Loyalty']);
    // Inheritance: a rule with no category of its own, in exactly one categorised set, is shown under that set's
    const plain = JSON.parse(JSON.stringify(descriptor));
    for (const a of plain.artifacts) if (a.id === 'MinTotalRule' || a.id === 'fraudScoreRule') { delete a.category; delete a.tags; }
    const again = buildIndex(plain).categories;
    eq(again.of.get('MinTotalRule'), {path: 'Orders/Validation', inherited: true, from: 'orderValidationRules'});
    eq(again.of.get('fraudScoreRule'), undefined, 'run by a flow, in no rule set: nothing to inherit');
    eq(again.uncategorised.map(a => a.id), ['fraudScoreRule']);
    // Without any category the tree is empty and nothing is "uncategorised"
    for (const a of plain.artifacts) delete a.category;
    const none = buildIndex(plain).categories;
    eq(none.has, false);
    eq(none.roots.length, 0);
    eq(none.uncategorised.length, 0);
});

test('search filters by tag and category', () => {
    eq(parseQuery('tag:vip total in:pricing'), {text: 'total', tags: ['vip'], category: 'pricing'});
    eq(parseQuery('tag:'), {text: '', tags: [], category: null});
    const vip = search.query('', {tags: ['vip']});
    eq(vip.flatMap(g => g.hits.map(h => h.artifact.id)), ['LoyaltyPointsRule', 'tierUpgradeRule', 'VipDiscountRule'], 'filter only: every match, by name');
    eq(search.query('', {tags: ['vip'], category: 'loyal'}).flatMap(g => g.hits.map(h => h.artifact.id)), ['LoyaltyPointsRule', 'tierUpgradeRule'], 'a level prefix selects the category');
    eq(search.query('', {category: 'pricing'}).flatMap(g => g.hits).length, index.categories.byPath.get('Pricing').totals.total, 'a category includes the ones below it');
    const vipTotal = search.query('total', {tags: ['vip']}).flatMap(g => g.hits.map(h => h.artifact.id));
    ok(vipTotal.includes('LoyaltyPointsRule') && vipTotal.every(id => ['LoyaltyPointsRule', 'tierUpgradeRule', 'VipDiscountRule'].includes(id)), 'text and filter together: ' + vipTotal);
    ok(search.query('vip').flatMap(g => g.hits).some(h => h.artifact && h.artifact.id === 'tierUpgradeRule'), 'a tag is searchable as plain text');
    eq(search.query('', {tags: ['nope']}).length, 0);
});

test('index back-links, bindings and problems', () => {
    const used = index.usedBy.get('MinTotalRule');
    eq(used.length, 1);
    eq(used[0].from, 'orderValidationRules');
    eq(used[0].path, 'members[3]');
    eq(index.uses.get('orderProcessingFlow').map(r => r.to), ['orderValidationRules', 'fraudScoreRule', 'pricingRules', 'approvalRules']);
    ok(index.bindingPaths.get('order').reads.has('order.total'), 'order.total read');
    ok(index.bindingPaths.get('order').writes.has('order.discount'), 'order.discount written');
    eq(index.problemCounts, {error: 1, warning: 1, info: 4, total: 6});
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
    has(artifactSummary(a('MinTotalRule'), index), 'Passes when order total is at least 120 (order.minTotal).');
    eq(artifactSummary(a('EmailFormatRule'), index), 'Fails with customer.email.invalid when customer email is not a valid email address.');
    has(artifactSummary(a('fraudScoreRule'), index), 'Takes the order and the customer. Its logic is compiled Java code');
    eq(artifactSummary(a('orderValidationRules'), index), 'Checks 8 rules in order and stops when number of rule violations is at least 3. Runs only if order is present.');
    has(artifactSummary(a('orderProcessingFlow'), index), 'running 3 rule sets and 1 rule, and returns approved.');
    eq(bindingSummary(index.bindings.get('order')), 'Read by 20 artifacts and written by 2. Compiled code in 2 more may also change it.');
    eq(problemsHeadline(index.problemCounts), 'Two steps are likely to fail. Four things are suggestions.');
    eq(kindCaption('rule', index.kindCounts), '14 XML · 4 Java builders · 2 @Rule classes · 2 validators');
});

test('kind labels', () => {
    const a = (id) => index.byId.get(id);
    eq(kindLabel(a('MinTotalRule')), 'XML · SpEL');
    eq(kindLabel(a('LoyaltyPointsRule')), 'XML · JavaScript');
    eq(kindLabel(a('ExpressShippingRule')), 'XML · Java');
    eq(kindLabel(a('EmailFormatRule')), 'Validator · r:email');
    eq(kindLabel(a('ConsistentDatesRule')), '@Rule class');
    eq(kindLabel(a('fraudScoreRule')), 'Java builder · lambda');
    eq(kindLabel(a('rangeCheckRule')), 'Java builder · SpEL');
    eq(kindLabel(a('tierUpgradeRule')), 'Java builder · JavaScript');
    eq(kindLabel(a('backorderRule')), 'Java builder · Java');
    eq(kindLabel(a('orderValidationRules')), 'XML · validating');
    eq(kindLabel(a('approvalRules')), 'XML');
    eq(kindShort(a('fraudScoreRule')), 'Java builder');
    eq(kindShort(a('backorderRule')), 'Java builder · Java');
});

test('script languages: one, none, mixed', () => {
    const a = (id) => index.byId.get(id);
    eq(scriptLanguage(a('MinTotalRule')), {code: 'el', name: 'SpEL', long: 'SpEL', codes: ['el']});
    eq(scriptLanguage(a('backorderRule')), {code: 'java', name: 'Java', long: 'Java (Janino)', codes: ['java']});
    eq(scriptLanguage(a('fraudScoreRule')), null, 'compiled code has no language');
    eq(scriptLanguage(a('ConsistentDatesRule')), null);
    const mixed = {type: 'rule', kind: 'xml-script', rule: {given: {kind: 'script', language: 'el', text: 'true'}, then: [{kind: 'script', language: 'js', text: 'x'}]}};
    eq(scriptLanguage(mixed), {code: 'mixed', name: 'mixed', long: 'SpEL and JavaScript', codes: ['el', 'js']});
    eq(kindLabel(mixed), 'XML · mixed');
    eq(kindLabel({type: 'rule', kind: 'xml-script', rule: {given: {kind: 'script', text: 'true'}}}), 'XML · SpEL', 'no language code means SpEL, rulii’s default');
    eq(languageName('java', true), 'Java (Janino)');
    eq(languageName('groovy'), 'groovy');
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
    eq(plainText(index.byId.get('MinTotalRule').rule.given), 'order total is at least 120 (order.minTotal)', 'a shared placeholder value reads as the value');
    const hidden = JSON.parse(JSON.stringify(index.byId.get('MinTotalRule').rule.given));
    hidden.placeholders = [{key: 'order.minTotal', defaultValue: '100', hidden: true}];
    eq(plainText(hidden), 'order total is at least order.minTotal (default 100)', 'a hidden one reads as written');
});

test('plain rendering: chips for bindings and placeholders', () => {
    const el = rendered(plainTokens(index.byId.get('MinTotalRule').rule.given));
    has(el.textContent, 'order total is at least');
    eq(el.querySelectorAll('.rx-chip').length, 1);
    eq(el.querySelector('.rx-ph-key').textContent, 'order.minTotal');
    eq(el.querySelector('.rx-ph-default').textContent, 'default 100');
});

test('placeholder values: shown, equal to the default, hidden, and in raw code', () => {
    const given = JSON.parse(JSON.stringify(index.byId.get('MinTotalRule').rule.given));
    given.placeholders = [{key: 'order.minTotal', defaultValue: '100', value: '150'}];
    let el = rendered(plainTokens(given));
    eq(el.querySelector('.rx-ph-value').textContent, '= 150');
    eq(el.querySelector('.rx-ph-default').textContent, 'default 100', 'the default stays when it differs');
    ok(el.querySelector('.rx-ph-chip.rx-ph-resolved'));

    given.placeholders = [{key: 'order.minTotal', defaultValue: '100', value: '100'}];
    el = rendered(plainTokens(given));
    eq(el.querySelector('.rx-ph-value').textContent, '= 100');
    ok(!el.querySelector('.rx-ph-default'), 'no default when it equals the value');

    given.placeholders = [{key: 'order.minTotal', defaultValue: '100', hidden: true}];
    el = rendered(plainTokens(given));
    ok(el.querySelector('.rx-ph-chip.rx-ph-hidden'));
    ok(!el.querySelector('.rx-ph-value'));
    eq(el.querySelector('.rx-ph-default').textContent, 'default 100');
    el = rendered(rawCode(given));
    has(el.querySelector('.rx-ph-inline').textContent, 'hidden');

    given.placeholders = [{key: 'order.minTotal', defaultValue: '100', value: '150'}];
    el = rendered(rawCode(given));
    has(el.querySelector('.rx-c-ph').textContent, '${order.minTotal:100}');
    eq(el.querySelector('.rx-ph-inline').textContent, '→ 150');

    given.placeholders = undefined;
    el = rendered(rawCode(given));
    ok(!el.querySelector('.rx-ph-inline'), 'nothing added when the descriptor carries no values');
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

import {fullGraph, focusGraph, filterGraph, toElk} from '/rulii-explorer/app/features/graph/graph-model.js';
import {buildFlowchart, toElkFlow} from '/rulii-explorer/app/features/flow/flow-model.js';
import {roundedPath, absolutePositions} from '/rulii-explorer/app/graph-engine/layout.js';

test('dependency graph model: nodes, missing targets, focus and filters', () => {
    const full = fullGraph(index);
    eq(full.nodes.size, 32, '30 artifacts + 2 missing targets');
    ok(full.nodes.has('missing:prefixRule'), 'prefixRule is a missing node');
    ok(full.nodes.has('missing:RangeCheckRule'), 'the mismatched lookup is a missing node too');
    eq(full.edges.filter(e => e.resolution === 'unresolved').length, 2);
    eq(full.edges.filter(e => e.type === 'contains').length, 18);
    const focus = focusGraph(full, 'orderValidationRules', '1');
    eq(focus.nodes.size, 10, 'the set, its 8 rules and the flow that runs it');
    eq(focus.nodes.get('MinTotalRule').order, 4);
    eq(focusGraph(full, 'orderValidationRules', 'all').nodes.size, 17, 'the whole connected component');
    const filtered = filterGraph(focus, {types: new Set(['ruleset', 'ruleflow']), packageId: ''});
    eq(filtered.nodes.size, 2);
    const elk = toElk(full, index, true);
    eq(elk.children.map(c => c.id), ['group:c:Fulfilment', 'group:c:Orders', 'group:c:Orders/Approval', 'group:c:Orders/Validation', 'group:c:Pricing', 'group:c:Pricing/Catalogue', 'group:c:Pricing/Loyalty', 'group:c:Risk', 'group:missing'], 'grouped by category when the application uses them');
    const plainIndex = buildIndex(JSON.parse(JSON.stringify(descriptor), (k, v) => k === 'category' ? undefined : v));
    eq(toElk(fullGraph(plainIndex), plainIndex, true).children.map(c => c.id), ['group:com.acme.order.config', 'group:com.acme.order.rules', 'group:rules/order', 'group:rules/pricing', 'group:missing'], 'by package otherwise');
    ok(full.nodes.get('orderValidationRules').width > 120 && full.nodes.get('orderValidationRules').caption === '8 rules · validating', 'measured node with caption');
});

test('flowchart model: spine, branches, async lane and handlers', () => {
    const flow = index.byId.get('orderProcessingFlow');
    const m = buildFlowchart(flow, index);
    const ids = m.nodes.map(n => n.id);
    ok(ids.includes('start') && ids.includes('return') && ids.includes('global'), 'terminals and global handler');
    eq(m.nodes.find(n => n.id === 'commands[1]').overline, 'RUN · RULE SET');
    eq(m.nodes.find(n => n.id === 'commands[1]').as, 'validation');
    eq(m.nodes.find(n => n.id === 'commands[2]').kind, 'decision');
    eq(m.nodes.find(n => n.id === 'commands[3]').overline, 'ASYNC · RULE');
    ok(m.asyncIds.has('commands[3]') && m.asyncIds.has('handler:commands[3]'), 'async lane holds the step and its handler');
    eq(m.nodes.find(n => n.id === 'commands[5]').overline, 'AWAIT · 5 S TIMEOUT');
    ok(m.edges.some(e => e.from === 'commands[3]' && e.to === 'commands[5]' && e.label === 'result'), 'async joins at the await');
    ok(m.edges.some(e => e.from === 'commands[2]' && e.to === 'commands[2].then[0]' && e.label === 'yes'), 'yes branch');
    ok(m.edges.some(e => e.from === 'commands[2]' && e.to === 'commands[3]' && e.label === 'no'), 'the no branch starts the async step');
    ok(m.edges.some(e => e.from === 'commands[2]' && e.to === 'commands[4]' && e.label === 'no'), 'and continues the spine');
    ok(!m.edges.some(e => e.from === 'commands[2].then[0]'), 'exit has no outgoing edge');
    ok(m.edges.some(e => e.from === 'commands[6].then[0]' && e.to === 'return') && m.edges.some(e => e.from === 'commands[6].otherwise[0]' && e.to === 'return'), 'both branches merge into return');
    const nightly = buildFlowchart(index.byId.get('nightlyRepriceFlow'), index);
    eq(nightly.containers.length, 1);
    eq(nightly.containers[0].children, ['commands[1].body[0]', 'commands[1].body[1]', 'commands[1].body[2]']);
    eq(nightly.nodes.find(n => n.id === 'commands[1].body[1]').resolution, 'unresolved');
    const elk = toElkFlow(nightly);
    ok(elk.children.some(c => c.id === 'container:commands[1]' && c.children.length === 3), 'container is a compound node');
});

test('layout helpers', () => {
    eq(roundedPath([[0, 0], [10, 0], [10, 10]], 4), 'M0 0L6 0Q10 0 10 4L10 10');
    const abs = absolutePositions({id: 'root', width: 100, height: 50, children: [{id: 'g', x: 10, y: 10, width: 50, height: 30, children: [{id: 'a', x: 5, y: 5, width: 10, height: 10}]}], edges: [{id: 'e', container: 'g', sections: [{startPoint: {x: 0, y: 0}, endPoint: {x: 5, y: 5}}]}]});
    eq(abs.nodes.get('a').x, 15);
    eq(abs.routes.get('e'), [[10, 10], [15, 15]]);
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
