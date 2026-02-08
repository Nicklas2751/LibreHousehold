<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';
    import {findMember, members} from '$lib/stores/memberStore';
    import {householdState} from "$lib/stores/householdState.svelte";
    import type {FinancialSummary, Member, MemberBalance} from '../generated-sources/openapi';

    let {summary, balances}: { summary: FinancialSummary | null, balances: MemberBalance[] } = $props();

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
</script>

<div class="card card-border bg-base-200 drop-shadow-xl md:col-span-2">
    <div class="card-body">
        <h2 class="card-title">{m["dashboard.financials.title"]()}</h2>

        {#if summary}
            <div class="stat p-0">
                <div class="stat-title">{m["dashboard.financials.total_balance_label"]()}</div>
                <!-- Logic for net balance: (owedToYou - youOwe) -->
                <div class="stat-value" class:text-success={(summary.owedToYou - summary.youOwe) > 0} class:text-error={(summary.owedToYou - summary.youOwe) < 0}>
                    {(summary.owedToYou - summary.youOwe).toFixed(2)} €
                </div>
            </div>

            <div class="divider my-2"></div>

            <ul class="space-y-2">
                {#each balances as balance}
                    {#if Math.abs(balance.balance) > 0.001}
                        <li class="flex justify-between items-center text-sm">
                        <span class="opacity-80">
                            {#if $householdState}
                                {#await getMember(balance.memberId)}
                                    <span class="loading loading-dots loading-xs"></span>
                                {:then member}
                                    {#if member}
                                        {#if balance.balance > 0}
                                            {m["dashboard.financials.owed_to_you"]({name: member.name})}
                                        {:else}
                                            {m["dashboard.financials.you_owe"]({name: member.name})}
                                        {/if}
                                    {/if}
                                {/await}
                            {/if}
                        </span>
                        <span class="font-bold" class:text-success={balance.balance > 0} class:text-error={balance.balance < 0}>
                            {Math.abs(balance.balance).toFixed(2)} €
                        </span>
                        </li>
                    {/if}
                {/each}
                {#if balances.every(b => Math.abs(b.balance) < 0.001)}
                    <li class="text-center opacity-50 italic">{m["dashboard.financials.settled"]()}</li>
                {/if}
            </ul>
        {:else}
            <div class="flex justify-center p-4">
                <span class="loading loading-dots"></span>
            </div>
        {/if}
    </div>
</div>



