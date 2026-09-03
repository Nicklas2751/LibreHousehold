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
