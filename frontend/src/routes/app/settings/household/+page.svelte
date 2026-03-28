<script lang="ts">
	import { QRCode } from '@castlenine/svelte-qrcode';
	import { CopyIcon } from '@indaco/svelte-iconoir/copy';
	import { ShareIosIcon } from '@indaco/svelte-iconoir/share-ios';
	import { RefreshIcon } from '@indaco/svelte-iconoir/refresh';
	import { BinIcon } from '@indaco/svelte-iconoir/bin';
	import { CrownIcon } from '@indaco/svelte-iconoir/crown';
	import { WarningTriangleIcon } from '@indaco/svelte-iconoir/warning-triangle';
	import { UserCircleIcon } from '@indaco/svelte-iconoir/user-circle';
	import { m } from '$lib/paraglide/messages.js';
	import { householdState, updateHouseholdState } from '$lib/stores/householdState.svelte';
	import { userState } from '$lib/stores/userState';
	import { members, loadMembers } from '$lib/stores/memberStore';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import { Configuration, HouseholdApi } from '../../../../generated-sources/openapi';
	import {
		generateInviteUrl,
		createInviteLinkShareData,
		checkCanBrowserShareInviteLink
	} from '$lib/setupWizardLogic';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';

	const api = new HouseholdApi(new Configuration({ basePath: '/api' }));

	const householdId = $derived($householdState?.id ?? '');
	const otherMembers = $derived($members.filter((mb) => mb.id !== $userState?.id));

	// Load members whenever householdId is available
	$effect(() => {
		if (householdId) loadMembers(householdId);
	});

	// --- Invite link ---
	let inviteUrl = $state('');

	// Initialise/update invite URL reactively from store
	$effect(() => {
		if ($householdState && !inviteUrl) {
			inviteUrl = generateInviteUrl(page.url.origin, $householdState.id);
		}
	});

	let generatingInvite = $state(false);

	const canShare = $derived(
		inviteUrl
			? checkCanBrowserShareInviteLink(
					createInviteLinkShareData(inviteUrl, m['settings.household.invite.share_text']())
				)
			: false
	);

	async function regenerateInvite() {
		generatingInvite = true;
		try {
			const result = await api.generateInviteLink({ householdId });
			inviteUrl = result.inviteUrl;
			addToast(new Toast(m['settings.household.invite.regenerated_toast'](), 'success'));
		} catch {
			addToast(new Toast(m['settings.household.invite.regenerate_error'](), 'error'));
		} finally {
			generatingInvite = false;
		}
	}

	function copyInviteLink() {
		navigator.clipboard.writeText(inviteUrl).then(() => {
			addToast(new Toast(m['settings.household.invite.copied_toast'](), 'success'));
		});
	}

	async function shareInviteLink() {
		const data = createInviteLinkShareData(inviteUrl, m['settings.household.invite.share_text']());
		await navigator.share(data);
	}

	// --- Household name (reactive to store) ---
	let householdName = $state('');
	let nameSaving = $state(false);

	$effect(() => {
		if ($householdState?.name) householdName = $householdState.name;
	});

	async function saveName() {
		nameSaving = true;
		try {
			const updated = await api.updateHousehold({
				householdId,
				householdUpdate: { name: householdName }
			});
			updateHouseholdState(updated);
			addToast(new Toast(m['settings.household.name.save_success'](), 'success'));
		} catch {
			addToast(new Toast(m['settings.household.name.save_error'](), 'error'));
		} finally {
			nameSaving = false;
		}
	}

	// --- Remove member ---
	let memberToRemove = $state<string | null>(null);
	let removeSubmitting = $state(false);

	function openRemoveMemberModal(id: string) {
		memberToRemove = id;
	}
	function closeRemoveMemberModal() {
		memberToRemove = null;
	}

	const memberToRemoveName = $derived($members.find((mb) => mb.id === memberToRemove)?.name ?? '');

	async function confirmRemoveMember() {
		if (!memberToRemove) return;
		removeSubmitting = true;
		try {
			await api.removeMember({ householdId, memberId: memberToRemove });
			members.update((all) => all.filter((mb) => mb.id !== memberToRemove));
			memberToRemove = null;
		} catch {
			addToast(new Toast(m['settings.household.members.remove_error'](), 'error'));
		} finally {
			removeSubmitting = false;
		}
	}

	// --- Transfer ownership ---
	let transferToId = $state('');
	let transferModalOpen = $state(false);
	let transferSubmitting = $state(false);

	function openTransferModal() {
		if (transferToId) transferModalOpen = true;
	}
	function closeTransferModal() {
		transferModalOpen = false;
	}

	async function confirmTransfer() {
		transferSubmitting = true;
		try {
			const updated = await api.transferOwnership({
				householdId,
				transferOwnershipRequest: { memberId: transferToId }
			});
			updateHouseholdState(updated);
			transferModalOpen = false;
			transferToId = '';
			addToast(new Toast(m['settings.household.transfer.success'](), 'success'));
			await goto('/app/settings');
		} catch {
			addToast(new Toast(m['settings.household.transfer.error'](), 'error'));
		} finally {
			transferSubmitting = false;
		}
	}

	// --- Delete household ---
	let deleteModalOpen = $state(false);
	let deleteConfirmName = $state('');
	let deleteSubmitting = $state(false);
	const deleteNameMatches = $derived(deleteConfirmName === $householdState?.name);

	async function confirmDeleteHousehold() {
		deleteSubmitting = true;
		try {
			await api.deleteHousehold({ householdId });
			await goto('/');
		} catch {
			addToast(new Toast(m['settings.household.danger.delete_error'](), 'error'));
		} finally {
			deleteSubmitting = false;
		}
	}
</script>

<div class="h-full overflow-y-auto p-4 pb-8">
	<!-- Invite link -->
	<div class="card mt-2 mb-4 bg-base-200 shadow-sm">
		<div class="card-body p-4">
			<h3 class="mb-2 text-base font-semibold">{m['settings.household.invite.title']()}</h3>
			<p class="mb-3 text-sm text-base-content/60">
				{m['settings.household.invite.description']()}
			</p>

			{#if inviteUrl}
				<div class="mb-3 flex justify-center">
					{#key inviteUrl}
						<div class="h-40 w-40">
							<QRCode isResponsive={true} data={inviteUrl} />
						</div>
					{/key}
				</div>
				<div class="join w-full">
					<input type="text" class="input join-item w-full text-xs" value={inviteUrl} readonly />
					{#if canShare}
						<button
							class="btn join-item btn-neutral"
							onclick={shareInviteLink}
							aria-label={m['settings.household.invite.share_button']()}
						>
							<ShareIosIcon />
						</button>
					{/if}
					<button
						class="btn join-item btn-neutral"
						onclick={copyInviteLink}
						aria-label={m['settings.household.invite.copy_button']()}
					>
						<CopyIcon />
					</button>
				</div>
			{/if}

			<button
				class="btn mt-3 gap-2 btn-outline btn-sm"
				onclick={regenerateInvite}
				disabled={generatingInvite}
			>
				{#if generatingInvite}
					<span class="loading loading-xs loading-spinner"></span>
				{:else}
					<RefreshIcon class="h-4 w-4" />
				{/if}
				{m['settings.household.invite.regenerate_button']()}
			</button>
		</div>
	</div>

	<!-- Household name -->
	<div class="card mb-4 bg-base-200 shadow-sm">
		<div class="card-body p-4">
			<h3 class="mb-3 text-base font-semibold">{m['settings.household.name.title']()}</h3>
			<fieldset class="fieldset">
				<legend class="fieldset-legend">{m['settings.household.name.label']()}</legend>
				<input
					type="text"
					class="validator input w-full"
					autocomplete="off"
					bind:value={householdName}
					minlength="3"
					required
				/>
			</fieldset>
			<div class="mt-3 card-actions justify-end">
				<button
					class="btn btn-sm btn-primary"
					onclick={saveName}
					disabled={nameSaving || householdName.trim().length < 3}
				>
					{#if nameSaving}
						<span class="loading loading-xs loading-spinner"></span>
					{/if}
					{m['settings.household.name.save_button']()}
				</button>
			</div>
		</div>
	</div>

	<!-- Members -->
	<div class="card mb-4 bg-base-200 shadow-sm">
		<div class="card-body p-4">
			<h3 class="mb-3 text-base font-semibold">{m['settings.household.members.title']()}</h3>
			<ul class="flex flex-col gap-2">
				{#each $members as member (member.id)}
					<li class="flex items-center gap-3">
						<div class="flex h-9 w-9 min-w-9 items-center justify-center rounded-full bg-base-300">
							<UserCircleIcon class="h-5 w-5 text-base-content/50" />
						</div>
						<div class="min-w-0 flex-1">
							<p class="truncate text-sm font-medium">{member.name}</p>
							<p class="truncate text-xs text-base-content/50">{member.email}</p>
						</div>
						{#if member.id === $householdState?.admin}
							<CrownIcon class="h-4 w-4 shrink-0 text-warning" />
						{/if}
						{#if member.id !== $userState?.id}
							<button
								class="btn text-error btn-ghost btn-xs"
								onclick={() => openRemoveMemberModal(member.id)}
								aria-label={m['settings.household.members.remove_aria']()}
							>
								<BinIcon class="h-4 w-4" />
							</button>
						{/if}
					</li>
				{/each}
			</ul>
		</div>
	</div>

	<!-- Transfer ownership -->
	<div class="card mb-4 bg-base-200 shadow-sm">
		<div class="card-body p-4">
			<h3 class="mb-2 flex items-center gap-2 text-base font-semibold">
				<CrownIcon class="h-5 w-5 text-warning" />
				{m['settings.household.transfer.title']()}
			</h3>
			<p class="mb-3 text-sm text-base-content/60">
				{m['settings.household.transfer.description']()}
			</p>
			<div class="flex gap-2">
				<select
					class="select flex-1 select-sm"
					bind:value={transferToId}
					aria-label={m['settings.household.transfer.select_label']()}
				>
					<option value="">{m['settings.household.transfer.placeholder']()}</option>
					{#each otherMembers as member (member.id)}
						<option value={member.id}>{member.name}</option>
					{/each}
				</select>
				<button class="btn btn-sm btn-warning" onclick={openTransferModal} disabled={!transferToId}>
					{m['settings.household.transfer.button']()}
				</button>
			</div>
		</div>
	</div>

	<!-- Danger zone -->
	<div class="card border border-error/40 bg-error/5 shadow-sm">
		<div class="card-body p-4">
			<h3 class="flex items-center gap-2 text-base font-semibold text-error">
				<WarningTriangleIcon class="h-5 w-5" />
				{m['settings.household.danger.title']()}
			</h3>
			<p class="mt-1 text-sm text-base-content/60">
				{m['settings.household.danger.description']()}
			</p>
			<div class="mt-3 card-actions justify-end">
				<button class="btn btn-outline btn-sm btn-error" onclick={() => (deleteModalOpen = true)}>
					{m['settings.household.danger.delete_button']()}
				</button>
			</div>
		</div>
	</div>
</div>

<!-- Remove member modal -->
{#if memberToRemove}
	<div class="modal-open modal">
		<div class="modal-box">
			<h3 class="text-lg font-bold">{m['settings.household.members.remove_modal.title']()}</h3>
			<p class="mt-3 text-sm">
				{m['settings.household.members.remove_modal.text']({ name: memberToRemoveName })}
			</p>
			<div class="modal-action">
				<button
					class="btn btn-ghost btn-sm"
					onclick={closeRemoveMemberModal}
					disabled={removeSubmitting}
				>
					{m['settings.household.members.remove_modal.cancel']()}
				</button>
				<button
					class="btn btn-sm btn-error"
					onclick={confirmRemoveMember}
					disabled={removeSubmitting}
				>
					{#if removeSubmitting}<span class="loading loading-xs loading-spinner"></span>{/if}
					{m['settings.household.members.remove_modal.confirm']()}
				</button>
			</div>
		</div>
		<div class="modal-backdrop" onclick={closeRemoveMemberModal}></div>
	</div>
{/if}

<!-- Transfer ownership modal -->
{#if transferModalOpen}
	{@const newOwner = $members.find((mb) => mb.id === transferToId)}
	<div class="modal-open modal">
		<div class="modal-box">
			<h3 class="text-lg font-bold">{m['settings.household.transfer.modal.title']()}</h3>
			<p class="mt-3 text-sm">
				{m['settings.household.transfer.modal.text']({ name: newOwner?.name ?? '' })}
			</p>
			<p class="mt-2 text-xs text-base-content/60">
				{m['settings.household.transfer.modal.hint']()}
			</p>
			<div class="modal-action">
				<button
					class="btn btn-ghost btn-sm"
					onclick={closeTransferModal}
					disabled={transferSubmitting}
				>
					{m['settings.household.transfer.modal.cancel']()}
				</button>
				<button
					class="btn btn-sm btn-warning"
					onclick={confirmTransfer}
					disabled={transferSubmitting}
				>
					{#if transferSubmitting}<span class="loading loading-xs loading-spinner"></span>{/if}
					{m['settings.household.transfer.modal.confirm']()}
				</button>
			</div>
		</div>
		<div class="modal-backdrop" onclick={closeTransferModal}></div>
	</div>
{/if}

<!-- Delete household modal -->
{#if deleteModalOpen}
	<div class="modal-open modal">
		<div class="modal-box">
			<h3 class="text-lg font-bold text-error">{m['settings.household.danger.modal.title']()}</h3>
			<p class="mt-3 text-sm">{m['settings.household.danger.modal.text']()}</p>
			<fieldset class="mt-4 fieldset">
				<legend class="fieldset-legend text-xs"
					>{m['settings.household.danger.modal.confirm_label']({
						name: $householdState?.name ?? ''
					})}</legend
				>
				<input
					type="text"
					class="input w-full"
					class:input-error={deleteConfirmName.length > 0 && !deleteNameMatches}
					bind:value={deleteConfirmName}
					placeholder={$householdState?.name ?? ''}
				/>
			</fieldset>
			<div class="modal-action">
				<button
					class="btn btn-ghost btn-sm"
					onclick={() => {
						deleteModalOpen = false;
						deleteConfirmName = '';
					}}
					disabled={deleteSubmitting}
				>
					{m['settings.household.danger.modal.cancel']()}
				</button>
				<button
					class="btn btn-sm btn-error"
					onclick={confirmDeleteHousehold}
					disabled={!deleteNameMatches || deleteSubmitting}
				>
					{#if deleteSubmitting}<span class="loading loading-xs loading-spinner"></span>{/if}
					{m['settings.household.danger.modal.confirm']()}
				</button>
			</div>
		</div>
		<div
			class="modal-backdrop"
			onclick={() => {
				deleteModalOpen = false;
				deleteConfirmName = '';
			}}
		></div>
	</div>
{/if}
