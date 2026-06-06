<script lang="ts">
	import { page } from '$app/state';
	import { m } from '$lib/paraglide/messages.js';
	import { afterNavigate, goto } from '$app/navigation';
	import PageTitleActionBar from '$lib/PageTitleActionBar.svelte';
	import { addTask, loadTasks, tasks, updateTaskDoneStatus } from '$lib/stores/taskStore';
	import { v4 as uuidv4 } from 'uuid';
	import { findMember, members } from '$lib/stores/memberStore';
	import { householdState } from '$lib/stores/householdState.svelte';
	import { checkIsDone, getDisplayDueDate } from '$lib/taskDueCalculator';
	import { filterTasks, TaskFilterType } from '$lib/taskFilter';
	import { userState } from '$lib/stores/userState';
	import type { Member, Task, TaskEdit } from '../../../../generated-sources/openapi';
	import MobileItemList from '$lib/MobileItemList.svelte';
	import DesktopItemList from '$lib/DesktopItemList.svelte';
	import TaskForm from '$lib/TaskForm.svelte';

	let isShowNewTaskForm: boolean = $state(page.params.new !== undefined);

	let filter: string = $state(TaskFilterType.ALL);
	const filteredTasks = $derived(filterTasks($tasks, filter as TaskFilterType, $userState?.id));

	afterNavigate(() => {
		isShowNewTaskForm = page.params.new !== undefined;
	});

	async function createTask(taskEdit: TaskEdit) {
		if ($householdState) {
			await addTask($householdState.id, {
				id: uuidv4(),
				...taskEdit
			});
		}
		await goto('/app/tasks');
	}

	async function getMember(memberId: string): Promise<Member | undefined> {
		if (memberId) {
			const member = $members.find((m) => m.id === memberId);
			if (member) {
				return member;
			}
			if ($householdState) {
				return findMember($householdState.id, memberId);
			}
		}
	}

	function isTaskOverdue(task: Task): boolean {
		if (!task.dueDate) return false;
		if (checkIsDone(task)) return false;

		const dueDate = new Date(task.dueDate);
		dueDate.setUTCHours(0, 0, 0, 0);
		// eslint-disable-next-line svelte/prefer-svelte-reactivity
		const today = new Date();
		today.setUTCHours(0, 0, 0, 0);
		return dueDate <= today;
	}
</script>

<div class="md:flex md:h-full md:flex-col">
	<div class="shrink-0">
		<PageTitleActionBar
			title={isShowNewTaskForm ? m['tasks.new.title']() : m['tasks.title']()}
			buttonText={isShowNewTaskForm
				? m['tasks.new.cancel_button']()
				: m['tasks.create_task_button']()}
			buttonOnClick={async () =>
				isShowNewTaskForm ? await goto('/app/tasks') : await goto('/app/tasks/new')}
		/>
	</div>

	<div class="p-5 md:flex md:min-h-0 md:flex-1 md:flex-col md:overflow-hidden">
		{#if isShowNewTaskForm}
			<TaskForm householdId={$householdState?.id ?? ''} onsubmit={createTask} />
		{:else}
			<form class="md:hidden">
				<input
					class="btn btn-sm"
					type="radio"
					name="task_filter"
					bind:group={filter}
					value={TaskFilterType.ALL}
					aria-label={m['tasks.filter.all']()}
				/>
				<input
					class="btn btn-sm"
					type="radio"
					name="task_filter"
					bind:group={filter}
					value={TaskFilterType.ASSIGNED_TO_ME}
					aria-label={m['tasks.filter.assigned_to_me']()}
				/>
				<input
					class="btn btn-sm"
					type="radio"
					name="task_filter"
					bind:group={filter}
					value={TaskFilterType.PENDING}
					aria-label={m['tasks.filter.pending']()}
				/>
				<input
					class="btn btn-sm"
					type="radio"
					name="task_filter"
					bind:group={filter}
					value={TaskFilterType.COMPLETED}
					aria-label={m['tasks.filter.completed']()}
				/>
			</form>
		{/if}

		<!-- Mobile Task List -->
		<MobileItemList
			loadItems={loadTasks}
			items={filteredTasks}
			noItemsMessage={m['tasks.no_tasks']()}
		>
			{#snippet singleItemView(task)}
				<div class="flex items-center justify-between gap-4">
					<a
						href="/app/tasks/edit/{task.id}"
						class="font-medium"
						class:text-secondary={isTaskOverdue(task)}
					>
						{task.title}
					</a>
					{#if task.dueDate}
						<span class="text-sm whitespace-nowrap" class:text-secondary={isTaskOverdue(task)}>
							{getDisplayDueDate(task)?.toLocaleDateString('de-DE')}
						</span>
					{/if}
				</div>
				{#if task.assignedTo}
					{#await getMember(task.assignedTo)}
						<span class="loading loading-xs loading-dots"></span>
					{:then member}
						{#if member}
							<span class="text-xs font-semibold opacity-60">{member.name}</span>
						{/if}
					{/await}
				{/if}
			{/snippet}
			{#snippet singleItemActions(task)}
				<input
					type="checkbox"
					bind:checked={
						() => checkIsDone(task),
						(checked) => {
							if ($householdState) {
								updateTaskDoneStatus($householdState.id, task.id, checked ? new Date() : null);
							}
						}
					}
					class="checkbox"
				/>
			{/snippet}
		</MobileItemList>

		<DesktopItemList
			loadItems={loadTasks}
			items={filteredTasks}
			noItemsMessage={m['tasks.no_tasks']()}
		>
			{#snippet header()}
				<form class="filter max-md:hidden">
					<input
						class="btn btn-square bg-base-300"
						type="reset"
						onclick={() => (filter = TaskFilterType.ALL)}
						value={m['tasks.filter.all']()}
					/>
					<input
						class="btn bg-base-300"
						type="radio"
						name="task_filter"
						bind:group={filter}
						value={TaskFilterType.ASSIGNED_TO_ME}
						aria-label={m['tasks.filter.assigned_to_me']()}
					/>
					<input
						class="btn bg-base-300"
						type="radio"
						name="task_filter"
						bind:group={filter}
						value={TaskFilterType.PENDING}
						aria-label={m['tasks.filter.pending']()}
					/>
					<input
						class="btn bg-base-300"
						type="radio"
						name="task_filter"
						bind:group={filter}
						value={TaskFilterType.COMPLETED}
						aria-label={m['tasks.filter.completed']()}
					/>
				</form>
			{/snippet}

			{#snippet itemContent(task)}
				<div class="flex flex-col">
					<a
						href="/app/tasks/edit/{task.id}"
						class="font-medium"
						class:text-secondary={isTaskOverdue(task)}
					>
						{task.title}
					</a>
					{#if task.assignedTo}
						{#await getMember(task.assignedTo)}
							<span class="loading loading-xs loading-dots"></span>
						{:then member}
							{#if member}
								<span class="text-xs font-semibold opacity-60">{member.name}</span>
							{/if}
						{/await}
					{/if}
				</div>
				{#if task.dueDate}
					<span class="text-sm whitespace-nowrap" class:text-secondary={isTaskOverdue(task)}>
						{getDisplayDueDate(task)?.toLocaleDateString('de-DE')}
					</span>
				{/if}
			{/snippet}

			{#snippet itemActions(task)}
				<input
					type="checkbox"
					bind:checked={
						() => checkIsDone(task),
						(checked) => {
							if ($householdState) {
								updateTaskDoneStatus($householdState.id, task.id, checked ? new Date() : null);
							}
						}
					}
					class="checkbox"
				/>
			{/snippet}
		</DesktopItemList>
	</div>
</div>
