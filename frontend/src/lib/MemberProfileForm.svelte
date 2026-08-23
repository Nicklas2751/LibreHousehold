<script lang="ts">
	import { readFileAsDataURL } from '$lib/setupWizardLogic';
	import { isValidPassword } from '$lib/passwordValidation';
	import PasswordField from '$lib/PasswordField.svelte';

	interface Props {
		contextHint: string;
		nameLabel: string;
		nameHint: string;
		namePlaceholder?: string;
		emailLabel: string;
		emailHint: string;
		emailPlaceholder?: string;
		backLabel: string;
		submitLabel: string;
		nameMinLength?: number;
		serverEmailError?: string | null;
		submitting?: boolean;
		passwordLabel?: string;
		passwordHint?: string;
		passwordPlaceholder?: string;
		onformsubmit: (data: {
			name: string;
			email: string;
			avatar: string;
			password?: string;
		}) => void | Promise<void>;
		onback: () => void;
		onClearEmailError?: () => void;
		onEmailInput?: (email: string) => void;
	}

	let {
		contextHint,
		nameLabel,
		nameHint,
		namePlaceholder,
		emailLabel,
		emailHint,
		emailPlaceholder,
		backLabel,
		submitLabel,
		nameMinLength = 3,
		serverEmailError = null,
		submitting = false,
		passwordLabel,
		passwordHint,
		passwordPlaceholder,
		onformsubmit,
		onback,
		onClearEmailError,
		onEmailInput
	}: Props = $props();

	let name = $state('');
	let email = $state('');
	let avatar = $state('');
	let password = $state('');
	let emailInput: HTMLInputElement | undefined = $state();

	// Defense-in-depth in addition to PasswordField's native minlength/maxlength validation.
	let passwordValid = $derived(!passwordLabel || isValidPassword(password));

	$effect(() => {
		if (serverEmailError) {
			emailInput?.setCustomValidity(serverEmailError);
		} else {
			emailInput?.setCustomValidity('');
		}
	});

	async function handleAvatarChange(event: Event) {
		const target = event.target as HTMLInputElement;
		const files = target.files;
		if (files && files.length > 0) {
			try {
				avatar = await readFileAsDataURL(files[0]);
			} catch {
				console.error('Failed to read avatar file');
			}
		}
	}

	function submit(event: SubmitEvent) {
		event.preventDefault();
		onformsubmit({ name, email, avatar, password: passwordLabel ? password : undefined });
	}
</script>

<label
	class="m-3 flex h-20 w-20 cursor-pointer items-center justify-center place-self-center rounded-full bg-neutral-content text-center"
>
	{#if avatar}
		<img src={avatar} alt={name} class="h-full w-full rounded-full object-cover" />
	{:else}
		<span class="text-3xl">👤</span>
	{/if}
	<input type="file" accept="image/*" class="hidden" onchange={handleAvatarChange} />
</label>
<p>{contextHint}</p>
<form onsubmit={submit}>
	<fieldset class="fieldset">
		<legend class="fieldset-legend">{nameLabel} *</legend>
		<input
			type="text"
			aria-label={nameLabel}
			class="input-bordered validator input w-full"
			minlength={nameMinLength}
			placeholder={namePlaceholder}
			bind:value={name}
			required
		/>
		<p class="validator-hint">{nameHint}</p>
	</fieldset>
	<fieldset class="fieldset">
		<legend class="fieldset-legend">{emailLabel} *</legend>
		<input
			type="email"
			aria-label={emailLabel}
			class="input-bordered validator input w-full"
			placeholder={emailPlaceholder}
			bind:this={emailInput}
			bind:value={email}
			oninput={() => {
				emailInput?.setCustomValidity('');
				onClearEmailError?.();
				onEmailInput?.(email);
			}}
			required
		/>
		<p class="validator-hint">{serverEmailError ?? emailHint}</p>
	</fieldset>
	{#if passwordLabel}
		<PasswordField
			label={passwordLabel}
			hint={passwordHint ?? ''}
			placeholder={passwordPlaceholder}
			bind:value={password}
		/>
	{/if}
	<div class="mt-4 flex justify-between gap-3">
		<button type="button" class="btn flex-1 btn-outline" onclick={onback}>{backLabel}</button>
		<button type="submit" class="btn flex-1 btn-primary" disabled={submitting || !passwordValid}>
			{#if submitting}
				<span class="loading loading-xs loading-spinner"></span>
			{/if}
			{submitLabel}
		</button>
	</div>
</form>
