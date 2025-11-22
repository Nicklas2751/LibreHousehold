import {type Writable, writable} from "svelte/store";
import {Configuration, type Member, MembersApi} from "../../generated-sources/openapi";

export const members: Writable<Member[]> = writable([]);

const apiConfig = new Configuration({basePath: '/api'});
const api = new MembersApi(apiConfig);

export const addMember = async (householdId: string, member: Member): Promise<Member> => {
    let savedMember = await api.createMember({householdId: householdId,member: member});
    members.update((all) => [savedMember, ...all]);
    return savedMember;
};

/**
 *
 * @param householdId
 * @returns Promise<void>
 */
export const loadMembers = async (householdId: string): Promise<boolean> => {
    members.set(await api.getMembers({householdId: householdId}));
    return true;
}