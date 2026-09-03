// Never navigate the user directly to our own /login SPA route. The backend is both the
// Authorization Server and its own OAuth2 client (ADR-013): authentication alone (POST /login)
// only yields an AccountPrincipal, not the AccountOidcPrincipal business API calls require. Only
// entering through this endpoint lets the server save the request, redirect to /login for the
// missing session, and then automatically resume the full Authorization Code + PKCE roundtrip
// after a successful login.
export const OAUTH2_LOGIN_PATH = '/oauth2/authorization/spa-backend-client';

export function redirectToOAuth2Login(): void {
	window.location.href = OAUTH2_LOGIN_PATH;
}

// Used right after household setup / invite join: the backend has already authenticated the
// Authorization Server session at that point (see AccountSessionAuthenticator), so this silently
// completes the Authorization Code + PKCE roundtrip without showing the login form — fetch()
// follows the whole redirect chain internally instead of navigating the browser, matching how the
// login form's own POST /login submission completes the same roundtrip (see login/+page.svelte).
export async function completeSilentOAuth2Login(): Promise<void> {
	await fetch(OAUTH2_LOGIN_PATH, { credentials: 'include' });
}
