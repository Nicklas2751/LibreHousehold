import { Configuration } from '../generated-sources/openapi';
import { getAccessToken } from './stores/authStore.svelte';

export function createApiConfig(): Configuration {
	return new Configuration({
		basePath: '/api',
		middleware: [
			{
				pre: async (context) => {
					const token = getAccessToken();
					if (!token) return;
					return {
						url: context.url,
						init: {
							...context.init,
							headers: {
								...context.init.headers,
								Authorization: `Bearer ${token}`
							}
						}
					};
				}
			}
		]
	});
}
