<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { m } from '$lib/paraglide/messages.js';
	import { confirmEmailVerification } from '$lib/stores/memberStore';
	import { bootstrapSession } from '$lib/stores/sessionBootstrap';
	import { session } from '$lib/stores/sessionState.svelte';

	let status: 'loading' | 'success' | 'error' = $state('loading');
	const isAuthenticated = $derived(session.status === 'authenticated');

	onMount(async () => {
		try {
			await confirmEmailVerification($page.params.token ?? '');
			status = 'success';
			// Refreshes session.currentUser.emailVerified in place so the verification banner
			// disappears immediately if this browser tab still holds an authenticated session
			// (e.g. the initial post-setup session) — a no-op guest probe otherwise.
			await bootstrapSession({ silent: true });
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
					{#if isAuthenticated}
						<a href="/app/dashboard" class="btn mt-4 btn-primary">
							{m['verification.confirm_page.success_button']()}
						</a>
					{:else}
						<a href="/login" class="btn mt-4 btn-primary">
							{m['verification.confirm_page.login_button']()}
						</a>
					{/if}
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
