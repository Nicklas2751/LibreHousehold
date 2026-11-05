import { FetchError, ResponseError } from '../../generated-sources/openapi';

export const HOUSEHOLD_ALREADY_EXISTS = '/problems/household-already-exists';
export const ACCOUNT_ALREADY_EXISTS = '/problems/account-already-exists';
export const INVALID_CREDENTIALS = '/problems/invalid-credentials';
export const EMAIL_NOT_VERIFIED = '/problems/email-not-verified';
export const LOCAL_LOGIN_DISABLED = '/problems/local-login-disabled';
export const ACCOUNT_LOCKED = '/problems/account-locked';
export const TOO_MANY_REQUESTS = '/problems/too-many-requests';

const HTTP_STATUS_UNAUTHORIZED = 401;
const HTTP_STATUS_FORBIDDEN = 403;
const HTTP_STATUS_TOO_MANY_REQUESTS = 429;
const RETRY_AFTER_HEADER = 'Retry-After';
const DELAY_SECONDS_PATTERN = /^\d+$/;

export type ConflictProblem = 'household-exists' | 'account-exists' | 'unknown';

export type LoginProblem =
	| { kind: 'invalid-credentials' }
	| { kind: 'unverified' }
	| { kind: 'locked'; lockedUntil: Date | null }
	| { kind: 'local-login-disabled' }
	| { kind: 'rate-limited'; retryAfterSeconds: number | null }
	| { kind: 'network' }
	| { kind: 'unexpected' };

type LoginProblemBody = { type?: string; lockedUntil?: string };

export function classifyConflictProblem(problemType: string | undefined): ConflictProblem {
	switch (problemType) {
		case HOUSEHOLD_ALREADY_EXISTS:
			return 'household-exists';
		case ACCOUNT_ALREADY_EXISTS:
			return 'account-exists';
		default:
			return 'unknown';
	}
}

// A missing status means the request never got a response, i.e. a network failure.
export function classifyLoginProblem(
	status: number | undefined,
	problem: LoginProblemBody | undefined,
	retryAfter: string | null
): LoginProblem {
	if (status === undefined) {
		return { kind: 'network' };
	}
	if (status === HTTP_STATUS_UNAUTHORIZED) {
		return { kind: 'invalid-credentials' };
	}
	if (status === HTTP_STATUS_TOO_MANY_REQUESTS) {
		return { kind: 'rate-limited', retryAfterSeconds: parseDelaySeconds(retryAfter) };
	}
	if (status === HTTP_STATUS_FORBIDDEN) {
		return classifyForbiddenLoginProblem(problem);
	}
	return { kind: 'unexpected' };
}

export async function readLoginProblem(err: unknown): Promise<LoginProblem> {
	if (err instanceof FetchError) {
		return classifyLoginProblem(undefined, undefined, null);
	}
	if (!(err instanceof ResponseError)) {
		return { kind: 'unexpected' };
	}
	const { response } = err;
	return classifyLoginProblem(
		response.status,
		await readProblemBody(response),
		response.headers.get(RETRY_AFTER_HEADER)
	);
}

function classifyForbiddenLoginProblem(problem: LoginProblemBody | undefined): LoginProblem {
	switch (problem?.type) {
		case EMAIL_NOT_VERIFIED:
			return { kind: 'unverified' };
		case LOCAL_LOGIN_DISABLED:
			return { kind: 'local-login-disabled' };
		case ACCOUNT_LOCKED:
			return { kind: 'locked', lockedUntil: parseDate(problem.lockedUntil) };
		default:
			return { kind: 'unexpected' };
	}
}

async function readProblemBody(response: Response): Promise<LoginProblemBody | undefined> {
	try {
		return await response.json();
	} catch {
		return undefined;
	}
}

// Retry-After may also be an HTTP date; only the delay-seconds form yields a countable value.
function parseDelaySeconds(retryAfter: string | null): number | null {
	if (retryAfter === null || !DELAY_SECONDS_PATTERN.test(retryAfter.trim())) {
		return null;
	}
	return Number(retryAfter.trim());
}

function parseDate(value: string | undefined): Date | null {
	if (!value) {
		return null;
	}
	const date = new Date(value);
	return Number.isNaN(date.getTime()) ? null : date;
}
