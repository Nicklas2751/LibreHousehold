<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { findMember, members } from '$lib/stores/memberStore';
	import { householdState } from '$lib/stores/householdState.svelte';
	import type { FinancialSummary, Member, MemberBalance } from '../generated-sources/openapi';
	import { goto } from '$app/navigation';

	let { summary, balances }: { summary: FinancialSummary | null; balances: MemberBalance[] } =
		$props();

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

	async function handleSettleClick(balance: MemberBalance) {
		// Navigate to settlement page or open modal with payerId (user) and debtorId (current user is payer if balance negative?)
		// Wait, logic:
		// if balance.balance < 0: "You owe X". Current user is Debtor. X is Creditor.
		// Route parameter names are tricky. Let's use a clear URL structure.
		// /app/settlement?creditorId=...&amount=...
		const creditorId = balance.memberId;
		const amount = Math.abs(balance.balance); // The amount to pay
		await goto(`/app/reimbursements/settle?creditorId=${creditorId}&amount=${amount}`);
	}
</script>

<div class="card bg-base-200 drop-shadow-xl card-border md:col-span-2">
	<div class="card-body">
		<h2 class="card-title">{m['dashboard.financials.title']()}</h2>

		{#if summary}
			<div class="stat p-0">
				<div class="stat-title">{m['dashboard.financials.total_balance_label']()}</div>
				<!-- Logic for net balance: (owedToYou - youOwe) -->
				<div
					class="stat-value"
					class:text-success={summary.owedToYou - summary.youOwe > 0}
					class:text-error={summary.owedToYou - summary.youOwe < 0}
				>
					{(summary.owedToYou - summary.youOwe).toFixed(2)} €
				</div>
			</div>

			<div class="divider my-2"></div>

			<ul class="space-y-4">
				{#each balances as balance (balance.memberId)}
					{@const hasDebt = Math.abs(balance.balance) > 0.001}
					{@const hasPending = balance.pendingBalance && Math.abs(balance.pendingBalance) > 0.001}

					{#if hasDebt || hasPending}
						<li
							class="flex flex-col gap-1 border-b border-base-200 pb-2 text-sm last:border-0 last:pb-0"
						>
							<div class="flex items-center justify-between">
								<span class="opacity-80">
									{#if $householdState}
										{#await getMember(balance.memberId)}
											<span class="loading loading-xs loading-dots"></span>
										{:then member}
											{#if member}
												{#if balance.balance > 0}
													{m['dashboard.financials.owed_to_you']({ name: member.name })}
												{:else if balance.balance < 0}
													{m['dashboard.financials.you_owe']({ name: member.name })}
												{:else}
													{member.name}
												{/if}
											{/if}
										{/await}
									{/if}
								</span>
								<div class="flex items-center gap-2">
									{#if balance.balance !== 0}
										<span
											class="font-bold"
											class:text-success={balance.balance > 0}
											class:text-error={balance.balance < 0}
										>
											{Math.abs(balance.balance).toFixed(2)} €
										</span>
									{/if}
									{#if balance.balance < -0.001}
										<button
											class="btn btn-outline btn-xs btn-error"
											onclick={() => handleSettleClick(balance)}
										>
											{m['dashboard.financials.settle_button']()}
										</button>
									{/if}
								</div>
							</div>

							<!-- Pending amounts display -->
							{#if hasPending}
								<div
									class="flex items-center justify-between px-2 text-xs text-base-content/60 italic"
								>
									<span>
										{#await getMember(balance.memberId) then member}
											{#if member}
												{#if (balance.pendingBalance || 0) > 0}
													{m['dashboard.financials.pending_incoming']({ name: member.name })}
												{:else}
													{m['dashboard.financials.pending_outgoing']({ name: member.name })}
												{/if}
											{/if}
										{/await}
									</span>
									<span>{Math.abs(balance.pendingBalance || 0).toFixed(2)} €</span>
								</div>
							{/if}
						</li>
					{/if}
				{/each}
				{#if balances.every((b) => Math.abs(b.balance) < 0.001 && Math.abs(b.pendingBalance || 0) < 0.001)}
					<li class="text-center italic opacity-50">{m['dashboard.financials.settled']()}</li>
				{/if}
			</ul>
		{:else}
			<div class="flex justify-center p-4">
				<span class="loading loading-dots"></span>
			</div>
		{/if}
	</div>
</div>
