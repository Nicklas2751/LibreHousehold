<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';
    import {TaskListIcon} from "@indaco/svelte-iconoir/task-list";
    import {EuroIcon} from "@indaco/svelte-iconoir/euro";
    import {StatsReportIcon} from "@indaco/svelte-iconoir/stats-report";
    import IconCard from '$lib/IconCard.svelte';
    import {CopyIcon} from "@indaco/svelte-iconoir/copy";
    import {addToast} from "$lib/stores/toastStore";
    import {Toast} from "$lib/toast";
    import {onMount} from 'svelte';
    import {QRCode} from "@castlenine/svelte-qrcode";
    import {ShareIosIcon} from "@indaco/svelte-iconoir/share-ios";

    const householdId = Math.random().toString(36).substring(2, 15);
    let inviteUrl: string = $state('');
    const maxSteps: number = 4;
    let step: number = $state(0);

    let householdName: string = $state('');
    let householdImage: string = $state('');
    let adminName: string = $state('');
    let adminEmail: string = $state('');

    onMount(() => {
        const baseUrl = `${window.location.protocol}//${window.location.host}`;
        inviteUrl = `${baseUrl}/invite/${householdId}`;
    });

    function nextStep() {
        if (step < maxSteps - 1) {
            step += 1;
        }
    }

    function goBackToStep(targetStep: number) {
        //TODO enable checking if going back is allowed again
        // if (targetStep >= 0 && targetStep < step) {
            step = targetStep;
        // }
    }

    function houseHoldNameInitials(): string {
        if (householdName.trim().length === 0) {
            return '+';
        }
        const words = householdName.trim().split(' ');
        if (words.length >= 2) {
            return words[0].charAt(0) + words[1].charAt(0);
        }
        return words[0].charAt(0);
    }

    function handleImageChange(event: Event) {
        const target = event.target as HTMLInputElement;
        const files = target.files;

        if (files && files.length > 0) {
            const file = files[0];
            const reader = new FileReader();

            reader.onload = (e) => {
                householdImage = e.target?.result as string;
            };

            reader.readAsDataURL(file);
        }
    }

    function canBrowserShareInviteLink(): boolean {
        if (!navigator.share || !navigator.canShare) {
            return false;
        }

        return navigator.canShare(createInviteLinkShareData());
    }

    function createInviteLinkShareData(): ShareData {
        return {
            text: m['setup.finish_step.invite_link_share_text'](),
            url: inviteUrl
        };
    }

    function copyInviteLink() {
        navigator.clipboard.writeText(inviteUrl)
            .then(() => {
                addToast(new Toast(m['setup.finish_step.invite_link_copied_toast'](), 'success'));
            })
            .catch(err => {
                console.error('Could not copy text: ', err);
            });
    }

    async function shareInviteLink() {
        const shareData = createInviteLinkShareData();
        await navigator.share(shareData)
            .then(() => {
                console.log('Invite link shared successfully');
                addToast(new Toast(m['setup.finish_step.invite_link_shared_toast'](), 'success'));
            })
            .catch(err => {
                addToast(new Toast(m['setup.finish_step.invite_link_cant_shared_toast'](err), 'error'));
            });
    }

    function finish() {

    }
</script>

<div class="w-12 h-12 rounded-lg bg-primary flex items-center justify-center mx-auto mb-4">
    <span class="text-white font-bold text-xl">LH</span>
</div>
<h1 class="text-2xl font-bold text-base-content text-center">LibreHousehold</h1>
<p class="text-base-content/70 mt-2 text-center">{m['setup.subtitle']()}</p>
<div class="flex flex-col justify-around gap-5">
    <ul class="steps mt-12">
        {#each {length: maxSteps} as _, i}
            <li class={ i <= step ? 'step step-primary' : 'step' } onclick={() => goBackToStep(i)} />
        {/each}
    </ul>
    {#if step === 0}
        <h2 class="text-xl font-bold text-base-content">{m['setup.welcome_step.title']()}</h2>
        <p>{m['setup.welcome_step.text']()}</p>
        <IconCard
            icon={TaskListIcon}
            title={m['setup.welcome_step.feature_cards.card_1.title']()}
            description={m['setup.welcome_step.feature_cards.card_1.description']()}
        />
        <IconCard
            icon={EuroIcon}
            title={m['setup.welcome_step.feature_cards.card_2.title']()}
            description={m['setup.welcome_step.feature_cards.card_2.description']()}
        />
        <IconCard
            icon={StatsReportIcon}
            title={m['setup.welcome_step.feature_cards.card_3.title']()}
            description={m['setup.welcome_step.feature_cards.card_3.description']()}
        />
        <button class="btn rounded-lg btn-primary w-full p-6" onclick={nextStep}>{m['setup.welcome_step.get_started_button']()}</button>
    {:else if step === 1}
        <h2 class="text-xl font-bold text-base-content">{m['setup.create_step.title']()}</h2>
        <p>{m['setup.create_step.text']()}</p>
        <label class="m-3 flex h-20 w-20 items-center text-center justify-center rounded-full bg-neutral-content place-self-center">
            {#if householdImage}
                <img src={householdImage} alt="{householdName}" class="w-full h-full object-cover rounded-full" />
            {:else}
                <p class="text-4xl font-bold text-black/50">{houseHoldNameInitials()}</p>
            {/if}
            <input type="file" accept="image/*" class="hidden" onchange={handleImageChange} />
        </label>
        <form onsubmit={nextStep}>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['setup.create_step.household_name_label']()} *</legend>
                <input type="text" class="input validator input-bordered w-full" minlength="3" placeholder={m['setup.create_step.household_name_placeholder']()} bind:value={householdName} required />
                <div class="validator-hint">{m['setup.create_step.household_name_error']()}</div>
            </fieldset>
            <div class="flex justify-between gap-3">
                <button type="button" class="btn btn-outline flex-1" onclick={() => goBackToStep(step-1)}>{m['setup.create_step.back_button']()}</button>
                <button type="submit" class="btn btn-primary flex-1">{m['setup.create_step.continue_button']()}</button>
            </div>
        </form>
    {:else if step === 2}
        <h2 class="text-xl font-bold text-base-content">{m['setup.create_account_step.title']()}</h2>
        <p>{m['setup.create_account_step.text']()}</p>
        <form onsubmit={nextStep}>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['setup.create_account_step.admin_name_label']()} *</legend>
                <input type="text" class="input validator input-bordered w-full" minlength="3" placeholder={m['setup.create_account_step.admin_name_placeholder']()} bind:value={adminName} required />
                <div class="validator-hint">{m['setup.create_account_step.admin_name_error']()}</div>
            </fieldset>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['setup.create_account_step.admin_email_label']()} *</legend>
                <input type="email" class="input validator input-bordered w-full" placeholder={m['setup.create_account_step.admin_email_placeholder']()} bind:value={adminEmail} required />
                <div class="validator-hint">{m['setup.create_account_step.admin_email_error']()}</div>
            </fieldset>
            <div class="flex justify-between gap-3">
                <button type="button" class="btn btn-outline flex-1" onclick={() => goBackToStep(step-1)}>{m['setup.create_account_step.back_button']()}</button>
                <button type="submit" class="btn btn-primary flex-1">{m['setup.create_account_step.continue_button']()}</button>
            </div>
        </form>
    {:else if step === 3}
        <h2 class="text-xl font-bold text-base-content">{m['setup.finish_step.title']()}</h2>
        <p>{m['setup.finish_step.invite_text']()}</p>
        <QRCode isResponsive={true} dispatchDownloadUrl={true} data={inviteUrl} />
        <div class="join">
            <label class="input join-item w-full">
                <input type="text" class="input w-full" value={inviteUrl} readonly />
            </label>
            {#if canBrowserShareInviteLink()}
                <button class="btn btn-neutral join-item" onclick={shareInviteLink}><ShareIosIcon/></button>
                {:else}
                <button class="btn btn-neutral join-item" onclick={copyInviteLink}><CopyIcon/></button>
            {/if}
        </div>
        <button class="btn rounded-lg btn-primary w-full p-6" onclick={finish}>{m['setup.finish_step.finish_button']()}</button>
    {/if}
</div>