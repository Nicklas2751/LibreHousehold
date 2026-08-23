export function createDebouncedAvailabilityChecker<T>(
	checkFn: (email: string) => Promise<T>,
	delayMs: number
): (email: string) => Promise<T> {
	let timeoutId: ReturnType<typeof setTimeout> | undefined;
	let latestCallId = 0;

	return (email: string) => {
		if (timeoutId) {
			clearTimeout(timeoutId);
		}
		const callId = ++latestCallId;
		return new Promise<T>((resolve, reject) => {
			timeoutId = setTimeout(() => {
				checkFn(email).then(
					(result) => {
						// Discards stale results: once the request is in flight it can no longer
						// be cancelled, but a late-resolving, superseded result must not overwrite
						// a result from an already-started, more recent call.
						if (callId === latestCallId) {
							resolve(result);
						}
					},
					(error) => {
						if (callId === latestCallId) {
							reject(error);
						}
					}
				);
			}, delayMs);
		});
	};
}
