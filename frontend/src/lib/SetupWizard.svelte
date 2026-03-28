<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { CopyIcon } from '@indaco/svelte-iconoir/copy';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import { onMount } from 'svelte';
	import { QRCode } from '@castlenine/svelte-qrcode';
	import { ShareIosIcon } from '@indaco/svelte-iconoir/share-ios';
	import {
		Configuration,
		type Household,
		HouseholdApi,
		type Member
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
	import { addMember } from '$lib/stores/memberStore';
	import { updateUserState } from '$lib/stores/userState';

	const householdId: string = uuidv4();
	let inviteUrl: string = $state('');
	const maxSteps: number = 3;
	let step: number = $state(0);

	let householdName: string = $state('');
	let householdImage: string = $state('');
	let adminName: string = $state('');
	let adminEmail: string = $state('');

	onMount(() => {
		const baseUrl = `${window.location.protocol}//${window.location.host}`;
		inviteUrl = generateInviteUrl(baseUrl, householdId);
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

	async function finish() {
		const apiConfig = new Configuration({ basePath: '/api' });
		const householdApi = new HouseholdApi(apiConfig);

		let adminMember: Member = {
			id: uuidv4(),
			name: adminName,
			email: adminEmail
		};
		let household: Household = {
			id: householdId,
			name: householdName,
			image: householdImage,
			admin: adminMember.id
		};

		try {
			household = await householdApi.createHousehold({ household: household });
			adminMember = await addMember(householdId, adminMember);
			updateHouseholdState(household);
			updateUserState(adminMember);
			nextStep();
		} catch (error) {
			console.error(error);
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
			<li class={i <= step ? 'step step-primary' : 'step'} onclick={() => goBackToStep(i)} />
		{/each}
	</ul>
	{#if step === 0}
		<h2 class="text-xl font-bold text-base-content">{m['setup.create_step.title']()}</h2>
		<p>{m['setup.create_step.text']()}</p>
		<label
			class="m-3 flex h-20 w-20 items-center justify-center place-self-center rounded-full bg-neutral-content text-center"
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
		<p>{m['setup.create_account_step.text']()}</p>
		<form onsubmit={finish}>
			<fieldset class="fieldset">
				<legend class="fieldset-legend"
					>{m['setup.create_account_step.admin_name_label']()} *</legend
				>
				<input
					type="text"
					class="input-bordered validator input w-full"
					minlength="3"
					placeholder={m['setup.create_account_step.admin_name_placeholder']()}
					bind:value={adminName}
					required
				/>
				<div class="validator-hint">{m['setup.create_account_step.admin_name_error']()}</div>
			</fieldset>
			<fieldset class="fieldset">
				<legend class="fieldset-legend"
					>{m['setup.create_account_step.admin_email_label']()} *</legend
				>
				<input
					type="email"
					class="input-bordered validator input w-full"
					placeholder={m['setup.create_account_step.admin_email_placeholder']()}
					bind:value={adminEmail}
					required
				/>
				<div class="validator-hint">{m['setup.create_account_step.admin_email_error']()}</div>
			</fieldset>
			<div class="flex justify-between gap-3">
				<button type="button" class="btn flex-1 btn-outline" onclick={() => goBackToStep(step - 1)}
					>{m['setup.create_account_step.back_button']()}</button
				>
				<button type="submit" class="btn flex-1 btn-primary"
					>{m['setup.finish_step.finish_button']()}</button
				>
			</div>
		</form>
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
