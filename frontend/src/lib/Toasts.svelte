<script lang="ts">
    import {toasts} from "$lib/stores/toastStore";
    import {fade} from "svelte/transition";
    import type {Toast} from "$lib/toast";

    function createAlertClassForToast(toast: Toast): string {
        switch (toast.type) {
            case 'success':
                return 'alert-success';
            case 'error':
                return 'alert-error';
            case 'info':
                return 'alert-info';
            case 'warning':
                return 'alert-warning';
            default:
                return 'alert-info';
        }
    }
</script>
{#each $toasts as toast}
    <div class="toast toast-top" transition:fade>
        <div class="alert {createAlertClassForToast(toast)}">
            <span>{toast.message}</span>
        </div>
    </div>
{/each}