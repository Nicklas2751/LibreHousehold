import {type Writable, writable} from "svelte/store";
import type {Household} from "../../generated-sources/openapi";

export const householdState: Writable<Household | undefined> = writable(undefined);

export const updateHouseholdState = (household: Household) => {
    if(!household) return;
    householdState.set(household);
};