// The setup wizard asks for the household before the account. Registering the account via a social
// login provider leaves the SPA with a full page redirect, so the household entered so far is kept
// in sessionStorage for the way back. It is form input only, never session or auth state.
const SETUP_DRAFT_KEY = 'librehousehold.setupDraft';

export interface SetupDraft {
	householdId: string;
	householdName: string;
	householdImage: string;
}

export function saveSetupDraft(draft: SetupDraft): void {
	if (tryStore(draft)) {
		return;
	}
	// A large household image can exceed the storage quota; the name alone is still worth keeping.
	tryStore({ ...draft, householdImage: '' });
}

export function takeSetupDraft(): SetupDraft | null {
	try {
		const storedDraft = sessionStorage.getItem(SETUP_DRAFT_KEY);
		sessionStorage.removeItem(SETUP_DRAFT_KEY);
		return storedDraft ? (JSON.parse(storedDraft) as SetupDraft) : null;
	} catch {
		return null;
	}
}

function tryStore(draft: SetupDraft): boolean {
	try {
		sessionStorage.setItem(SETUP_DRAFT_KEY, JSON.stringify(draft));
		return true;
	} catch {
		return false;
	}
}
