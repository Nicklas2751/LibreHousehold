import { describe, expect, it } from 'vitest';
import { isValidPassword } from './passwordValidation';

describe('isValidPassword', () => {
	it('rejects a password with fewer than 8 characters', () => {
		// given
		const password = 'a'.repeat(7);

		// when
		const result = isValidPassword(password);

		// then
		expect(result).toBe(false);
	});

	it('accepts a password with exactly 8 characters', () => {
		// given
		const password = 'a'.repeat(8);

		// when
		const result = isValidPassword(password);

		// then
		expect(result).toBe(true);
	});

	it('rejects a password with more than 128 characters', () => {
		// given
		const password = 'a'.repeat(129);

		// when
		const result = isValidPassword(password);

		// then
		expect(result).toBe(false);
	});

	it('accepts a password with exactly 128 characters', () => {
		// given
		const password = 'a'.repeat(128);

		// when
		const result = isValidPassword(password);

		// then
		expect(result).toBe(true);
	});
});
