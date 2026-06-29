<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { login } from '$lib/stores/authStore.svelte';
	import { AuthApi, Configuration } from '../../generated-sources/openapi';
	import { m } from '$lib/paraglide/messages.js';
	import type { AuthProviders } from '../../generated-sources/openapi';

	let providers = $state<AuthProviders | null>(null);
	let submitting = $state(false);
	let email = $state('');
	let password = $state('');

	const errorCode = $derived(page.url.searchParams.get('error'));

	function getXsrfToken(): string {
		return (
			document.cookie
				.split('; ')
				.find((c) => c.startsWith('XSRF-TOKEN='))
				?.split('=')[1] ?? ''
		);
	}

	async function handleSubmit(event: SubmitEvent) {
		event.preventDefault();
		submitting = true;
		try {
			await fetch('/api/login', {
				method: 'POST',
				headers: {
					'Content-Type': 'application/x-www-form-urlencoded',
					'X-XSRF-TOKEN': getXsrfToken()
				},
				body: new URLSearchParams({ username: email, password }),
				credentials: 'include',
				redirect: 'follow'
			});
		} finally {
			submitting = false;
		}
	}

	onMount(async () => {
		if (!sessionStorage.getItem('lh_pkce_started')) {
			sessionStorage.setItem('lh_pkce_started', '1');
			await login();
			return;
		}
		const api = new AuthApi(new Configuration({ basePath: '/api' }));
		providers = await api.getAuthProviders();
	});
</script>

<div class="hero min-h-screen bg-base-200">
	<div class="hero-content">
		<div class="md:card md:w-96 md:bg-base-100 md:shadow-sm">
			<div class="md:card-body">
				<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary">
					<span class="text-xl font-bold text-white">LH</span>
				</div>
				<h1 class="text-center text-2xl font-bold text-base-content">{m['login.title']()}</h1>
				<p class="mt-2 text-center text-base-content/70">{m['login.subtitle']()}</p>

				{#if errorCode}
					<div class="alert alert-error" role="alert">
						<span>
							{#if errorCode === 'bad_credentials'}
								{m['login.error_bad_credentials']()}
							{:else if errorCode === 'account_locked'}
								{m['login.error_account_locked']()}
							{:else if errorCode === 'email_already_registered'}
								{m['login.error_email_already_registered']()}
							{:else}
								{m['login.error_generic']()}
							{/if}
						</span>
					</div>
				{/if}

				{#if providers !== null}
					{#if !providers.local && providers.socialProviders.length === 0}
						<div class="alert alert-warning" role="alert">
							<span>{m['login.no_providers']()}</span>
						</div>
					{:else}
						{#if providers.local}
							<form onsubmit={handleSubmit} class="flex flex-col gap-4" novalidate>
								<div class="flex flex-col gap-1">
									<label for="email" class="label label-text text-sm font-medium">
										{m['login.email_label']()}
									</label>
									<input
										id="email"
										type="email"
										name="username"
										class="input input-bordered w-full"
										placeholder={m['login.email_placeholder']()}
										bind:value={email}
										autocomplete="email"
										required
										aria-label={m['login.email_label']()}
									/>
								</div>
								<div class="flex flex-col gap-1">
									<label for="password" class="label label-text text-sm font-medium">
										{m['login.password_label']()}
									</label>
									<input
										id="password"
										type="password"
										name="password"
										class="input input-bordered w-full"
										placeholder={m['login.password_placeholder']()}
										bind:value={password}
										autocomplete="current-password"
										required
										aria-label={m['login.password_label']()}
									/>
								</div>
								<button type="submit" class="btn btn-primary w-full" disabled={submitting}>
									{m['login.submit_button']()}
								</button>
							</form>
						{/if}

						{#if providers.local && providers.socialProviders.length > 0}
							<div class="divider">{m['login.divider_text']()}</div>
						{/if}

						{#if providers.socialProviders.length > 0}
							<div class="flex flex-col gap-2">
								{#each providers.socialProviders as provider (provider)}
									<a
										href="/oauth2/authorization/{provider}"
										class="btn btn-neutral w-full"
										aria-label={m['login.social_button']({
											provider: provider.charAt(0).toUpperCase() + provider.slice(1)
										})}
									>
										{m['login.social_button']({
											provider: provider.charAt(0).toUpperCase() + provider.slice(1)
										})}
									</a>
								{/each}
							</div>
						{/if}
					{/if}
				{:else}
					<div class="flex justify-center py-4">
						<span class="loading loading-spinner loading-md"></span>
					</div>
				{/if}
			</div>
		</div>
	</div>
</div>
