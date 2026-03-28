<script lang="ts">
	import { page } from '$app/state';
	import { m } from '$lib/paraglide/messages.js';
	import { goto } from '$app/navigation';
	import PageTitleActionBar from '$lib/PageTitleActionBar.svelte';
	import { householdState } from '$lib/stores/householdState.svelte';
	import { userState } from '$lib/stores/userState';
	import { createReimbursement } from '$lib/stores/reimbursementStore';
	import { loadMembers, members } from '$lib/stores/memberStore';
	import { loadDebtorExpenses } from '$lib/stores/expenseStore';
	import type { Expense } from '../../../../generated-sources/openapi';

	let creditorId = $state('');
	let amount = $state(0.0);
	// Parse query params
	$effect(() => {
		const urlParams = new URLSearchParams(page.url.search);
		creditorId = urlParams.get('creditorId') || '';
		amount = parseFloat(urlParams.get('amount') || '0');
	});

	// Load detailed expenses
	let expenseList: Expense[] = $state([]);
	$effect(() => {
		if ($householdState && $userState && creditorId) {
			// "You owe X" -> Payer is X (creditorId), Debtor is Me ($userState.id)
			if ($members.length === 0) loadMembers($householdState.id);

			loadDebtorExpenses($householdState.id, creditorId, $userState.id).then((res) => {
				expenseList = res;
			});
		}
	});

	let creditorName = $derived.by(() => {
		const m = $members.find((mem) => mem.id === creditorId);
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
	title={m['settle.title']()}
	buttonText={m['settle.cancel_button']()}
	buttonOnClick={async () => await goto('/app/dashboard')}
/>

<div class="mx-auto max-w-2xl p-5">
	<div class="card bg-base-200 shadow-xl card-border">
		<div class="card-body">
			<h2 class="mb-6 card-title justify-center text-2xl">
				{m['settle.paying_label']({ name: creditorName })}
			</h2>

			<div class="mb-8 text-center">
				<span class="text-6xl font-bold text-error">
					{amount.toFixed(2)} €
				</span>
				<p class="mt-2 text-sm text-base-content/60">{m['settle.total_amount_label']()}</p>
			</div>

			<div class="divider">{m['settle.details_label']()}</div>

			{#if expenseList.length > 0}
				<ul class="list mb-6 max-h-60 overflow-y-auto rounded-box bg-base-100 shadow-sm">
					{#each expenseList as expense (expense.id)}
						<li class="list-row items-center border-b border-base-200 p-3 last:border-0">
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
				<p class="mb-6 text-center text-base-content/60 italic">
					{m['settle.no_details']()}
				</p>
			{/if}

			<div class="mt-4 card-actions justify-center">
				<button class="btn w-full btn-lg btn-primary md:w-auto" onclick={handleSettle}>
					{m['settle.confirm_payment_button']()}
				</button>
			</div>

			<p class="mt-4 text-center text-xs text-base-content/60">
				{m['settle.disclaimer']()}
			</p>
		</div>
	</div>
</div>
