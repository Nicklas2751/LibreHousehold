import { defineConfig } from 'vitest/config';
import { paraglideVitePlugin } from '@inlang/paraglide-js';
import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { playwright } from '@vitest/browser-playwright';

const apiUrl = new URL(process.env.VITE_API_URL ?? 'http://localhost');
const apiProxyTarget = `${apiUrl.protocol}//${apiUrl.host}`;
const apiProxyPrefix = apiUrl.pathname === '/' ? '' : apiUrl.pathname;

export default defineConfig({
	plugins: [
		tailwindcss(),
		sveltekit(),
		paraglideVitePlugin({
			project: './project.inlang',
			outdir: './src/lib/paraglide',
			strategy: ['url', 'preferredLanguage', 'baseLocale'] //"localStorage",
		})
	],
	server: {
		cors: { origin: /^https?:\/\/(?:(?:[^:]+\.)?localhost|127\.0\.0\.1|\[::1\])(?::\d+)?$/ },
		proxy: {
			'/api': {
				target: apiProxyTarget,
				changeOrigin: true,
				rewrite: (path) => path.replace(/^\/api/, apiProxyPrefix)
			},
			// Spring Security's own endpoints (form login, logout, OAuth2) never live under the
			// API base path (/v1) — passed through unprefixed so the local login flow is testable
			// without a shared reverse proxy in front of frontend and backend.
			// xfwd sets X-Forwarded-Host/Proto on the proxied request, which server.forward-headers-
			// strategy=framework (application.yaml) needs to rewrite the backend's self-referential
			// URLs (OAuth2 authorize/login redirects) to this dev-server origin instead of the
			// backend's own — see TestLibrehouseholdApplication for the matching authorization-uri/
			// redirect-uri overrides.
			// GET /login (exactly that path, no sub-path) is bypassed to SvelteKit because it's also
			// our own client-side route (src/routes/login/+page.svelte); the form's POST submission
			// AND the OAuth2 redirect-uri callback (GET /login/oauth2/code/spa-backend-client) must
			// still reach the backend.
			'/login': {
				target: apiProxyTarget,
				changeOrigin: true,
				xfwd: true,
				bypass: (req) => {
					if (req.method === 'POST') {
						return undefined;
					}
					const path = req.url?.split('?')[0];
					return path === '/login' ? req.url : undefined;
				}
			},
			'/logout': { target: apiProxyTarget, changeOrigin: true, xfwd: true },
			'/oauth2': { target: apiProxyTarget, changeOrigin: true, xfwd: true }
		}
	},
	test: {
		expect: { requireAssertions: true },
		projects: [
			{
				extends: './vite.config.ts',
				test: {
					name: 'client',
					browser: {
						enabled: true,
						provider: playwright(),
						instances: [{ browser: 'chromium' }]
					},
					include: ['src/**/*.svelte.{test,spec}.{js,ts}'],
					exclude: ['src/lib/server/**'],
					setupFiles: ['./vitest-setup-client.ts']
				}
			},
			{
				extends: './vite.config.ts',
				test: {
					name: 'server',
					environment: 'node',
					include: ['src/**/*.{test,spec}.{js,ts}'],
					exclude: ['src/**/*.svelte.{test,spec}.{js,ts}']
				}
			}
		]
	}
});
