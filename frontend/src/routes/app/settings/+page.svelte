<script lang="ts">
    import { UserCircleIcon } from '@indaco/svelte-iconoir/user-circle';
    import { HouseRoomsIcon } from '@indaco/svelte-iconoir/house-rooms';
    import { HalfMoonIcon } from '@indaco/svelte-iconoir/half-moon';
    import { BrightnessIcon } from '@indaco/svelte-iconoir/brightness';
    import { LanguageIcon } from '@indaco/svelte-iconoir/language';
    import { NavArrowRightIcon } from '@indaco/svelte-iconoir/nav-arrow-right';
    import { m } from '$lib/paraglide/messages.js';
    import { locales } from '$lib/paraglide/runtime.js';
    import { householdState } from '$lib/stores/householdState.svelte';
    import { userState } from '$lib/stores/userState';
    import { theme, language, setTheme, setLanguage } from '$lib/stores/settingsStore';
    import type { Language } from '$lib/stores/settingsStore';
    import PageTitle from '$lib/PageTitle.svelte';

    const isOwner = $derived($householdState?.admin === $userState?.id);

    function toggleTheme() {
        setTheme($theme === 'dark' ? 'light' : 'dark');
    }

    function changeLocale(event: Event) {
        setLanguage((event.target as HTMLSelectElement).value as Language);
    }
</script>

<div class="h-full overflow-y-auto p-4">
    <PageTitle title={m['menu.settings']()} />

    <!-- Appearance -->
    <div class="card bg-base-200 mt-5 shadow-sm">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base flex items-center gap-2 mb-1">
                {m['settings.appearance.title']()}
            </h3>
            <div class="flex items-center justify-between">
                <span class="text-sm text-base-content/70">{m['settings.appearance.dark_mode_label']()}</span>
                <label class="swap swap-rotate text-primary cursor-pointer" aria-label={m['settings.appearance.dark_mode_label']()}>
                    <input
                        type="checkbox"
                        checked={$theme === 'dark'}
                        onchange={toggleTheme}
                    />
                    <!-- sun: shown when NOT dark -->
                    <BrightnessIcon class="swap-off w-6 h-6" />
                    <!-- moon: shown when dark -->
                    <HalfMoonIcon class="swap-on w-6 h-6" />
            </div>
        </div>
    </div>

    <!-- Language -->
    <div class="card bg-base-200 mt-3 shadow-sm">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base flex items-center gap-2">
                <LanguageIcon class="text-primary" />
                {m['settings.language.title']()}
            </h3>
            <div class="mt-1">
                <select
                    class="select select-sm w-full"
                    value={$language}
                    onchange={changeLocale}
                    aria-label={m['settings.language.select_label']()}
                >
                    {#each locales as locale}
                        <option value={locale}>{locale === 'de' ? 'Deutsch' : 'English'}</option>
                    {/each}
                </select>
            </div>
        </div>
    </div>

    <!-- Nav cards -->
    <div class="mt-5 flex flex-col gap-3">
        <a href="/app/settings/user" class="card bg-base-200 shadow-sm hover:bg-base-300 transition-colors">
            <div class="card-body p-4 flex flex-row items-center gap-4">
                <div class="flex h-11 w-11 min-w-11 items-center justify-center rounded-full bg-primary/15">
                    <UserCircleIcon class="text-primary" />
                </div>
                <div class="flex-1">
                    <h3 class="font-semibold">{m['settings.user.title']()}</h3>
                    <p class="text-sm text-base-content/60">{m['settings.user.description']()}</p>
                </div>
                <NavArrowRightIcon class="text-base-content/40" />
            </div>
        </a>

        {#if isOwner}
            <a href="/app/settings/household" class="card bg-base-200 shadow-sm hover:bg-base-300 transition-colors">
                <div class="card-body p-4 flex flex-row items-center gap-4">
                    <div class="flex h-11 w-11 min-w-11 items-center justify-center rounded-full bg-secondary/15">
                        <HouseRoomsIcon class="text-secondary" />
                    </div>
                    <div class="flex-1">
                        <h3 class="font-semibold">{m['settings.household.title']()}</h3>
                        <p class="text-sm text-base-content/60">{m['settings.household.description']()}</p>
                    </div>
                    <NavArrowRightIcon class="text-base-content/40" />
                </div>
            </a>
        {/if}
    </div>
</div>
