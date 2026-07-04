<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { handleCallback, getUser } from '$lib/stores/authStore.svelte';
	import { CopyIcon } from '@indaco/svelte-iconoir/copy';
	import { ShareIosIcon } from '@indaco/svelte-iconoir/share-ios';
	import { QRCode } from '@castlenine/svelte-qrcode';
	import { HouseholdApi } from '../../generated-sources/openapi';
	import { createApiConfig } from '$lib/api';
	import {
		generateInviteUrl,
		checkCanBrowserShareInviteLink,
		createInviteLinkShareData
	} from '$lib/setupWizardLogic';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import { m } from '$lib/paraglide/messages.js';
	import { updateHouseholdState } from '$lib/stores/householdState.svelte';

	let error = $state<string | null>(null);
	let setupComplete = $state(false);
	let inviteUrl = $state('');

	function canBrowserShare(): boolean {
		const shareData = createInviteLinkShareData(inviteUrl, m['setup.finish_step.invite_link_share_text']());
		return checkCanBrowserShareInviteLink(shareData);
	}

	function copyInviteLink() {
		navigator.clipboard.writeText(inviteUrl).then(() => {
			addToast(new Toast(m['setup.finish_step.invite_link_copied_toast'](), 'success'));
		});
	}

	async function shareInviteLink() {
		const shareData = createInviteLinkShareData(inviteUrl, m['setup.finish_step.invite_link_share_text']());
		await navigator.share(shareData).then(() => {
			addToast(new Toast(m['setup.finish_step.invite_link_shared_toast'](), 'success'));
		}).catch((err) => {
			addToast(new Toast(m['setup.finish_step.invite_link_cant_shared_toast'](err), 'error'));
		});
	}

	onMount(async () => {
		try {
			await handleCallback();
			const raw = sessionStorage.getItem('lh_pending_setup');
			if (raw) {
				const { householdName, householdImage, memberName } = JSON.parse(raw);
				const authUser = getUser()!;
				const householdApi = new HouseholdApi(createApiConfig());
				const response = await householdApi.setupHousehold({
					householdSetup: {
						household: { id: crypto.randomUUID(), name: householdName, image: householdImage },
						member: { id: authUser.profile.sub, name: memberName, isAdmin: true }
					}
				});
				sessionStorage.removeItem('lh_pending_setup');
				const baseUrl = `${window.location.protocol}//${window.location.host}`;
				inviteUrl = generateInviteUrl(baseUrl, response.inviteToken);
				updateHouseholdState(response.household);
				setupComplete = true;
			} else {
				await goto('/app/dashboard');
			}
		} catch (e) {
			error = String(e);
		}
	});
</script>

<div class="hero min-h-screen bg-base-200">
	{#if error}
		<div class="alert alert-error max-w-sm" role="alert">
			<span>{error}</span>
		</div>
	{:else if setupComplete}
		<div class="md:card md:w-96 md:bg-base-100 md:shadow-sm">
			<div class="md:card-body flex flex-col gap-4">
				<h2 class="text-xl font-bold text-base-content">{m['setup.callback_setup_success.title']()}</h2>
				<p>{m['setup.callback_setup_success.invite_text']()}</p>
				<QRCode isResponsive={true} dispatchDownloadUrl={true} data={inviteUrl} />
				<div class="join">
					<label class="input join-item w-full">
						<input type="text" class="input w-full" value={inviteUrl} readonly />
					</label>
					{#if canBrowserShare()}
						<button class="btn join-item btn-neutral" onclick={shareInviteLink}>
							<ShareIosIcon />
						</button>
					{:else}
						<button class="btn join-item btn-neutral" onclick={copyInviteLink}>
							<CopyIcon />
						</button>
					{/if}
				</div>
				<button class="btn btn-primary w-full" onclick={() => goto('/app/dashboard')}>
					{m['setup.callback_setup_success.dashboard_button']()}
				</button>
			</div>
		</div>
	{:else}
		<span class="loading loading-spinner loading-lg"></span>
	{/if}
</div>
