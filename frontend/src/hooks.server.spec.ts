import { describe, expect, it, vi } from 'vitest';
import type { RequestEvent } from '@sveltejs/kit';
import type { ResolveOptions } from '@sveltejs/kit/hooks';
import { handle } from './hooks.server';

const PAGE_HTML = '<html lang="%paraglide.lang%">';

describe('handle', () => {
	it.each<{ headers: Record<string, string>; expectedHtml: string }>([
		{ headers: { cookie: 'PARAGLIDE_LOCALE=de' }, expectedHtml: '<html lang="de">' },
		{ headers: { cookie: 'PARAGLIDE_LOCALE=en' }, expectedHtml: '<html lang="en">' },
		{ headers: { 'accept-language': 'de-DE' }, expectedHtml: '<html lang="de">' }
	])('requestWith $headers_setsHtmlLangToDetectedLocale', async ({ headers, expectedHtml }) => {
		// given
		const event = { request: new Request('http://localhost/', { headers }) } as RequestEvent;
		const resolve = vi.fn(async (_event: RequestEvent, options?: ResolveOptions) => {
			const html = await options?.transformPageChunk?.({ html: PAGE_HTML, done: true });
			return new Response(html);
		});

		// when
		const response = await handle({ event, resolve });

		// then
		expect(await response.text()).toBe(expectedHtml);
	});
});
