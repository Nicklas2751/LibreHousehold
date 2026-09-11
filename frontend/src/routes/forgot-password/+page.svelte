<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { requestPasswordReset } from '$lib/stores/passwordReset';

	let email = $state('');
	let submitting = $state(false);
	let submitted = $state(false);

	async function handleSubmit(event: SubmitEvent) {
		event.preventDefault();
		submitting = true;
		try {
			await requestPasswordReset(email);
		} catch {
			// Deliberately ignored: the success message below is shown regardless of the
			// outcome, so a client never learns whether the email is registered (ENUM1).
		} finally {
			submitting = false;
			submitted = true;
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
					{m['forgot_password.title']()}
				</h2>
				{#if submitted}
					<div class="mt-4 alert alert-success">
						<span>{m['forgot_password.success_message']()}</span>
					</div>
				{:else}
					<form onsubmit={handleSubmit} class="mt-4 flex flex-col gap-3">
						<fieldset class="fieldset">
							<legend class="fieldset-legend">{m['forgot_password.email_label']()} *</legend>
							<input
								type="email"
								aria-label={m['forgot_password.email_label']()}
								class="input-bordered validator input w-full"
								bind:value={email}
								required
							/>
						</fieldset>
						<button type="submit" class="btn mt-2 w-full btn-primary" disabled={submitting}>
							{#if submitting}
								<span class="loading loading-xs loading-spinner"></span>
							{/if}
							{m['forgot_password.submit_button']()}
						</button>
					</form>
				{/if}
				<a href="/login" class="mt-3 text-center text-sm link link-hover">
					{m['forgot_password.back_to_login_link']()}
				</a>
			</div>
		</div>
	</div>
</div>
