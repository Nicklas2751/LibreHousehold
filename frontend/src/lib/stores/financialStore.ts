import { writable } from 'svelte/store';
import {
	FinancialsApi,
	type FinancialSummary,
	type MemberBalance
} from '../../generated-sources/openapi';
import { apiConfiguration } from '$lib/api/httpClient';

export const financialSummary = writable<FinancialSummary | null>(null);
export const memberBalances = writable<MemberBalance[]>([]);

const api = new FinancialsApi(apiConfiguration);

export const loadFinancialSummary = async (householdId: string, userId: string): Promise<void> => {
	const result = await api.getFinancialSummary({ householdId, userId });
	financialSummary.set(result);
};

export const loadMemberBalances = async (householdId: string, userId: string): Promise<void> => {
	const result = await api.getMemberBalances({ householdId, userId });
	memberBalances.set(result);
};
