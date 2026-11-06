import type { CurrentUser } from '../../generated-sources/openapi';

export type SessionStatus = 'bootstrapping' | 'authenticated' | 'guest';

export const session: { status: SessionStatus; currentUser: CurrentUser | null; expired: boolean } =
	$state({
		status: 'bootstrapping',
		currentUser: null,
		expired: false
	});

export function setAuthenticated(user: CurrentUser) {
	session.status = 'authenticated';
	session.currentUser = user;
	session.expired = false;
}

export function setGuest() {
	session.status = 'guest';
	session.currentUser = null;
	session.expired = false;
}

// A guest whose session ended while using the app, as opposed to one who was never signed in.
export function setSessionExpired() {
	session.status = 'guest';
	session.currentUser = null;
	session.expired = true;
}
