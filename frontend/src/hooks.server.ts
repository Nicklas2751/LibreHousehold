import type { Handle } from '@sveltejs/kit/hooks';
import { paraglideMiddleware } from '#lib/paraglide/server.js';

const handleParaglide: Handle = ({ event, resolve }) =>
	// SvelteKit 3 makes event.request readonly. The middleware only hands back a changed request for
	// the URL strategy, which this app does not use, so the original request stays valid.
	paraglideMiddleware(event.request, ({ locale }) =>
		resolve(event, {
			transformPageChunk: ({ html }) => html.replace('%paraglide.lang%', locale)
		})
	);

export const handle: Handle = handleParaglide;
