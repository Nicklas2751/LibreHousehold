<script lang="ts">
	import AppMenu from '$lib/AppMenu.svelte';
	import { browser } from '$app/environment';
	import { initSettings } from '$lib/stores/settingsStore';
	import { session } from '$lib/stores/sessionState.svelte';
	import { shouldRedirectToLogin } from '$lib/stores/sessionGuard';
	import { redirectToOAuth2Login } from '$lib/oauth2Login';
	import { resendVerificationEmail } from '$lib/stores/memberStore';
	import {
		dismissVerificationBanner,
		isVerificationBannerDismissed
	} from '$lib/verificationBannerDismissal';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import { m } from '$lib/paraglide/messages.js';

	if (browser) initSettings();

	$effect(() => {
		if (shouldRedirectToLogin(session.status)) {
			redirectToOAuth2Login();
		}
	});

	let { children } = $props();

	const memberId = $derived(session.currentUser?.member.id ?? '');
	const householdId = $derived(session.currentUser?.household.id ?? '');
	let dismissTick = $state(0);
	const showVerificationBanner = $derived.by(() => {
		void dismissTick;
		return session.currentUser?.emailVerified === false && !isVerificationBannerDismissed(memberId);
	});

	function closeVerificationBanner() {
		dismissVerificationBanner(memberId);
		dismissTick++;
	}

	let resendPending = $state(false);

	async function resend() {
		resendPending = true;
		try {
			await resendVerificationEmail(householdId, memberId);
			addToast(new Toast(m['verification.resend_success_toast'](), 'success'));
		} catch {
			addToast(new Toast(m['verification.resend_error_toast'](), 'error'));
		} finally {
			resendPending = false;
		}
	}
</script>

<div class="flex h-dvh flex-col">
	<div
		class="flex-1 overflow-x-hidden overflow-y-auto pb-[env(safe-area-inset-bottom)] text-base-content md:flex md:justify-around"
	>
		<div class="w-full md:h-full md:max-w-[1200px] md:min-w-[1200px]">
			{#if session.status === 'authenticated'}
				{#if showVerificationBanner}
					<div role="alert" class="alert alert-warning m-2 flex items-center justify-between">
						<span>{m['verification.banner_text']()}</span>
						<div class="flex gap-2">
							<button class="btn btn-sm" disabled={resendPending} onclick={resend}>
								{m['verification.resend_button']()}
							</button>
							<button
								class="btn btn-sm btn-ghost"
								aria-label={m['verification.close_button']()}
								onclick={closeVerificationBanner}
							>
								✕
							</button>
						</div>
					</div>
				{/if}
				{@render children()}
			{:else if session.status === 'bootstrapping'}
				<div class="flex h-full items-center justify-center">
					<span class="loading loading-lg loading-spinner"></span>
				</div>
			{/if}
		</div>
	</div>
	<AppMenu />
</div>
