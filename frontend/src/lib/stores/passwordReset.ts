import { SessionApi } from '../../generated-sources/openapi';
import { apiConfiguration } from '$lib/api/httpClient';

const api = new SessionApi(apiConfiguration);

export const requestPasswordReset = async (email: string): Promise<void> => {
	await api.requestPasswordReset({ passwordResetRequest: { email } });
};

export const confirmPasswordReset = async (token: string, newPassword: string): Promise<void> => {
	await api.confirmPasswordReset({ passwordResetConfirm: { token, newPassword } });
};
