import { describe, expect, it } from 'vitest';
import { ResponseError } from '../../generated-sources/openapi';
import { extractErrorStatus } from './errorStatus';

describe('extractErrorStatus', () => {
	it('reads the status from a ResponseError', () => {
		// given
		const error = new ResponseError(new Response(null, { status: 409 }));

		// when
		const result = extractErrorStatus(error);

		// then
		expect(result).toBe(409);
	});

	it('reads the status from an object-like error with a status property', () => {
		// given
		const error = { status: 404 };

		// when
		const result = extractErrorStatus(error);

		// then
		expect(result).toBe(404);
	});

	it('returns undefined for an error without a recognizable status', () => {
		// given
		const error = new Error('boom');

		// when
		const result = extractErrorStatus(error);

		// then
		expect(result).toBeUndefined();
	});
});
