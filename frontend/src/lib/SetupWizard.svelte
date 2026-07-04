<script lang="ts">
	import { onMount } from 'svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { CopyIcon } from '@indaco/svelte-iconoir/copy';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import { QRCode } from '@castlenine/svelte-qrcode';
	import { ShareIosIcon } from '@indaco/svelte-iconoir/share-ios';
	import {
		type Household,
		type HouseholdSetup,
		HouseholdApi,
		AuthApi,
		type Member,
		ResponseError,
		type AuthProviders
	} from '../generated-sources/openapi';
	import { createApiConfig } from '$lib/api';
	import { v4 as uuidv4 } from 'uuid';
	import { updateHouseholdState } from '$lib/stores/householdState.svelte';
	import {
		calculateNextStep,
		calculateTargetStep,
		checkCanBrowserShareInviteLink,
		createInviteLinkShareData,
		generateHouseholdNameInitials,
		generateInviteUrl,
		readFileAsDataURL
	} from '$lib/setupWizardLogic';
	import { login } from '$lib/stores/authStore.svelte';
	import { updateUserState } from '$lib/stores/userState';

	const householdId: string = uuidv4();
	let inviteUrl: string = $state('');
	const maxSteps: number = 3;
	let step: number = $state(0);

	let householdName: string = $state('');
	let householdImage: string = $state('');

	let memberName: string = $state('');
	let email: string = $state('');
	let password: string = $state('');
	let passwordConfirm: string = $state('');
	let passwordMismatch: boolean = $state(false);
	let serverEmailError: string | null = $state(null);
	let submitting: boolean = $state(false);

	let providers = $state<AuthProviders | null>(null);

	onMount(async () => {
		const api = new AuthApi(createApiConfig());
		providers = await api.getAuthProviders();
	});

	function nextStep() {
		step = calculateNextStep(step, maxSteps);
	}

	function goBackToStep(targetStep: number) {
		step = calculateTargetStep(step, targetStep, maxSteps);
	}

	function houseHoldNameInitials(): string {
		return generateHouseholdNameInitials(householdName);
	}

	async function handleImageChange(event: Event) {
		const target = event.target as HTMLInputElement;
		const files = target.files;
		if (files && files.length > 0) {
			try {
				householdImage = await readFileAsDataURL(files[0]);
			} catch (error) {
				console.error('Error reading file:', error);
			}
		}
	}

	function canBrowserShareInviteLink(): boolean {
		const shareData = createInviteLinkShareData(
			inviteUrl,
			m['setup.finish_step.invite_link_share_text']()
		);
		return checkCanBrowserShareInviteLink(shareData);
	}

	function getInviteLinkShareData(): ShareData {
		return createInviteLinkShareData(inviteUrl, m['setup.finish_step.invite_link_share_text']());
	}

	function copyInviteLink() {
		navigator.clipboard
			.writeText(inviteUrl)
			.then(() => {
				addToast(new Toast(m['setup.finish_step.invite_link_copied_toast'](), 'success'));
			})
			.catch((err) => {
				console.error('Could not copy text: ', err);
			});
	}

	async function shareInviteLink() {
		const shareData = getInviteLinkShareData();
		await navigator
			.share(shareData)
			.then(() => {
				addToast(new Toast(m['setup.finish_step.invite_link_shared_toast'](), 'success'));
			})
			.catch((err) => {
				addToast(new Toast(m['setup.finish_step.invite_link_cant_shared_toast'](err), 'error'));
			});
	}

	async function submitLocalRegistration(event: SubmitEvent) {
		event.preventDefault();
		if (password !== passwordConfirm) {
			passwordMismatch = true;
			return;
		}
		passwordMismatch = false;
		serverEmailError = null;
		submitting = true;
		try {
			const authApi = new AuthApi(createApiConfig());
			const response = await authApi.registerLocal({
				localRegistration: {
					householdName,
					householdImage: householdImage || undefined,
					memberName,
					email,
					password
				}
			});
			const baseUrl = `${window.location.protocol}//${window.location.host}`;
			inviteUrl = generateInviteUrl(baseUrl, response.inviteToken);
			updateHouseholdState(response.household);
			const member: Member = { id: uuidv4(), name: memberName, isAdmin: true };
			updateUserState(member);
			nextStep();
		} catch (err: unknown) {
			const status =
				err instanceof ResponseError
					? err.response.status
					: typeof err === 'object' && err !== null && 'status' in err
						? (err as { status: unknown }).status
						: undefined;
			if (status === 409) {
				serverEmailError = m['setup.registration_step.email_taken']();
			} else {
				addToast(new Toast(m['setup.registration_step.setup_error'](), 'error'));
			}
		} finally {
			submitting = false;
		}
	}

	function startSocialRegistration(provider: string) {
		sessionStorage.setItem(
			'lh_pending_setup',
			JSON.stringify({ householdName, householdImage, memberName })
		);
		window.location.href = `/oauth2/authorization/${provider}`;
	}
</script>

<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary">
	<span class="text-xl font-bold text-white">LH</span>
</div>
<h1 class="text-center text-2xl font-bold text-base-content">LibreHousehold</h1>
<p class="mt-2 text-center text-base-content/70">{m['subtitle']()}</p>
<div class="flex flex-col justify-around gap-5">
	<ul class="steps mt-12">
		{#each { length: maxSteps } as _, i (i)}
			<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
			<!-- svelte-ignore a11y_click_events_have_key_events -->
			<li class={i <= step ? 'step step-primary' : 'step'} onclick={() => goBackToStep(i)}></li>
		{/each}
	</ul>
	{#if step === 0}
		<h2 class="text-xl font-bold text-base-content">{m['setup.create_step.title']()}</h2>
		<p>{m['setup.create_step.text']()}</p>
		<label
			class="m-3 flex h-20 w-20 cursor-pointer items-center justify-center place-self-center rounded-full bg-neutral-content text-center"
		>
			{#if householdImage}
				<img
					src={householdImage}
					alt={householdName}
					class="h-full w-full rounded-full object-cover"
				/>
			{:else}
				<p class="text-4xl font-bold text-black/50">{houseHoldNameInitials()}</p>
			{/if}
			<input type="file" accept="image/*" class="hidden" onchange={handleImageChange} />
		</label>
		<form onsubmit={nextStep}>
			<fieldset class="fieldset">
				<legend class="fieldset-legend">{m['setup.create_step.household_name_label']()} *</legend>
				<input
					type="text"
					class="input-bordered validator input w-full"
					minlength="3"
					placeholder={m['setup.create_step.household_name_placeholder']()}
					bind:value={householdName}
					required
				/>
				<div class="validator-hint">{m['setup.create_step.household_name_error']()}</div>
			</fieldset>
			<div class="flex justify-between gap-3">
				<a type="button" class="btn flex-1 btn-outline" href="/"
					>{m['setup.create_step.back_button']()}</a
				>
				<button type="submit" class="btn flex-1 btn-primary"
					>{m['setup.create_step.continue_button']()}</button
				>
			</div>
		</form>
	{:else if step === 1}
		<h2 class="text-xl font-bold text-base-content">{m['setup.registration_step.title']()}</h2>
		<p>{m['setup.registration_step.text']()}</p>
		<form onsubmit={submitLocalRegistration} class="flex flex-col gap-4" novalidate>
			<fieldset class="fieldset">
				<legend class="fieldset-legend">{m['setup.registration_step.name_label']()} *</legend>
				<input
					type="text"
					class="input-bordered validator input w-full"
					minlength="3"
					placeholder={m['setup.registration_step.name_placeholder']()}
					bind:value={memberName}
					required
				/>
				<div class="validator-hint">{m['setup.registration_step.name_error']()}</div>
			</fieldset>
			<fieldset class="fieldset">
				<legend class="fieldset-legend">{m['setup.registration_step.email_label']()} *</legend>
				<input
					type="email"
					class="input-bordered validator input w-full"
					placeholder={m['setup.registration_step.email_placeholder']()}
					bind:value={email}
					autocomplete="email"
					required
				/>
				<div class="validator-hint">{m['setup.registration_step.email_error']()}</div>
				{#if serverEmailError}
					<div class="label">
						<span class="label-text-alt text-error">{serverEmailError}</span>
					</div>
				{/if}
			</fieldset>
			<fieldset class="fieldset">
				<legend class="fieldset-legend">{m['setup.registration_step.password_label']()} *</legend>
				<input
					type="password"
					class="input-bordered validator input w-full"
					minlength="8"
					placeholder={m['setup.registration_step.password_placeholder']()}
					bind:value={password}
					autocomplete="new-password"
					required
				/>
			</fieldset>
			<fieldset class="fieldset">
				<legend class="fieldset-legend"
					>{m['setup.registration_step.password_confirm_label']()} *</legend
				>
				<input
					type="password"
					class="input-bordered validator input w-full"
					minlength="8"
					placeholder={m['setup.registration_step.password_placeholder']()}
					bind:value={passwordConfirm}
					autocomplete="new-password"
					required
				/>
				{#if passwordMismatch}
					<div class="label">
						<span class="label-text-alt text-error"
							>{m['setup.registration_step.password_mismatch']()}</span
						>
					</div>
				{/if}
			</fieldset>
			<div class="flex justify-between gap-3">
				<button
					type="button"
					class="btn flex-1 btn-outline"
					onclick={() => goBackToStep(step - 1)}
				>
					{m['setup.registration_step.back_button']()}
				</button>
				<button type="submit" class="btn flex-1 btn-primary" disabled={submitting}>
					{m['setup.registration_step.continue_button']()}
				</button>
			</div>
		</form>

		{#if providers !== null && providers.socialProviders.length > 0}
			<div class="divider">{m['setup.registration_step.social_separator']()}</div>
			<div class="flex flex-col gap-2">
				{#each providers.socialProviders as provider (provider)}
					<button
						type="button"
						class="btn btn-neutral w-full"
						onclick={() => startSocialRegistration(provider)}
						disabled={memberName.trim().length < 3}
					>
						{m['login.social_button']({
							provider: provider.charAt(0).toUpperCase() + provider.slice(1)
						})}
					</button>
				{/each}
			</div>
		{/if}
	{:else if step === 2}
		<h2 class="text-xl font-bold text-base-content">{m['setup.finish_step.title']()}</h2>
		<p>{m['setup.finish_step.invite_text']()}</p>
		<QRCode isResponsive={true} dispatchDownloadUrl={true} data={inviteUrl} />
		<div class="join">
			<label class="input join-item w-full">
				<input type="text" class="input w-full" value={inviteUrl} readonly />
			</label>
			{#if canBrowserShareInviteLink()}
				<button class="btn join-item btn-neutral" onclick={shareInviteLink}>
					<ShareIosIcon />
				</button>
			{:else}
				<button class="btn join-item btn-neutral" onclick={copyInviteLink}>
					<CopyIcon />
				</button>
			{/if}
		</div>
		<button class="btn w-full rounded-lg p-6 btn-primary" onclick={login}
			>{m['setup.finish_step.close_setup_button']()}</button
		>
	{/if}
</div>
