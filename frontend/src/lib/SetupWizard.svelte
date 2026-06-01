<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { CopyIcon } from '@indaco/svelte-iconoir/copy';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import { QRCode } from '@castlenine/svelte-qrcode';
	import { ShareIosIcon } from '@indaco/svelte-iconoir/share-ios';
	import {
		Configuration,
		type Household,
		type HouseholdSetup,
		HouseholdApi,
		type Member,
		ResponseError
	} from '../generated-sources/openapi';
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
	import { goto } from '$app/navigation';
	import { updateUserState } from '$lib/stores/userState';
	import MemberProfileForm from '$lib/MemberProfileForm.svelte';

	const householdId: string = uuidv4();
	let inviteUrl: string = $state('');
	const maxSteps: number = 3;
	let step: number = $state(0);

	let householdName: string = $state('');
	let householdImage: string = $state('');
	let serverEmailError: string | null = $state(null);

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
			const file = files[0];
			try {
				householdImage = await readFileAsDataURL(file);
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
				console.log('Invite link shared successfully');
				addToast(new Toast(m['setup.finish_step.invite_link_shared_toast'](), 'success'));
			})
			.catch((err) => {
				addToast(new Toast(m['setup.finish_step.invite_link_cant_shared_toast'](err), 'error'));
			});
	}

	async function finish(data: { name: string; email: string; avatar: string }) {
		const apiConfig = new Configuration({ basePath: '/api' });
		const householdApi = new HouseholdApi(apiConfig);

		const adminMember: Member = {
			id: uuidv4(),
			name: data.name,
			email: data.email,
			avatar: data.avatar || undefined,
			isAdmin: true
		};
		const household: Household = {
			id: householdId,
			name: householdName,
			image: householdImage
		};
		const householdSetup: HouseholdSetup = { household, member: adminMember };

		serverEmailError = null;
		try {
			const response = await householdApi.setupHousehold({ householdSetup });
			const baseUrl = `${window.location.protocol}//${window.location.host}`;
			inviteUrl = generateInviteUrl(baseUrl, response.inviteToken);
			updateHouseholdState(response.household);
			updateUserState(adminMember);
			nextStep();
		} catch (err: unknown) {
			const status =
				err instanceof ResponseError
					? err.response.status
					: typeof err === 'object' && err !== null && 'status' in err
						? (err as { status: unknown }).status
						: undefined;
			if (status === 409) {
				serverEmailError = m['invite.email_taken']();
			} else {
				addToast(new Toast(m['setup.create_account_step.setup_error'](), 'error'));
			}
		}
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
		<h2 class="text-xl font-bold text-base-content">{m['setup.create_account_step.title']()}</h2>
		<MemberProfileForm
			contextHint={m['setup.create_account_step.text']()}
			nameLabel={m['setup.create_account_step.admin_name_label']()}
			nameHint={m['setup.create_account_step.admin_name_error']()}
			namePlaceholder={m['setup.create_account_step.admin_name_placeholder']()}
			emailLabel={m['setup.create_account_step.admin_email_label']()}
			emailHint={m['setup.create_account_step.admin_email_error']()}
			emailPlaceholder={m['setup.create_account_step.admin_email_placeholder']()}
			backLabel={m['setup.create_account_step.back_button']()}
			submitLabel={m['setup.finish_step.finish_button']()}
			{serverEmailError}
			onClearEmailError={() => {
				serverEmailError = null;
			}}
			onformsubmit={finish}
			onback={() => goBackToStep(step - 1)}
		/>
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
		<button class="btn w-full rounded-lg p-6 btn-primary" onclick={() => goto('/app/dashboard')}
			>{m['setup.finish_step.close_setup_button']()}</button
		>
	{/if}
</div>
