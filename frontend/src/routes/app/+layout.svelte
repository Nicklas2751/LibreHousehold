<script lang="ts">
    import AppMenu from "$lib/AppMenu.svelte";

    let {children} = $props();
</script>

<div class="h-dvh flex flex-col">
    <div class="flex-1 overflow-y-auto overflow-x-hidden md:flex md:justify-around text-base-content [padding-bottom:env(safe-area-inset-bottom)]">
        <div class="md:max-w-[1200px] md:min-w-[1200px] w-full">
            {@render children()}
        </div>
    </div>
    <AppMenu/>
</div>