import {writable, type Writable} from "svelte/store";
import type {Member} from "../../generated-sources/openapi";

export const userState: Writable<Member | undefined> = writable(undefined);

export const updateUserState = (member: Member) => {
    if(!member) return;
    userState.set(member);
};