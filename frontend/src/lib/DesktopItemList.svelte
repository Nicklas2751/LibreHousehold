<script lang="ts" generics="T extends { id: string }">
    import {householdState} from "$lib/stores/householdState.svelte";
    import type {Snippet} from "svelte";

    interface Props {
        loadItems: (householdId: string) => Promise<void>;
        items: T[];
        noItemsMessage: string;
        header?: Snippet;
        itemContent: Snippet<[T]>;
        itemActions: Snippet<[T]>;
    }

    const {
        loadItems,
        items,
        noItemsMessage,
        header,
        itemContent,
        itemActions
    }: Props = $props();
</script>

<div class="max-md:hidden card card-border bg-base-200 drop-shadow-xl mt-10">
    <div class="border-b border-b-gray-500 p-2 flex justify-between flex-column min-h-[53px]">
        {#if header}
            {@render header()}
        {/if}
    </div>
    <div class="card-body">
        {#if $householdState}
            {#await loadItems($householdState.id)}
                <div class="flex justify-center items-center h-64">
                    <span class="loading loading-dots"></span>
                </div>
            {:then _}
                {#if items.length === 0}
                    <p class="text-base-content/50 text-center">{noItemsMessage}</p>
                {:else}
                    <ul class="list bg-base-100 rounded-box shadow-md">
                        {#each items as item (item.id)}
                            <li class="list-row items-center">
                                <div class="list-col-grow flex justify-between items-center gap-4">
                                    {@render itemContent(item)}
                                </div>
                                {@render itemActions(item)}
                            </li>
                        {/each}
                    </ul>
                {/if}
            {/await}
        {/if}
    </div>
</div>

