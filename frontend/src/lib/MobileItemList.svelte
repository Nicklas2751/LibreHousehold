<script lang="ts" generics="T extends { id: string }">
	import { householdState } from '$lib/stores/householdState.svelte';
	import type { Snippet } from 'svelte';

	interface Props {
		loadItems: (householdId: string) => Promise<void>;
		items: T[];
		noItemsMessage: string;
		singleItemView: Snippet<[T]>;
		singleItemActions: Snippet<[T]>;
	}

	const { loadItems, items, noItemsMessage, singleItemView, singleItemActions }: Props = $props();
</script>

<!-- Mobile Item List -->
<div class="pb-10 md:hidden">
	{#if $householdState}
		{#await loadItems($householdState.id)}
			<div class="flex h-64 items-center justify-center">
				<span class="loading loading-dots"></span>
			</div>
		{:then}
			{#if items.length === 0}
				<p class="mt-10 text-center text-base-content/50">{noItemsMessage}</p>
			{:else}
				<ul class="mt-4 space-y-2">
					{#each items as item (item.id)}
						<li class="flex items-center gap-3 rounded-lg bg-base-200 p-3">
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
