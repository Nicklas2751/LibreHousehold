<script lang="ts">
    import { page } from '$app/state';
    import { NavArrowLeftIcon } from '@indaco/svelte-iconoir/nav-arrow-left';
    import { m } from '$lib/paraglide/messages.js';

    let { children } = $props();

    const isSubPage = $derived(page.url.pathname !== '/app/settings');
    const subPageTitle = $derived.by(() => {
        if (page.url.pathname.startsWith('/app/settings/user')) return m['settings.user.title']();
        if (page.url.pathname.startsWith('/app/settings/household')) return m['settings.household.title']();
        return '';
    });
</script>

{#if isSubPage}
    <div class="flex items-center gap-2 mt-4 ml-2 mr-4">
        <a href="/app/settings" class="btn btn-ghost btn-sm btn-circle" aria-label={m['settings.back']()}>
            <NavArrowLeftIcon />
        </a>
        <h2 class="text-xl font-bold">{subPageTitle}</h2>
    </div>
{/if}

{@render children()}
