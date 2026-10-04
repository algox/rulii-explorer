/**
 * Entry point: registers the elements, starts the router, loads the descriptor and builds the
 * indexes and the search index once (SOLUTION §9.2).
 */
import './features/shell/app.js';
import './features/shell/topbar.js';
import './features/shell/sidebar.js';
import './features/shell/palette.js';
import './features/shell/help.js';
import './features/shell/source.js';
import './features/shell/hovercard.js';
import './features/overview/overview.js';
import './features/problems/problems.js';
import './features/graph/graph.js';
import './features/flow/flowchart.js';
import './features/artifact/artifact.js';
import './features/artifact/package.js';
import './features/artifact/category.js';
import './features/binding/binding.js';
import './features/states/states.js';

import {store} from './state/store.js';
import {startRouter} from './routing/router.js';
import {boot, openApplication, openFile, openUrl} from './boot.js';

store.applyTheme(store.state.theme);
startRouter(store);

/** A hook for the browser tests and the console: the store and the source switches. */
globalThis.__rx = {store, boot, openUrl, openFile, openApplication};

export {boot};

boot();
