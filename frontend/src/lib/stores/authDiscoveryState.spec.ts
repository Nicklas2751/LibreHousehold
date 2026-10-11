import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { AuthDiscovery } from '../../generated-sources/openapi';

const { mockGetAuthDiscovery } = vi.hoisted(() => ({
	mockGetAuthDiscovery: vi.fn()
}));

vi.mock('../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../generated-sources/openapi')>();
	return {
		...original,
		SessionApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.getAuthDiscovery = mockGetAuthDiscovery;
		})
	};
});

// The module caches the discovery result for the SPA's lifetime, so every test loads a fresh copy.
async function importFreshAuthDiscoveryState() {
	vi.resetModules();
	return import('./authDiscoveryState.svelte');
}

describe('authDiscoveryState', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('loadAuthDiscovery_successfulResponse_populatesProvidersAndLocalEnabled', async () => {
		// given
		const discovery: AuthDiscovery = {
			localEnabled: false,
			socialProviders: [{ id: 'google', displayName: 'Google' }]
		};
		mockGetAuthDiscovery.mockResolvedValue(discovery);
		const { authDiscovery, loadAuthDiscovery } = await importFreshAuthDiscoveryState();

		// when
		await loadAuthDiscovery();

		// then
		expect(authDiscovery).toEqual(discovery);
	});

	it('loadAuthDiscovery_calledTwice_onlyFetchesOnce', async () => {
		// given
		mockGetAuthDiscovery.mockResolvedValue({ localEnabled: true, socialProviders: [] });
		const { loadAuthDiscovery } = await importFreshAuthDiscoveryState();

		// when
		await loadAuthDiscovery();
		await loadAuthDiscovery();

		// then
		expect(mockGetAuthDiscovery).toHaveBeenCalledTimes(1);
	});

	it('loadAuthDiscovery_failedResponse_keepsLocalOnlyDefaults', async () => {
		// given
		mockGetAuthDiscovery.mockRejectedValue(new Error('network down'));
		const { authDiscovery, loadAuthDiscovery } = await importFreshAuthDiscoveryState();

		// when
		await loadAuthDiscovery();

		// then
		expect(authDiscovery).toEqual({ localEnabled: true, socialProviders: [] });
	});

	it('loadAuthDiscovery_calledAgainAfterFailure_fetchesAgain', async () => {
		// given
		mockGetAuthDiscovery.mockRejectedValueOnce(new Error('network down'));
		mockGetAuthDiscovery.mockResolvedValueOnce({ localEnabled: true, socialProviders: [] });
		const { loadAuthDiscovery } = await importFreshAuthDiscoveryState();
		await loadAuthDiscovery();

		// when
		await loadAuthDiscovery();

		// then
		expect(mockGetAuthDiscovery).toHaveBeenCalledTimes(2);
	});
});
