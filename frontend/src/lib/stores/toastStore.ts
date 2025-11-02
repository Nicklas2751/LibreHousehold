import {type Writable, writable} from "svelte/store";
import type {Toast} from "$lib/toast";

export const toasts: Writable<Toast[]> = writable([]);

export const addToast = (toast: Toast) => {
    toasts.update((all) => [toast, ...all]);

    setTimeout(
        () =>   dismissToast(toast.id),
        toast.timeout
    );
};

export const dismissToast = (id: number) => {
    toasts.update((all) => all.filter((t) => t.id !== id));
};