import { describe, expect, it } from 'vitest';
import { safeReturnTo } from './safeReturnTo';

const ORIGIN = 'http://localhost:5173';

describe('safeReturnTo', () => {
	it('appPathWithQueryAndHash_returnsPathWithQueryAndHash', () => {
		// when
		const result = safeReturnTo('/app/tasks?filter=open#today', ORIGIN);

		// then
		expect(result).toBe('/app/tasks?filter=open#today');
	});

	it.each([
		{ returnTo: '//evil.example' },
		{ returnTo: '/\\evil.example' },
		{ returnTo: '/%5Cevil.example' },
		{ returnTo: '/\t/evil.example' },
		{ returnTo: '/login' },
		{ returnTo: '/app' },
		{ returnTo: '/app/../login' },
		{ returnTo: 'https://evil.example/app/x' },
		{ returnTo: 'javascript:alert(1)' },
		{ returnTo: '' },
		{ returnTo: null }
	])('unsafe target $returnTo — returns the dashboard', ({ returnTo }) => {
		// when
		const result = safeReturnTo(returnTo, ORIGIN);

		// then
		expect(result).toBe('/app/dashboard');
	});
});
