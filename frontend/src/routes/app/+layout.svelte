<script lang="ts">
    import AppMenu from "$lib/AppMenu.svelte";

    let {children} = $props();
</script>

<AppMenu/>

<div class="md:flex md:justify-around [padding-bottom:calc(4rem+env(safe-area-inset-bottom))] overflow-auto">
    <div class="md:max-w-[1200px] md:min-w-[1200px]">
        {@render children()}
    </div>
</div>