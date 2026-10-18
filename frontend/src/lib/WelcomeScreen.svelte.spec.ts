import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import WelcomeScreen from './WelcomeScreen.svelte';
import { setAuthenticated, setGuest } from '$lib/stores/sessionState.svelte';

const { mockGoto } = vi.hoisted(() => ({
	mockGoto: vi.fn()
}));

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

vi.mock('./paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('./paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

describe('WelcomeScreen', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('authenticated session — navigates to the dashboard', async () => {
		// given
		setAuthenticated({
			member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
			household: { id: 'household-id', name: 'Die Testfamilie' },
			preferences: {},
			emailVerified: true,
			socialLoginOnly: false
		});

		// when
		render(WelcomeScreen);

		// then
		await vi.waitFor(() => expect(mockGoto).toHaveBeenCalledWith('/app/dashboard'));
	});

	it('guest session — stays on the welcome screen', async () => {
		// given
		setGuest();

		// when
		render(WelcomeScreen);

		// then
		await new Promise((resolve) => setTimeout(resolve, 50));
		expect(mockGoto).not.toHaveBeenCalled();
	});
});
