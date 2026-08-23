import { describe, expect, it } from 'vitest';
import { ResponseError } from '../../generated-sources/openapi';
import { extractErrorStatus } from './errorStatus';

describe('extractErrorStatus', () => {
	it('liest den Status aus einem ResponseError', () => {
		// given
		const error = new ResponseError(new Response(null, { status: 409 }));

		// when
		const result = extractErrorStatus(error);

		// then
		expect(result).toBe(409);
	});

	it('liest den Status aus einem objektartigen Fehler mit status-Property', () => {
		// given
		const error = { status: 404 };

		// when
		const result = extractErrorStatus(error);

		// then
		expect(result).toBe(404);
	});

	it('gibt undefined zurück für einen Fehler ohne erkennbaren Status', () => {
		// given
		const error = new Error('boom');

		// when
		const result = extractErrorStatus(error);

		// then
		expect(result).toBeUndefined();
	});
});
