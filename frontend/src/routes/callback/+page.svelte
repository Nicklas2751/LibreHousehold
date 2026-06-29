<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { handleCallback } from '$lib/stores/authStore.svelte';

	let error = $state<string | null>(null);

	onMount(async () => {
		try {
			await handleCallback();
			await goto('/app/dashboard');
		} catch (e) {
			error = String(e);
		}
	});
</script>

<div class="hero min-h-screen bg-base-200">
	{#if error}
		<div class="alert alert-error max-w-sm" role="alert">
			<span>{error}</span>
		</div>
	{:else}
		<span class="loading loading-spinner loading-lg"></span>
	{/if}
</div>
