import { apiConfiguration } from '$lib/api/httpClient';
import { type AuthDiscovery, SessionApi } from '../../generated-sources/openapi';

const sessionApi = new SessionApi(apiConfiguration);

// Defaults to local login only until the discovery has loaded, so the login form never flickers
// away on a slow response, and stays usable if the discovery request fails.
export const authDiscovery: AuthDiscovery = $state({ localEnabled: true, socialProviders: [] });

let pendingDiscovery: Promise<void> | null = null;

// The login methods are static server configuration, so they are fetched once per SPA lifetime.
export function loadAuthDiscovery(): Promise<void> {
	pendingDiscovery ??= fetchAuthDiscovery();
	return pendingDiscovery;
}

async function fetchAuthDiscovery(): Promise<void> {
	try {
		const discovery = await sessionApi.getAuthDiscovery();
		authDiscovery.localEnabled = discovery.localEnabled;
		authDiscovery.socialProviders = discovery.socialProviders;
	} catch {
		// Keep the local-only defaults and allow the next caller to retry.
		pendingDiscovery = null;
	}
}
