<script lang="ts">
    import '../app.css';
    import favicon from '$lib/assets/favicon.svg';

    let {children} = $props();
</script>

<svelte:head>
    <link rel="icon" href={favicon}/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
</svelte:head>

<div class="hero bg-base-200 min-h-screen">
    <div class="hero-content">
        <div class="md:card md:bg-base-100 md:w-96 md:shadow-sm">
            <div class="md:card-body">
                {@render children()}
            </div>
        </div>
    </div>
</div>