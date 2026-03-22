<script lang="ts">
    import { UserCircleIcon } from '@indaco/svelte-iconoir/user-circle';
    import { WarningTriangleIcon } from '@indaco/svelte-iconoir/warning-triangle';
    import { m } from '$lib/paraglide/messages.js';
    import { userState, updateUserState } from '$lib/stores/userState';
    import { householdState } from '$lib/stores/householdState.svelte';
    import { members, loadMembers } from '$lib/stores/memberStore';
    import { addToast } from '$lib/stores/toastStore';
    import { Toast } from '$lib/toast';
    import { Configuration, HouseholdApi } from '../../../../generated-sources/openapi';
    import { goto } from '$app/navigation';

    const api = new HouseholdApi(new Configuration({ basePath: '/api' }));

    const householdId = $derived($householdState?.id ?? '');
    const memberId = $derived($userState?.id ?? '');
    const isOwner = $derived($householdState?.admin === memberId);
    const otherMembers = $derived($members.filter((mb) => mb.id !== memberId));

    // Load members if not yet loaded
    $effect(() => {
        if (householdId && $members.length === 0) loadMembers(householdId);
    });

    // --- Profile form (reactive to store changes) ---
    let displayName = $state($userState?.name ?? '');
    let email = $state($userState?.email ?? '');
    let profileSaving = $state(false);

    // Keep form in sync when userState is loaded/updated externally
    $effect(() => {
        if ($userState?.name) displayName = $userState.name;
        if ($userState?.email) email = $userState.email;
    });

    async function saveProfile() {
        profileSaving = true;
        try {
            const updated = await api.updateMember({
                householdId,
                memberId,
                memberUpdate: { name: displayName, email }
            });
            updateUserState(updated);
            // Also update the shared members store so other pages reflect the change
            members.update((all) => all.map((mb) => mb.id === updated.id ? updated : mb));
            addToast(new Toast(m['settings.user.profile.save_success'](), 'success'));
        } catch {
            addToast(new Toast(m['settings.user.profile.save_error'](), 'error'));
        } finally {
            profileSaving = false;
        }
    }

    // --- Password form ---
    let oldPassword = $state('');
    let newPassword = $state('');
    let confirmPassword = $state('');
    let passwordSaving = $state(false);
    const passwordMismatch = $derived(
        newPassword.length > 0 && confirmPassword.length > 0 && newPassword !== confirmPassword
    );

    async function savePassword() {
        if (passwordMismatch) return;
        passwordSaving = true;
        try {
            await api.changePassword({
                householdId,
                memberId,
                passwordChangeRequest: { oldPassword, newPassword }
            });
            oldPassword = '';
            newPassword = '';
            confirmPassword = '';
            addToast(new Toast(m['settings.user.password.save_success'](), 'success'));
        } catch (err: unknown) {
            const status = (err as Response)?.status ?? (err as { status?: number })?.status;
            if (status === 403) {
                addToast(new Toast(m['settings.user.password.wrong_password'](), 'error'));
            } else {
                addToast(new Toast(m['settings.user.password.save_error'](), 'error'));
            }
        } finally {
            passwordSaving = false;
        }
    }

    // --- Delete account modal ---
    let deleteModalOpen = $state(false);
    let transferMemberId = $state('');
    let deleteHousehold = $state(false);
    let deleteSubmitting = $state(false);

    function openDeleteModal() { deleteModalOpen = true; }
    function closeDeleteModal() {
        deleteModalOpen = false;
        transferMemberId = '';
        deleteHousehold = false;
    }

    async function confirmDelete() {
        deleteSubmitting = true;
        try {
            if (isOwner && !deleteHousehold && transferMemberId) {
                await api.transferOwnership({
                    householdId,
                    transferOwnershipRequest: { memberId: transferMemberId }
                });
            } else if (isOwner && deleteHousehold) {
                await api.deleteHousehold({ householdId });
            }
            await api.deleteAccount({ householdId, memberId });
            await goto('/');
        } catch {
            addToast(new Toast(m['settings.user.danger.delete_error'](), 'error'));
        } finally {
            deleteSubmitting = false;
        }
    }
</script>

<div class="h-full overflow-y-auto p-4 pb-8">

    <!-- Avatar + name header -->
    <div class="flex items-center gap-4 mb-6 mt-2">
        <div class="flex h-16 w-16 items-center justify-center rounded-full bg-primary/15">
            <UserCircleIcon class="text-primary w-9 h-9" />
        </div>
        <div>
            <p class="font-bold text-lg">{$userState?.name ?? '—'}</p>
            <p class="text-sm text-base-content/60">{$userState?.email ?? '—'}</p>
        </div>
    </div>

    <!-- Profile section -->
    <div class="card bg-base-200 shadow-sm mb-4">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base mb-3">{m['settings.user.profile.title']()}</h3>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['settings.user.profile.name_label']()}</legend>
                <input
                    type="text"
                    class="input validator w-full"
                    autocomplete="name"
                    bind:value={displayName}
                    minlength="1"
                    required
                />
            </fieldset>
            <fieldset class="fieldset mt-2">
                <legend class="fieldset-legend">{m['settings.user.profile.email_label']()}</legend>
                <input
                    type="email"
                    class="input validator w-full"
                    autocomplete="email"
                    bind:value={email}
                    required
                />
            </fieldset>
            <div class="card-actions justify-end mt-3">
                <button
                    class="btn btn-primary btn-sm"
                    onclick={saveProfile}
                    disabled={profileSaving || !displayName || !email}
                >
                    {#if profileSaving}
                        <span class="loading loading-spinner loading-xs"></span>
                    {/if}
                    {m['settings.user.profile.save_button']()}
                </button>
            </div>
        </div>
    </div>

    <!-- Password section -->
    <div class="card bg-base-200 shadow-sm mb-4">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base mb-3">{m['settings.user.password.title']()}</h3>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['settings.user.password.old_label']()}</legend>
                <input
                    type="password"
                    class="input validator w-full"
                    autocomplete="current-password"
                    bind:value={oldPassword}
                    required
                />
            </fieldset>
            <fieldset class="fieldset mt-2">
                <legend class="fieldset-legend">{m['settings.user.password.new_label']()}</legend>
                <input
                    type="password"
                    class="input w-full"
                    autocomplete="new-password"
                    class:input-error={passwordMismatch}
                    bind:value={newPassword}
                    minlength="8"
                    required
                />
            </fieldset>
            <fieldset class="fieldset mt-2">
                <legend class="fieldset-legend">{m['settings.user.password.confirm_label']()}</legend>
                <input
                    type="password"
                    class="input w-full"
                    autocomplete="new-password"
                    class:input-error={passwordMismatch}
                    bind:value={confirmPassword}
                    required
                />
                {#if passwordMismatch}
                    <p class="label text-error text-xs mt-1">{m['settings.user.password.mismatch']()}</p>
                {/if}
            </fieldset>
            <div class="card-actions justify-end mt-3">
                <button
                    class="btn btn-primary btn-sm"
                    onclick={savePassword}
                    disabled={passwordSaving || passwordMismatch || !oldPassword || !newPassword || !confirmPassword}
                >
                    {#if passwordSaving}
                        <span class="loading loading-spinner loading-xs"></span>
                    {/if}
                    {m['settings.user.password.save_button']()}
                </button>
            </div>
        </div>
    </div>

    <!-- Danger zone -->
    <div class="card border border-error/40 bg-error/5 shadow-sm">
        <div class="card-body p-4">
            <h3 class="font-semibold text-base text-error flex items-center gap-2">
                <WarningTriangleIcon class="w-5 h-5" />
                {m['settings.user.danger.title']()}
            </h3>
            <p class="text-sm text-base-content/60 mt-1">{m['settings.user.danger.description']()}</p>
            <div class="card-actions justify-end mt-3">
                <button class="btn btn-error btn-sm btn-outline" onclick={openDeleteModal}>
                    {m['settings.user.danger.delete_button']()}
                </button>
            </div>
        </div>
    </div>
</div>

<!-- Delete account modal -->
{#if deleteModalOpen}
    <div class="modal modal-open">
        <div class="modal-box">
            <h3 class="font-bold text-lg text-error">{m['settings.user.delete_modal.title']()}</h3>

            {#if isOwner}
                <p class="mt-3 text-sm">{m['settings.user.delete_modal.owner_notice']()}</p>
                <div class="mt-4 flex flex-col gap-3">
                    <label class="flex items-start gap-3 cursor-pointer">
                        <input
                            type="radio"
                            class="radio radio-primary mt-0.5"
                            name="delete_action"
                            checked={!deleteHousehold}
                            onchange={() => deleteHousehold = false}
                        />
                        <div>
                            <p class="font-semibold text-sm">{m['settings.user.delete_modal.transfer_label']()}</p>
                            <select
                                class="select select-sm w-full mt-1"
                                bind:value={transferMemberId}
                                disabled={deleteHousehold}
                                aria-label={m['settings.user.delete_modal.transfer_select_label']()}
                            >
                                <option value="">{m['settings.user.delete_modal.transfer_placeholder']()}</option>
                                {#each otherMembers as member}
                                    <option value={member.id}>{member.name}</option>
                                {/each}
                            </select>
                        </div>
                    </label>
                    <label class="flex items-start gap-3 cursor-pointer">
                        <input
                            type="radio"
                            class="radio radio-error mt-0.5"
                            name="delete_action"
                            checked={deleteHousehold}
                            onchange={() => { deleteHousehold = true; transferMemberId = ''; }}
                        />
                        <div>
                            <p class="font-semibold text-sm text-error">{m['settings.user.delete_modal.delete_household_label']()}</p>
                            <p class="text-xs text-base-content/60 mt-0.5">{m['settings.user.delete_modal.delete_household_hint']()}</p>
                        </div>
                    </label>
                </div>
            {:else}
                <p class="mt-3 text-sm">{m['settings.user.delete_modal.confirm_text']()}</p>
            {/if}

            <div class="modal-action">
                <button class="btn btn-ghost btn-sm" onclick={closeDeleteModal} disabled={deleteSubmitting}>
                    {m['settings.user.delete_modal.cancel']()}
                </button>
                <button
                    class="btn btn-error btn-sm"
                    onclick={confirmDelete}
                    disabled={deleteSubmitting || (isOwner && !deleteHousehold && !transferMemberId)}
                >
                    {#if deleteSubmitting}
                        <span class="loading loading-spinner loading-xs"></span>
                    {/if}
                    {m['settings.user.delete_modal.confirm']()}
                </button>
            </div>
        </div>
        <div class="modal-backdrop" onclick={closeDeleteModal}></div>
    </div>
{/if}
