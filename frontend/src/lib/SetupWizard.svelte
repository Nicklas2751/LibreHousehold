<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';
    import {CopyIcon} from "@indaco/svelte-iconoir/copy";
    import {addToast} from "$lib/stores/toastStore";
    import {Toast} from "$lib/toast";
    import {onMount} from 'svelte';
    import {QRCode} from "@castlenine/svelte-qrcode";
    import {ShareIosIcon} from "@indaco/svelte-iconoir/share-ios";
    import {Configuration, type CreateHouseholdRequest, HouseholdApi} from "../generated-sources/openapi";
    import {v4 as uuidv4} from 'uuid';
    import {updateHouseholdState} from "$lib/stores/householdState.svelte";
    import {Household} from "$lib/household";
    import {Admin} from "$lib/admin";
    import {
        calculateNextStep,
        calculateTargetStep,
        checkCanBrowserShareInviteLink,
        createInviteLinkShareData,
        generateHouseholdNameInitials,
        generateInviteUrl,
        readFileAsDataURL
    } from '$lib/setupWizardLogic';

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
        const shareData = createInviteLinkShareData(inviteUrl, m['setup.finish_step.invite_link_share_text']());
        return checkCanBrowserShareInviteLink(shareData);
    }

    function getInviteLinkShareData(): ShareData {
        return createInviteLinkShareData(inviteUrl, m['setup.finish_step.invite_link_share_text']());
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
        const shareData = getInviteLinkShareData();
        await navigator.share(shareData)
            .then(() => {
                console.log('Invite link shared successfully');
                addToast(new Toast(m['setup.finish_step.invite_link_shared_toast'](), 'success'));
            })
            .catch(err => {
                addToast(new Toast(m['setup.finish_step.invite_link_cant_shared_toast'](err), 'error'));
            });
    }

    async function finish() {
        const apiConfig = new Configuration({basePath: '/api'});
        const api = new HouseholdApi(apiConfig);

        const body = {
            // Household (optional)
            household: {
                id: householdId,
                name: householdName,
                image: householdImage,
                admin: {
                    name: adminName,
                    email: adminEmail,
                },
            }
        } satisfies CreateHouseholdRequest;

        try {
            const data = await api.createHousehold(body);
            console.log(data);
            const adminObj = data.admin as { name: string; email: string };
            const household = new Household(data.id, data.name, new Admin(adminObj.name, adminObj.email));
            household.image = data.image;
            household.isSaved = true;
            updateHouseholdState(household);
            nextStep();
        } catch (error) {
            console.error(error);
        }
    }
</script>

<div class="w-12 h-12 rounded-lg bg-primary flex items-center justify-center mx-auto mb-4">
    <span class="text-white font-bold text-xl">LH</span>
</div>
<h1 class="text-2xl font-bold text-base-content text-center">LibreHousehold</h1>
<p class="text-base-content/70 mt-2 text-center">{m['subtitle']()}</p>
<div class="flex flex-col justify-around gap-5">
    <ul class="steps mt-12">
        {#each {length: maxSteps} as _, i}
            <li class={ i <= step ? 'step step-primary' : 'step' } onclick={() => goBackToStep(i)}/>
        {/each}
    </ul>
    {#if step === 0}
        <h2 class="text-xl font-bold text-base-content">{m['setup.create_step.title']()}</h2>
        <p>{m['setup.create_step.text']()}</p>
        <label class="m-3 flex h-20 w-20 items-center text-center justify-center rounded-full bg-neutral-content place-self-center">
            {#if householdImage}
                <img src={householdImage} alt="{householdName}" class="w-full h-full object-cover rounded-full"/>
            {:else}
                <p class="text-4xl font-bold text-black/50">{houseHoldNameInitials()}</p>
            {/if}
            <input type="file" accept="image/*" class="hidden" onchange={handleImageChange}/>
        </label>
        <form onsubmit={nextStep}>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['setup.create_step.household_name_label']()} *</legend>
                <input type="text" class="input validator input-bordered w-full" minlength="3"
                       placeholder={m['setup.create_step.household_name_placeholder']()} bind:value={householdName}
                       required/>
                <div class="validator-hint">{m['setup.create_step.household_name_error']()}</div>
            </fieldset>
            <div class="flex justify-between gap-3">
                <a type="button" class="btn btn-outline flex-1"
                        href="/">{m['setup.create_step.back_button']()}</a>
                <button type="submit" class="btn btn-primary flex-1">{m['setup.create_step.continue_button']()}</button>
            </div>
        </form>
    {:else if step === 1}
        <h2 class="text-xl font-bold text-base-content">{m['setup.create_account_step.title']()}</h2>
        <p>{m['setup.create_account_step.text']()}</p>
        <form onsubmit={finish}>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['setup.create_account_step.admin_name_label']()} *</legend>
                <input type="text" class="input validator input-bordered w-full" minlength="3"
                       placeholder={m['setup.create_account_step.admin_name_placeholder']()} bind:value={adminName}
                       required/>
                <div class="validator-hint">{m['setup.create_account_step.admin_name_error']()}</div>
            </fieldset>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">{m['setup.create_account_step.admin_email_label']()} *</legend>
                <input type="email" class="input validator input-bordered w-full"
                       placeholder={m['setup.create_account_step.admin_email_placeholder']()} bind:value={adminEmail}
                       required/>
                <div class="validator-hint">{m['setup.create_account_step.admin_email_error']()}</div>
            </fieldset>
            <div class="flex justify-between gap-3">
                <button type="button" class="btn btn-outline flex-1"
                        onclick={() => goBackToStep(step-1)}>{m['setup.create_account_step.back_button']()}</button>
                <button type="submit" class="btn btn-primary flex-1">{m['setup.finish_step.finish_button']()}</button>
            </div>
        </form>
    {:else if step === 2}
        <h2 class="text-xl font-bold text-base-content">{m['setup.finish_step.title']()}</h2>
        <p>{m['setup.finish_step.invite_text']()}</p>
        <QRCode isResponsive={true} dispatchDownloadUrl={true} data={inviteUrl}/>
        <div class="join">
            <label class="input join-item w-full">
                <input type="text" class="input w-full" value={inviteUrl} readonly/>
            </label>
            {#if canBrowserShareInviteLink()}
                <button class="btn btn-neutral join-item" onclick={shareInviteLink}>
                    <ShareIosIcon/>
                </button>
            {:else}
                <button class="btn btn-neutral join-item" onclick={copyInviteLink}>
                    <CopyIcon/>
                </button>
            {/if}
        </div>
        <!-- TODO: navigate to dashboard -->
        <button class="btn rounded-lg btn-primary w-full p-6">{m['setup.finish_step.close_setup_button']()}</button>
    {/if}
</div>