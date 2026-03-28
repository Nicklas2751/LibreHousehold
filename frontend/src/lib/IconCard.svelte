<script lang="ts">
	import type { ComponentType } from 'svelte';

	export let icon: ComponentType;
	export let title: string;
	export let description: string;
</script>

<div class="card card-side bg-base-content/15 p-1 shadow-sm card-xs">
	<figure class="min-w-[64px]">
		<div
			class="m-3 flex h-10 min-h-10 w-10 min-w-10 items-center justify-center rounded-full bg-primary-content"
		>
			<svelte:component this={icon} class="text-primary" />
		</div>
	</figure>
	<div class="ml-2 card-body">
		<h2 class="card-title">{title}</h2>
		<p class="text-start">{description}</p>
	</div>
</div>
