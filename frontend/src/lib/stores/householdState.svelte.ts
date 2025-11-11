import type {Household} from "$lib/household";
import {type Writable, writable} from "svelte/store";

export const householdState: Writable<Household | undefined> = writable(undefined);

export const updateHouseholdState = (household: Household) => {
    if(!household) return;
    householdState.set(household);
};