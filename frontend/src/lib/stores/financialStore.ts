import {writable} from "svelte/store";
import {Configuration, FinancialsApi, type FinancialSummary, type MemberBalance} from "../../generated-sources/openapi";

export const financialSummary = writable<FinancialSummary | null>(null);
export const memberBalances = writable<MemberBalance[]>([]);

const apiConfig = new Configuration({basePath: '/api'});
const api = new FinancialsApi(apiConfig);

export const loadFinancialSummary = async (householdId: string, userId: string): Promise<void> => {
    const result = await api.getFinancialSummary({householdId, userId});
    financialSummary.set(result);
};

export const loadMemberBalances = async (householdId: string, userId: string): Promise<void> => {
    const result = await api.getMemberBalances({householdId, userId});
    memberBalances.set(result);
};


