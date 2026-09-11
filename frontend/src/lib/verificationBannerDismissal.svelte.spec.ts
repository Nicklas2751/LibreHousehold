import { describe, it, expect, beforeEach } from 'vitest';
import {
	dismissVerificationBanner,
	isVerificationBannerDismissed
} from './verificationBannerDismissal';

describe('verificationBannerDismissal', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	it('isVerificationBannerDismissed_notDismissed_returnsFalse', () => {
		// given
		const memberId = 'member-1';

		// when
		const result = isVerificationBannerDismissed(memberId);

		// then
		expect(result).toBe(false);
	});

	it('isVerificationBannerDismissed_dismissedForSameMemberId_returnsTrue', () => {
		// given
		const memberId = 'member-1';
		dismissVerificationBanner(memberId);

		// when
		const result = isVerificationBannerDismissed(memberId);

		// then
		expect(result).toBe(true);
	});

	it('isVerificationBannerDismissed_dismissedForDifferentMemberId_returnsFalse', () => {
		// given
		dismissVerificationBanner('member-1');

		// when
		const result = isVerificationBannerDismissed('member-2');

		// then
		expect(result).toBe(false);
	});
});
