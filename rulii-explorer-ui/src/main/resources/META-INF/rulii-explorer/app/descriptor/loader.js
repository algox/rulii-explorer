/**
 * Loads the descriptor from the address in the page's `rulii-descriptor` meta tag and maps every
 * outcome to one of the designed states (SOLUTION §8.3, S-States).
 *
 * @typedef {object} LoadResult
 * @property {'ready'|'empty'|'not-exposed'|'unauthorized'|'forbidden'|'failed'|'unreachable'|'unsupported'} status
 * @property {object} [descriptor]
 * @property {{httpStatus?: number, message: string, detail?: string}} [error]
 */

export const SUPPORTED_MAJOR = '1';

/** The descriptor URL, resolved against the page. */
export function descriptorUrl() {
    const meta = document.querySelector('meta[name="rulii-descriptor"]');
    return new URL(meta && meta.content ? meta.content : '/actuator/rulii', document.baseURI).toString();
}

/** @returns {Promise<LoadResult>} */
export async function loadDescriptor(url = descriptorUrl()) {
    let response;
    try {
        response = await fetch(url, {headers: {Accept: 'application/json'}, credentials: 'same-origin'});
    } catch (e) {
        return {status: 'unreachable', error: {message: 'The application did not answer.', detail: String(e && e.message || e)}};
    }
    if (response.status === 404) return {status: 'not-exposed', error: {httpStatus: 404, message: 'The rule descriptor is not exposed.'}};
    if (response.status === 401) return {status: 'unauthorized', error: {httpStatus: 401, message: 'Sign-in required.'}};
    if (response.status === 403) return {status: 'forbidden', error: {httpStatus: 403, message: 'Access denied.'}};

    let body = null;
    const text = await response.text();
    try {
        body = text ? JSON.parse(text) : null;
    } catch (e) {
        body = null;
    }

    if (!response.ok) {
        // The starter's error payload is {"descriptorVersion": "1.0", "error": {"message": "..."}}.
        const error = body && body.error;
        const message = error && typeof error === 'object' ? error.message : (typeof error === 'string' ? error : body && body.message);
        return {
            status: 'failed',
            error: {
                httpStatus: response.status,
                message: 'The rule descriptor could not be built.',
                detail: message ? String(message) : (body ? null : text.slice(0, 400))
            }
        };
    }
    if (!body || typeof body !== 'object' || !body.descriptorVersion) {
        return {status: 'failed', error: {httpStatus: response.status, message: 'The response is not a rule descriptor.', detail: text.slice(0, 400)}};
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
