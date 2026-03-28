<script lang="ts">
	import PageTitle from '$lib/PageTitle.svelte';

	interface Props {
		title: string;
		buttonText: string;
		buttonOnClick?: () => void;
	}

	const { title, buttonText, buttonOnClick }: Props = $props();
</script>

<div class="mt-5 mr-5 ml-5 flex justify-between md:mt-2">
	<PageTitle margin={false} {title} />
	<button class="btn btn-primary" onclick={buttonOnClick}>{buttonText}</button>
</div>
