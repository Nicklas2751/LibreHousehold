<script lang="ts">
    import {page} from '$app/state';
    import {m} from '$lib/paraglide/messages.js';
    import {afterNavigate, goto} from "$app/navigation";
    import PageTitleActionBar from "$lib/PageTitleActionBar.svelte";
    import {
        addExpense,
        deleteExpense,
        expenses,
        findExpense,
        loadExpenses,
        updateExpense
    } from "$lib/stores/expenseStore";
    import {v4 as uuidv4} from "uuid";
    import {findMember, loadMembers, members} from "$lib/stores/memberStore";
    import {householdState} from "$lib/stores/householdState.svelte";
    import {userState} from "$lib/stores/userState";
    import {categories, loadCategories} from "$lib/stores/categoryStore";
    import type {Expense, Member} from "../../../../generated-sources/openapi";
    import {EditIcon} from "@indaco/svelte-iconoir/edit";
    import {BinIcon} from "@indaco/svelte-iconoir/bin";
    import {isExpenseMutable} from "$lib/expenseLogic";

    const today = new Date().toISOString().split('T')[0];
    let date: string = $state(today);

    let isShowForm: boolean = $state(false);
    let expenseToEdit: Expense | null = $state(null);

    // Initial load logic
    $effect(() => {
        handleNavigation();
    });

    afterNavigate(() => {
        handleNavigation();
    });

    function handleNavigation() {
        const param = page.params.new;
        if (param === 'new') {
            isShowForm = true;
            expenseToEdit = null;
            date = today;
        } else if (param) {
            // It's an ID
            isShowForm = true;
            loadExpenseForEdit(param);
        } else {
            isShowForm = false;
            expenseToEdit = null;
        }
    }

    async function loadExpenseForEdit(id: string) {
        if ($householdState) {
            let found = $expenses.find(e => e.id === id);

            if (!found) {
                found = await findExpense($householdState.id, id);
            }

            if (found) {
                expenseToEdit = found;
                date = found.date.toISOString().split('T')[0];
            } else {
                // Not found, maybe deleted or wrong ID
                await goto("/app/expenses");
            }
        }
    }

    async function saveExpense(event: Event) {
        event.preventDefault();
        if ($householdState) {
            const form = event.target as HTMLFormElement;

            // Get splitBetween members (checkboxes)
            const splitBetweenCheckboxes = form.querySelectorAll('input[name="splitBetween"]:checked');
            const splitBetween = Array.from(splitBetweenCheckboxes).map(cb => (cb as HTMLInputElement).value);

            if (expenseToEdit) {
                // Update
                await updateExpense($householdState.id, expenseToEdit.id, {
                    title: form.expenseTitle.value,
                    amount: parseFloat(form.amount.value),
                    paidBy: form.paidBy.value,
                    date: new Date(form.date.value),
                    categoryId: form.categoryId.value,
                    splitBetween: splitBetween.length > 0 ? splitBetween : undefined,
                    notes: form.notes.value || undefined
                });
            } else {
                // Create
                await addExpense($householdState.id, {
                    id: uuidv4(),
                    title: form.expenseTitle.value,
                    amount: parseFloat(form.amount.value),
                    paidBy: form.paidBy.value,
                    date: new Date(form.date.value),
                    categoryId: form.categoryId.value,
                    splitBetween: splitBetween.length > 0 ? splitBetween : undefined,
                    notes: form.notes.value || undefined
                });
            }
        }
        date = today;
        expenseToEdit = null;
        (event.target as HTMLFormElement).reset();
        await goto("/app/expenses")
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

    function canEditExpense(expense: Expense): boolean {
        return expense.paidBy === $userState?.id && isExpenseMutable(expense);
    }

    async function handleDeleteExpense(expenseId: string) {
        if ($householdState && confirm(m["expenses.delete_confirm"]())) {
            await deleteExpense($householdState.id, expenseId);
            if (expenseToEdit?.id === expenseId) {
                await goto("/app/expenses");
            }
        }
    }

    async function handleEditClick(expenseId: string) {
        await goto(`/app/expenses/${expenseId}`);
    }
</script>

<PageTitleActionBar title={isShowForm ? (expenseToEdit ? m["expenses.edit.title"]() : m["expenses.new.title"]()) : m["expenses.title"]()}
                    buttonText={isShowForm ? m["expenses.new.cancel_button"]() : m["expenses.create_expense_button"]()}
                    buttonOnClick={async () => isShowForm ? await goto("/app/expenses") : await goto("/app/expenses/new")}/>

<div class="p-5">
    {#if isShowForm}
        <div class="card card-border bg-base-200 drop-shadow-xl mt-10">
            <form class="card-body grid md:grid-cols-2 md:gap-x-4" onsubmit={saveExpense}>

                <fieldset class="fieldset md:col-span-2">
                    <legend class="fieldset-legend">{m["expenses.new.expense_title_label"]()} *</legend>
                    <input name="expenseTitle" type="text" class="input validator w-full"
                           placeholder={m["expenses.new.expense_title_placeholder"]()}
                           minlength="3"
                           value={expenseToEdit?.title ?? ''}
                           required/>
                    <div class="validator-hint hidden">{m['expenses.new.expense_title_error']()}</div>
                </fieldset>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">{m["expenses.new.amount_label"]()} *</legend>
                    <label class="input input-bordered flex items-center gap-2">
                        <input name="amount" type="number" step="0.01" min="0.01" class="grow" required
                               value={expenseToEdit?.amount ?? ''}/>
                        <span class="text-base-content/60">€</span>
                    </label>
                </fieldset>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">{m["expenses.new.date_label"]()} *</legend>
                    <input type="date" name="date" class="input w-full" max={today}
                           value={date} required/>
                </fieldset>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">{m["expenses.new.paid_by_label"]()} *</legend>
                    {#if $householdState}
                        {#await loadMembers($householdState.id)}
                            <span class="loading loading-dots"></span>
                        {:then _}
                            <select name="paidBy" class="select w-full" required>
                                <option value="" selected={!expenseToEdit?.paidBy && !$userState?.id}>{m["expenses.new.paid_by_select_placeholder"]()}</option>
                                {#each $members as member (member.id)}
                                    <option value={member.id}
                                            selected={member.id === (expenseToEdit?.paidBy ?? $userState?.id)}>
                                        {member.name}
                                    </option>
                                {/each}
                            </select>
                        {/await}
                    {/if}
                </fieldset>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">{m["expenses.new.category_label"]()} *</legend>
                    {#if $householdState}
                        {#await loadCategories($householdState.id)}
                            <span class="loading loading-dots"></span>
                        {:then _}
                            <select name="categoryId" class="select w-full" required>
                                <option value="" selected={!expenseToEdit?.categoryId}>{m["expenses.new.category_select_placeholder"]()}</option>
                                {#each $categories as category (category.id)}
                                    <option value={category.id} selected={category.id === expenseToEdit?.categoryId}>
                                        {#if category.icon}{category.icon} {/if}{category.name}
                                    </option>
                                {/each}
                            </select>
                            {#if $categories.length === 0}
                                <div class="text-xs text-base-content/60 mt-1">
                                    {m["expenses.new.no_categories_hint"]()}
                                </div>
                            {/if}
                        {/await}
                    {/if}
                </fieldset>

                <fieldset class="fieldset md:col-span-2">
                    <legend class="fieldset-legend">{m["expenses.new.split_between_label"]()}</legend>
                    <p class="text-xs text-base-content/60 mb-2">{m["expenses.new.split_between_hint"]()}</p>
                    {#if $householdState}
                        {#await loadMembers($householdState.id)}
                            <span class="loading loading-dots"></span>
                        {:then _}
                            <div class="flex flex-wrap gap-3">
                                {#each $members as member (member.id)}
                                    <label class="cursor-pointer label gap-2">
                                        <input type="checkbox" name="splitBetween" value={member.id} class="checkbox checkbox-sm"
                                               checked={expenseToEdit?.splitBetween ? expenseToEdit.splitBetween.includes(member.id) : false}/>
                                        <span class="label-text">{member.name}</span>
                                    </label>
                                {/each}
                            </div>
                        {/await}
                    {/if}
                </fieldset>

                <fieldset class="fieldset md:col-span-2">
                    <legend class="fieldset-legend">{m["expenses.new.notes_label"]()}</legend>
                    <textarea name="notes" class="textarea h-24 w-full"
                              placeholder={m["expenses.new.notes_placeholder"]()}>{expenseToEdit?.notes ?? ''}</textarea>
                </fieldset>

                <button type="submit" class="btn btn-primary">
                    {expenseToEdit ? m['expenses.edit.save_button']() : m['expenses.new.create_button']()}
                </button>
            </form>
        </div>
    {/if}

    <!-- Mobile Expense List -->
    <div class="md:hidden px-5 pb-20">
        {#if $householdState}
            {#await loadExpenses($householdState.id)}
                <div class="flex justify-center items-center h-64">
                    <span class="loading loading-dots"></span>
                </div>
            {:then _}
                {#if $expenses.length === 0}
                    <p class="text-base-content/50 text-center mt-10">{m["expenses.no_expenses"]()}</p>
                {:else}
                    <ul class="space-y-2 mt-4">
                        {#each $expenses as expense (expense.id)}
                            <li class="bg-base-200 rounded-lg p-3">
                                <div class="flex justify-between items-start">
                                    <div class="flex-1">
                                        <span class="font-medium">{expense.title}</span>
                                        <p class="text-xs text-base-content/60">
                                            {#await getMember(expense.paidBy)}
                                                <span class="loading loading-dots loading-xs"></span>
                                            {:then member}
                                                {#if member}
                                                    {member.name}
                                                {/if}
                                            {/await}
                                            • {new Date(expense.date).toLocaleDateString('de-DE')}
                                        </p>
                                    </div>
                                    <span class="font-bold">{expense.amount.toFixed(2)}€</span>
                                </div>
                                {#if canEditExpense(expense)}
                                    <div class="flex gap-2 mt-2">
                                        <button class="btn btn-xs btn-ghost" onclick={() => handleEditClick(expense.id)} aria-label={m["expenses.edit.title"]()}>
                                            <EditIcon/>
                                        </button>
                                        <button class="btn btn-xs btn-error" onclick={() => handleDeleteExpense(expense.id)} aria-label={m["expenses.delete_button"]()}>
                                            <BinIcon/>
                                        </button>
                                    </div>
                                {/if}
                            </li>
                        {/each}
                    </ul>
                {/if}
            {/await}
        {/if}
    </div>

    <!-- Desktop Expense List -->
    <div id="expense-list-desktop" class="max-md:hidden card card-border bg-base-200 drop-shadow-xl mt-10">
        <div class="border-b border-b-gray-500 p-2 flex justify-between flex-column">
            <h2 class="card-title">{m["expenses.list_title"]()}</h2>
        </div>
        <div class="card-body">
            {#if $householdState}
                {#await loadExpenses($householdState.id)}
                    <span class="loading loading-dots"></span>
                {:then _}
                    {#if $expenses.length === 0}
                        <p class="text-base-content/50 text-center">{m["expenses.no_expenses"]()}</p>
                    {:else}
                        <ul class="list bg-base-100 rounded-box shadow-md">
                            {#each $expenses as expense (expense.id)}
                                <li class="list-row items-center">
                                    <div class="list-col-grow flex justify-between items-center gap-4">
                                        <div class="flex flex-col">
                                            <span class="font-medium">{expense.title}</span>
                                            {#await getMember(expense.paidBy)}
                                                <span class="loading loading-dots loading-xs"></span>
                                            {:then member}
                                                {#if member}
                                                    <span class="text-xs uppercase font-semibold opacity-60">
                                                        {m["expenses.paid_by"]()} {member.name}
                                                    </span>
                                                {/if}
                                            {/await}
                                            <span class="text-xs text-base-content/60">
                                                {new Date(expense.date).toLocaleDateString('de-DE')}
                                            </span>
                                        </div>
                                        <div class="flex items-center gap-3">
                                            <span class="font-bold text-lg">{expense.amount.toFixed(2)}€</span>
                                            {#if canEditExpense(expense)}
                                                <button class="btn btn-sm btn-ghost" onclick={() => handleEditClick(expense.id)} aria-label={m["expenses.edit.title"]()}>
                                                    <EditIcon/>
                                                </button>
                                                <button class="btn btn-sm btn-ghost text-error" onclick={() => handleDeleteExpense(expense.id)} aria-label={m["expenses.delete_button"]()}>
                                                    <BinIcon/>
                                                </button>
                                            {/if}
                                        </div>
                                    </div>
                                </li>
                            {/each}
                        </ul>
                    {/if}
                {/await}
            {/if}
        </div>
    </div>
</div>

