<script lang="ts">
    interface Props {
        title: string;
        margin?: boolean;
    }

    const {
        title,
        margin = true
    }: Props = $props();
</script>

<h2 class={`text-xl font-bold ${margin ? "mt-5 ml-5" : ""}`}>{title}</h2>