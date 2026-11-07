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
			// The cookie keeps the chosen language across navigations and reloads; the browser
			// language only decides until a language has been chosen.
			strategy: ['cookie', 'preferredLanguage', 'baseLocale']
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
			// Spring Security's OAuth2 endpoints for social login (authorization redirect and provider
			// callback) never live under the API base path (/v1) — passed through unprefixed so social
			// login is testable without a shared reverse proxy in front of frontend and backend. Local
			// login and logout are API endpoints (/v1/login, /v1/logout) and go through /api above.
			// xfwd sets X-Forwarded-Host/Proto on the proxied request, which server.forward-headers-
			// strategy=framework (application.yaml) needs to build the provider redirect-uri for this
			// dev-server origin instead of the backend's own.
			// Only /login/oauth2 is proxied: /login itself is our own client-side route
			// (src/routes/login/+page.svelte).
			'/login/oauth2': { target: apiProxyTarget, changeOrigin: true, xfwd: true },
			'/oauth2': { target: apiProxyTarget, changeOrigin: true, xfwd: true }
		}
	},
	test: {
		expect: { requireAssertions: true },
		coverage: {
			provider: 'v8',
			// lcov feeds SonarCloud (sonar.javascript.lcov.reportPaths); text gives a quick local summary.
			reporter: ['lcov', 'text-summary'],
			include: ['src/**/*.{ts,svelte}'],
			exclude: [
				'src/**/*.{test,spec}.{js,ts}',
				'src/**/*.d.ts',
				'src/generated-sources/**',
				'src/lib/paraglide/**'
			]
		},
		projects: [
			{
				extends: './vite.config.ts',
				test: {
					name: 'client',
					browser: {
						enabled: true,
						// Fixed locale so specs see the base language regardless of the machine's language
						provider: playwright({ contextOptions: { locale: 'en-US' } }),
						instances: [{ browser: 'chromium' }],
						// Vitest 5 matches locator text exactly by default; the specs match on text fragments
						locators: { exact: false }
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
