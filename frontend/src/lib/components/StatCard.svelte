<script lang="ts">
    interface Props {
		title: string;
		value: string;
		colSpan?: string;
	}

	const { title, value, colSpan = 'md:col-span-2' }: Props = $props();
</script>

<div class={`card card-border bg-base-200 drop-shadow-xl ${colSpan}`}>
	<div class="card-body">
		<h2 class="card-title">{title}</h2>
		<p class="text-lg font-bold">{value}</p>
	</div>
</div>
