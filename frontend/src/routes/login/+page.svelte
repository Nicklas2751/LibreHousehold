<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { goto } from '$app/navigation';
	import PasswordField from '$lib/PasswordField.svelte';
	import { getCsrfTokenFromCookieHeader } from '$lib/api/csrf';
	import { bootstrapSession } from '$lib/stores/sessionBootstrap';

	type LoginErrorType = 'none' | 'unverified' | 'generic';

	let email = $state('');
	let password = $state('');
	let submitting = $state(false);
	let loginErrorType = $state<LoginErrorType>('none');

	function classifyErrorQueryParam(url: string): LoginErrorType {
		const params = new URL(url).searchParams;
		if (!params.has('error')) return 'none';
		return params.get('reason') === 'unverified' ? 'unverified' : 'generic';
	}

	async function handleSubmit(event: SubmitEvent) {
		event.preventDefault();
		loginErrorType = 'none';
		submitting = true;
		try {
			const csrfToken = getCsrfTokenFromCookieHeader(document.cookie);
			const response = await fetch('/login', {
				method: 'POST',
				credentials: 'include',
				headers: {
					'Content-Type': 'application/x-www-form-urlencoded',
					...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {})
				},
				body: new URLSearchParams({ username: email, password })
			});
			const errorType = classifyErrorQueryParam(response.url);
			if (errorType !== 'none') {
				loginErrorType = errorType;
				return;
			}
			await bootstrapSession();
			await goto('/app/dashboard');
		} catch {
			loginErrorType = 'generic';
		} finally {
			submitting = false;
		}
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
				{#if loginErrorType === 'unverified'}
					<div class="mt-4 alert alert-warning">
						<span>{m['login.error_unverified']()}</span>
					</div>
				{:else if loginErrorType === 'generic'}
					<div class="mt-4 alert alert-error">
						<span>{m['login.error']()}</span>
					</div>
				{/if}
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
			</div>
		</div>
	</div>
</div>
