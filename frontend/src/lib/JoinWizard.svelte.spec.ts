import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import JoinWizard from './JoinWizard.svelte';

const {
	mockResolveInvite,
	mockJoinHousehold,
	mockGetAuthProviders,
	mockGoto,
	mockNavigateToSocialProvider
} = vi.hoisted(() => ({
	mockResolveInvite: vi.fn(),
	mockJoinHousehold: vi.fn(),
	mockGetAuthProviders: vi.fn(),
	mockGoto: vi.fn(),
	mockNavigateToSocialProvider: vi.fn()
}));

vi.mock('../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../generated-sources/openapi')>();
	return {
		...original,
		MembersApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.resolveInvite = mockResolveInvite;
			this.joinHousehold = mockJoinHousehold;
		}),
		AuthApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.getAuthProviders = mockGetAuthProviders;
		})
	};
});

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

vi.mock('$lib/setupWizardLogic', async (importOriginal) => {
	const original = await importOriginal<typeof import('./setupWizardLogic')>();
	return { ...original, navigateToSocialProvider: mockNavigateToSocialProvider };
});

vi.mock('./paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('./paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

const validInviteInfo = {
	householdId: '11111111-1111-1111-1111-111111111111',
	householdName: 'Die Müllers',
	validUntil: new Date('2099-12-31')
};

async function fillLocalForm(name: string, email: string, password = 'supersecret123') {
	await page.getByRole('textbox', { name: /Dein Name/i }).fill(name);
	await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill(email);
	await page.getByLabelText('Passwort', { exact: true }).fill(password);
	await page.getByLabelText('Passwort bestätigen').fill(password);
}

describe('JoinWizard', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockGetAuthProviders.mockResolvedValue({ local: true, socialProviders: [] });
	});

	afterEach(() => {
		sessionStorage.removeItem('lh_pending_join');
	});

	it('ungültiger Token — zeigt Fehlermeldung an', async () => {
		mockResolveInvite.mockRejectedValue({ status: 404 });

		render(JoinWizard, { token: '00000000-0000-0000-0000-000000000000' });

		await expect.element(page.getByText('ungültig oder abgelaufen')).toBeVisible();
	});

	it('gültiger Token — zeigt Haushaltsnamen im Formular an', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);

		render(JoinWizard, { token: 'abc-token' });

		await expect.element(page.getByText('Die Müllers')).toBeVisible();
	});

	it('Formular abschicken — ruft joinHousehold mit Passwort statt Client-ID auf', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockResolvedValue({
			id: 'member-id',
			name: 'Max Mustermann',
			email: 'max@example.com',
			isAdmin: false
		});

		render(JoinWizard, { token: 'valid-token' });

		await fillLocalForm('Max Mustermann', 'max@example.com');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		expect(mockJoinHousehold).toHaveBeenCalledWith(
			expect.objectContaining({
				token: 'valid-token',
				localMemberRegistration: expect.objectContaining({
					name: 'Max Mustermann',
					email: 'max@example.com',
					password: 'supersecret123'
				})
			})
		);
		const [call] = mockJoinHousehold.mock.calls;
		expect(call[0].localMemberRegistration).not.toHaveProperty('id');
	});

	it('erfolgreicher Beitritt — zeigt Erfolgsmeldung und navigiert zum Dashboard', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockResolvedValue({
			id: 'member-id',
			name: 'Max Mustermann',
			email: 'max@example.com',
			isAdmin: false
		});

		render(JoinWizard, { token: 'valid-token' });

		await fillLocalForm('Max Mustermann', 'max@example.com');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		await expect.element(page.getByText('Erfolgreich beigetreten')).toBeVisible();
		await page.getByRole('button', { name: /Zum Dashboard/i }).click();

		expect(mockGoto).toHaveBeenCalledWith('/app/dashboard');
	});

	it('409-Fehler — Fehlertext direkt am E-Mail-Feld sichtbar', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockRejectedValue({ status: 409 });

		render(JoinWizard, { token: 'valid-token' });

		await fillLocalForm('Max Mustermann', 'taken@example.com');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		await expect.element(page.getByText('bereits registriert')).toBeVisible();
	});

	it('Soziale Provider vorhanden — zeigt Social-Buttons an', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockGetAuthProviders.mockResolvedValue({ local: true, socialProviders: ['google'] });

		render(JoinWizard, { token: 'valid-token' });

		await expect.element(page.getByRole('button', { name: /Google/i })).toBeVisible();
	});

	it('Name zu kurz — Social-Button ist deaktiviert', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockGetAuthProviders.mockResolvedValue({ local: true, socialProviders: ['google'] });

		render(JoinWizard, { token: 'valid-token' });

		await expect.element(page.getByRole('button', { name: /Google/i })).toBeDisabled();
	});

	it('Social-Button-Klick — speichert Einladungsdaten inkl. Token und leitet weiter', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockGetAuthProviders.mockResolvedValue({ local: true, socialProviders: ['google'] });

		render(JoinWizard, { token: 'valid-token' });

		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('button', { name: /Google/i }).click();

		const stored = JSON.parse(sessionStorage.getItem('lh_pending_join') ?? '{}');
		expect(stored).toEqual(
			expect.objectContaining({
				token: 'valid-token',
				memberName: 'Max Mustermann',
				householdId: validInviteInfo.householdId,
				householdName: validInviteInfo.householdName
			})
		);
		expect(mockNavigateToSocialProvider).toHaveBeenCalledWith('google');
	});
});
