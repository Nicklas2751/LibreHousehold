import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiConfiguration, csrfMiddleware, sessionExpiredMiddleware } from './httpClient';
import { session, setAuthenticated, setGuest } from '../stores/sessionState.svelte';

const { mockRedirectToOAuth2Login } = vi.hoisted(() => ({
	mockRedirectToOAuth2Login: vi.fn()
}));

vi.mock('../oauth2Login', () => ({
	OAUTH2_LOGIN_PATH: '/oauth2/authorization/spa-backend-client',
	redirectToOAuth2Login: mockRedirectToOAuth2Login
}));

const currentUser = {
	member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
	household: { id: 'household-id', name: 'Die Testfamilie' },
	preferences: {}
};

function clearCookies() {
	document.cookie.split(';').forEach((cookie) => {
		const name = cookie.split('=')[0].trim();
		if (name) {
			document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
		}
	});
}

describe('csrfMiddleware', () => {
	beforeEach(() => {
		clearCookies();
	});

	it('sets the X-XSRF-TOKEN header on POST when an XSRF-TOKEN cookie is set', async () => {
		// given
		document.cookie = 'XSRF-TOKEN=csrf-token-value';

		// when
		const result = await csrfMiddleware.pre?.({
			fetch,
			url: '/api/some-resource',
			init: { method: 'POST', headers: {} }
		});

		// then
		expect(result?.init.headers).toEqual(
			expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-token-value' })
		);
	});

	it('sets no header on GET even when an XSRF-TOKEN cookie is set', async () => {
		// given
		document.cookie = 'XSRF-TOKEN=csrf-token-value';

		// when
		const result = await csrfMiddleware.pre?.({
			fetch,
			url: '/api/some-resource',
			init: { method: 'GET', headers: {} }
		});

		// then
		expect(result).toBeUndefined();
	});

	it('sets no header when no XSRF-TOKEN cookie is present', async () => {
		// given (no cookie set)

		// when
		const result = await csrfMiddleware.pre?.({
			fetch,
			url: '/api/some-resource',
			init: { method: 'POST', headers: {} }
		});

		// then
		expect(result).toBeUndefined();
	});
});

describe('apiConfiguration', () => {
	it('sends requests with credentials include', () => {
		// when
		const result = apiConfiguration.credentials;

		// then
		expect(result).toBe('include');
	});

	it('registers the csrfMiddleware', () => {
		// when
		const result = apiConfiguration.middleware;

		// then
		expect(result).toContain(csrfMiddleware);
	});

	it('registers the sessionExpiredMiddleware', () => {
		// when
		const result = apiConfiguration.middleware;

		// then
		expect(result).toContain(sessionExpiredMiddleware);
	});
});

describe('sessionExpiredMiddleware', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('on 401 with status authenticated — sets session to guest and redirects to the OAuth2 login entry point', async () => {
		// given
		setAuthenticated(currentUser);

		// when
		await sessionExpiredMiddleware.post?.({
			fetch,
			url: '/api/tasks',
			init: {},
			response: new Response(null, { status: 401 })
		});

		// then
		expect(session.status).toBe('guest');
		expect(mockRedirectToOAuth2Login).toHaveBeenCalled();
	});

	it('on 401 with status guest — does not change the session and does not redirect', async () => {
		// given
		setGuest();

		// when
		await sessionExpiredMiddleware.post?.({
			fetch,
			url: '/api/tasks',
			init: {},
			response: new Response(null, { status: 401 })
		});

		// then
		expect(session.status).toBe('guest');
		expect(mockRedirectToOAuth2Login).not.toHaveBeenCalled();
	});

	it('on a non-401 response — does not change the session and does not redirect', async () => {
		// given
		setAuthenticated(currentUser);

		// when
		await sessionExpiredMiddleware.post?.({
			fetch,
			url: '/api/tasks',
			init: {},
			response: new Response(null, { status: 500 })
		});

		// then
		expect(session.status).toBe('authenticated');
		expect(mockRedirectToOAuth2Login).not.toHaveBeenCalled();
	});
});
