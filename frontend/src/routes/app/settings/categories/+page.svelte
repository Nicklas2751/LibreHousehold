<script lang="ts">
	import { EditPencilIcon } from '@indaco/svelte-iconoir/edit-pencil';
	import { BinIcon } from '@indaco/svelte-iconoir/bin';
	import { m } from '$lib/paraglide/messages.js';
	import { householdState } from '$lib/stores/householdState.svelte';
	import {
		categories,
		loadCategories,
		addCategory,
		updateCategory,
		deleteCategory
	} from '$lib/stores/categoryStore';
	import { addToast } from '$lib/stores/toastStore';
	import { Toast } from '$lib/toast';
	import PageTitle from '$lib/PageTitle.svelte';

	const householdId = $derived($householdState?.id ?? '');

	// --- Create form ---
	let newName = $state('');
	let newIcon = $state('');
	let creating = $state(false);

	async function handleCreate(event: Event) {
		event.preventDefault();
		if (!householdId) return;
		creating = true;
		try {
			await addCategory(householdId, {
				id: crypto.randomUUID(),
				name: newName.trim(),
				icon: newIcon.trim() || undefined
			});
			addToast(new Toast(m['settings.categories.create_success'](), 'success', 3000));
			newName = '';
			newIcon = '';
		} catch {
			addToast(new Toast(m['settings.categories.create_error'](), 'error', 5000));
		} finally {
			creating = false;
		}
	}

	// --- Edit modal ---
	let editModal: HTMLDialogElement | null = $state(null);
	let editId = $state('');
	let editName = $state('');
	let editIcon = $state('');
	let saving = $state(false);

	function openEditModal(id: string, name: string, icon: string | undefined) {
		editId = id;
		editName = name;
		editIcon = icon ?? '';
		editModal?.showModal();
	}

	async function handleSave() {
		if (!householdId) return;
		saving = true;
		try {
			await updateCategory(householdId, editId, {
				name: editName.trim(),
				icon: editIcon.trim() || undefined
			});
			addToast(new Toast(m['settings.categories.edit_modal.save_success'](), 'success', 3000));
			editModal?.close();
		} catch {
			addToast(new Toast(m['settings.categories.edit_modal.save_error'](), 'error', 5000));
		} finally {
			saving = false;
		}
	}

	// --- Delete modal ---
	let deleteModal: HTMLDialogElement | null = $state(null);
	let deleteId = $state('');
	let deleteName = $state('');
	let deleting = $state(false);

	function openDeleteModal(id: string, name: string) {
		deleteId = id;
		deleteName = name;
		deleteModal?.showModal();
	}

	async function handleDelete() {
		if (!householdId) return;
		deleting = true;
		deleteModal?.close();
		try {
			await deleteCategory(householdId, deleteId);
		} finally {
			deleting = false;
		}
	}
</script>

<div class="h-full overflow-y-auto p-4">
	<PageTitle title={m['settings.categories.title']()} />

	{#await loadCategories(householdId)}
		<div class="mt-6 flex justify-center">
			<span class="loading loading-md loading-dots"></span>
		</div>
	{:then}
		<!-- Category list -->
		<div class="card mt-5 bg-base-200 shadow-sm">
			<div class="card-body p-4">
				<h3 class="mb-3 text-base font-semibold">{m['settings.categories.title']()}</h3>
				{#if $categories.length === 0}
					<p class="text-sm text-base-content/50">{m['settings.categories.empty_hint']()}</p>
				{:else}
					<ul class="flex flex-col gap-2">
						{#each $categories as category (category.id)}
							<li class="flex items-center gap-3">
								<div
									class="flex h-9 w-9 min-w-9 items-center justify-center rounded-full bg-accent/10 text-lg"
								>
									{#if category.icon}
										{category.icon}
									{:else}
										<span class="text-xs text-base-content/30">—</span>
									{/if}
								</div>
								<span class="min-w-0 flex-1 truncate text-sm font-medium">{category.name}</span>
								<button
									class="btn text-base-content/60 btn-ghost btn-xs"
									onclick={() => openEditModal(category.id, category.name, category.icon)}
									aria-label={m['settings.categories.edit_modal.title']()}
								>
									<EditPencilIcon class="h-4 w-4" />
								</button>
								<button
									class="btn text-error btn-ghost btn-xs"
									onclick={() => openDeleteModal(category.id, category.name)}
									aria-label={m['settings.categories.delete_modal.title']()}
									disabled={deleting}
								>
									<BinIcon class="h-4 w-4" />
								</button>
							</li>
						{/each}
					</ul>
				{/if}
			</div>
		</div>

		<!-- Create form -->
		<div class="card mt-4 bg-base-200 shadow-sm">
			<div class="card-body p-4">
				<h3 class="mb-3 text-base font-semibold">{m['settings.categories.create_button']()}</h3>
				<form onsubmit={handleCreate} class="flex flex-col gap-3">
					<div>
						<label class="label" for="new-category-name">
							<span class="label-text">{m['settings.categories.name_label']()}</span>
						</label>
						<input
							id="new-category-name"
							type="text"
							class="input w-full"
							bind:value={newName}
							placeholder={m['settings.categories.name_placeholder']()}
							minlength="2"
							maxlength="50"
							required
						/>
					</div>
					<div>
						<label class="label" for="new-category-icon">
							<span class="label-text">{m['settings.categories.icon_label']()}</span>
						</label>
						<input
							id="new-category-icon"
							type="text"
							class="input w-full"
							bind:value={newIcon}
							placeholder={m['settings.categories.icon_placeholder']()}
							maxlength="10"
						/>
						<p class="mt-1 text-xs text-base-content/50">{m['settings.categories.icon_hint']()}</p>
					</div>
					<button
						type="submit"
						class="btn self-end btn-sm btn-primary"
						disabled={creating || newName.trim().length < 2}
					>
						{#if creating}
							<span class="loading loading-xs loading-spinner"></span>
						{/if}
						{m['settings.categories.create_button']()}
					</button>
				</form>
			</div>
		</div>
	{:catch}
		<p class="mt-6 text-center text-sm text-error">{m['settings.categories.load_error']()}</p>
	{/await}
</div>

<!-- Edit modal -->
<dialog bind:this={editModal} class="modal">
	<div class="modal-box">
		<h3 class="text-lg font-bold">{m['settings.categories.edit_modal.title']()}</h3>
		<div class="mt-4 flex flex-col gap-3">
			<div>
				<label class="label" for="edit-category-name">
					<span class="label-text">{m['settings.categories.name_label']()}</span>
				</label>
				<input
					id="edit-category-name"
					type="text"
					class="input w-full"
					bind:value={editName}
					minlength="2"
					maxlength="50"
					required
				/>
			</div>
			<div>
				<label class="label" for="edit-category-icon">
					<span class="label-text">{m['settings.categories.icon_label']()}</span>
				</label>
				<input
					id="edit-category-icon"
					type="text"
					class="input w-full"
					bind:value={editIcon}
					maxlength="10"
					placeholder={m['settings.categories.icon_placeholder']()}
				/>
				<p class="mt-1 text-xs text-base-content/50">{m['settings.categories.icon_hint']()}</p>
			</div>
		</div>
		<div class="modal-action">
			<form method="dialog">
				<button class="btn btn-ghost btn-sm">{m['settings.categories.edit_modal.cancel']()}</button>
			</form>
			<button
				class="btn btn-sm btn-primary"
				onclick={handleSave}
				disabled={saving || editName.trim().length < 2}
			>
				{#if saving}
					<span class="loading loading-xs loading-spinner"></span>
				{/if}
				{m['settings.categories.edit_modal.save_button']()}
			</button>
		</div>
	</div>
	<form method="dialog" class="modal-backdrop">
		<button>close</button>
	</form>
</dialog>

<!-- Delete modal -->
<dialog bind:this={deleteModal} class="modal">
	<div class="modal-box">
		<h3 class="text-lg font-bold">{m['settings.categories.delete_modal.title']()}</h3>
		<p class="py-4 text-sm">
			{m['settings.categories.delete_modal.text']({ name: deleteName })}
		</p>
		<div class="modal-action">
			<form method="dialog">
				<button class="btn btn-ghost btn-sm"
					>{m['settings.categories.delete_modal.cancel']()}</button
				>
			</form>
			<button class="btn btn-sm btn-error" onclick={handleDelete}>
				{m['settings.categories.delete_modal.confirm']()}
			</button>
		</div>
	</div>
	<form method="dialog" class="modal-backdrop">
		<button>close</button>
	</form>
</dialog>
