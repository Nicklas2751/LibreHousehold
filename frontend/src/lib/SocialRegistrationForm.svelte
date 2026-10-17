<script lang="ts">
	import { untrack } from 'svelte';
	import { m } from '$lib/paraglide/messages.js';
	import type { PendingSocialRegistration } from '../generated-sources/openapi';

	interface Props {
		registration: PendingSocialRegistration;
		hint: string;
		nameLabel: string;
		nameHint: string;
		submitLabel: string;
		backLabel: string;
		nameMinLength?: number;
		submitting?: boolean;
		errorMessage?: string | null;
		onformsubmit: (name: string) => void | Promise<void>;
		onback: () => void;
	}

	const {
		registration,
		hint,
		nameLabel,
		nameHint,
		submitLabel,
		backLabel,
		nameMinLength = 3,
		submitting = false,
		errorMessage = null,
		onformsubmit,
		onback
	}: Props = $props();

	// Only prefilled from the provider: the user may change the name, while the e-mail stays as reported.
	let name = $state(untrack(() => registration.name) ?? '');

	function submit(event: SubmitEvent) {
		event.preventDefault();
		onformsubmit(name);
	}
</script>

<p>{hint}</p>
{#if errorMessage}
	<div class="alert alert-error">
		<span>{errorMessage}</span>
	</div>
{/if}
<form onsubmit={submit}>
	<fieldset class="fieldset">
		<legend class="fieldset-legend">{nameLabel} *</legend>
		<input
			type="text"
			aria-label={nameLabel}
			class="input-bordered validator input w-full"
			minlength={nameMinLength}
			bind:value={name}
			required
		/>
		<p class="validator-hint">{nameHint}</p>
	</fieldset>
	<fieldset class="fieldset">
		<legend class="fieldset-legend">{m['auth.social_registration_email_label']()}</legend>
		<p class="font-medium">{registration.email}</p>
		<p class="label">{m['auth.social_registration_email_hint']()}</p>
	</fieldset>
	<div class="mt-4 flex justify-between gap-3">
		<button type="button" class="btn flex-1 btn-outline" onclick={onback}>{backLabel}</button>
		<button type="submit" class="btn flex-1 btn-primary" disabled={submitting}>
			{#if submitting}
				<span class="loading loading-xs loading-spinner"></span>
			{/if}
			{submitLabel}
		</button>
	</div>
</form>
