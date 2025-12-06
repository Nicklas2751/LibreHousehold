<script lang="ts">
    import {page} from '$app/state';
    import {HomeIcon} from "@indaco/svelte-iconoir/home";
    import {ClipboardCheckIcon} from "@indaco/svelte-iconoir/clipboard-check";
    import {CashIcon} from "@indaco/svelte-iconoir/cash";
    import {StatsReportIcon} from "@indaco/svelte-iconoir/stats-report";
    import {SettingsIcon} from "@indaco/svelte-iconoir/settings";
    import {m} from '$lib/paraglide/messages.js';
    import {goto} from "$app/navigation";
    import {filterTasks, TaskFilterType} from "$lib/taskFilter";
    import {tasks} from "$lib/stores/taskStore";

    const countOfUpcomingTasks: number = $derived(filterTasks($tasks, TaskFilterType.PENDING, undefined).length);
</script>

<div class="dock md:hidden">
    <button class:dock-active={page.url.pathname === '/app/dashboard'} onclick={ async () => await goto("/app/dashboard")}>
        <HomeIcon/>
        <span class="dock-label">{m["menu.dashboard"]()}</span>
    </button>

    <button class:dock-active={page.url.pathname.startsWith('/app/tasks')} onclick={ async () => await goto("/app/tasks")}>
        <ClipboardCheckIcon/>
        <span class="dock-label">{m["menu.tasks"]()}</span>
    </button>

    <button class:dock-active={page.url.pathname === '/app/expenses'} onclick={ async () => await goto("/app/expenses")}>
        <CashIcon/>
        <span class="dock-label">{m["menu.expenses"]()}</span>
    </button>

    <button class:dock-active={page.url.pathname === '/app/statistics'} onclick={ async () => await goto("/app/statistics")}>
        <StatsReportIcon/>
        <span class="dock-label">{m["menu.statistics"]()}</span>
    </button>

    <button class:dock-active={page.url.pathname === '/app/settings'} onclick={ async () => await goto("/app/settings")}>
        <SettingsIcon/>
        <span class="dock-label">{m["menu.settings"]()}</span>
    </button>
</div>
<div class="max-sm:hidden fixed bottom-0 w-screen flex justify-around">
    <ul class="menu bg-base-200 menu-horizontal rounded-box ">
        <li>
            <a class:menu-active={page.url.pathname === '/app/dashboard'} href="/app/dashboard">
                <HomeIcon/>
                {m["menu.dashboard"]()}
            </a>
        </li>
        <li>
            <a class:menu-active={page.url.pathname.startsWith('/app/tasks')} href="/app/tasks">
                <ClipboardCheckIcon/>
                {m["menu.tasks"]()}
                <span class="badge badge-xs" class:hidden={countOfUpcomingTasks === 0}>{countOfUpcomingTasks}</span>
            </a>
        </li>
        <li>
            <a class:menu-active={page.url.pathname === '/app/expenses'} href="/app/expenses">
                <CashIcon/>
                {m["menu.expenses"]()}
            </a>
        </li>
        <li>
            <a class:menu-active={page.url.pathname === '/app/statistics'} href="/app/statistics">
                <StatsReportIcon/>
                {m["menu.statistics"]()}
            </a>
        </li>
        <li>
            <a class:menu-active={page.url.pathname === '/app/settings'} href="/app/settings">
                <SettingsIcon/>
                {m["menu.settings"]()}
            </a>
        </li>
    </ul>
</div>