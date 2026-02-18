<script lang="ts">
    import {page} from '$app/state';
    import {m} from '$lib/paraglide/messages.js';
    import {goto} from "$app/navigation";
    import PageTitleActionBar from "$lib/PageTitleActionBar.svelte";
    import {householdState} from "$lib/stores/householdState.svelte";
    import {userState} from "$lib/stores/userState";
    import {createReimbursement} from "$lib/stores/reimbursementStore";
    import {loadMembers, members} from "$lib/stores/memberStore";
    import {loadDebtorExpenses} from "$lib/stores/expenseStore";
    import type {Expense} from "../../../../../generated-sources/openapi";

    let creditorId = $state("");
    let amount = $state(0.0);
    // Parse query params
    $effect(() => {
        const urlParams = new URLSearchParams(page.url.search);
        creditorId = urlParams.get('creditorId') || "";
        amount = parseFloat(urlParams.get('amount') || "0");
    });

    // Load detailed expenses
    let expenseList: Expense[] = $state([]);
    $effect(() => {
        if ($householdState && $userState && creditorId) {
            // "You owe X" -> Payer is X (creditorId), Debtor is Me ($userState.id)
            if ($members.length === 0) loadMembers($householdState.id);

            loadDebtorExpenses($householdState.id, creditorId, $userState.id).then(res => {
                expenseList = res;
            });
        }
    });

    async function getMember(id: string): Promise<Member | undefined> {
        if (!id) return undefined;
        let member = $members.find(m => m.id === id);
        if (!member && $householdState) {
            member = await findMember($householdState.id, id);
        }
        return member;
    }

    let creditorName = $derived.by(() => {
        const m = $members.find(mem => mem.id === creditorId);
        return m ? m.name : 'Unknown';
    });

    async function handleSettle() {
        if ($householdState && $userState) {
            await createReimbursement($householdState.id, {
                creditorId: creditorId,
                debtorId: $userState.id,
                amount: amount,
                notes: `Settlement via app`
            });
            await goto('/app/dashboard');
        }
    }
</script>

<PageTitleActionBar
    title={m["settle.title"]()}
    buttonText={m["settle.cancel_button"]()}
    buttonOnClick={async () => await goto("/app/dashboard")}
/>

<div class="p-5 max-w-2xl mx-auto">
    <div class="card bg-base-200 card-border shadow-xl">
        <div class="card-body">
            <h2 class="card-title text-2xl justify-center mb-6">
                {m["settle.paying_label"]({name: creditorName})}
            </h2>

            <div class="text-center mb-8">
                <span class="text-6xl font-bold text-error">
                    {amount.toFixed(2)} €
                </span>
                <p class="text-sm text-base-content/60 mt-2">{m["settle.total_amount_label"]()}</p>
            </div>

            <div class="divider">{m["settle.details_label"]()}</div>

            {#if expenseList.length > 0}
                <ul class="list bg-base-100 rounded-box shadow-sm max-h-60 overflow-y-auto mb-6">
                    {#each expenseList as expense}
                        <li class="list-row items-center p-3 border-b border-base-200 last:border-0">
                            <div class="flex-1">
                                <div class="font-medium">{expense.title}</div>
                                <div class="text-xs text-base-content/60">
                                    {new Date(expense.date).toLocaleDateString('de-DE')}
                                </div>
                            </div>
                            <div class="font-bold">
                                {expense.amount.toFixed(2)} €
                            </div>
                        </li>
                    {/each}
                </ul>
            {:else}
                <p class="text-center text-base-content/60 italic mb-6">
                    {m["settle.no_details"]()}
                </p>
            {/if}

            <div class="card-actions justify-center mt-4">
                <button class="btn btn-primary btn-lg w-full md:w-auto" onclick={handleSettle}>
                    {m["settle.confirm_payment_button"]()}
                </button>
            </div>

            <p class="text-xs text-center mt-4 text-base-content/60">
                {m["settle.disclaimer"]()}
            </p>
        </div>
    </div>
</div>


