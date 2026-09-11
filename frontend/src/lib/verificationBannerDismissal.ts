const DISMISSED_STORAGE_KEY = 'email-verification-banner-dismissed';

export function isVerificationBannerDismissed(memberId: string): boolean {
	try {
		return localStorage.getItem(DISMISSED_STORAGE_KEY) === memberId;
	} catch {
		return false;
	}
}

export function dismissVerificationBanner(memberId: string): void {
	try {
		localStorage.setItem(DISMISSED_STORAGE_KEY, memberId);
	} catch {
		// Ignore storage failures (private browsing, quota) - the banner simply reappears on reload.
	}
}
