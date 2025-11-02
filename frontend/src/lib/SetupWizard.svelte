<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';
    import {TaskListIcon} from "@indaco/svelte-iconoir/task-list";
    import {EuroIcon} from "@indaco/svelte-iconoir/euro";
    import {StatsReportIcon} from "@indaco/svelte-iconoir/stats-report";
    import IconCard from './IconCard.svelte';

    const maxSteps: number = 4;
    let step: number = $state(0);

    let householdName: string = $state('');
    let householdImage: string = $state('');

    function nextStep() {
        if (step < maxSteps - 1) {
            step += 1;
        }
    }

    function goBackToStep(targetStep: number) {
        if (targetStep >= 0 && targetStep < step) {
            step = targetStep;
        }
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
    {/if}
</div>