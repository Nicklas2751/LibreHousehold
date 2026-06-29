import { describe, it, expect, vi, beforeEach } from 'vitest';
import { get } from 'svelte/store';

const mockSigninRedirect = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));
const mockSigninRedirectCallback = vi.hoisted(() => vi.fn());
const mockRemoveUser = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));

const MockUserManager = vi.hoisted(() => {
	const mockSigninRedirectRef = mockSigninRedirect;
	const mockSigninRedirectCallbackRef = mockSigninRedirectCallback;
	const mockRemoveUserRef = mockRemoveUser;
	return vi.fn(function (this: Record<string, unknown>) {
		this.signinRedirect = mockSigninRedirectRef;
		this.signinRedirectCallback = mockSigninRedirectCallbackRef;
		this.removeUser = mockRemoveUserRef;
	} as unknown as new () => unknown);
});

vi.hoisted(() => {
	const store: Record<string, string> = {};
	const mockSessionStorage = {
		getItem: (key: string) => store[key] ?? null,
		setItem: (key: string, value: string) => {
			store[key] = value;
		},
		removeItem: (key: string) => {
			delete store[key];
		}
	};
	(globalThis as Record<string, unknown>).window = {
		location: { origin: 'http://localhost:5173' },
		sessionStorage: mockSessionStorage
	};
	(globalThis as Record<string, unknown>).sessionStorage = mockSessionStorage;
});

vi.mock('oidc-client-ts', () => ({
	UserManager: MockUserManager,
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	WebStorageStateStore: vi.fn(function (this: any) {} as any)
}));

vi.mock('$app/environment', () => ({ browser: true }));

import { isAuthenticated, login, handleCallback, logout, getAccessToken } from './authStore.svelte';

describe('authStore', () => {
	beforeEach(async () => {
		vi.clearAllMocks();
		await logout();
	});

	describe('isAuthenticated', () => {
		it('initialState_returnsFalse', () => {
			// given / when / then
			expect(get(isAuthenticated)).toBe(false);
		});
	});

	describe('login', () => {
		it('whenCalled_invokesSigninRedirect', async () => {
			// given
			mockSigninRedirect.mockResolvedValueOnce(undefined);

			// when
			await login();

			// then
			expect(mockSigninRedirect).toHaveBeenCalledOnce();
		});
	});

	describe('handleCallback', () => {
		it('withValidCode_setsUserAndIsAuthenticatedTrue', async () => {
			// given
			const mockUser = { access_token: 'test-token-123', profile: {} };
			mockSigninRedirectCallback.mockResolvedValueOnce(mockUser);

			// when
			await handleCallback();

			// then
			expect(get(isAuthenticated)).toBe(true);
		});

		it('withError_propagatesException', async () => {
			// given
			mockSigninRedirectCallback.mockRejectedValueOnce(new Error('PKCE exchange failed'));

			// when / then
			await expect(handleCallback()).rejects.toThrow('PKCE exchange failed');
		});
	});

	describe('logout', () => {
		it('afterLogin_clearsUserAndIsAuthenticatedFalse', async () => {
			// given
			const mockUser = { access_token: 'test-token-123', profile: {} };
			mockSigninRedirectCallback.mockResolvedValueOnce(mockUser);
			await handleCallback();
			expect(get(isAuthenticated)).toBe(true);

			// when
			await logout();

			// then
			expect(get(isAuthenticated)).toBe(false);
		});
	});

	describe('getAccessToken', () => {
		it('withNoUser_returnsNull', () => {
			// given / when
			const token = getAccessToken();

			// then
			expect(token).toBeNull();
		});

		it('afterSuccessfulCallback_returnsToken', async () => {
			// given
			const mockUser = { access_token: 'test-token-456', profile: {} };
			mockSigninRedirectCallback.mockResolvedValueOnce(mockUser);
			await handleCallback();

			// when
			const token = getAccessToken();

			// then
			expect(token).toBe('test-token-456');
		});
	});
});
