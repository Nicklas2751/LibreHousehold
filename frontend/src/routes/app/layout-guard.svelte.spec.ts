import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import { createRawSnippet } from 'svelte';
import AppLayout from './+layout.svelte';
import { session, setAuthenticated, setGuest } from '$lib/stores/sessionState.svelte';

const { mockRedirectToOAuth2Login } = vi.hoisted(() => ({
	mockRedirectToOAuth2Login: vi.fn()
}));

vi.mock('$lib/oauth2Login', () => ({
	OAUTH2_LOGIN_PATH: '/oauth2/authorization/spa-backend-client',
	redirectToOAuth2Login: mockRedirectToOAuth2Login
}));

const currentUser = {
	member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
	household: { id: 'household-id', name: 'Die Testfamilie' },
	preferences: {}
};

const childrenSnippet = createRawSnippet(() => ({
	render: () => `<div>protected content</div>`
}));

describe('App layout guard', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('status guest — redirects to the OAuth2 login entry point and does not render children', async () => {
		// given
		setGuest();

		// when
		render(AppLayout, { children: childrenSnippet });

		// then
		await expect.element(page.getByText('protected content')).not.toBeInTheDocument();
		await vi.waitFor(() => expect(mockRedirectToOAuth2Login).toHaveBeenCalled());
	});

	it('status bootstrapping — shows a loading indicator and does not render children', async () => {
		// given
		session.status = 'bootstrapping';
		session.currentUser = null;

		// when
		render(AppLayout, { children: childrenSnippet });

		// then
		await expect.element(page.getByText('protected content')).not.toBeInTheDocument();
		expect(document.querySelector('.loading-spinner')).not.toBeNull();
		expect(mockRedirectToOAuth2Login).not.toHaveBeenCalled();
	});

	it('status authenticated — renders children', async () => {
		// given
		setAuthenticated(currentUser);

		// when
		render(AppLayout, { children: childrenSnippet });

		// then
		await expect.element(page.getByText('protected content')).toBeVisible();
		expect(mockRedirectToOAuth2Login).not.toHaveBeenCalled();
	});
});
