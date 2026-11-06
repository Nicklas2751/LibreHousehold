<script lang="ts">
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';
	import Toasts from '$lib/Toasts.svelte';
	import { page } from '$app/state';
	import { browser } from '$app/environment';
	import { bootstrapSession } from '$lib/stores/sessionBootstrap';

	// Runs on every page load, including public pages. A 401 just means guest and stays silent; any
	// other failure leaves the session status untouched and must be reported, otherwise a signed-in
	// user would wait on the app's loading indicator without any hint.
	if (browser) bootstrapSession();

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
