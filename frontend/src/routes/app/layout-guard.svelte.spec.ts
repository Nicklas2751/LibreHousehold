import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import { createRawSnippet } from 'svelte';
import AppLayout from './+layout.svelte';
import {
	session,
	setAuthenticated,
	setGuest,
	setSessionExpired
} from '#lib/stores/sessionState.svelte.ts';

const { mockGoto, mockPage } = vi.hoisted(() => ({
	mockGoto: vi.fn(),
	mockPage: { url: new URL('http://localhost:5173/app/tasks') }
}));

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

vi.mock('$app/state', () => ({ page: mockPage }));

const currentUser = {
	member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
	household: { id: 'household-id', name: 'Die Testfamilie' },
	preferences: {},
	emailVerified: true,
	socialLoginOnly: false,
	sessionTimeoutSeconds: 1800
};

const childrenSnippet = createRawSnippet(() => ({
	render: () => `<div>protected content</div>`
}));

describe('App layout guard', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('guest_navigatesToLoginWithReturnToAndReplaceState', async () => {
		// given
		setGuest();

		// when
		render(AppLayout, { children: childrenSnippet });

		// then
		await vi.waitFor(() =>
			expect(mockGoto).toHaveBeenCalledWith('/login?returnTo=%2Fapp%2Ftasks', {
				replace: true
			})
		);
	});

	it('sessionExpired_navigatesToLoginWithReturnToAndExpiryReason', async () => {
		// given
		setSessionExpired();

		// when
		render(AppLayout, { children: childrenSnippet });

		// then
		await vi.waitFor(() =>
			expect(mockGoto).toHaveBeenCalledWith(
				'/login?returnTo=%2Fapp%2Ftasks&reason=session-expired',
				{ replace: true }
			)
		);
	});

	it('guest_doesNotRenderChildren', async () => {
		// given
		setGuest();

		// when
		render(AppLayout, { children: childrenSnippet });

		// then
		await expect.element(page.getByText('protected content')).not.toBeInTheDocument();
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
		expect(mockGoto).not.toHaveBeenCalled();
	});

	it('status authenticated — renders children', async () => {
		// given
		setAuthenticated(currentUser);

		// when
		render(AppLayout, { children: childrenSnippet });

		// then
		await expect.element(page.getByText('protected content')).toBeVisible();
		expect(mockGoto).not.toHaveBeenCalled();
	});
});
