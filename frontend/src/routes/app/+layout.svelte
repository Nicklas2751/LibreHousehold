<script lang="ts">
    import AppMenu from "$lib/AppMenu.svelte";

    let {children} = $props();
</script>

<AppMenu/>

<div class="md:flex md:justify-around">
    <div class="md:max-w-[1200px] md:min-w-[1200px]">
        {@render children()}
    </div>
</div>