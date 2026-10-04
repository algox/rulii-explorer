/**
 * Loads a descriptor into the store: the one the page names, another application's, or a file.
 * `boot` runs once at start-up and again whenever the source changes; everything derived from
 * the descriptor (indexes, search, graph layouts, the open sidebar packages) is rebuilt.
 */
import {store} from './state/store.js';
import {loadDescriptor} from './descriptor/loader.js';
import {buildIndex} from './descriptor/indexes.js';
import {buildSearch} from './search/search.js';
import {clearLayoutCache} from './graph-engine/layout.js';
import {currentSource, externalSourcesAllowed, forgetFile, pageUrlFor, rememberFile} from './descriptor/source.js';
import {navigate} from './routing/router.js';

let generation = 0;

/**
 * @param {import('./descriptor/source.js').Source} [source] defaults to what the address and the session say.
 * @param {{reset?: boolean}} [options] reset: a switch, so forget layouts and go to the overview.
 */
export async function boot(source = currentSource(), options = {}) {
    const gen = ++generation;
    const shown = source.kind === 'file' ? {kind: 'file', name: source.name} : {kind: source.kind, url: source.url};
    if (options.reset) {
        clearLayoutCache();
        store.set({expanded: new Set()});
        navigate('#/');
    }
    store.set({status: 'loading', source: shown, descriptor: null, index: null, search: null, error: null, sourceOpen: false});
    if (source.kind === 'file' && source.text === undefined) {
        // A reload: the file is gone with the page, only its name was kept.
        store.set({status: 'pick-source'});
        return;
    }
    const result = await loadDescriptor(source);
    if (gen !== generation) return; // a newer source won
    if (result.descriptor && result.status !== 'unsupported') {
        const index = buildIndex(result.descriptor);
        const search = buildSearch(result.descriptor, index);
        store.set({status: result.status, descriptor: result.descriptor, index, search, error: null});
    } else {
        store.set({status: result.status, descriptor: result.descriptor || null, index: null, search: null, error: result.error || null});
    }
}

/** Shows the descriptor at a URL (another Actuator, or a JSON file on a server) and puts it in the address. */
export function openUrl(url) {
    if (!externalSourcesAllowed()) return;
    forgetFile();
    history.pushState(null, '', pageUrlFor(url));
    return boot(currentSource(), {reset: true});
}

/** Shows a file from the user's machine; it stays in memory, its name in the session. */
export async function openFile(file) {
    if (!externalSourcesAllowed() || !file) return;
    const text = await file.text();
    rememberFile(file.name);
    history.pushState(null, '', pageUrlFor(null));
    return boot({kind: 'file', name: file.name, text}, {reset: true});
}

/** Back to the application's own descriptor. */
export function openApplication() {
    forgetFile();
    history.pushState(null, '', pageUrlFor(null));
    return boot(currentSource(), {reset: true});
}

/** Browser back/forward across a source change reloads the descriptor the address now names. */
addEventListener('popstate', () => {
    const next = currentSource();
    const now = store.state.source || {};
    if (next.kind !== now.kind || next.url !== now.url || next.name !== now.name) boot(next);
});
