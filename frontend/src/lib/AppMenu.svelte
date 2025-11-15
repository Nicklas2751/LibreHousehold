<script>
    import {HomeIcon} from "@indaco/svelte-iconoir/home";
    import {ClipboardCheckIcon} from "@indaco/svelte-iconoir/clipboard-check";
    import {CashIcon} from "@indaco/svelte-iconoir/cash";
    import {StatsReportIcon} from "@indaco/svelte-iconoir/stats-report";
    import {SettingsIcon} from "@indaco/svelte-iconoir/settings";
    import {m} from '$lib/paraglide/messages.js';
</script>

<div class="dock md:hidden">
    <button class="dock-active">
        <HomeIcon/>
        <span class="dock-label">{m["menu.dashboard"]()}</span>
    </button>

    <button>
        <ClipboardCheckIcon/>
        <span class="dock-label">{m["menu.tasks"]()}</span>
    </button>

    <button>
        <CashIcon/>
        <span class="dock-label">{m["menu.expenses"]()}</span>
    </button>

    <button>
        <StatsReportIcon/>
        <span class="dock-label">{m["menu.statistics"]()}</span>
    </button>

    <button>
        <SettingsIcon/>
        <span class="dock-label">{m["menu.settings"]()}</span>
    </button>
</div>
<div class="max-sm:hidden fixed bottom-0 w-screen flex justify-around">
    <ul class="menu bg-base-200 menu-horizontal rounded-box ">
        <li>
            <a class="menu-active" href="/app/dashboard">
                <HomeIcon/>
                {m["menu.dashboard"]()}
            </a>
        </li>
        <li>
            <a href="/app/tasks">
                <ClipboardCheckIcon/>
                {m["menu.tasks"]()}
                <span class="badge badge-xs">99+</span>
            </a>
        </li>
        <li>
            <a href="/app/expenses">
                <CashIcon/>
                {m["menu.expenses"]()}
            </a>
        </li>
        <li>
            <a href="/app/statistics">
                <StatsReportIcon/>
                {m["menu.statistics"]()}
            </a>
        </li>
        <li>
            <a href="/app/settings">
                <SettingsIcon/>
                {m["menu.settings"]()}
            </a>
        </li>
    </ul>
</div>