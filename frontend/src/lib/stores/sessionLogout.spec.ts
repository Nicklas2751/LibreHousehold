import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { logout } from './sessionLogout';

const { mockLogout } = vi.hoisted(() => ({ mockLogout: vi.fn() }));

vi.mock('../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../generated-sources/openapi')>();
	return {
		...original,
		SessionApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.logout = mockLogout;
		})
	};
});

describe('logout', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('logout_anySession_callsLogoutEndpoint', async () => {
		// given
		mockLogout.mockResolvedValue(undefined);
		vi.stubGlobal('window', { location: { replace: vi.fn() } });

		// when
		await logout();

		// then
		expect(mockLogout).toHaveBeenCalled();
	});

	it('logout_anySession_replacesLocationWithRoot', async () => {
		// given
		mockLogout.mockResolvedValue(undefined);
		const fakeWindow = { location: { replace: vi.fn() } };
		vi.stubGlobal('window', fakeWindow);

		// when
		await logout();

		// then
		expect(fakeWindow.location.replace).toHaveBeenCalledWith('/');
	});

	it('logout_apiRequestFails_stillReplacesLocationWithRoot', async () => {
		// given
		mockLogout.mockRejectedValue(new Error('network error'));
		const fakeWindow = { location: { replace: vi.fn() } };
		vi.stubGlobal('window', fakeWindow);

		// when
		await logout();

		// then
		expect(fakeWindow.location.replace).toHaveBeenCalledWith('/');
	});
});
