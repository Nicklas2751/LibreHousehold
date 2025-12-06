<script lang="ts">
    import {page} from '$app/state';
    import {m} from '$lib/paraglide/messages.js';
    import {afterNavigate, goto} from "$app/navigation";
    import PageTitleActionBar from "$lib/PageTitleActionBar.svelte";
    import {addTask, loadTasks, tasks, updateTaskDoneStatus} from "$lib/stores/taskStore";
    import {v4 as uuidv4} from "uuid";
    import {findMember, loadMembers, members} from "$lib/stores/memberStore";
    import {householdState} from "$lib/stores/householdState.svelte";
    import {addInterval, checkIsDone} from "$lib/taskDueCalculator";
    import {filterTasks, TaskFilterType} from "$lib/taskFilter";
    import {userState} from "$lib/stores/userState";
    import type {Member, Task} from "../../../../generated-sources/openapi";

    const today = new Date().toISOString().split('T')[0];
    let dueDate: string = $state(today);

    let recurrenceUnit: string = $state("days");
    let recurrenceTimes: number = $state(1);
    const nextRecurrenceDate: string = $derived(calcNextRecurrenceDate(dueDate, recurrenceUnit, recurrenceTimes));

    let isShowNewTaskForm: boolean = $state(page.params.new !== undefined);

    let filter: string = $state(TaskFilterType.ALL);
    const filteredTasks = $derived(filterTasks($tasks, filter as TaskFilterType, $userState?.id));

    let isNewTaskRecurring: boolean = $state(false);

    afterNavigate(() => {
        isShowNewTaskForm = page.params.new !== undefined;
    });

    function calcNextRecurrenceDate(currentDueDate: string, unit: string, times: number): string {
        const date = new Date(currentDueDate);
        return addInterval(date, unit, times).toLocaleDateString();
    }

    async function createTask(event: Event) {
        event.preventDefault();
        if ($householdState) {
            await addTask($householdState.id, {
                id: uuidv4(),
                title: (event.target as HTMLFormElement).newTaskTitle.value,
                description: (event.target as HTMLFormElement).newTaskDescription.value,
                assignedTo: (event.target as HTMLFormElement).newTaskAssignedTo.value,
                dueDate: new Date((event.target as HTMLFormElement).newTaskDueDate.value),
                recurring: isNewTaskRecurring,
                recurrenceUnit: isNewTaskRecurring ? (event.target as HTMLFormElement).newTaskRecurrenceUnit.value : undefined,
                recurrenceInterval: isNewTaskRecurring ? parseInt((event.target as HTMLFormElement).newTaskRecurrenceTimes.value) : undefined
            });
        }
        dueDate = today;
        (event.target as HTMLFormElement).reset();
        await goto("/app/tasks")
    }

    async function getMember(memberId: string): Promise<Member | undefined> {
        if (memberId) {
            const member = $members.find(m => m.id === memberId);
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
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        return dueDate < today;
    }
</script>

<PageTitleActionBar title={isShowNewTaskForm ? m["tasks.new.title"]() : m["tasks.title"]()}
                    buttonText={isShowNewTaskForm ? m["tasks.new.cancel_button"]() : m["tasks.create_task_button"]()}
                    buttonOnClick={async () => isShowNewTaskForm ? await goto("/app/tasks") : await goto("/app/tasks/new")}/>

<div class="p-5">
    {#if isShowNewTaskForm}
        <div class="card card-border bg-base-200 drop-shadow-xl mt-10">
            <form class="card-body grid md:grid-cols-2 md:gap-x-4" onsubmit={createTask}>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">{m["tasks.new.task_title_label"]()} *</legend>
                    <input name="newTaskTitle" type="text" class="input validator w-full"
                           placeholder={m["tasks.new.task_title_placeholder"]()}
                           minlength="3"
                           required/>
                    <div class="validator-hint hidden">{m['tasks.new.task_title_error']()}</div>
                </fieldset>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">{m["tasks.new.assigned_to_label"]()}</legend>
                    {#if $householdState}
                        {#await loadMembers($householdState.id)}
                            <span class="loading loading-dots"></span>
                        {:then _}
                            <select name="newTaskAssignedTo" class="select w-full">
                                <option selected>{m["tasks.new.assigned_to_select_placeholder"]()}</option>
                                {#each $members as member (member.id)}
                                    <option value={member.id}>{member.name}</option>
                                {/each}
                            </select>
                        {/await}
                    {/if}
                </fieldset>

                <fieldset class="fieldset md:col-span-2">
                    <legend class="fieldset-legend">{m["tasks.new.description_label"]()}</legend>
                    <textarea name="newTaskDescription" class="textarea h-24 w-full"
                              placeholder={m["tasks.new.description_placeholder"]()}></textarea>
                </fieldset>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">{m["tasks.new.due_date_label"]()} *</legend>
                    <input type="date" name="newTaskDueDate" class="input validator w-full" min={today}
                           defaultValue={dueDate} required/>
                    <div class="validator-hint hidden">{m['tasks.new.due_date_error']()}</div>
                </fieldset>

                <fieldset class="fieldset bg-base-100 border-base-300 rounded-box w-64 border p-4">
                    <legend class="fieldset-legend">{m["tasks.new.recurring_label"]()}</legend>
                    <input type="checkbox" name="newTaskIsRecurring" class="toggle" bind:checked={isNewTaskRecurring}/>
                </fieldset>

                {#if isNewTaskRecurring}
                    <fieldset class="fieldset">
                        <legend class="fieldset-legend">{m["tasks.new.recurrence_pattern_times_label"]()} *</legend>
                        <input type="number" name="newTaskRecurrenceTimes" class="input validator w-full" min="1"
                               step="1" bind:value={recurrenceTimes} defaultValue={recurrenceTimes} required/>
                        <div class="validator-hint hidden">{m['tasks.new.due_date_error']()}</div>
                    </fieldset>

                    <fieldset class="fieldset">
                        <legend class="fieldset-legend">{m["tasks.new.recurrence_pattern_label"]()} *</legend>
                        <select name="newTaskRecurrenceUnit" class="select w-full" bind:value={recurrenceUnit} required>
                            <option value="days">{m["tasks.new.recurrence_pattern_days"]()}</option>
                            <option value="weeks">{m["tasks.new.recurrence_pattern_weeks"]()}</option>
                            <option value="months">{m["tasks.new.recurrence_pattern_months"]()}</option>
                            <option value="years">{m["tasks.new.recurrence_pattern_years"]()}</option>
                        </select>
                    </fieldset>

                    <p>{m["tasks.new.next_recurrence_date_label"]({
                        date: new Date(dueDate).toLocaleDateString(),
                        nextDate: nextRecurrenceDate
                    })}</p>
                {/if}

                <button type="submit" class="btn btn-primary">{m['tasks.new.create_button']()}</button>
            </form>
        </div>
    {:else}
        <form class="md:hidden">
            <input class="btn btn-sm" type="radio" name="task_filter" bind:group={filter} value={TaskFilterType.ALL}
                   aria-label={m["tasks.filter.all"]()}/>
            <input class="btn btn-sm" type="radio" name="task_filter" bind:group={filter}
                   value={TaskFilterType.ASSIGNED_TO_ME} aria-label={m["tasks.filter.assigned_to_me"]()}/>
            <input class="btn btn-sm" type="radio" name="task_filter" bind:group={filter} value={TaskFilterType.PENDING}
                   aria-label={m["tasks.filter.pending"]()}/>
            <input class="btn btn-sm" type="radio" name="task_filter" bind:group={filter} value={TaskFilterType.COMPLETED}
                   aria-label={m["tasks.filter.completed"]()}/>
        </form>
    {/if}

    <!-- Mobile Task List -->
    <div class="md:hidden pb-10">
        {#if $householdState}
            {#await loadTasks($householdState.id)}
                <div class="flex justify-center items-center h-64">
                    <span class="loading loading-dots"></span>
                </div>
            {:then _}
                {#if filteredTasks.length === 0}
                    <p class="text-base-content/50 text-center mt-10">{m["tasks.no_tasks"]()}</p>
                {:else}
                    <ul class="space-y-2 mt-4">
                        {#each filteredTasks as task (task.id)}
                            <li class="bg-base-200 rounded-lg p-3 flex items-center gap-3">
                                <div class="flex-1">
                                    <div class="flex justify-between items-center gap-4">
                                        <span class="font-medium"
                                              class:text-secondary={isTaskOverdue(task)}>
                                            {task.title}
                                        </span>
                                        {#if task.dueDate}
                                            <span class="text-sm whitespace-nowrap"
                                                  class:text-secondary={isTaskOverdue(task)}>
                                                {new Date(task.dueDate).toLocaleDateString('de-DE')}
                                            </span>
                                        {/if}
                                    </div>
                                    {#if task.assignedTo}
                                        {#await getMember(task.assignedTo)}
                                            <span class="loading loading-dots loading-xs"></span>
                                        {:then member}
                                            {#if member}
                                                <span class="text-xs uppercase font-semibold opacity-60">{member.name}</span>
                                            {/if}
                                        {/await}
                                    {/if}
                                </div>
                                <input type="checkbox"
                                       bind:checked={
                                           () => checkIsDone(task),
                                           (checked) => {
                                               if ($householdState) {
                                                   updateTaskDoneStatus($householdState.id, task.id, checked ? new Date() : null);
                                               }
                                           }
                                       }
                                       class="checkbox"/>
                            </li>
                        {/each}
                    </ul>
                {/if}
            {/await}
        {/if}
    </div>

    <div id="task-list-desktop" class="max-md:hidden card card-border bg-base-200 drop-shadow-xl mt-10">
        <div class="border-b-1 border-b-gray-500 p-2 flex justify-between flex-column">
            <h2 class="card-title">{m["tasks.list_title"]()}</h2>
            <form class="filter max-md:hidden">
                <input class="btn bg-base-300 btn-square" type="reset" onclick={() => filter = TaskFilterType.ALL}
                       value={m["tasks.filter.all"]()}/>
                <input class="btn bg-base-300" type="radio" name="task_filter" bind:group={filter}
                       value={TaskFilterType.ASSIGNED_TO_ME} aria-label={m["tasks.filter.assigned_to_me"]()}/>
                <input class="btn bg-base-300" type="radio" name="task_filter" bind:group={filter}
                       value={TaskFilterType.PENDING} aria-label={m["tasks.filter.pending"]()}/>
                <input class="btn bg-base-300" type="radio" name="task_filter" bind:group={filter}
                       value={TaskFilterType.COMPLETED} aria-label={m["tasks.filter.completed"]()}/>
            </form>
        </div>
        <div class="card-body">
            {#if $householdState}
                {#await loadTasks($householdState.id)}
                    <span class="loading loading-dots"></span>
                {:then _}
                    {#if filteredTasks.length === 0}
                        <p class="text-base-content/50 text-center">{m["tasks.no_tasks"]()}</p>
                    {:else}
                        <ul class="list bg-base-100 rounded-box shadow-md">
                            {#each filteredTasks as task (task.id)}
                                <li class="list-row items-center">
                                    <div class="list-col-grow flex justify-between items-center gap-4">
                                        <div class="flex flex-col">
                                            <span class="font-medium"
                                                  class:text-secondary={isTaskOverdue(task)}>
                                                {task.title}
                                            </span>
                                            {#if task.assignedTo}
                                                {#await getMember(task.assignedTo)}
                                                    <span class="loading loading-dots loading-xs"></span>
                                                {:then member}
                                                    {#if member}
                                                        <span class="text-xs uppercase font-semibold opacity-60">{member.name}</span>
                                                    {/if}
                                                {/await}
                                            {/if}
                                        </div>
                                        {#if task.dueDate}
                                            <span class="text-sm whitespace-nowrap"
                                                  class:text-secondary={isTaskOverdue(task)}>
                                                {new Date(task.dueDate).toLocaleDateString('de-DE')}
                                            </span>
                                        {/if}
                                    </div>
                                    <input type="checkbox" bind:checked={
                                        () => checkIsDone(task),
                                        (checked) => {
                                            if ($householdState) {
                                                updateTaskDoneStatus($householdState.id, task.id, checked ? new Date() : null);
                                            }
                                        }
                                    } class="checkbox"/>
                                </li>
                            {/each}
                        </ul>
                    {/if}
                {/await}
            {/if}
        </div>
    </div>
</div>