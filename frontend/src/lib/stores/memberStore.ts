import { type Writable, writable } from 'svelte/store';
import { type Member, MembersApi } from '../../generated-sources/openapi';
import { apiConfiguration } from '$lib/api/httpClient';

export const members: Writable<Member[]> = writable([]);

const api = new MembersApi(apiConfiguration);

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
