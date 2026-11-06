import { m } from '$lib/paraglide/messages.js';
import { Toast } from '$lib/toast';
import { addToast } from '$lib/stores/toastStore';
import { apiConfiguration } from '$lib/api/httpClient';
import { extractErrorStatus } from '$lib/api/errorStatus';
import { SessionApi } from '../../generated-sources/openapi';
import { setAuthenticated, setGuest } from './sessionState.svelte';
import { updateHouseholdState } from './householdState.svelte';
import { updateUserState } from './userState';
import { applyPreferences } from './settingsStore';

const HTTP_STATUS_UNAUTHORIZED = 401;

const sessionApi = new SessionApi(apiConfiguration);

export async function bootstrapSession(options: { silent?: boolean } = {}): Promise<void> {
	try {
		const currentUser = await sessionApi.getCurrentUser();
		setAuthenticated(currentUser);
		updateHouseholdState(currentUser.household);
		updateUserState(currentUser.member);
		applyPreferences(currentUser.preferences);
	} catch (err: unknown) {
		if (extractErrorStatus(err) === HTTP_STATUS_UNAUTHORIZED) {
			setGuest();
			return;
		}
		// Any other failure says nothing about the session, so a signed-in user stays signed in.
		if (!options.silent) {
			addToast(new Toast(m['session.bootstrap_error'](), 'error'));
		}
	}
}
