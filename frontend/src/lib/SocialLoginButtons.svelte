<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { authDiscovery } from '$lib/stores/authDiscoveryState.svelte';
	import { startSocialLogin } from '$lib/socialLogin';
	import type { SocialLoginContext } from '../generated-sources/openapi';

	interface Props {
		socialLoginContext: SocialLoginContext;
	}

	const { socialLoginContext }: Props = $props();

	let startingProviderId: string | null = $state(null);

	async function handleProviderClick(registrationId: string) {
		startingProviderId = registrationId;
		await startSocialLogin(registrationId, socialLoginContext);
		startingProviderId = null;
	}
</script>

{#if authDiscovery.socialProviders.length > 0}
	{#if authDiscovery.localEnabled}
		<div class="divider">{m['auth.social_login_divider']()}</div>
	{/if}
	<!-- Without the divider the buttons follow the page heading directly and need their own gap. -->
	<div class={['flex flex-col gap-2', !authDiscovery.localEnabled && 'mt-4']}>
		{#each authDiscovery.socialProviders as provider (provider.id)}
			<button
				type="button"
				class="btn w-full btn-outline"
				disabled={startingProviderId !== null}
				onclick={() => handleProviderClick(provider.id)}
			>
				{#if startingProviderId === provider.id}
					<span class="loading loading-xs loading-spinner"></span>
				{/if}
				{m['auth.social_login_button']({ provider: provider.displayName })}
			</button>
		{/each}
	</div>
{/if}
