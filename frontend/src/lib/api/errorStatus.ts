import { ResponseError } from '../../generated-sources/openapi';

export function extractErrorStatus(err: unknown): number | undefined {
	if (err instanceof ResponseError) {
		return err.response.status;
	}
	if (typeof err === 'object' && err !== null && 'status' in err) {
		return (err as { status: unknown }).status as number;
	}
	return undefined;
}
