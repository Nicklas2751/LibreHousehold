<script lang="ts" generics="T extends { id: string }">
	import { householdState } from '$lib/stores/householdState.svelte';
	import type { Snippet } from 'svelte';

	interface Props {
		loadItems: (householdId: string) => Promise<void>;
		items: T[];
		noItemsMessage: string;
		header?: Snippet;
		itemContent: Snippet<[T]>;
		itemActions: Snippet<[T]>;
	}

	const { loadItems, items, noItemsMessage, header, itemContent, itemActions }: Props = $props();
</script>

<div
	class="card mt-2 flex h-full flex-col overflow-hidden bg-base-200 drop-shadow-xl card-border max-md:hidden"
>
	{#if header}
		<div class="flex-column flex min-h-[53px] justify-between border-b border-b-gray-500 p-2">
			{@render header()}
		</div>
	{/if}

	<div class="card-body min-h-0 overflow-y-auto">
		{#if $householdState}
			{#await loadItems($householdState.id)}
				<div class="flex h-64 items-center justify-center">
					<span class="loading loading-dots"></span>
				</div>
			{:then}
				{#if items.length === 0}
					<p class="text-center text-base-content/50">{noItemsMessage}</p>
				{:else}
					<ul class="list rounded-box bg-base-100 shadow-md">
						{#each items as item (item.id)}
							<li class="list-row items-center">
								<div class="list-col-grow flex items-center justify-between gap-4">
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
