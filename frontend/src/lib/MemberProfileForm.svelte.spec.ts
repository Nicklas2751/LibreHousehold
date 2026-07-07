import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import MemberProfileForm from './MemberProfileForm.svelte';

const baseProps = {
	contextHint: 'Kontext',
	nameLabel: 'Dein Name',
	nameHint: 'Name-Fehler',
	emailLabel: 'Deine E-Mail',
	emailHint: 'E-Mail-Fehler',
	backLabel: 'Zurück',
	submitLabel: 'Weiter',
	onformsubmit: vi.fn(),
	onback: vi.fn()
};

describe('MemberProfileForm', () => {
	it('requirePasswordFalse_doesNotRenderPasswordFields', async () => {
		// given / when
		render(MemberProfileForm, { ...baseProps, requirePassword: false });

		// then
		await expect.element(page.getByRole('textbox', { name: baseProps.nameLabel })).toBeVisible();
		expect(page.getByLabelText(/Passwort/i).elements().length).toBe(0);
	});

	it('requirePasswordTrue_rendersPasswordAndConfirmFields', async () => {
		// given / when
		render(MemberProfileForm, {
			...baseProps,
			requirePassword: true,
			passwordLabel: 'Passwort',
			passwordConfirmLabel: 'Passwort bestätigen'
		});

		// then
		await expect.element(page.getByLabelText('Passwort', { exact: true })).toBeVisible();
		await expect.element(page.getByLabelText('Passwort bestätigen')).toBeVisible();
	});

	it('requirePasswordTrue_mismatchedPasswords_showsErrorAndDoesNotSubmit', async () => {
		// given
		const onformsubmit = vi.fn();
		render(MemberProfileForm, {
			...baseProps,
			onformsubmit,
			requirePassword: true,
			passwordLabel: 'Passwort',
			passwordConfirmLabel: 'Passwort bestätigen',
			passwordMismatchError: 'Passwörter stimmen nicht überein'
		});

		// when
		await page.getByRole('textbox', { name: baseProps.nameLabel }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: baseProps.emailLabel }).fill('max@example.com');
		await page.getByLabelText('Passwort', { exact: true }).fill('supersecret123');
		await page.getByLabelText('Passwort bestätigen').fill('different123');
		await page.getByRole('button', { name: baseProps.submitLabel }).click();

		// then
		await expect.element(page.getByText('Passwörter stimmen nicht überein')).toBeVisible();
		expect(onformsubmit).not.toHaveBeenCalled();
	});

	it('requirePasswordTrue_matchingPasswords_callsOnformsubmitWithPassword', async () => {
		// given
		const onformsubmit = vi.fn();
		render(MemberProfileForm, {
			...baseProps,
			onformsubmit,
			requirePassword: true,
			passwordLabel: 'Passwort',
			passwordConfirmLabel: 'Passwort bestätigen'
		});

		// when
		await page.getByRole('textbox', { name: baseProps.nameLabel }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: baseProps.emailLabel }).fill('max@example.com');
		await page.getByLabelText('Passwort', { exact: true }).fill('supersecret123');
		await page.getByLabelText('Passwort bestätigen').fill('supersecret123');
		await page.getByRole('button', { name: baseProps.submitLabel }).click();

		// then
		expect(onformsubmit).toHaveBeenCalledWith(
			expect.objectContaining({
				name: 'Max Mustermann',
				email: 'max@example.com',
				password: 'supersecret123'
			})
		);
	});
});
