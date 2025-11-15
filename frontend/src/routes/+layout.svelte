<script lang="ts">
    import '../app.css';
    import favicon from '$lib/assets/favicon.svg';
    import Toasts from "$lib/Toasts.svelte";

    let {children} = $props();
</script>

<svelte:head>
    <link rel="icon" href={favicon}/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
</svelte:head>

{@render children()}

<Toasts />