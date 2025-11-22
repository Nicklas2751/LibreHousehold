<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';

    interface Props {
        title: string;
        colSpan?: string;
        items?: any[];
        emptyMessage: string;
        viewAllHref: string;
        buttonLabel: string;
        buttonOnClick?: () => void;
    }

    const {
        title,
        colSpan = 'md:col-span-3',
        items = [],
        emptyMessage,
        viewAllHref,
        buttonLabel,
        buttonOnClick,
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
                    <div>
                        <p>{item}</p>
                    </div>
                {/each}
            </div>
        {/if}
    </div>
</div>

