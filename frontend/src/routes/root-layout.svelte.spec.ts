import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { createRawSnippet } from 'svelte';
import RootLayout from './+layout.svelte';

const { mockBootstrapSession } = vi.hoisted(() => ({ mockBootstrapSession: vi.fn() }));

vi.mock('#lib/stores/sessionBootstrap.ts', () => ({ bootstrapSession: mockBootstrapSession }));

vi.mock('$app/state', () => ({ page: { url: new URL('http://localhost:5173/app/dashboard') } }));

const childrenSnippet = createRawSnippet(() => ({
	render: () => `<div>page content</div>`
}));

describe('Root layout', () => {
	it('onLoad_bootstrapsSessionWithErrorToast', async () => {
		// when
		render(RootLayout, { children: childrenSnippet });

		// then
		await vi.waitFor(() => expect(mockBootstrapSession).toHaveBeenCalledWith());
	});
});
