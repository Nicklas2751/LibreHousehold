<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import SocialLoginButtons from '$lib/SocialLoginButtons.svelte';
	import { authDiscovery, loadAuthDiscovery } from '$lib/stores/authDiscoveryState.svelte';
	import { SocialLoginContextContextEnum } from '../../generated-sources/openapi';
	import PasswordField from '$lib/PasswordField.svelte';
	import { bootstrapSession } from '$lib/stores/sessionBootstrap';
	import { session } from '$lib/stores/sessionState.svelte';
	import { loginWithPassword } from '$lib/stores/sessionLogin';
	import { type LoginProblem, readLoginProblem } from '$lib/api/problemMapping';

	type LoginError =
		| LoginProblem
		| { kind: 'none' }
		| { kind: 'social-login-no-account' }
		| { kind: 'social-login-failed' };

	let email = $state('');
	let password = $state('');
	let submitting = $state(false);
	// A failed social login comes back as a full page redirect to this page, so its reason is in
	// the page URL rather than in a fetch response.
	let loginError = $state<LoginError>(classifyErrorQueryParam(page.url));

	onMount(() => {
		loadAuthDiscovery();
	});

	// Covers both an already signed-in visitor and the session bootstrapped after a successful login.
	$effect(() => {
		if (session.status === 'authenticated') {
			goto('/app/dashboard', { replaceState: true });
		}
	});

	function classifyErrorQueryParam(url: URL): LoginError {
		if (!url.searchParams.has('error')) return { kind: 'none' };
		const reason = url.searchParams.get('reason');
		if (reason === 'social-login-no-account') return { kind: 'social-login-no-account' };
		if (reason === 'social-login-failed') return { kind: 'social-login-failed' };
		return { kind: 'unexpected' };
	}

	async function handleSubmit(event: SubmitEvent) {
		event.preventDefault();
		loginError = { kind: 'none' };
		submitting = true;
		try {
			await loginWithPassword(email, password);
		} catch (err: unknown) {
			loginError = await readLoginProblem(err);
			submitting = false;
			return;
		}
		await bootstrapSession();
		submitting = false;
	}
</script>

<div class="hero min-h-screen bg-base-200">
	<div class="hero-content">
		<div class="md:card md:w-96 md:bg-base-100 md:shadow-sm">
			<div class="md:card-body">
				<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary">
					<span class="text-xl font-bold text-white">LH</span>
				</div>
				<h1 class="text-center text-2xl font-bold text-base-content">LibreHousehold</h1>
				<p class="mt-2 text-center text-base-content/70">{m['subtitle']()}</p>
				<h2 class="mt-4 text-xl font-bold text-base-content">{m['login.title']()}</h2>
				{#if loginError.kind === 'invalid-credentials'}
					<div class="mt-4 alert alert-error">
						<span>{m['login.error_invalid_credentials']()}</span>
					</div>
				{:else if loginError.kind === 'unverified'}
					<div class="mt-4 alert alert-warning">
						<span>{m['login.error_unverified']()}</span>
					</div>
				{:else if loginError.kind === 'locked'}
					<div class="mt-4 alert alert-warning">
						<span>
							{loginError.lockedUntil
								? m['login.error_locked']({ time: loginError.lockedUntil.toLocaleTimeString() })
								: m['login.error_locked_unknown_time']()}
						</span>
					</div>
				{:else if loginError.kind === 'rate-limited'}
					<div class="mt-4 alert alert-warning">
						<span>
							{loginError.retryAfterSeconds === null
								? m['login.error_rate_limited_unknown']()
								: m['login.error_rate_limited']({ seconds: loginError.retryAfterSeconds })}
						</span>
					</div>
				{:else if loginError.kind === 'local-login-disabled'}
					<div class="mt-4 alert alert-warning">
						<span>{m['login.error_local_login_disabled']()}</span>
					</div>
				{:else if loginError.kind === 'social-login-no-account'}
					<div class="mt-4 alert alert-warning">
						<span>{m['login.error_social_login_no_account']()}</span>
					</div>
				{:else if loginError.kind === 'social-login-failed'}
					<div class="mt-4 alert alert-error">
						<span>{m['login.error_social_login_failed']()}</span>
					</div>
				{:else if loginError.kind === 'network'}
					<div class="mt-4 alert alert-error">
						<span>{m['login.error_network']()}</span>
					</div>
				{:else if loginError.kind === 'unexpected'}
					<div class="mt-4 alert alert-error">
						<span>{m['login.error_unexpected']()}</span>
					</div>
				{/if}
				{#if authDiscovery.localEnabled}
					<form onsubmit={handleSubmit} class="mt-4 flex flex-col gap-3">
						<fieldset class="fieldset">
							<legend class="fieldset-legend">{m['login.email_label']()} *</legend>
							<input
								type="email"
								aria-label={m['login.email_label']()}
								class="input-bordered validator input w-full"
								placeholder={m['login.email_placeholder']()}
								bind:value={email}
								required
							/>
						</fieldset>
						<PasswordField
							label={m['login.password_label']()}
							hint=""
							placeholder={m['login.password_placeholder']()}
							autocomplete="current-password"
							bind:value={password}
						/>
						<button type="submit" class="btn mt-2 w-full btn-primary" disabled={submitting}>
							{#if submitting}
								<span class="loading loading-xs loading-spinner"></span>
							{/if}
							{m['login.submit_button']()}
						</button>
					</form>
					<a href="/forgot-password" class="mt-3 text-center text-sm link link-hover">
						{m['login.forgot_password_link']()}
					</a>
				{/if}
				<SocialLoginButtons socialLoginContext={{ context: SocialLoginContextContextEnum.Login }} />
				<a class="btn mt-4 w-full btn-outline" href="/">{m['login.back_button']()}</a>
			</div>
		</div>
	</div>
</div>
