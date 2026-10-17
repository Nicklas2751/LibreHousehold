import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import SocialRegistrationForm from './SocialRegistrationForm.svelte';

vi.mock('./paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('./paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

function renderForm(overrides: { errorMessage?: string | null } = {}) {
	const onformsubmit = vi.fn();
	const onback = vi.fn();
	render(SocialRegistrationForm, {
		registration: { name: 'Max Mustermann', email: 'max@example.com' },
		hint: 'Bitte prüfe deine Angaben.',
		nameLabel: 'Dein Name',
		nameHint: 'Mindestens 3 Zeichen',
		submitLabel: 'Einrichtung abschließen',
		backLabel: 'Zurück',
		onformsubmit,
		onback,
		...overrides
	});
	return { onformsubmit, onback };
}

describe('SocialRegistrationForm', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('prefills the name from the provider', async () => {
		// when
		renderForm();

		// then
		await expect
			.element(page.getByRole('textbox', { name: /Dein Name/i }))
			.toHaveValue('Max Mustermann');
	});

	it('shows the e-mail from the provider without an input to change it', async () => {
		// when
		renderForm();

		// then
		await expect.element(page.getByText('max@example.com')).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: /E-Mail/i })).not.toBeInTheDocument();
	});

	it('submits the changed name', async () => {
		// given
		const { onformsubmit } = renderForm();
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Maxi Mustermann');

		// when
		await page.getByRole('button', { name: 'Einrichtung abschließen' }).click();

		// then
		expect(onformsubmit).toHaveBeenCalledWith('Maxi Mustermann');
	});

	it('shows the given error message', async () => {
		// when
		renderForm({ errorMessage: 'Für diese E-Mail-Adresse gibt es schon ein Konto.' });

		// then
		await expect
			.element(page.getByText('Für diese E-Mail-Adresse gibt es schon ein Konto.'))
			.toBeVisible();
	});

	it('calls onback when going back', async () => {
		// given
		const { onback } = renderForm();

		// when
		await page.getByRole('button', { name: 'Zurück' }).click();

		// then
		expect(onback).toHaveBeenCalled();
	});
});
