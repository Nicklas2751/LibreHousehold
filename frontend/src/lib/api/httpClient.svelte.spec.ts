import { beforeEach, describe, expect, it } from 'vitest';
import { apiConfiguration, csrfMiddleware } from './httpClient';

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
});
