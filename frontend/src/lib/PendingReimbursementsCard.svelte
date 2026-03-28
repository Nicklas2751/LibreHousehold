<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import { reimbursements, updateReimbursement } from '$lib/stores/reimbursementStore';
	import { findMember, members } from '$lib/stores/memberStore';
	import { householdState } from '$lib/stores/householdState.svelte';
	import { userState } from '$lib/stores/userState';
	import { fade } from 'svelte/transition';
	import type { Member, Reimbursement } from '../generated-sources/openapi';
	import { loadFinancialSummary, loadMemberBalances } from '$lib/stores/financialStore';

	// Filter logic: reimbursements where I am the creditor and status is PENDING
	const pendingReimbursements = $derived(
		$reimbursements.filter(
			(r) => $userState && r.creditorId === $userState.id && r.status === 'PENDING'
		)
	);

	async function getMember(id: string): Promise<Member | undefined> {
		if (!id) return undefined;
		let member = $members.find((m) => m.id === id);
		if (!member && $householdState) {
			member = await findMember($householdState.id, id);
		}
		return member;
	}

	async function handleConfirm(reimbursement: Reimbursement) {
		if ($householdState && $userState) {
			await updateReimbursement($householdState.id, reimbursement.id, { status: 'CONFIRMED' });
			await loadFinancialSummary($householdState.id, $userState.id);
			await loadMemberBalances($householdState.id, $userState.id);
		}
	}

	async function handleReject(reimbursement: Reimbursement) {
		if ($householdState && $userState) {
			await updateReimbursement($householdState.id, reimbursement.id, { status: 'REJECTED' });
			await loadFinancialSummary($householdState.id, $userState.id);
			await loadMemberBalances($householdState.id, $userState.id);
		}
	}
</script>

<div class="card bg-base-200 drop-shadow-xl card-border md:col-span-2">
	<div class="card-body">
		<h2 class="card-title">{m['dashboard.pending_reimbursements_card.title']()}</h2>

		{#if pendingReimbursements.length === 0}
			<div class="flex justify-center p-4 italic opacity-50">
				{m['dashboard.pending_reimbursements_card.no_pending']()}
			</div>
		{:else}
			<div class="mt-2 space-y-3">
				{#each pendingReimbursements as item (item.id)}
					<div class="rounded-lg border border-base-300 bg-base-100 p-3 shadow-sm" transition:fade>
						<div class="mb-2 flex items-center gap-2">
							{#await getMember(item.debtorId)}
								<span class="loading loading-xs loading-dots"></span>
							{:then member}
								<span class="font-medium">
									{m['dashboard.pending_reimbursements_card.incoming_text']({
										name: member?.name || 'Unknown',
										amount: item.amount.toFixed(2) + ' €'
									})}
								</span>
							{/await}
						</div>
						<div class="flex justify-end gap-2">
							<button class="btn btn-outline btn-sm btn-error" onclick={() => handleReject(item)}>
								{m['dashboard.pending_reimbursements_card.reject_button']()}
							</button>
							<button class="btn btn-sm btn-success" onclick={() => handleConfirm(item)}>
								{m['dashboard.pending_reimbursements_card.confirm_button']()}
							</button>
						</div>
					</div>
				{/each}
			</div>
		{/if}
	</div>
</div>
