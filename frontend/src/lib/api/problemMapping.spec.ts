import { describe, expect, it } from 'vitest';
import {
	ACCOUNT_ALREADY_EXISTS,
	classifyConflictProblem,
	HOUSEHOLD_ALREADY_EXISTS
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
