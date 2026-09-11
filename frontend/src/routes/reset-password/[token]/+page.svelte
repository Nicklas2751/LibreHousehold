<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { m } from '$lib/paraglide/messages.js';
	import PasswordField from '$lib/PasswordField.svelte';
	import { confirmPasswordReset } from '$lib/stores/passwordReset';
	import { extractErrorStatus } from '$lib/api/errorStatus';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';

	const HTTP_STATUS_CONFLICT = 409;

	let newPassword = $state('');
	let submitting = $state(false);
	let tokenInvalid = $state(false);

	async function handleSubmit(event: SubmitEvent) {
		event.preventDefault();
		tokenInvalid = false;
		submitting = true;
		try {
			await confirmPasswordReset($page.params.token ?? '', newPassword);
			addToast(new Toast(m['reset_password.success_toast'](), 'success'));
			await goto('/login');
		} catch (err: unknown) {
			if (extractErrorStatus(err) === HTTP_STATUS_CONFLICT) {
				tokenInvalid = true;
			} else {
				addToast(new Toast(m['reset_password.error_toast'](), 'error'));
			}
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
				<h2 class="mt-4 text-xl font-bold text-base-content">
					{m['reset_password.title']()}
				</h2>
				{#if tokenInvalid}
					<div class="mt-4 alert alert-error">
						<span>{m['reset_password.invalid_token_error']()}</span>
					</div>
					<a href="/forgot-password" class="mt-3 text-center text-sm link link-hover">
						{m['reset_password.back_to_forgot_password_link']()}
					</a>
				{:else}
					<form onsubmit={handleSubmit} class="mt-4 flex flex-col gap-3">
						<PasswordField
							label={m['reset_password.password_label']()}
							hint=""
							autocomplete="new-password"
							bind:value={newPassword}
						/>
						<button type="submit" class="btn mt-2 w-full btn-primary" disabled={submitting}>
							{#if submitting}
								<span class="loading loading-xs loading-spinner"></span>
							{/if}
							{m['reset_password.submit_button']()}
						</button>
					</form>
				{/if}
			</div>
		</div>
	</div>
</div>
