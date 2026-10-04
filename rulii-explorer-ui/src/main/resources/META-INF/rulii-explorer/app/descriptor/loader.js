/**
 * Loads the descriptor from a source (descriptor/source.js): the application's own endpoint, an
 * address on another origin, or the text of a file, and maps every outcome to one of the designed
 * states (SOLUTION §8.3, S-States).
 *
 * @typedef {object} LoadResult
 * @property {'ready'|'empty'|'not-exposed'|'unauthorized'|'forbidden'|'failed'|'unreachable'|'unsupported'} status
 * @property {object} [descriptor]
 * @property {{httpStatus?: number, message: string, detail?: string, crossOrigin?: boolean, mixedContent?: boolean}} [error]
 */
import {applicationUrl, currentSource, isCrossOrigin, isMixedContent} from './source.js';

export const SUPPORTED_MAJOR = '1';

/** The application's descriptor URL, resolved against the page. */
export function descriptorUrl() {
    return applicationUrl();
}

/**
 * @param {import('./source.js').Source|string} [source] a source, or a URL for convenience.
 * @returns {Promise<LoadResult>}
 */
export async function loadDescriptor(source = currentSource()) {
    if (typeof source === 'string') source = {kind: 'url', url: source};
    if (source.kind === 'file') return fromText(source.text, {file: true});
    const url = source.url;
    const cross = isCrossOrigin(url);
    if (source.invalid) {
        return {status: 'unreachable', error: {message: 'The address is not a web address.', detail: url, crossOrigin: true}};
    }
    let response;
    try {
        // Another origin gets no cookies: with them, the remote would have to allow credentials
        // explicitly, which the usual `allowed-origins=*` setup does not.
        response = await fetch(url, {headers: {Accept: 'application/json'}, credentials: cross ? 'omit' : 'same-origin'});
    } catch (e) {
        return {
            status: 'unreachable',
            error: {message: cross ? 'The address did not answer.' : 'The application did not answer.', detail: String(e && e.message || e), crossOrigin: cross, mixedContent: isMixedContent(url)}
        };
    }
    if (response.status === 404) return {status: 'not-exposed', error: {httpStatus: 404, message: cross ? 'Nothing at this address.' : 'The rule descriptor is not exposed.'}};
    if (response.status === 401) return {status: 'unauthorized', error: {httpStatus: 401, message: 'Sign-in required.'}};
    if (response.status === 403) return {status: 'forbidden', error: {httpStatus: 403, message: 'Access denied.'}};
    return fromText(await response.text(), {httpStatus: response.status, ok: response.ok});
}

/**
 * Reads a descriptor from its text: the body of a response, or the content of a file.
 *
 * @param {string} text
 * @param {{httpStatus?: number, ok?: boolean, file?: boolean}} context
 * @returns {LoadResult}
 */
export function fromText(text, context = {}) {
    let body = null;
    text = text == null ? '' : String(text);
    try {
        body = text ? JSON.parse(text) : null;
    } catch (e) {
        body = null;
    }
    if (context.ok === false) {
        // The starter's error payload is {"descriptorVersion": "1.0", "error": {"message": "..."}}.
        const error = body && body.error;
        const message = error && typeof error === 'object' ? error.message : (typeof error === 'string' ? error : body && body.message);
        return {
            status: 'failed',
            error: {
                httpStatus: context.httpStatus,
                message: 'The rule descriptor could not be built.',
                detail: message ? String(message) : (body ? null : text.slice(0, 400))
            }
        };
    }
    if (!body || typeof body !== 'object' || !body.descriptorVersion) {
        return {status: 'failed', error: {httpStatus: context.httpStatus, message: context.file ? 'The file is not a rule descriptor.' : 'The response is not a rule descriptor.', detail: text.slice(0, 400)}};
    }
    const major = String(body.descriptorVersion).split('.')[0];
    if (major !== SUPPORTED_MAJOR) {
        return {status: 'unsupported', descriptor: body, error: {message: 'Descriptor version ' + body.descriptorVersion + ' is not supported by this explorer (' + SUPPORTED_MAJOR + '.x).'}};
    }
    normalise(body);
    return {status: body.artifacts.length === 0 ? 'empty' : 'ready', descriptor: body};
}

/** Fills in the optional arrays so views never test for undefined. */
function normalise(d) {
    d.packages ||= [];
    d.artifacts ||= [];
    d.references ||= [];
    d.bindings ||= [];
    d.problems ||= [];
    d.application ||= {};
    for (const a of d.artifacts) {
        a.parameters ||= [];
        if (a.rule) a.rule.then ||= [];
        if (a.ruleSet) a.ruleSet.members ||= [];
        if (a.ruleFlow) a.ruleFlow.commands ||= [];
    }
}
