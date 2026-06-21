<script lang="ts">
	import { page } from '$app/state';
	import { m } from '$lib/paraglide/messages.js';
	import { afterNavigate, goto } from '$app/navigation';
	import PageTitleActionBar from '$lib/PageTitleActionBar.svelte';
	import {
		addExpense,
		deleteExpense,
		expenses,
		findExpense,
		loadExpenses,
		updateExpense
	} from '$lib/stores/expenseStore';
	import { v4 as uuidv4 } from 'uuid';
	import { findMember, loadMembers, members } from '$lib/stores/memberStore';
	import { householdState } from '$lib/stores/householdState.svelte';
	import { userState } from '$lib/stores/userState';
	import { categories, loadCategories } from '$lib/stores/categoryStore';
	import { financialSummary, loadFinancialSummary } from '$lib/stores/financialStore';
	import type { Expense, Member } from '../../../../generated-sources/openapi';
	import { EditIcon } from '@indaco/svelte-iconoir/edit';
	import { BinIcon } from '@indaco/svelte-iconoir/bin';
	import { isExpenseMutable } from '$lib/expenseLogic';
	import MobileItemList from '$lib/MobileItemList.svelte';
	import DesktopItemList from '$lib/DesktopItemList.svelte';

	const today = new Date().toISOString().split('T')[0];
	let date: string = $state(today);

	let isShowForm: boolean = $state(false);
	let expenseToEdit: Expense | null = $state(null);

	let detailExpense: Expense | null = $state(null);
	let detailModal: HTMLDialogElement | null = $state(null);

	let deleteModal: HTMLDialogElement | null = $state(null);
	let expenseToDelete: Expense | null = $state(null);

	// Load financial summary, categories and members on page open
	$effect(() => {
		if ($householdState && $userState) {
			loadFinancialSummary($householdState.id, $userState.id);
			loadCategories($householdState.id);
			loadMembers($householdState.id);
		}
	});

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
			let found = $expenses.find((e) => e.id === id);

			if (!found) {
				found = await findExpense($householdState.id, id);
			}

			if (found) {
				expenseToEdit = found;
				date = found.date.toISOString().split('T')[0];
			} else {
				// Not found, maybe deleted or wrong ID
				await goto('/app/expenses');
			}
		}
	}

	async function saveExpense(event: Event) {
		event.preventDefault();
		if ($householdState) {
			const form = event.target as HTMLFormElement;

			// Get splitBetween members (checkboxes)
			const splitBetweenCheckboxes = form.querySelectorAll('input[name="splitBetween"]:checked');
			const splitBetween = Array.from(splitBetweenCheckboxes).map(
				(cb) => (cb as HTMLInputElement).value
			);

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
		await goto('/app/expenses');
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

	function canEditExpense(expense: Expense): boolean {
		return expense.paidBy === $userState?.id && isExpenseMutable(expense);
	}

	function isOwnExpense(expense: Expense): boolean {
		return expense.paidBy === $userState?.id;
	}

	function isUserInvolved(expense: Expense): boolean {
		const split = expense.splitBetween ?? [];
		const userId = $userState?.id;
		if (!userId) return true;
		if (expense.paidBy === userId) return true;
		if (split.length === 0) return true;
		return split.includes(userId);
	}

	function getUserShare(expense: Expense): number | null {
		const split = expense.splitBetween ?? [];
		const userId = $userState?.id;
		if (!userId || split.length === 0) return null;
		if (!split.includes(userId)) return null;
		return expense.amount / split.length;
	}

	function getSplitLabel(expense: Expense): string {
		const split = expense.splitBetween ?? [];
		if (split.length === 0) return m['expenses.split_all_members']();
		return split.map((id) => $members.find((member) => member.id === id)?.name ?? '…').join(', ');
	}

	async function handleDeleteExpense(expenseId: string) {
		if ($householdState) {
			await deleteExpense($householdState.id, expenseId);
			if (expenseToEdit?.id === expenseId) {
				await goto('/app/expenses');
			}
		}
	}

	async function handleEditClick(expenseId: string) {
		await goto(`/app/expenses/${expenseId}`);
	}

	function showDetailModal(expense: Expense) {
		detailExpense = expense;
		detailModal?.showModal();
	}
</script>

<div class="md:flex md:h-full md:flex-col">
	<div class="shrink-0">
		<PageTitleActionBar
			title={isShowForm
				? expenseToEdit
					? m['expenses.edit.title']()
					: m['expenses.new.title']()
				: m['expenses.title']()}
			buttonText={isShowForm
				? m['expenses.new.cancel_button']()
				: m['expenses.create_expense_button']()}
			buttonOnClick={async () =>
				isShowForm ? await goto('/app/expenses') : await goto('/app/expenses/new')}
		/>
	</div>

	<div class="p-5 md:flex md:min-h-0 md:flex-1 md:flex-col md:overflow-hidden">
		{#if isShowForm}
			<div class="card mt-10 bg-base-200 drop-shadow-xl card-border">
				<form class="card-body grid md:grid-cols-2 md:gap-x-4" onsubmit={saveExpense}>
					<fieldset class="fieldset md:col-span-2">
						<legend class="fieldset-legend">{m['expenses.new.expense_title_label']()} *</legend>
						<input
							name="expenseTitle"
							type="text"
							class="validator input w-full"
							placeholder={m['expenses.new.expense_title_placeholder']()}
							minlength="3"
							maxlength="200"
							value={expenseToEdit?.title ?? ''}
							required
						/>
						<div class="validator-hint hidden">{m['expenses.new.expense_title_error']()}</div>
					</fieldset>

					<fieldset class="fieldset">
						<legend class="fieldset-legend">{m['expenses.new.amount_label']()} *</legend>
						<label class="input-bordered input flex items-center gap-2">
							<input
								name="amount"
								type="number"
								step="0.01"
								min="0.01"
								max="99999999.99"
								class="validator grow"
								required
								value={expenseToEdit?.amount ?? ''}
							/>
							<span class="text-base-content/60">€</span>
						</label>
						<div class="validator-hint hidden">{m['expenses.new.amount_error']()}</div>
					</fieldset>

					<fieldset class="fieldset">
						<legend class="fieldset-legend">{m['expenses.new.date_label']()} *</legend>
						<input type="date" name="date" class="input w-full" max={today} value={date} required />
					</fieldset>

					<fieldset class="fieldset">
						<legend class="fieldset-legend">{m['expenses.new.paid_by_label']()} *</legend>
						{#if $householdState}
							{#await loadMembers($householdState.id)}
								<span class="loading loading-dots"></span>
							{:then}
								<select name="paidBy" class="select w-full" required>
									<option value="" selected={!expenseToEdit?.paidBy && !$userState?.id}
										>{m['expenses.new.paid_by_select_placeholder']()}</option
									>
									{#each $members as member (member.id)}
										<option
											value={member.id}
											selected={member.id === (expenseToEdit?.paidBy ?? $userState?.id)}
										>
											{member.name}
										</option>
									{/each}
								</select>
							{/await}
						{/if}
					</fieldset>

					<fieldset class="fieldset">
						<legend class="fieldset-legend">{m['expenses.new.category_label']()} *</legend>
						{#if $householdState}
							{#await loadCategories($householdState.id)}
								<span class="loading loading-dots"></span>
							{:then}
								<select name="categoryId" class="select w-full" required>
									<option value="" selected={!expenseToEdit?.categoryId}
										>{m['expenses.new.category_select_placeholder']()}</option
									>
									{#each $categories as category (category.id)}
										<option
											value={category.id}
											selected={category.id === expenseToEdit?.categoryId}
										>
											{category.icon ? `${category.icon} ${category.name}` : category.name}
										</option>
									{/each}
								</select>
								{#if $categories.length === 0}
									<div class="mt-1 text-xs text-base-content/60">
										{m['expenses.new.no_categories_hint']()}
									</div>
								{/if}
							{/await}
						{/if}
					</fieldset>

					<fieldset class="fieldset md:col-span-2">
						<legend class="fieldset-legend">{m['expenses.new.split_between_label']()}</legend>
						<p class="mb-2 text-xs text-base-content/60">
							{m['expenses.new.split_between_hint']()}
						</p>
						{#if $householdState}
							{#await loadMembers($householdState.id)}
								<span class="loading loading-dots"></span>
							{:then}
								<div class="flex flex-wrap gap-3">
									{#each $members as member (member.id)}
										<label class="label cursor-pointer gap-2">
											<input
												type="checkbox"
												name="splitBetween"
												value={member.id}
												class="checkbox checkbox-sm"
												checked={expenseToEdit?.splitBetween
													? expenseToEdit.splitBetween.includes(member.id)
													: false}
											/>
											<span class="label-text">{member.name}</span>
										</label>
									{/each}
								</div>
							{/await}
						{/if}
					</fieldset>

					<fieldset class="fieldset md:col-span-2">
						<legend class="fieldset-legend">{m['expenses.new.notes_label']()}</legend>
						<textarea
							name="notes"
							class="validator textarea h-24 w-full"
							placeholder={m['expenses.new.notes_placeholder']()}
							maxlength="1000">{expenseToEdit?.notes ?? ''}</textarea
						>
						<div class="validator-hint hidden">{m['expenses.new.notes_max_error']()}</div>
					</fieldset>

					<button type="submit" class="btn btn-primary">
						{expenseToEdit ? m['expenses.edit.save_button']() : m['expenses.new.create_button']()}
					</button>
				</form>
			</div>
		{/if}

		<!-- Balance summary banner -->
		{#if !isShowForm && $financialSummary}
			<div class="mb-3 flex flex-wrap gap-2">
				{#if $financialSummary.youOwe > 0.001}
					<div class="badge badge-error badge-lg gap-1 font-medium">
						{m['expenses.balance_summary.you_owe']({
							amount: $financialSummary.youOwe.toFixed(2) + ' €'
						})}
					</div>
				{/if}
				{#if $financialSummary.owedToYou > 0.001}
					<div class="badge badge-success badge-lg gap-1 font-medium">
						{m['expenses.balance_summary.owed_to_you']({
							amount: $financialSummary.owedToYou.toFixed(2) + ' €'
						})}
					</div>
				{/if}
			</div>
		{/if}

		<!-- Mobile Expense List -->
		<MobileItemList
			loadItems={loadExpenses}
			items={$expenses}
			noItemsMessage={m['expenses.no_expenses']()}
		>
			{#snippet singleItemView(expense)}
				<div
					class="flex flex-1 cursor-pointer items-center justify-between gap-2"
					class:opacity-50={!isExpenseMutable(expense) || !isUserInvolved(expense)}
					role="button"
					tabindex="0"
					onclick={() => showDetailModal(expense)}
					onkeydown={(e) => e.key === 'Enter' && showDetailModal(expense)}
				>
					<div>
						<div class="flex items-center gap-1">
							<span class="font-medium">
								{expense.title}
							</span>
							{#if !isExpenseMutable(expense)}
								<span class="badge badge-ghost badge-xs"
									>{m['expenses.detail.settled_badge']()}</span
								>
							{/if}
						</div>
						<p class="text-xs text-base-content/60">
							{#await getMember(expense.paidBy)}
								<span class="loading loading-xs loading-dots"></span>
							{:then member}
								{#if member}{member.name} •{/if}
							{/await}
							{new Date(expense.date).toLocaleDateString('de-DE')}
						</p>
						<p class="text-xs text-base-content/40">{getSplitLabel(expense)}</p>
					</div>
					<div class="shrink-0 text-right">
						<div
							class="font-bold"
							class:text-success={isOwnExpense(expense)}
							class:text-error={!isOwnExpense(expense)}
						>
							{isOwnExpense(expense) ? '' : '−'}{expense.amount.toFixed(2)} €
						</div>
						{#if getUserShare(expense) !== null}
							<div class="text-xs text-base-content/60">
								{m['expenses.user_share_label']()}: {getUserShare(expense)!.toFixed(2)} €
							</div>
						{/if}
					</div>
				</div>
			{/snippet}
		</MobileItemList>

		<!-- Desktop Expense List -->
		<DesktopItemList
			loadItems={loadExpenses}
			items={$expenses}
			noItemsMessage={m['expenses.no_expenses']()}
		>
			{#snippet itemContent(expense)}
				<div
					class="flex flex-col cursor-pointer"
					class:opacity-50={!isExpenseMutable(expense) || !isUserInvolved(expense)}
					role="button"
					tabindex="0"
					onclick={() => showDetailModal(expense)}
					onkeydown={(e) => e.key === 'Enter' && showDetailModal(expense)}
				>
					<div class="flex items-center gap-1">
						<span class="font-medium">
							{expense.title}
						</span>
						{#if !isExpenseMutable(expense)}
							<span class="badge badge-ghost badge-xs">{m['expenses.detail.settled_badge']()}</span>
						{/if}
					</div>
					{#await getMember(expense.paidBy)}
						<span class="loading loading-xs loading-dots"></span>
					{:then member}
						{#if member}
							<span class="flex items-center gap-1 text-xs font-semibold opacity-60">
								{member.name}
							</span>
						{/if}
					{/await}
					<span class="text-xs text-base-content/60">
						{new Date(expense.date).toLocaleDateString('de-DE')}
					</span>
					<span class="text-xs text-base-content/40">{getSplitLabel(expense)}</span>
				</div>
				<div class="shrink-0 text-right">
					<div
						class="text-lg font-bold"
						class:text-success={isOwnExpense(expense)}
						class:text-error={!isOwnExpense(expense)}
						class:opacity-50={!isExpenseMutable(expense) || !isUserInvolved(expense)}
					>
						{isOwnExpense(expense) ? '' : '−'}{expense.amount.toFixed(2)} €
					</div>
					{#if getUserShare(expense) !== null}
						<div
							class="text-xs text-base-content/60"
							class:opacity-50={!isExpenseMutable(expense) || !isUserInvolved(expense)}
						>
							{m['expenses.user_share_label']()}: {getUserShare(expense)!.toFixed(2)} €
						</div>
					{/if}
				</div>
			{/snippet}
		</DesktopItemList>
	</div>
</div>

<!-- Expense Detail Modal -->
<dialog class="modal" bind:this={detailModal}>
	<div class="modal-box w-full max-w-md">
		{#if detailExpense}
			<h3 class="mb-4 text-lg font-bold">{detailExpense.title}</h3>

			<div class="space-y-3 text-sm">
				<div class="flex items-center justify-between">
					<span class="opacity-60">{m['expenses.new.amount_label']()}</span>
					<span
						class="font-bold text-base"
						class:text-success={isOwnExpense(detailExpense)}
						class:text-error={!isOwnExpense(detailExpense)}
					>
						{isOwnExpense(detailExpense) ? '' : '−'}{detailExpense.amount.toFixed(2)} €
					</span>
				</div>

				{#if getUserShare(detailExpense) !== null}
					<div class="flex items-center justify-between">
						<span class="opacity-60">{m['expenses.user_share_label']()}</span>
						<span class="font-medium">{getUserShare(detailExpense)!.toFixed(2)} €</span>
					</div>
				{/if}

				<div class="flex items-center justify-between">
					<span class="opacity-60">{m['expenses.detail.paid_by_label']()}</span>
					{#await getMember(detailExpense.paidBy)}
						<span class="loading loading-xs loading-dots"></span>
					{:then member}
						<span class="font-medium">{member?.name ?? '—'}</span>
					{/await}
				</div>

				<div class="flex items-center justify-between">
					<span class="opacity-60">{m['expenses.detail.date_label']()}</span>
					<span>{new Date(detailExpense.date).toLocaleDateString('de-DE')}</span>
				</div>

				<div class="flex items-center justify-between">
					<span class="opacity-60">{m['expenses.detail.category_label']()}</span>
					<span>
						{#if $categories.length > 0}
							{@const cat = $categories.find((c) => c.id === detailExpense?.categoryId)}
							{cat ? (cat.icon ? `${cat.icon} ${cat.name}` : cat.name) : '—'}
						{:else}
							—
						{/if}
					</span>
				</div>

				<div class="flex items-center justify-between">
					<span class="opacity-60">{m['expenses.split_between_label']()}</span>
					<span class="text-right">{getSplitLabel(detailExpense)}</span>
				</div>

				{#if !isExpenseMutable(detailExpense)}
					<div class="flex items-center justify-between">
						<span class="opacity-60">Status</span>
						<span class="badge badge-ghost">{m['expenses.detail.settled_badge']()}</span>
					</div>
				{/if}

				<div class="divider my-2"></div>

				<div>
					<p class="mb-1 opacity-60">{m['expenses.detail.notes_label']()}</p>
					<p class="text-base-content/80">
						{detailExpense.notes ?? m['expenses.detail.no_notes']()}
					</p>
				</div>
			</div>
		{/if}

		<div class="modal-action flex-wrap gap-2">
			{#if detailExpense && canEditExpense(detailExpense)}
				<button
					class="btn btn-outline btn-sm"
					onclick={() => {
						const id = detailExpense!.id;
						detailModal?.close();
						handleEditClick(id);
					}}
				>
					<EditIcon />{m['expenses.edit.title']()}
				</button>
				<button
					class="btn btn-error btn-outline btn-sm"
					onclick={() => {
						expenseToDelete = detailExpense;
						detailModal?.close();
						deleteModal?.showModal();
					}}
				>
					<BinIcon />{m['expenses.delete_button']()}
				</button>
			{/if}
			<form method="dialog" class="ml-auto">
				<button class="btn">{m['expenses.detail.close_button']()}</button>
			</form>
		</div>
	</div>
	<form method="dialog" class="modal-backdrop">
		<button>close</button>
	</form>
</dialog>

<!-- Delete Confirmation Modal -->
<dialog bind:this={deleteModal} class="modal">
	<div class="modal-box">
		<h3 class="text-lg font-bold">{m['expenses.delete_modal.title']()}</h3>
		<p class="py-4">
			{m['expenses.delete_modal.text']({ title: expenseToDelete?.title ?? '' })}
		</p>
		<div class="modal-action">
			<form method="dialog">
				<button class="btn">{m['expenses.delete_modal.cancel']()}</button>
			</form>
			<button
				class="btn btn-error"
				onclick={async () => {
					if (expenseToDelete) {
						await handleDeleteExpense(expenseToDelete.id);
						deleteModal?.close();
						expenseToDelete = null;
					}
				}}
			>
				{m['expenses.delete_modal.confirm']()}
			</button>
		</div>
	</div>
	<form method="dialog" class="modal-backdrop">
		<button>close</button>
	</form>
</dialog>
