<script lang="ts">
	import { readFileAsDataURL } from '$lib/setupWizardLogic';

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
		requirePassword?: boolean;
		passwordLabel?: string;
		passwordPlaceholder?: string;
		passwordConfirmLabel?: string;
		passwordMismatchError?: string;
		name?: string;
		avatar?: string;
		onformsubmit: (data: {
			name: string;
			email: string;
			avatar: string;
			password?: string;
		}) => void | Promise<void>;
		onback: () => void;
		onClearEmailError?: () => void;
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
		requirePassword = false,
		passwordLabel,
		passwordPlaceholder,
		passwordConfirmLabel,
		passwordMismatchError,
		name = $bindable(''),
		avatar = $bindable(''),
		onformsubmit,
		onback,
		onClearEmailError
	}: Props = $props();

	let email = $state('');
	let password = $state('');
	let passwordConfirm = $state('');
	let passwordMismatch = $state(false);
	let emailInput: HTMLInputElement | undefined = $state();

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
		if (requirePassword) {
			if (password !== passwordConfirm) {
				passwordMismatch = true;
				return;
			}
			passwordMismatch = false;
		}
		onformsubmit({ name, email, avatar, password: requirePassword ? password : undefined });
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
			}}
			required
		/>
		<p class="validator-hint">{serverEmailError ?? emailHint}</p>
	</fieldset>
	{#if requirePassword}
		<fieldset class="fieldset">
			<legend class="fieldset-legend">{passwordLabel} *</legend>
			<input
				type="password"
				aria-label={passwordLabel}
				class="input-bordered validator input w-full"
				minlength="8"
				placeholder={passwordPlaceholder}
				bind:value={password}
				autocomplete="new-password"
				required
			/>
		</fieldset>
		<fieldset class="fieldset">
			<legend class="fieldset-legend">{passwordConfirmLabel} *</legend>
			<input
				type="password"
				aria-label={passwordConfirmLabel}
				class="input-bordered validator input w-full"
				minlength="8"
				placeholder={passwordPlaceholder}
				bind:value={passwordConfirm}
				autocomplete="new-password"
				required
			/>
			{#if passwordMismatch}
				<div class="label">
					<span class="label-text-alt text-error">{passwordMismatchError}</span>
				</div>
			{/if}
		</fieldset>
	{/if}
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
