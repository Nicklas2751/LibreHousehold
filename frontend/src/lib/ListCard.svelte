<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';

    interface DisplayItem {
        id: string;
        title: string;
        subtitle: string;
        isHighlighted?: boolean;
    }

    interface Props {
        title: string;
        colSpan?: string;
        items?: any[];
        emptyMessage: string;
        viewAllHref: string;
        buttonLabel: string;
        buttonOnClick?: () => void;
        itemMapper?: (item: any) => DisplayItem;
    }

    const {
        title,
        colSpan = 'md:col-span-3',
        items = [],
        emptyMessage,
        viewAllHref,
        buttonLabel,
        buttonOnClick,
        itemMapper,
    }: Props = $props();
</script>

<div class={`card card-border bg-base-200 drop-shadow-xl ${colSpan}`}>
    <div class="border-b-1 border-b-gray-500 p-2 flex justify-between flex-column">
        <h2 class="card-title">{title}</h2>
        <a href={viewAllHref} class="text-primary">{m["dashboard.view_all"]()}</a>
    </div>
    <div class="card-body">
        {#if items.length === 0}
            <p class="text-base-content/50 text-center">{emptyMessage}</p>
            <div class="card-actions justify-center">
                <button class="btn btn-sm btn-primary" onclick={buttonOnClick}>
                    {buttonLabel}
                </button>
            </div>
        {:else}
            <div class="space-y-2">
                {#each items as item (item.id)}
                    {@const displayItem = itemMapper ? itemMapper(item) : item}
                    <div class="flex justify-between items-center gap-4 p-2 bg-base-100 rounded-lg"
                         class:text-secondary={displayItem.isHighlighted}>
                        <span class="font-medium truncate flex-shrink">{displayItem.title}</span>
                        <span class="text-sm whitespace-nowrap flex-shrink-0">{displayItem.subtitle}</span>
                    </div>
                {/each}
            </div>
        {/if}
    </div>
</div>

