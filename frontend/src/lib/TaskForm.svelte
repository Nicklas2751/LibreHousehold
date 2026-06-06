<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { loadMembers, members } from '$lib/stores/memberStore';
	import { addInterval } from '$lib/taskDueCalculator';
	import { untrack } from 'svelte';
	import { TaskEditRecurrenceUnitEnum, type Task, type TaskEdit } from '../generated-sources/openapi';

	interface Props {
		task?: Task;
		householdId: string;
		onsubmit: (data: TaskEdit) => Promise<void>;
		ondelete?: () => Promise<void>;
	}

	let { task = undefined, householdId, onsubmit, ondelete = undefined }: Props = $props();

	const today = new Date().toISOString().split('T')[0];

	let title: string = $state(untrack(() => task?.title ?? ''));
	let description: string = $state(untrack(() => task?.description ?? ''));
	let assignedTo: string = $state(untrack(() => task?.assignedTo ?? ''));
	let dueDate: string = $state(
		untrack(() => (task?.dueDate ? new Date(task.dueDate).toISOString().split('T')[0] : today))
	);
	let recurring: boolean = $state(untrack(() => task?.recurring ?? false));
	let recurrenceUnit: TaskEditRecurrenceUnitEnum = $state(
		untrack(
			() =>
				(task?.recurrenceUnit as unknown as TaskEditRecurrenceUnitEnum) ??
				TaskEditRecurrenceUnitEnum.Days
		)
	);
	let recurrenceInterval: number = $state(untrack(() => task?.recurrenceInterval ?? 1));

	const nextRecurrenceDate: string = $derived(
		addInterval(new Date(dueDate), recurrenceUnit, recurrenceInterval).toLocaleDateString()
	);

	let submitting: boolean = $state(false);
	let deleteModal: HTMLDialogElement | null = $state(null);

	async function handleSubmit(event: Event) {
		event.preventDefault();
		submitting = true;
		try {
			await onsubmit({
				title,
				dueDate: new Date(dueDate),
				description: description || undefined,
				assignedTo: assignedTo || undefined,
				recurring,
				recurrenceUnit: recurring ? recurrenceUnit : undefined,
				recurrenceInterval: recurring ? recurrenceInterval : undefined
			});
		} finally {
			submitting = false;
		}
	}

	async function confirmDelete() {
		deleteModal?.close();
		await ondelete?.();
	}
</script>

<div class="card mt-10 bg-base-200 drop-shadow-xl card-border">
	<form class="card-body grid md:grid-cols-2 md:gap-x-4" onsubmit={handleSubmit}>
		<fieldset class="fieldset">
			<legend class="fieldset-legend">{m['tasks.new.task_title_label']()} *</legend>
			<input
				type="text"
				class="validator input w-full"
				placeholder={m['tasks.new.task_title_placeholder']()}
				minlength="3"
				required
				bind:value={title}
			/>
			<div class="validator-hint hidden">{m['tasks.new.task_title_error']()}</div>
		</fieldset>

		<fieldset class="fieldset">
			<legend class="fieldset-legend">{m['tasks.new.assigned_to_label']()}</legend>
			{#if householdId}
				{#await loadMembers(householdId)}
					<span class="loading loading-dots"></span>
				{:then}
					<select class="select w-full" bind:value={assignedTo}>
						<option value="">{m['tasks.new.assigned_to_select_placeholder']()}</option>
						{#each $members as member (member.id)}
							<option value={member.id}>{member.name}</option>
						{/each}
					</select>
				{/await}
			{:else}
				<span class="loading loading-dots"></span>
			{/if}
		</fieldset>

		<fieldset class="fieldset md:col-span-2">
			<legend class="fieldset-legend">{m['tasks.new.description_label']()}</legend>
			<textarea
				class="textarea h-24 w-full"
				placeholder={m['tasks.new.description_placeholder']()}
				bind:value={description}
			></textarea>
		</fieldset>

		<fieldset class="fieldset">
			<legend class="fieldset-legend">{m['tasks.new.due_date_label']()} *</legend>
			<input type="date" class="validator input w-full" min={today} required bind:value={dueDate} />
			<div class="validator-hint hidden">{m['tasks.new.due_date_error']()}</div>
		</fieldset>

		<fieldset class="fieldset w-64 rounded-box border border-base-300 bg-base-100 p-4">
			<legend class="fieldset-legend">{m['tasks.new.recurring_label']()}</legend>
			<input type="checkbox" class="toggle" bind:checked={recurring} />
		</fieldset>

		{#if recurring}
			<fieldset class="fieldset">
				<legend class="fieldset-legend"
					>{m['tasks.new.recurrence_pattern_times_label']()} *</legend
				>
				<input
					type="number"
					class="validator input w-full"
					min="1"
					step="1"
					required
					bind:value={recurrenceInterval}
				/>
				<div class="validator-hint hidden">{m['tasks.new.due_date_error']()}</div>
			</fieldset>

			<fieldset class="fieldset">
				<legend class="fieldset-legend">{m['tasks.new.recurrence_pattern_label']()} *</legend>
				<select class="select w-full" required bind:value={recurrenceUnit}>
					<option value={TaskEditRecurrenceUnitEnum.Days}
						>{m['tasks.new.recurrence_pattern_days']()}</option
					>
					<option value={TaskEditRecurrenceUnitEnum.Weeks}
						>{m['tasks.new.recurrence_pattern_weeks']()}</option
					>
					<option value={TaskEditRecurrenceUnitEnum.Months}
						>{m['tasks.new.recurrence_pattern_months']()}</option
					>
					<option value={TaskEditRecurrenceUnitEnum.Years}
						>{m['tasks.new.recurrence_pattern_years']()}</option
					>
				</select>
			</fieldset>

			<p class="md:col-span-2">
				{m['tasks.new.next_recurrence_date_label']({
					date: new Date(dueDate).toLocaleDateString(),
					nextDate: nextRecurrenceDate
				})}
			</p>
		{/if}

		<div class="mt-2 flex gap-3 md:col-span-2">
			{#if ondelete}
				<button type="button" class="btn btn-error btn-outline" onclick={() => deleteModal?.showModal()}>
					{m['tasks.edit.delete_button']()}
				</button>
			{/if}
			<button type="submit" class="btn btn-primary ml-auto" disabled={submitting}>
				{#if submitting}
					<span class="loading loading-xs loading-spinner"></span>
				{/if}
				{task !== undefined ? m['tasks.edit.save_button']() : m['tasks.new.create_button']()}
			</button>
		</div>
	</form>
</div>

{#if ondelete}
	<dialog bind:this={deleteModal} class="modal">
		<div class="modal-box">
			<h3 class="text-lg font-bold">{m['tasks.edit.delete_button']()}</h3>
			<p class="py-4">{m['tasks.edit.delete_confirm']()}</p>
			<div class="modal-action">
				<form method="dialog">
					<button class="btn">{m['tasks.new.cancel_button']()}</button>
				</form>
				<button class="btn btn-error" onclick={confirmDelete}
					>{m['tasks.edit.delete_button']()}</button
				>
			</div>
		</div>
		<form method="dialog" class="modal-backdrop">
			<button>close</button>
		</form>
	</dialog>
{/if}
