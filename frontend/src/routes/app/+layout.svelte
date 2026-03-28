<script lang="ts">
	import AppMenu from '$lib/AppMenu.svelte';
	import { browser } from '$app/environment';
	import { initSettings } from '$lib/stores/settingsStore';

	if (browser) initSettings();

	let { children } = $props();
</script>

<div class="flex h-dvh flex-col">
	<div
		class="flex-1 overflow-x-hidden overflow-y-auto pb-[env(safe-area-inset-bottom)] text-base-content md:flex md:justify-around"
	>
		<div class="w-full md:h-full md:max-w-[1200px] md:min-w-[1200px]">
			{@render children()}
		</div>
	</div>
	<AppMenu />
</div>
