import type { CurrentUser } from '../../generated-sources/openapi';

export type SessionStatus = 'bootstrapping' | 'authenticated' | 'guest';

export const session: { status: SessionStatus; currentUser: CurrentUser | null } = $state({
	status: 'bootstrapping',
	currentUser: null
});

export function setAuthenticated(user: CurrentUser) {
	session.status = 'authenticated';
	session.currentUser = user;
}

export function setGuest() {
	session.status = 'guest';
	session.currentUser = null;
}
