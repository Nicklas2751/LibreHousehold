<script lang="ts">
	import { EyeIcon } from '@indaco/svelte-iconoir/eye';
	import { EyeClosedIcon } from '@indaco/svelte-iconoir/eye-closed';

	interface Props {
		label: string;
		hint: string;
		placeholder?: string;
		value: string;
		autocomplete?: 'new-password' | 'current-password';
	}

	let {
		label,
		hint,
		placeholder,
		value = $bindable(),
		autocomplete = 'new-password'
	}: Props = $props();

	let revealed = $state(false);
</script>

<fieldset class="fieldset">
	<legend class="fieldset-legend">{label} *</legend>
	<label class="input-bordered validator input w-full">
		<input
			type={revealed ? 'text' : 'password'}
			aria-label={label}
			class="grow"
			{placeholder}
			{autocomplete}
			bind:value
			minlength="8"
			maxlength="128"
			required
		/>
		<button
			type="button"
			aria-label={revealed ? 'Hide password' : 'Show password'}
			onclick={() => (revealed = !revealed)}
		>
			{#if revealed}
				<EyeClosedIcon />
			{:else}
				<EyeIcon />
			{/if}
		</button>
	</label>
	<p class="validator-hint">{hint}</p>
</fieldset>
