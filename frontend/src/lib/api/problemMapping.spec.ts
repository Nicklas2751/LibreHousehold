import { describe, expect, it } from 'vitest';
import { FetchError, ResponseError } from '../../generated-sources/openapi';
import {
	ACCOUNT_ALREADY_EXISTS,
	ACCOUNT_LOCKED,
	classifyConflictProblem,
	classifyLoginProblem,
	EMAIL_NOT_VERIFIED,
	HOUSEHOLD_ALREADY_EXISTS,
	INVALID_CREDENTIALS,
	LOCAL_LOGIN_DISABLED,
	readLoginProblem,
	TOO_MANY_REQUESTS
} from './problemMapping';

describe('classifyConflictProblem', () => {
	it('recognizes /problems/household-already-exists', () => {
		// given
		const problemType = HOUSEHOLD_ALREADY_EXISTS;

		// when
		const result = classifyConflictProblem(problemType);

		// then
		expect(result).toBe('household-exists');
	});

	it('recognizes /problems/account-already-exists', () => {
		// given
		const problemType = ACCOUNT_ALREADY_EXISTS;

		// when
		const result = classifyConflictProblem(problemType);

		// then
		expect(result).toBe('account-exists');
	});

	it('returns unknown for an unrecognized problem type', () => {
		// given
		const problemType = '/problems/some-other-problem';

		// when
		const result = classifyConflictProblem(problemType);

		// then
		expect(result).toBe('unknown');
	});

	it('returns unknown for a missing problem type', () => {
		// given
		const problemType = undefined;

		// when
		const result = classifyConflictProblem(problemType);

		// then
		expect(result).toBe('unknown');
	});
});

describe('classifyLoginProblem', () => {
	it.each([
		{
			status: 401,
			problem: { type: INVALID_CREDENTIALS },
			expected: { kind: 'invalid-credentials' }
		},
		{ status: 403, problem: { type: EMAIL_NOT_VERIFIED }, expected: { kind: 'unverified' } },
		{
			status: 403,
			problem: { type: LOCAL_LOGIN_DISABLED },
			expected: { kind: 'local-login-disabled' }
		},
		{
			status: 403,
			problem: { type: '/problems/some-other-problem' },
			expected: { kind: 'unexpected' }
		},
		{ status: 403, problem: undefined, expected: { kind: 'unexpected' } },
		{ status: 500, problem: undefined, expected: { kind: 'unexpected' } },
		{ status: undefined, problem: undefined, expected: { kind: 'network' } }
	])(
		'classifyLoginProblem_status$status_type$problem.type_$expected.kind',
		({ status, problem, expected }) => {
			// when
			const result = classifyLoginProblem(status, problem, null);

			// then
			expect(result).toEqual(expected);
		}
	);

	it('classifyLoginProblem_accountLocked_lockedWithLockedUntil', () => {
		// given
		const lockedUntil = '2026-11-05T10:15:00Z';

		// when
		const result = classifyLoginProblem(403, { type: ACCOUNT_LOCKED, lockedUntil }, '900');

		// then
		expect(result).toEqual({ kind: 'locked', lockedUntil: new Date(lockedUntil) });
	});

	it('classifyLoginProblem_accountLockedWithoutLockedUntil_lockedWithoutTime', () => {
		// when
		const result = classifyLoginProblem(403, { type: ACCOUNT_LOCKED }, null);

		// then
		expect(result).toEqual({ kind: 'locked', lockedUntil: null });
	});

	it('classifyLoginProblem_tooManyRequestsWithRetryAfter_rateLimitedWithSeconds', () => {
		// when
		const result = classifyLoginProblem(429, { type: TOO_MANY_REQUESTS }, '42');

		// then
		expect(result).toEqual({ kind: 'rate-limited', retryAfterSeconds: 42 });
	});

	it.each([null, 'Wed, 21 Oct 2026 07:28:00 GMT'])(
		'classifyLoginProblem_tooManyRequestsWithRetryAfter %s_rateLimitedWithoutSeconds',
		(retryAfter) => {
			// when
			const result = classifyLoginProblem(429, { type: TOO_MANY_REQUESTS }, retryAfter);

			// then
			expect(result).toEqual({ kind: 'rate-limited', retryAfterSeconds: null });
		}
	);
});

describe('readLoginProblem', () => {
	it('readLoginProblem_responseErrorWithProblemBody_classifiesByStatusTypeAndRetryAfter', async () => {
		// given
		const error = new ResponseError(
			new Response(JSON.stringify({ type: TOO_MANY_REQUESTS, status: 429 }), {
				status: 429,
				headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '30' }
			})
		);

		// when
		const result = await readLoginProblem(error);

		// then
		expect(result).toEqual({ kind: 'rate-limited', retryAfterSeconds: 30 });
	});

	it('readLoginProblem_responseErrorWithoutJsonBody_unexpected', async () => {
		// given
		const error = new ResponseError(new Response('<html>Forbidden</html>', { status: 403 }));

		// when
		const result = await readLoginProblem(error);

		// then
		expect(result).toEqual({ kind: 'unexpected' });
	});

	it('readLoginProblem_fetchError_network', async () => {
		// given
		const error = new FetchError(new TypeError('Failed to fetch'));

		// when
		const result = await readLoginProblem(error);

		// then
		expect(result).toEqual({ kind: 'network' });
	});

	it('readLoginProblem_otherError_unexpected', async () => {
		// given
		const error = new Error('boom');

		// when
		const result = await readLoginProblem(error);

		// then
		expect(result).toEqual({ kind: 'unexpected' });
	});
});
