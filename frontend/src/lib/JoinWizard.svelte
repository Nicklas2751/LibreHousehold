<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import {
		MembersApi,
		AuthApi,
		type AuthProviders,
		type InviteInfo,
		ResponseError
	} from '../generated-sources/openapi';
	import { updateHouseholdState } from '$lib/stores/householdState.svelte';
	import { updateUserState } from '$lib/stores/userState';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import MemberProfileForm from '$lib/MemberProfileForm.svelte';
	import { createApiConfig } from '$lib/api';
	import { navigateToSocialProvider } from '$lib/setupWizardLogic';

	interface Props {
		token: string;
	}

	const { token }: Props = $props();

	const membersApi = new MembersApi(createApiConfig());

	let inviteInfo: InviteInfo | null = $state(null);
	let invalidLink = $state(false);
	let step = $state(0);
	let joining = $state(false);
	let serverEmailError = $state<string | null>(null);
	let providers = $state<AuthProviders | null>(null);
	let memberName = $state('');
	let memberAvatar = $state('');

	onMount(async () => {
		try {
			inviteInfo = await membersApi.resolveInvite({ token });
		} catch {
			invalidLink = true;
		}
		const authApi = new AuthApi(createApiConfig());
		providers = await authApi.getAuthProviders();
	});

	async function join(data: { name: string; email: string; avatar: string; password?: string }) {
		joining = true;
		serverEmailError = null;
		try {
			const member = await membersApi.joinHousehold({
				token,
				localMemberRegistration: {
					name: data.name,
					email: data.email,
					password: data.password ?? '',
					avatar: data.avatar || undefined
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
			const status =
				err instanceof ResponseError
					? err.response.status
					: typeof err === 'object' && err !== null && 'status' in err
						? (err as { status: unknown }).status
						: undefined;
			if (status === 409) {
				serverEmailError = m['invite.email_taken']();
			} else {
				addToast(new Toast(m['invite.join_error'](), 'error'));
			}
		} finally {
			joining = false;
		}
	}

	function startSocialJoin(provider: string) {
		if (!inviteInfo) return;
		sessionStorage.setItem(
			'lh_pending_join',
			JSON.stringify({
				token,
				memberName,
				memberAvatar,
				householdId: inviteInfo.householdId,
				householdName: inviteInfo.householdName
			})
		);
		navigateToSocialProvider(provider);
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
				requirePassword
				passwordLabel={m['setup.registration_step.password_label']()}
				passwordPlaceholder={m['setup.registration_step.password_placeholder']()}
				passwordConfirmLabel={m['setup.registration_step.password_confirm_label']()}
				passwordMismatchError={m['setup.registration_step.password_mismatch']()}
				bind:name={memberName}
				bind:avatar={memberAvatar}
				{serverEmailError}
				onClearEmailError={() => {
					serverEmailError = null;
				}}
				submitting={joining}
				onformsubmit={join}
				onback={() => goto('/')}
			/>

			{#if providers !== null && providers.socialProviders.length > 0}
				<div class="divider">{m['setup.registration_step.social_separator']()}</div>
				<div class="flex flex-col gap-2">
					{#each providers.socialProviders as provider (provider)}
						<button
							type="button"
							class="btn btn-neutral w-full"
							onclick={() => startSocialJoin(provider)}
							disabled={memberName.trim().length < 3}
						>
							{m['login.social_button']({
								provider: provider.charAt(0).toUpperCase() + provider.slice(1)
							})}
						</button>
					{/each}
				</div>
			{/if}
		{:else if step === 1}
			<h2 class="text-xl font-bold text-base-content">{m['invite.success_title']()}</h2>
			<p>{m['invite.success_text']({ name: inviteInfo.householdName })}</p>
			<button class="btn mt-4 w-full btn-primary" onclick={() => goto('/app/dashboard')}>
				{m['invite.success_button']()}
			</button>
		{/if}
	</div>
{/if}
