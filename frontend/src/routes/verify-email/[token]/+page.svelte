<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { m } from '$lib/paraglide/messages.js';
	import { confirmEmailVerification } from '$lib/stores/memberStore';

	let status: 'loading' | 'success' | 'error' = $state('loading');

	onMount(async () => {
		try {
			await confirmEmailVerification($page.params.token ?? '');
			status = 'success';
		} catch {
			status = 'error';
		}
	});
</script>

<div class="hero min-h-screen bg-base-200">
	<div class="hero-content">
		<div class="md:card md:w-96 md:bg-base-100 md:shadow-sm">
			<div class="md:card-body text-center">
				{#if status === 'loading'}
					<span class="loading loading-lg loading-spinner mx-auto"></span>
					<p class="mt-4">{m['verification.confirm_page.loading']()}</p>
				{:else if status === 'success'}
					<h1 class="text-xl font-bold text-base-content">
						{m['verification.confirm_page.success_title']()}
					</h1>
					<p class="mt-2 text-base-content/70">{m['verification.confirm_page.success_text']()}</p>
					<a href="/app/dashboard" class="btn mt-4 btn-primary">
						{m['verification.confirm_page.success_button']()}
					</a>
				{:else}
					<h1 class="text-xl font-bold text-error">
						{m['verification.confirm_page.error_title']()}
					</h1>
					<p class="mt-2 text-base-content/70">{m['verification.confirm_page.error_text']()}</p>
				{/if}
			</div>
		</div>
	</div>
</div>
