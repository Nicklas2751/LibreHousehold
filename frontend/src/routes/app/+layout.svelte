<script lang="ts">
	import AppMenu from '$lib/AppMenu.svelte';
	import { browser } from '$app/environment';
	import { initSettings } from '$lib/stores/settingsStore';
	import { session } from '$lib/stores/sessionState.svelte';
	import { shouldRedirectToLogin } from '$lib/stores/sessionGuard';
	import { redirectToOAuth2Login } from '$lib/oauth2Login';

	if (browser) initSettings();

	$effect(() => {
		if (shouldRedirectToLogin(session.status)) {
			redirectToOAuth2Login();
		}
	});

	let { children } = $props();
</script>

<div class="flex h-dvh flex-col">
	<div
		class="flex-1 overflow-x-hidden overflow-y-auto pb-[env(safe-area-inset-bottom)] text-base-content md:flex md:justify-around"
	>
		<div class="w-full md:h-full md:max-w-[1200px] md:min-w-[1200px]">
			{#if session.status === 'authenticated'}
				{@render children()}
			{:else if session.status === 'bootstrapping'}
				<div class="flex h-full items-center justify-center">
					<span class="loading loading-lg loading-spinner"></span>
				</div>
			{/if}
		</div>
	</div>
	<AppMenu />
</div>
