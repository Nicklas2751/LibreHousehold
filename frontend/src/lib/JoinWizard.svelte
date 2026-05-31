<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import {
		Configuration,
		MembersApi,
		type InviteInfo,
		type Member
	} from '../generated-sources/openapi';
	import { updateHouseholdState } from '$lib/stores/householdState.svelte';
	import { updateUserState } from '$lib/stores/userState';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import { readFileAsDataURL } from '$lib/setupWizardLogic';

	interface Props {
		token: string;
	}

	const { token }: Props = $props();

	const membersApi = new MembersApi(new Configuration({ basePath: '/api' }));

	let inviteInfo: InviteInfo | null = $state(null);
	let invalidLink = $state(false);
	let step = $state(0);
	let joining = $state(false);

	let memberName = $state('');
	let memberEmail = $state('');
	let memberAvatar = $state('');
	let joinError = $state<string | null>(null);

	onMount(async () => {
		try {
			inviteInfo = await membersApi.resolveInvite({ token });
		} catch {
			invalidLink = true;
		}
	});

	async function handleAvatarChange(event: Event) {
		const target = event.target as HTMLInputElement;
		const files = target.files;
		if (files && files.length > 0) {
			try {
				memberAvatar = await readFileAsDataURL(files[0]);
			} catch {
				console.error('Failed to read avatar file');
			}
		}
	}

	async function join() {
		joining = true;
		try {
			const member: Member = await membersApi.joinHousehold({
				token,
				memberRegistration: {
					id: crypto.randomUUID(),
					name: memberName,
					email: memberEmail,
					avatar: memberAvatar || undefined
				}
			});
			updateUserState(member);
			if (inviteInfo) {
				updateHouseholdState({
					id: inviteInfo.householdId,
					name: inviteInfo.householdName
				});
			}
			step = 1;
		} catch (err: unknown) {
			const status = (err as Response)?.status ?? (err as { status?: number })?.status;
			if (status === 409) {
				joinError = m['invite.email_taken']();
			} else {
				addToast(new Toast(m['invite.join_error'](), 'error'));
			}
		} finally {
			joining = false;
		}
	}
</script>

<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary">
	<span class="text-xl font-bold text-white">LH</span>
</div>
<h1 class="text-center text-2xl font-bold text-base-content">LibreHousehold</h1>
<p class="mt-2 text-center text-base-content/70">{m['subtitle']()}</p>

{#if invalidLink}
	<div class="mt-8 alert alert-error">
		<span>{m['invite.invalid_link']()}</span>
	</div>
{:else if inviteInfo === null}
	<div class="mt-8 flex justify-center">
		<span class="loading loading-lg loading-spinner"></span>
	</div>
{:else}
	<div class="flex flex-col justify-around gap-5">
		<ul class="steps mt-12">
			{#each { length: 2 } as _, i (i)}
				<li class={i <= step ? 'step step-primary' : 'step'}></li>
			{/each}
		</ul>

		{#if step === 0}
			<h2 class="text-xl font-bold text-base-content">{m['invite.title']()}</h2>
			<p>{m['invite.household_hint']({ name: inviteInfo.householdName })}</p>
			<label
				class="m-3 flex h-20 w-20 cursor-pointer items-center justify-center place-self-center rounded-full bg-neutral-content text-center"
			>
				{#if memberAvatar}
					<img
						src={memberAvatar}
						alt={memberName}
						class="h-full w-full rounded-full object-cover"
					/>
				{:else}
					<span class="text-3xl">👤</span>
				{/if}
				<input type="file" accept="image/*" class="hidden" onchange={handleAvatarChange} />
			</label>
			<form onsubmit={join}>
				<fieldset class="fieldset">
					<legend class="fieldset-legend">{m['invite.name_label']()} *</legend>
					<input
						type="text"
						aria-label={m['invite.name_label']()}
						class="input-bordered validator input w-full"
						minlength="1"
						bind:value={memberName}
						required
					/>
				</fieldset>
				<fieldset class="fieldset">
					<legend class="fieldset-legend">{m['invite.email_label']()} *</legend>
					<input
						type="email"
						aria-label={m['invite.email_label']()}
						class="input-bordered validator input w-full"
						bind:value={memberEmail}
						required
					/>
				</fieldset>
				{#if joinError}
					<div class="mt-2 alert alert-error"><span>{joinError}</span></div>
				{/if}
				<div class="mt-4 flex justify-between gap-3">
					<a href="/" class="btn flex-1 btn-outline">{m['setup.create_step.back_button']()}</a>
					<button type="submit" class="btn flex-1 btn-primary" disabled={joining}>
						{#if joining}
							<span class="loading loading-xs loading-spinner"></span>
						{/if}
						{m['invite.join_button']()}
					</button>
				</div>
			</form>
		{:else if step === 1}
			<h2 class="text-xl font-bold text-base-content">{m['invite.success_title']()}</h2>
			<p>{m['invite.success_text']({ name: inviteInfo.householdName })}</p>
			<button class="btn mt-4 w-full btn-primary" onclick={() => goto('/app/dashboard')}>
				{m['invite.success_button']()}
			</button>
		{/if}
	</div>
{/if}
