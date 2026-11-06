const DEFAULT_TARGET = '/app/dashboard';
const APP_PATH_PREFIX = '/app/';

// Parsing with the URL API normalizes backslashes, encoded and control characters and dot segments
// the same way the browser does, so a target that resolves to another origin or outside the app
// cannot slip through as an open redirect.
export function safeReturnTo(returnTo: string | null, origin: string): string {
	if (!returnTo) {
		return DEFAULT_TARGET;
	}
	const target = new URL(returnTo, origin);
	if (target.origin !== origin || !target.pathname.startsWith(APP_PATH_PREFIX)) {
		return DEFAULT_TARGET;
	}
	return target.pathname + target.search + target.hash;
}
