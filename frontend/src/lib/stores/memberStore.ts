import { type Writable, writable } from 'svelte/store';
import { Configuration, type Member, MembersApi } from '../../generated-sources/openapi';

export const members: Writable<Member[]> = writable([]);

const apiConfig = new Configuration({ basePath: '/api' });
const api = new MembersApi(apiConfig);

/**
 *
 * @param householdId
 * @returns Promise<void>
 */
export const loadMembers = async (householdId: string): Promise<boolean> => {
	members.set(await api.getMembers({ householdId: householdId }));
	return true;
};

export const findMember = async (
	householdId: string,
	memberId: string
): Promise<Member | undefined> => {
	const foundMember = await api.getMember({ householdId: householdId, memberId: memberId });
	if (foundMember) {
		return foundMember;
	}
};
