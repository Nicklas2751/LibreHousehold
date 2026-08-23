export const HOUSEHOLD_ALREADY_EXISTS = '/problems/household-already-exists';
export const ACCOUNT_ALREADY_EXISTS = '/problems/account-already-exists';

export type ConflictProblem = 'household-exists' | 'account-exists' | 'unknown';

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
