import { m } from '$lib/paraglide/messages.js';
import { Toast } from '$lib/toast';
import { addToast } from '$lib/stores/toastStore';
import { apiConfiguration } from '$lib/api/httpClient';
import { extractErrorStatus } from '$lib/api/errorStatus';
import { SessionApi } from '../../generated-sources/openapi';
import { setAuthenticated, setGuest } from './sessionState.svelte';
import { updateHouseholdState } from './householdState.svelte';
import { updateUserState } from './userState';

const HTTP_STATUS_UNAUTHORIZED = 401;

const sessionApi = new SessionApi(apiConfiguration);

export async function bootstrapSession(): Promise<void> {
	try {
		const currentUser = await sessionApi.getCurrentUser();
		setAuthenticated(currentUser);
		updateHouseholdState(currentUser.household);
		updateUserState(currentUser.member);
	} catch (err: unknown) {
		setGuest();
		if (extractErrorStatus(err) !== HTTP_STATUS_UNAUTHORIZED) {
			addToast(new Toast(m['session.bootstrap_error'](), 'error'));
		}
	}
}
