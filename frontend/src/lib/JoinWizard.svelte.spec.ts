import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import JoinWizard from './JoinWizard.svelte';

const { mockResolveInvite, mockJoinHousehold, mockGoto } = vi.hoisted(() => ({
	mockResolveInvite: vi.fn(),
	mockJoinHousehold: vi.fn(),
	mockGoto: vi.fn()
}));

vi.mock('../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../generated-sources/openapi')>();
	return {
		...original,
		MembersApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.resolveInvite = mockResolveInvite;
			this.joinHousehold = mockJoinHousehold;
		})
	};
});

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

vi.mock('./paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('./paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

const validInviteInfo = {
	householdId: '11111111-1111-1111-1111-111111111111',
	householdName: 'Die Müllers',
	validUntil: new Date('2099-12-31')
};

describe('JoinWizard', () => {
	beforeEach(() => {
		vi.clearAllMocks();
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

	it('Formular abschicken — ruft joinHousehold mit korrekten Daten auf', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockResolvedValue({
			id: 'member-id',
			name: 'Max Mustermann',
			email: 'max@example.com',
			isAdmin: false
		});

		render(JoinWizard, { token: 'valid-token' });

		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		expect(mockJoinHousehold).toHaveBeenCalledWith(
			expect.objectContaining({
				token: 'valid-token',
				memberRegistration: expect.objectContaining({
					name: 'Max Mustermann',
					email: 'max@example.com'
				})
			})
		);
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

		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		await expect.element(page.getByText('Erfolgreich beigetreten')).toBeVisible();
		await page.getByRole('button', { name: /Zum Dashboard/i }).click();

		expect(mockGoto).toHaveBeenCalledWith('/app/dashboard');
	});

	it('409-Fehler — zeigt E-Mail bereits registriert Meldung', async () => {
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockRejectedValue({ status: 409 });

		render(JoinWizard, { token: 'valid-token' });

		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('taken@example.com');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		await expect.element(page.getByText('bereits registriert')).toBeVisible();
	});
});
