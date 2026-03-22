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

    function openRemoveMemberModal(id: string) { memberToRemove = id; }
    function closeRemoveMemberModal() { memberToRemove = null; }

    const memberToRemoveName = $derived(
        $members.find((mb) => mb.id === memberToRemove)?.name ?? ''
    );

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

    function openTransferModal() { if (transferToId) transferModalOpen = true; }
    function closeTransferModal() { transferModalOpen = false; }

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
    <div class="card bg-base-200 shadow-sm mb-4 mt-2">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base mb-2">{m['settings.household.invite.title']()}</h3>
            <p class="text-sm text-base-content/60 mb-3">{m['settings.household.invite.description']()}</p>

            {#if inviteUrl}
                <div class="flex justify-center mb-3">
                    {#key inviteUrl}
                        <div class="w-40 h-40">
                            <QRCode isResponsive={true} data={inviteUrl} />
                        </div>
                    {/key}
                </div>
                <div class="join w-full">
                    <input type="text" class="input join-item w-full text-xs" value={inviteUrl} readonly />
                    {#if canShare}
                        <button class="btn btn-neutral join-item" onclick={shareInviteLink} aria-label={m['settings.household.invite.share_button']()}>
                            <ShareIosIcon />
                        </button>
                    {/if}
                    <button class="btn btn-neutral join-item" onclick={copyInviteLink} aria-label={m['settings.household.invite.copy_button']()}>
                        <CopyIcon />
                    </button>
                </div>
            {/if}

            <button class="btn btn-outline btn-sm mt-3 gap-2" onclick={regenerateInvite} disabled={generatingInvite}>
                {#if generatingInvite}
                    <span class="loading loading-spinner loading-xs"></span>
                {:else}
                    <RefreshIcon class="w-4 h-4" />
                {/if}
                {m['settings.household.invite.regenerate_button']()}
            </button>
        </div>
    </div>

    <!-- Household name -->
    <div class="card bg-base-200 shadow-sm mb-4">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base mb-3">{m['settings.household.name.title']()}</h3>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['settings.household.name.label']()}</legend>
                <input
                    type="text"
                    class="input validator w-full"
                    autocomplete="off"
                    bind:value={householdName}
                    minlength="3"
                    required
                />
            </fieldset>
            <div class="card-actions justify-end mt-3">
                <button
                    class="btn btn-primary btn-sm"
                    onclick={saveName}
                    disabled={nameSaving || householdName.trim().length < 3}
                >
                    {#if nameSaving}
                        <span class="loading loading-spinner loading-xs"></span>
                    {/if}
                    {m['settings.household.name.save_button']()}
                </button>
            </div>
        </div>
    </div>

    <!-- Members -->
    <div class="card bg-base-200 shadow-sm mb-4">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base mb-3">{m['settings.household.members.title']()}</h3>
            <ul class="flex flex-col gap-2">
                {#each $members as member}
                    <li class="flex items-center gap-3">
                        <div class="flex h-9 w-9 min-w-9 items-center justify-center rounded-full bg-base-300">
                            <UserCircleIcon class="text-base-content/50 w-5 h-5" />
                        </div>
                        <div class="flex-1 min-w-0">
                            <p class="font-medium text-sm truncate">{member.name}</p>
                            <p class="text-xs text-base-content/50 truncate">{member.email}</p>
                        </div>
                        {#if member.id === $householdState?.admin}
                            <CrownIcon class="text-warning w-4 h-4 shrink-0" />
                        {/if}
                        {#if member.id !== $userState?.id}
                            <button
                                class="btn btn-ghost btn-xs text-error"
                                onclick={() => openRemoveMemberModal(member.id)}
                                aria-label={m['settings.household.members.remove_aria']()}
                            >
                                <BinIcon class="w-4 h-4" />
                            </button>
                        {/if}
                    </li>
                {/each}
            </ul>
        </div>
    </div>

    <!-- Transfer ownership -->
    <div class="card bg-base-200 shadow-sm mb-4">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base flex items-center gap-2 mb-2">
                <CrownIcon class="text-warning w-5 h-5" />
                {m['settings.household.transfer.title']()}
            </h3>
            <p class="text-sm text-base-content/60 mb-3">{m['settings.household.transfer.description']()}</p>
            <div class="flex gap-2">
                <select
                    class="select select-sm flex-1"
                    bind:value={transferToId}
                    aria-label={m['settings.household.transfer.select_label']()}
                >
                    <option value="">{m['settings.household.transfer.placeholder']()}</option>
                    {#each otherMembers as member}
                        <option value={member.id}>{member.name}</option>
                    {/each}
                </select>
                <button
                    class="btn btn-warning btn-sm"
                    onclick={openTransferModal}
                    disabled={!transferToId}
                >
                    {m['settings.household.transfer.button']()}
                </button>
            </div>
        </div>
    </div>

    <!-- Danger zone -->
    <div class="card border border-error/40 bg-error/5 shadow-sm">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base text-error flex items-center gap-2">
                <WarningTriangleIcon class="w-5 h-5" />
                {m['settings.household.danger.title']()}
            </h3>
            <p class="text-sm text-base-content/60 mt-1">{m['settings.household.danger.description']()}</p>
            <div class="card-actions justify-end mt-3">
                <button class="btn btn-error btn-sm btn-outline" onclick={() => deleteModalOpen = true}>
                    {m['settings.household.danger.delete_button']()}
                </button>
            </div>
        </div>
    </div>
</div>

<!-- Remove member modal -->
{#if memberToRemove}
    <div class="modal modal-open">
        <div class="modal-box">
            <h3 class="font-bold text-lg">{m['settings.household.members.remove_modal.title']()}</h3>
            <p class="mt-3 text-sm">{m['settings.household.members.remove_modal.text']({ name: memberToRemoveName })}</p>
            <div class="modal-action">
                <button class="btn btn-ghost btn-sm" onclick={closeRemoveMemberModal} disabled={removeSubmitting}>
                    {m['settings.household.members.remove_modal.cancel']()}
                </button>
                <button class="btn btn-error btn-sm" onclick={confirmRemoveMember} disabled={removeSubmitting}>
                    {#if removeSubmitting}<span class="loading loading-spinner loading-xs"></span>{/if}
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
    <div class="modal modal-open">
        <div class="modal-box">
            <h3 class="font-bold text-lg">{m['settings.household.transfer.modal.title']()}</h3>
            <p class="mt-3 text-sm">{m['settings.household.transfer.modal.text']({ name: newOwner?.name ?? '' })}</p>
            <p class="mt-2 text-xs text-base-content/60">{m['settings.household.transfer.modal.hint']()}</p>
            <div class="modal-action">
                <button class="btn btn-ghost btn-sm" onclick={closeTransferModal} disabled={transferSubmitting}>
                    {m['settings.household.transfer.modal.cancel']()}
                </button>
                <button class="btn btn-warning btn-sm" onclick={confirmTransfer} disabled={transferSubmitting}>
                    {#if transferSubmitting}<span class="loading loading-spinner loading-xs"></span>{/if}
                    {m['settings.household.transfer.modal.confirm']()}
                </button>
            </div>
        </div>
        <div class="modal-backdrop" onclick={closeTransferModal}></div>
    </div>
{/if}

<!-- Delete household modal -->
{#if deleteModalOpen}
    <div class="modal modal-open">
        <div class="modal-box">
            <h3 class="font-bold text-lg text-error">{m['settings.household.danger.modal.title']()}</h3>
            <p class="mt-3 text-sm">{m['settings.household.danger.modal.text']()}</p>
            <fieldset class="fieldset mt-4">
                <legend class="fieldset-legend text-xs">{m['settings.household.danger.modal.confirm_label']({ name: $householdState?.name ?? '' })}</legend>
                <input
                    type="text"
                    class="input w-full"
                    class:input-error={deleteConfirmName.length > 0 && !deleteNameMatches}
                    bind:value={deleteConfirmName}
                    placeholder={$householdState?.name ?? ''}
                />
            </fieldset>
            <div class="modal-action">
                <button class="btn btn-ghost btn-sm" onclick={() => { deleteModalOpen = false; deleteConfirmName = ''; }} disabled={deleteSubmitting}>
                    {m['settings.household.danger.modal.cancel']()}
                </button>
                <button class="btn btn-error btn-sm" onclick={confirmDeleteHousehold} disabled={!deleteNameMatches || deleteSubmitting}>
                    {#if deleteSubmitting}<span class="loading loading-spinner loading-xs"></span>{/if}
                    {m['settings.household.danger.modal.confirm']()}
                </button>
            </div>
        </div>
        <div class="modal-backdrop" onclick={() => { deleteModalOpen = false; deleteConfirmName = ''; }}></div>
    </div>
{/if}
