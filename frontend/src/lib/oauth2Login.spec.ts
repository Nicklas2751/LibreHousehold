import { describe, it, expect, vi, afterEach } from 'vitest';
import { redirectToOAuth2Login } from './oauth2Login';

describe('redirectToOAuth2Login', () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('redirectToOAuth2Login_withProviderId_navigatesToProviderSpecificPath', () => {
		// given
		const fakeWindow = { location: { href: '' } };
		vi.stubGlobal('window', fakeWindow);

		// when
		redirectToOAuth2Login('google');

		// then
		expect(fakeWindow.location.href).toBe('/oauth2/authorization/google');
	});

	it('redirectToOAuth2Login_withoutProviderId_navigatesToDefaultSpaClientPath', () => {
		// given
		const fakeWindow = { location: { href: '' } };
		vi.stubGlobal('window', fakeWindow);

		// when
		redirectToOAuth2Login();

		// then
		expect(fakeWindow.location.href).toBe('/oauth2/authorization/spa-backend-client');
	});
});
