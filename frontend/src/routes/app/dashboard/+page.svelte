<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';
    import StatCard from '$lib/StatCard.svelte';
    import ListCard from '$lib/ListCard.svelte';
    import {goto} from "$app/navigation";
    import PageTitle from "$lib/PageTitle.svelte";
    import {loadTasks, tasks} from "$lib/stores/taskStore";
    import {expenses, loadExpenses} from "$lib/stores/expenseStore";
    import {householdState} from "$lib/stores/householdState.svelte";
    import {filterTasks, TaskFilterType} from "$lib/taskFilter";
    import {
        financialSummary,
        loadFinancialSummary,
        loadMemberBalances,
        memberBalances
    } from "$lib/stores/financialStore";
    import {loadMembers, members} from "$lib/stores/memberStore";
    import {userState} from "$lib/stores/userState";
    import FinancialCard from "$lib/FinancialCard.svelte";
    import type {Expense, Task} from "../../../generated-sources/openapi";

    interface DisplayItem {
        id: string;
        title: string;
        subtitle: string;
        isHighlighted?: boolean;
    }

    function mapTaskToDisplayItem(task: Task): DisplayItem {
        const dueDate = task.dueDate ? new Date(task.dueDate) : null;
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const isOverdue: boolean = dueDate !== undefined && dueDate !== null && dueDate < today;

        return {
            id: task.id,
            title: task.title,
            subtitle: dueDate ? dueDate.toLocaleDateString('de-DE') : '',
            isHighlighted: isOverdue
        };
    }

    function mapExpenseToDisplayItem(expense: Expense): DisplayItem {
        return {
            id: expense.id,
            title: expense.title,
            subtitle: expense.amount.toLocaleString() + " €"
        };
    }

    const recentExpenses = $derived.by(() => {
        return $expenses
            .sort((a, b) => {
                if (!a.date) return 1;
                if (!b.date) return -1;
                return new Date(a.date).getTime() - new Date(b.date).getTime();
            })
            .slice(0, 10);
    });

    const countOfUpcomingTasks: number = $derived(filterTasks($tasks, TaskFilterType.PENDING, undefined).length);
    const upcomingTasks = $derived.by(() => {
        const pending = filterTasks($tasks, TaskFilterType.PENDING, undefined);
        return pending
            .sort((a, b) => {
                if (!a.dueDate) return 1;
                if (!b.dueDate) return -1;
                return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
            })
            .slice(0, 10);
    });

    $effect(() => {
        if ($householdState && $userState) {
            loadFinancialSummary($householdState.id, $userState.id);
            loadMemberBalances($householdState.id, $userState.id);
            // Ensure members are loaded for name resolution
            if ($members.length === 0) {
                loadMembers($householdState.id);
            }
        }
    });
</script>

<PageTitle title={m["dashboard.title"]()} />

<div class="grid md:grid-cols-6 gap-4 p-5">

    <StatCard
        title={m["dashboard.task_due_card.title"]()}
        value={countOfUpcomingTasks.toString()}
    />

    <FinancialCard summary={$financialSummary} balances={$memberBalances} />

    {#if $householdState}
        {#await loadTasks($householdState.id)}
            <div class="card card-border bg-base-200 drop-shadow-xl md:col-span-3">
                <div class="card-body flex items-center justify-center">
                    <span class="loading loading-dots"></span>
                </div>
            </div>
        {:then _}
            <ListCard
                title={m["dashboard.upcoming_tasks_card.title"]()}
                items={upcomingTasks}
                itemMapper={mapTaskToDisplayItem}
                emptyMessage={m["dashboard.upcoming_tasks_card.no_tasks"]()}
                viewAllHref="/app/tasks"
                buttonLabel={m["dashboard.upcoming_tasks_card.create_button"]()}
                buttonOnClick={async () => await goto("/app/tasks/new")}
            />
        {/await}
    {:else}
        <ListCard
            title={m["dashboard.upcoming_tasks_card.title"]()}
            items={[]}
            emptyMessage={m["dashboard.upcoming_tasks_card.no_tasks"]()}
            viewAllHref="/app/tasks"
            buttonLabel={m["dashboard.upcoming_tasks_card.create_button"]()}
            buttonOnClick={async () => await goto("/app/tasks/new")}
        />
    {/if}

    {#if $householdState}
        {#await loadExpenses($householdState.id)}
            <div class="card card-border bg-base-200 drop-shadow-xl md:col-span-3">
                <div class="card-body flex items-center justify-center">
                    <span class="loading loading-dots"></span>
                </div>
            </div>
        {:then _}
            <ListCard
                    title={m["dashboard.recent_expenses_card.title"]()}
                    items={recentExpenses}
                    itemMapper={mapExpenseToDisplayItem}
                    emptyMessage={m["dashboard.recent_expenses_card.no_expenses"]()}
                    viewAllHref="/app/expenses"
                    buttonLabel={m["dashboard.recent_expenses_card.add_button"]()}
                    buttonOnClick={async () => await goto("/app/expenses/new")}
            />
        {/await}
    {:else}
        <ListCard
                title={m["dashboard.recent_expenses_card.title"]()}
                items={[]}
                emptyMessage={m["dashboard.recent_expenses_card.no_expenses"]()}
                viewAllHref="/app/expenses"
                buttonLabel={m["dashboard.recent_expenses_card.add_button"]()}
                buttonOnClick={async () => await goto("/app/expenses/new")}
        />
    {/if}

</div>