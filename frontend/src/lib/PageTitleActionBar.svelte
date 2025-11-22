<script lang="ts">
    import PageTitle from "$lib/PageTitle.svelte";

    interface Props {
        title: string;
        buttonText: string;
        buttonOnClick?: () => void;
    }

    const {
        title,
        buttonText,
        buttonOnClick
    }: Props = $props();
</script>

<div class="flex justify-between mt-5 ml-5 mr-5">
    <PageTitle margin={false} title={title}/>
    <button class="btn btn-primary" onclick={buttonOnClick}>{buttonText}</button>
</div>