<script lang="ts">
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { m } from '$lib/paraglide/messages.js';
	import PageTitleActionBar from '$lib/PageTitleActionBar.svelte';
	import { tasks, loadTasks, editTask, deleteTask } from '$lib/stores/taskStore';
	import { householdState } from '$lib/stores/householdState.svelte';
	import TaskForm from '$lib/TaskForm.svelte';
	import type { TaskEdit } from '../../../../../generated-sources/openapi';

	const taskId = page.params.taskId!;
	const task = $derived($tasks.find((t) => t.id === taskId));

	async function handleEdit(taskEdit: TaskEdit) {
		if ($householdState) {
			await editTask($householdState.id, taskId, taskEdit);
		}
		await goto('/app/tasks');
	}

	async function handleDelete() {
		if ($householdState) {
			await deleteTask($householdState.id, taskId);
		}
		await goto('/app/tasks');
	}
</script>

<div class="md:flex md:h-full md:flex-col">
	<div class="shrink-0">
		<PageTitleActionBar
			title={m['tasks.edit.title']()}
			buttonText={m['tasks.new.cancel_button']()}
			buttonOnClick={async () => await goto('/app/tasks')}
		/>
	</div>

	<div class="p-5 md:flex md:min-h-0 md:flex-1 md:flex-col md:overflow-hidden">
		{#if $householdState}
			{#await loadTasks($householdState.id)}
				<span class="loading loading-dots loading-lg"></span>
			{:then}
				{#if task}
					<TaskForm
						{task}
						householdId={$householdState.id}
						onsubmit={handleEdit}
						ondelete={handleDelete}
					/>
				{:else}
					<span class="loading loading-dots loading-lg"></span>
				{/if}
			{/await}
		{/if}
	</div>
</div>
