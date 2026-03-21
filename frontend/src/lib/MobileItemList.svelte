<script lang="ts" generics="T extends { id: string }">
    import {householdState} from "$lib/stores/householdState.svelte";
    import type {Snippet} from "svelte";

    interface Props {
        loadItems: (householdId: string) => Promise<void>;
        items: T[];
        noItemsMessage: string;
        singleItemView: Snippet<[T]>;
        singleItemActions: Snippet<[T]>;

    }

    const {
        loadItems,
        items,
        noItemsMessage,
        singleItemView,
        singleItemActions,
    }: Props = $props();

</script>

<!-- Mobile Item List -->
<div class="md:hidden pb-10">
    {#if $householdState}
        {#await loadItems($householdState.id)}
            <div class="flex justify-center items-center h-64">
                <span class="loading loading-dots"></span>
            </div>
        {:then _}
            {#if items.length === 0}
                <p class="text-base-content/50 text-center mt-10">{noItemsMessage}</p>
            {:else}
                <ul class="space-y-2 mt-4">
                    {#each items as item (item.id)}
                        <li class="bg-base-200 rounded-lg p-3 flex items-center gap-3">
                            <div class="flex-1">
                                {@render singleItemView(item)}
                            </div>
                            {@render singleItemActions(item)}
                        </li>
                    {/each}
                </ul>
            {/if}
        {/await}
    {/if}
</div>