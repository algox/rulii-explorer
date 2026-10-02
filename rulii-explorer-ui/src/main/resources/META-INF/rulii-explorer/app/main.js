/**
 * Entry point: registers the elements, starts the router, loads the descriptor and builds the
 * indexes and the search index once (SOLUTION §9.2).
 */
import './features/shell/app.js';
import './features/shell/topbar.js';
import './features/shell/sidebar.js';
import './features/shell/palette.js';
import './features/shell/help.js';
import './features/shell/hovercard.js';
import './features/overview/overview.js';
import './features/problems/problems.js';
import './features/graph/graph.js';
import './features/flow/flowchart.js';
import './features/artifact/artifact.js';
import './features/artifact/package.js';
import './features/binding/binding.js';
import './features/states/states.js';

import {store} from './state/store.js';
import {startRouter} from './routing/router.js';
import {loadDescriptor} from './descriptor/loader.js';
import {buildIndex} from './descriptor/indexes.js';
import {buildSearch} from './search/search.js';

store.applyTheme(store.state.theme);
startRouter(store);

/** A hook for the browser tests and the console: the store and its indexes. */
globalThis.__rx = {store};

export async function boot() {
    const result = await loadDescriptor();
    if (result.descriptor && result.status !== 'unsupported') {
        const index = buildIndex(result.descriptor);
        const search = buildSearch(result.descriptor, index);
        store.set({status: result.status, descriptor: result.descriptor, index, search, error: null});
    } else {
        store.set({status: result.status, descriptor: result.descriptor || null, index: null, search: null, error: result.error || null});
    }
}

boot();
