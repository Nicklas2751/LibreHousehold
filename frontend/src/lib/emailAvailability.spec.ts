import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createDebouncedAvailabilityChecker } from './emailAvailability';

describe('createDebouncedAvailabilityChecker', () => {
	beforeEach(() => {
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('calls checkFn only after the debounce time has elapsed', async () => {
		// given
		const checkFn = vi.fn().mockResolvedValue({ available: true });
		const debouncedCheck = createDebouncedAvailabilityChecker(checkFn, 400);

		// when
		debouncedCheck('max@example.com');

		// then
		expect(checkFn).not.toHaveBeenCalled();
		await vi.advanceTimersByTimeAsync(400);
		expect(checkFn).toHaveBeenCalledExactlyOnceWith('max@example.com');
	});

	it('cancels a pending call when called again before it fires', async () => {
		// given
		const checkFn = vi.fn().mockResolvedValue({ available: true });
		const debouncedCheck = createDebouncedAvailabilityChecker(checkFn, 400);

		// when
		debouncedCheck('first@example.com');
		await vi.advanceTimersByTimeAsync(200);
		debouncedCheck('second@example.com');
		await vi.advanceTimersByTimeAsync(200);

		// then
		expect(checkFn).not.toHaveBeenCalled();
		await vi.advanceTimersByTimeAsync(200);
		expect(checkFn).toHaveBeenCalledExactlyOnceWith('second@example.com');
	});

	it('returns the result of checkFn once the debounce time has elapsed', async () => {
		// given
		const checkFn = vi.fn().mockResolvedValue({ available: false });
		const debouncedCheck = createDebouncedAvailabilityChecker(checkFn, 400);

		// when
		const resultPromise = debouncedCheck('taken@example.com');
		await vi.advanceTimersByTimeAsync(400);
		const result = await resultPromise;

		// then
		expect(result).toEqual({ available: false });
	});

	it('discards a late-resolving result from a stale, already-started request (out-of-order across debounce windows)', async () => {
		// given
		let resolveFirst!: (value: { available: boolean }) => void;
		let resolveSecond!: (value: { available: boolean }) => void;
		const checkFn = vi
			.fn<(email: string) => Promise<{ available: boolean }>>()
			.mockImplementationOnce(
				() =>
					new Promise<{ available: boolean }>((resolve) => {
						resolveFirst = resolve;
					})
			)
			.mockImplementationOnce(
				() =>
					new Promise<{ available: boolean }>((resolve) => {
						resolveSecond = resolve;
					})
			);
		const debouncedCheck = createDebouncedAvailabilityChecker(checkFn, 400);

		// when: the first call fully runs through its debounce window and starts the request
		const firstResultPromise = debouncedCheck('first@example.com');
		await vi.advanceTimersByTimeAsync(400);
		// the second call only starts AFTER the first debounce window has already elapsed
		// (the first request is already in flight and can no longer be cancelled via clearTimeout)
		const secondResultPromise = debouncedCheck('second@example.com');
		await vi.advanceTimersByTimeAsync(400);

		let firstResolvedValue: { available: boolean } | undefined;
		firstResultPromise.then((value) => {
			firstResolvedValue = value;
		});

		// the most recently started request (second) resolves first
		resolveSecond({ available: false });
		const secondResult = await secondResultPromise;
		// the earlier-started, now-stale request (first) resolves afterwards
		resolveFirst({ available: true });
		await Promise.resolve();
		await Promise.resolve();

		// then
		expect(secondResult).toEqual({ available: false });
		expect(firstResolvedValue).toBeUndefined();
	});
});
