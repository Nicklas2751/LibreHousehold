<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import {
		MembersApi,
		type InviteInfo,
		type Problem,
		ResponseError
	} from '../generated-sources/openapi';
	import { apiConfiguration } from '$lib/api/httpClient';
	import { extractErrorStatus } from '$lib/api/errorStatus';
	import { classifyConflictProblem } from '$lib/api/problemMapping';
	import { createDebouncedAvailabilityChecker } from '$lib/emailAvailability';
	import { isValidEmail } from '$lib/setupWizardLogic';
	import { updateHouseholdState } from '$lib/stores/householdState.svelte';
	import { updateUserState } from '$lib/stores/userState';
	import { goto } from '$app/navigation';
	import { onDestroy, onMount } from 'svelte';
	import MemberProfileForm from '$lib/MemberProfileForm.svelte';
	import { completeSilentOAuth2Login } from '$lib/oauth2Login';
	import { bootstrapSession } from '$lib/stores/sessionBootstrap';

	const EMAIL_AVAILABILITY_DEBOUNCE_MS = 400;

	interface Props {
		token: string;
	}

	const { token }: Props = $props();

	const membersApi = new MembersApi(apiConfiguration);
	const checkEmailAvailability = createDebouncedAvailabilityChecker(
		(email: string) => membersApi.checkEmailAvailability({ email }),
		EMAIL_AVAILABILITY_DEBOUNCE_MS
	);

	onDestroy(() => {
		checkEmailAvailability.cancel();
	});

	let inviteInfo: InviteInfo | null = $state(null);
	let invalidLink = $state(false);
	let step = $state(0);
	let joining = $state(false);
	let serverEmailError = $state<string | null>(null);
	let enteringDashboard = $state(false);

	onMount(async () => {
		try {
			inviteInfo = await membersApi.resolveInvite({ token });
		} catch {
			invalidLink = true;
		}
	});

	async function handleEmailInput(email: string) {
		if (!isValidEmail(email)) {
			return;
		}
		const { available } = await checkEmailAvailability(email);
		if (!available) {
			serverEmailError = m['invite.email_taken']();
		}
	}

	async function join(data: { name: string; email: string; avatar: string; password?: string }) {
		joining = true;
		serverEmailError = null;
		try {
			const member = await membersApi.joinHousehold({
				token,
				memberRegistration: {
					id: crypto.randomUUID(),
					name: data.name,
					email: data.email,
					avatar: data.avatar || undefined,
					localRegistration: { password: data.password ?? '' }
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
			await handleJoinError(err);
		} finally {
			joining = false;
		}
	}

	async function handleJoinError(err: unknown) {
		const status = extractErrorStatus(err);
		if (status !== 409) {
			addToast(new Toast(m['invite.join_error'](), 'error'));
			return;
		}
		const problem = await readConflictProblem(err);
		if (!problem) {
			addToast(new Toast(m['invite.join_error'](), 'error'));
			return;
		}
		switch (classifyConflictProblem(problem.type)) {
			case 'account-exists':
				serverEmailError = m['invite.email_taken']();
				break;
			default:
				addToast(new Toast(m['invite.join_error'](), 'error'));
		}
	}

	async function enterDashboard() {
		enteringDashboard = true;
		await completeSilentOAuth2Login();
		await bootstrapSession();
		await goto('/app/dashboard');
	}

	async function readConflictProblem(err: unknown): Promise<Problem | undefined> {
		if (!(err instanceof ResponseError)) {
			return undefined;
		}
		try {
			return await err.response.json();
		} catch {
			// Body was not valid JSON (e.g. empty body or a proxy error page instead of Problem JSON) —
			// fall back to the generic error instead of an unhandled exception.
			return undefined;
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
			<MemberProfileForm
				contextHint={m['invite.household_hint']({ name: inviteInfo.householdName })}
				nameLabel={m['invite.name_label']()}
				nameHint={m['invite.name_error']()}
				namePlaceholder={m['invite.name_placeholder']()}
				emailLabel={m['invite.email_label']()}
				emailHint={m['setup.create_account_step.admin_email_error']()}
				emailPlaceholder={m['invite.email_placeholder']()}
				backLabel={m['setup.create_step.back_button']()}
				submitLabel={m['invite.join_button']()}
				passwordLabel={m['invite.password_label']()}
				passwordHint={m['invite.password_hint']()}
				passwordPlaceholder={m['invite.password_placeholder']()}
				{serverEmailError}
				onClearEmailError={() => {
					serverEmailError = null;
				}}
				onEmailInput={handleEmailInput}
				submitting={joining}
				onformsubmit={join}
				onback={() => goto('/')}
			/>
		{:else if step === 1}
			<h2 class="text-xl font-bold text-base-content">{m['invite.success_title']()}</h2>
			<p>{m['invite.success_text']({ name: inviteInfo.householdName })}</p>
			<button
				class="btn mt-4 w-full btn-primary"
				disabled={enteringDashboard}
				onclick={enterDashboard}
			>
				{#if enteringDashboard}
					<span class="loading loading-xs loading-spinner"></span>
				{/if}
				{m['invite.success_button']()}
			</button>
		{/if}
	</div>
{/if}
