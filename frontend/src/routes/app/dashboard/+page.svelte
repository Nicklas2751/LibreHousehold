<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';
    import StatCard from '$lib/components/StatCard.svelte';
    import ListCard from '$lib/components/ListCard.svelte';
    import {goto} from "$app/navigation";
</script>

<h2 class="text-xl font-bold pt-5 pl-5">{m["dashboard.title"]()}</h2>

<div class="grid md:grid-cols-6 gap-4 p-5">

    <StatCard
        title={m["dashboard.task_due_card.title"]()}
        value="0"
    />

    <StatCard
        title={m["dashboard.you_owe_card.title"]()}
        value="0,00€"
    />

    <StatCard
        title={m["dashboard.owed_to_you_card.title"]()}
        value="0,00€"
    />

    <ListCard
        title={m["dashboard.upcoming_tasks_card.title"]()}
        emptyMessage={m["dashboard.upcoming_tasks_card.no_tasks"]()}
        viewAllHref="/app/tasks"
        buttonLabel={m["dashboard.upcoming_tasks_card.create_button"]()}
        buttonOnClick={async () => await goto("/app/tasks/new")}
    />

    <ListCard
        title={m["dashboard.recent_expenses_card.title"]()}
        emptyMessage={m["dashboard.recent_expenses_card.no_expenses"]()}
        viewAllHref="/app/expenses"
        buttonLabel={m["dashboard.recent_expenses_card.add_button"]()}
        buttonOnClick={async () => await goto("/app/expenses/new")}
    />

</div>