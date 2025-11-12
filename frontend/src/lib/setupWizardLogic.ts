/**
 * Pure logic functions for the SetupWizard component
 * These functions are extracted for better testability
 */

/**
 * Generates initials from a household name
 * Returns '+' for empty names, first letter for single word, or first two letters for multiple words
 */
export function generateHouseholdNameInitials(householdName: string): string {
	if (householdName.trim().length === 0) {
		return '+';
	}
	// Filter out empty strings from split to handle multiple spaces
	const words = householdName.trim().split(' ').filter(word => word.length > 0);
	if (words.length >= 2) {
		return words[0].charAt(0) + words[1].charAt(0);
	}
	return words[0].charAt(0);
}

/**
 * Determines if the user can proceed to the next step
 */
export function canProceedToNextStep(currentStep: number, maxSteps: number): boolean {
	return currentStep < maxSteps - 1;
}

/**
 * Calculates the next step number
 */
export function calculateNextStep(currentStep: number, maxSteps: number): number {
	if (canProceedToNextStep(currentStep, maxSteps)) {
		return currentStep + 1;
	}
	return currentStep;
}

/**
 * Validates if a step transition is allowed
 * Users can only go back to previous steps, not forward or to the last step (after household creation)
 */
export function canGoBackToStep(
	currentStep: number,
	targetStep: number,
	maxSteps: number
): boolean {
	const isNotOnLastStep = currentStep !== maxSteps - 1;
	const isValidTargetStep = targetStep >= 0 && targetStep < currentStep;
	return isNotOnLastStep && isValidTargetStep;
}

/**
 * Calculates the target step if going back is allowed
 */
export function calculateTargetStep(
	currentStep: number,
	targetStep: number,
	maxSteps: number
): number {
	if (canGoBackToStep(currentStep, targetStep, maxSteps)) {
		return targetStep;
	}
	return currentStep;
}

/**
 * Creates share data object for the invite link
 */
export function createInviteLinkShareData(inviteUrl: string, shareText: string): ShareData {
	return {
		text: shareText,
		url: inviteUrl
	};
}

/**
 * Checks if the browser supports sharing with the given data
 */
export function checkCanBrowserShareInviteLink(shareData: ShareData): boolean {
	if (!navigator.share || !navigator.canShare) {
		return false;
	}

	return navigator.canShare(shareData);
}

/**
 * Generates the invite URL based on base URL and household ID
 */
export function generateInviteUrl(baseUrl: string, householdId: string): string {
    if (baseUrl.endsWith('/')) {
        baseUrl = baseUrl.slice(0, -1);
    }
	return `${baseUrl}/invite/${householdId}`;
}

/**
 * Validates household name
 */
export function isValidHouseholdName(name: string): boolean {
	return name.trim().length >= 3;
}

/**
 * Validates admin name
 */
export function isValidAdminName(name: string): boolean {
	return name.trim().length >= 3;
}

/**
 * Validates email address format
 */
export function isValidEmail(email: string): boolean {
	const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
	return emailRegex.test(email.trim());
}

/**
 * Reads a file and converts it to a base64 data URL
 */
export function readFileAsDataURL(file: File): Promise<string> {
	return new Promise((resolve, reject) => {
		const reader = new FileReader();

		reader.onload = (e) => {
			const result = e.target?.result;
			if (typeof result === 'string') {
				resolve(result);
			} else {
				reject(new Error('Failed to read file as data URL'));
			}
		};

		reader.onerror = () => {
			reject(new Error('Error reading file'));
		};

		reader.readAsDataURL(file);
	});
}

