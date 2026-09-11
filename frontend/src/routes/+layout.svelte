<script lang="ts">
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';
	import Toasts from '$lib/Toasts.svelte';
	import { page } from '$app/state';
	import { browser } from '$app/environment';
	import { bootstrapSession } from '$lib/stores/sessionBootstrap';

	// Runs on every page load, including public/guest-accessible routes (login, setup, invite) —
	// an unexpected error here must not surface as a generic error toast there, since being a
	// guest is the expected default state on those pages, not a failure. Explicit bootstrapSession()
	// calls right after login/setup/join (see login/+page.svelte, SetupWizard.svelte, JoinWizard.svelte)
	// stay loud, since a failure right after a successful auth action is worth surfacing.
	if (browser) bootstrapSession({ silent: true });

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
</svelte:head>

{#key page.url.pathname}
	{@render children()}
{/key}

<Toasts />
