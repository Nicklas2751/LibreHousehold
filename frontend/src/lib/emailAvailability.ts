export interface DebouncedAvailabilityChecker<T> {
	(email: string): Promise<T>;
	cancel(): void;
}

export function createDebouncedAvailabilityChecker<T>(
	checkFn: (email: string) => Promise<T>,
	delayMs: number
): DebouncedAvailabilityChecker<T> {
	let timeoutId: ReturnType<typeof setTimeout> | undefined;
	let latestCallId = 0;

	const debouncedCheck = ((email: string) => {
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
	}) as DebouncedAvailabilityChecker<T>;

	debouncedCheck.cancel = () => {
		if (timeoutId) {
			clearTimeout(timeoutId);
		}
		// Also invalidates a call whose timer already fired and whose request is in flight,
		// so its result is discarded when it resolves (same mechanism as a superseded call).
		latestCallId++;
	};

	return debouncedCheck;
}
