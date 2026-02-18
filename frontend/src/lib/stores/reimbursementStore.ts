import {writable} from "svelte/store";
import {
    Configuration,
    type Reimbursement,
    type ReimbursementCreate,
    ReimbursementsApi,
    type ReimbursementUpdate
} from "../../generated-sources/openapi";
import {addToast} from "$lib/stores/toastStore";
import {Toast} from "$lib/toast";

export const reimbursements = writable<Reimbursement[]>([]);

const apiConfig = new Configuration({basePath: '/api'});
const api = new ReimbursementsApi(apiConfig);

export const loadReimbursements = async (householdId: string): Promise<void> => {
    const result = await api.getReimbursements({householdId});
    reimbursements.set(result);
};

export const createReimbursement = async (householdId: string, reimbursement: ReimbursementCreate): Promise<void> => {
    let savedReimbursement = await api.createReimbursement({householdId, reimbursementCreate: reimbursement});
    reimbursements.update((all) => [savedReimbursement, ...all]);
};


export const updateReimbursement = async (householdId: string, reimbursementId: string, reimbursementUpdate: ReimbursementUpdate): Promise<void> => {
    // Optimistic update: Update the store immediately
    let previousReimbursements: Reimbursement[] = [];
    reimbursements.update((all) => {
        previousReimbursements = [...all];
        return all.map((reimbursement) =>
            reimbursement.id === reimbursementId ? {...reimbursement, ...reimbursementUpdate} : reimbursement
        );
    });

    try {
        await api.updateReimbursement({householdId, reimbursementId, reimbursementUpdate});
    } catch (error) {
        // Rollback on error
        reimbursements.set(previousReimbursements);
        addToast(new Toast("Failed to update reimbursement", "error", 5000));
        console.error("Failed to update reimbursement:", error);
    }
};

