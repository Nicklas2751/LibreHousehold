<script lang="ts" generics="T extends { id: string }">
	import { m } from '$lib/paraglide/messages.js';

	interface DisplayItem {
		id: string;
		title: string;
		subtitle: string;
		isHighlighted?: boolean;
	}

	interface Props {
		title: string;
		colSpan?: string;
		items?: T[];
		emptyMessage: string;
		viewAllHref: string;
		buttonLabel: string;
		buttonOnClick?: () => void;
		itemMapper?: (item: T) => DisplayItem;
	}

	const {
		title,
		colSpan = 'md:col-span-3',
		items = [],
		emptyMessage,
		viewAllHref,
		buttonLabel,
		buttonOnClick,
		itemMapper
	}: Props = $props();

	function toDisplayItem(item: T): DisplayItem {
		return itemMapper ? itemMapper(item) : (item as unknown as DisplayItem);
	}
</script>

<div class={`card bg-base-200 drop-shadow-xl card-border ${colSpan}`}>
	<div class="flex-column flex justify-between border-b-1 border-b-gray-500 p-2">
		<h2 class="card-title">{title}</h2>
		<a href={viewAllHref} class="text-primary">{m['dashboard.view_all']()}</a>
	</div>
	<div class="card-body">
		{#if items.length === 0}
			<p class="text-center text-base-content/50">{emptyMessage}</p>
			<div class="card-actions justify-center">
				<button class="btn btn-sm btn-primary" onclick={buttonOnClick}>
					{buttonLabel}
				</button>
			</div>
		{:else}
			<div class="space-y-2">
				{#each items as item (item.id)}
					{@const displayItem = toDisplayItem(item)}
					<div
						class="flex items-center justify-between gap-4 rounded-lg bg-base-100 p-2"
						class:text-secondary={displayItem.isHighlighted}
					>
						<span class="flex-shrink truncate font-medium">{displayItem.title}</span>
						<span class="flex-shrink-0 text-sm whitespace-nowrap">{displayItem.subtitle}</span>
					</div>
				{/each}
			</div>
		{/if}
	</div>
</div>
