<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { goto } from '$app/navigation';
	import PasswordField from '$lib/PasswordField.svelte';
	import { getCsrfTokenFromCookieHeader } from '$lib/api/csrf';
	import { bootstrapSession } from '$lib/stores/sessionBootstrap';

	let email = $state('');
	let password = $state('');
	let submitting = $state(false);
	let loginError = $state(false);

	function hasErrorQueryParam(url: string): boolean {
		return new URL(url).searchParams.has('error');
	}

	async function handleSubmit(event: SubmitEvent) {
		event.preventDefault();
		loginError = false;
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
			if (hasErrorQueryParam(response.url)) {
				loginError = true;
				return;
			}
			await bootstrapSession();
			await goto('/app/dashboard');
		} catch {
			loginError = true;
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
				{#if loginError}
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
			</div>
		</div>
	</div>
</div>
