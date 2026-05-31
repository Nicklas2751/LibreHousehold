<script lang="ts">
	import { page } from '$app/stores';
	import JoinWizard from '$lib/JoinWizard.svelte';
</script>

<div class="flex min-h-screen items-center justify-center">
	<div class="card w-full max-w-md p-8 shadow-xl">
		<JoinWizard token={$page.params.token ?? ''} />
	</div>
</div>
