import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
    calculateNextStep,
    calculateTargetStep,
    canGoBackToStep,
    canProceedToNextStep,
    checkCanBrowserShareInviteLink,
    createInviteLinkShareData,
    generateHouseholdNameInitials,
    generateInviteUrl,
    isValidAdminName,
    isValidEmail,
    isValidHouseholdName
} from './setupWizardLogic';

describe('setupWizardLogic', () => {
	describe('generateHouseholdNameInitials', () => {
		it('should return "+" for empty string', () => {
			const result = generateHouseholdNameInitials('');
			expect(result).toBe('+');
		});

		it('should return "+" for whitespace only string', () => {
			const result = generateHouseholdNameInitials('   ');
			expect(result).toBe('+');
		});

		it('should return first letter for single word', () => {
			const result = generateHouseholdNameInitials('Family');
			expect(result).toBe('F');
		});

		it('should return first two letters for two words', () => {
			const result = generateHouseholdNameInitials('Smith Family');
			expect(result).toBe('SF');
		});

		it('should return first two letters for multiple words', () => {
			const result = generateHouseholdNameInitials('The Smith Family House');
			expect(result).toBe('TS');
		});

		it('should trim whitespace before processing', () => {
			const result = generateHouseholdNameInitials('  Smith Family  ');
			expect(result).toBe('SF');
		});

		it('should handle names with extra spaces between words', () => {
			const result = generateHouseholdNameInitials('Smith    Family');
			expect(result).toBe('SF');
		});
	});

	describe('canProceedToNextStep', () => {
		it('should return true when current step is less than max steps minus one', () => {
			const result = canProceedToNextStep(0, 4);
			expect(result).toBe(true);
		});

		it('should return false when current step equals max steps minus one', () => {
			const result = canProceedToNextStep(3, 4);
			expect(result).toBe(false);
		});

		it('should return false when current step exceeds max steps', () => {
			const result = canProceedToNextStep(5, 4);
			expect(result).toBe(false);
		});

		it('should return true for first step with max steps 4', () => {
			const result = canProceedToNextStep(0, 4);
			expect(result).toBe(true);
		});

		it('should return true for middle steps', () => {
			const result = canProceedToNextStep(2, 4);
			expect(result).toBe(true);
		});
	});

	describe('calculateNextStep', () => {
		it('should increment step when not at max', () => {
			const result = calculateNextStep(1, 4);
			expect(result).toBe(2);
		});

		it('should not increment when at max step', () => {
			const result = calculateNextStep(3, 4);
			expect(result).toBe(3);
		});

		it('should return 1 when at step 0', () => {
			const result = calculateNextStep(0, 4);
			expect(result).toBe(1);
		});

		it('should stay at same step when already at last allowed step', () => {
			const result = calculateNextStep(3, 4);
			expect(result).toBe(3);
		});
	});

	describe('canGoBackToStep', () => {
		it('should return true when going back from middle step to previous step', () => {
			const result = canGoBackToStep(2, 1, 4);
			expect(result).toBe(true);
		});

		it('should return false when on last step', () => {
			const result = canGoBackToStep(3, 2, 4);
			expect(result).toBe(false);
		});

		it('should return false when target step is negative', () => {
			const result = canGoBackToStep(2, -1, 4);
			expect(result).toBe(false);
		});

		it('should return false when target step equals current step', () => {
			const result = canGoBackToStep(2, 2, 4);
			expect(result).toBe(false);
		});

		it('should return false when target step is greater than current step', () => {
			const result = canGoBackToStep(1, 2, 4);
			expect(result).toBe(false);
		});

		it('should return true when going back multiple steps', () => {
			const result = canGoBackToStep(2, 0, 4);
			expect(result).toBe(true);
		});

		it('should return true when going back to step 0 from step 1', () => {
			const result = canGoBackToStep(1, 0, 4);
			expect(result).toBe(true);
		});
	});

	describe('calculateTargetStep', () => {
		it('should return target step when going back is allowed', () => {
			const result = calculateTargetStep(2, 1, 4);
			expect(result).toBe(1);
		});

		it('should return current step when on last step', () => {
			const result = calculateTargetStep(3, 2, 4);
			expect(result).toBe(3);
		});

		it('should return current step when target is invalid', () => {
			const result = calculateTargetStep(2, 3, 4);
			expect(result).toBe(2);
		});

		it('should return current step when target is negative', () => {
			const result = calculateTargetStep(2, -1, 4);
			expect(result).toBe(2);
		});

		it('should allow going back to step 0', () => {
			const result = calculateTargetStep(2, 0, 4);
			expect(result).toBe(0);
		});
	});

	describe('createInviteLinkShareData', () => {
		it('should create valid ShareData object', () => {
			const inviteUrl = 'https://example.com/invite/123';
			const shareText = 'Join our household!';
			const result = createInviteLinkShareData(inviteUrl, shareText);

			expect(result).toEqual({
				text: shareText,
				url: inviteUrl
			});
		});

		it('should handle empty share text', () => {
			const inviteUrl = 'https://example.com/invite/123';
			const shareText = '';
			const result = createInviteLinkShareData(inviteUrl, shareText);

			expect(result.text).toBe('');
			expect(result.url).toBe(inviteUrl);
		});

		it('should handle special characters in URL', () => {
			const inviteUrl = 'https://example.com/invite/abc-123_XYZ';
			const shareText = 'Join!';
			const result = createInviteLinkShareData(inviteUrl, shareText);

			expect(result.url).toBe(inviteUrl);
		});
	});

	describe('canBrowserShareInviteLink', () => {
		beforeEach(() => {
			// Reset navigator mocks before each test
			vi.unstubAllGlobals();
		});

		it('should return false when navigator.share is not available', () => {
			vi.stubGlobal('navigator', {});
			const shareData = { text: 'Test', url: 'https://example.com' };
			const result = checkCanBrowserShareInviteLink(shareData);

			expect(result).toBe(false);
		});

		it('should return false when navigator.canShare is not available', () => {
			vi.stubGlobal('navigator', { share: vi.fn() });
			const shareData = { text: 'Test', url: 'https://example.com' };
			const result = checkCanBrowserShareInviteLink(shareData);

			expect(result).toBe(false);
		});

		it('should return false when canShare returns false', () => {
			vi.stubGlobal('navigator', {
				share: vi.fn(),
				canShare: vi.fn().mockReturnValue(false)
			});
			const shareData = { text: 'Test', url: 'https://example.com' };
			const result = checkCanBrowserShareInviteLink(shareData);

			expect(result).toBe(false);
		});

		it('should return true when canShare returns true', () => {
			vi.stubGlobal('navigator', {
				share: vi.fn(),
				canShare: vi.fn().mockReturnValue(true)
			});
			const shareData = { text: 'Test', url: 'https://example.com' };
			const result = checkCanBrowserShareInviteLink(shareData);

			expect(result).toBe(true);
		});

		it('should call canShare with the provided share data', () => {
			const canShareMock = vi.fn().mockReturnValue(true);
			vi.stubGlobal('navigator', {
				share: vi.fn(),
				canShare: canShareMock
			});
			const shareData = { text: 'Test', url: 'https://example.com' };
			checkCanBrowserShareInviteLink(shareData);

			expect(canShareMock).toHaveBeenCalledWith(shareData);
		});
	});

	describe('generateInviteUrl', () => {
		it('should generate correct invite URL', () => {
			const baseUrl = 'https://example.com';
			const householdId = '12345';
			const result = generateInviteUrl(baseUrl, householdId);

			expect(result).toBe('https://example.com/invite/12345');
		});

		it('should handle base URL with trailing slash', () => {
			const baseUrl = 'https://example.com/';
			const householdId = '12345';
			const result = generateInviteUrl(baseUrl, householdId);

			expect(result).toBe('https://example.com//invite/12345');
		});

		it('should handle localhost URLs', () => {
			const baseUrl = 'http://localhost:3000';
			const householdId = 'abc-123';
			const result = generateInviteUrl(baseUrl, householdId);

			expect(result).toBe('http://localhost:3000/invite/abc-123');
		});

		it('should handle UUID household IDs', () => {
			const baseUrl = 'https://example.com';
			const householdId = '550e8400-e29b-41d4-a716-446655440000';
			const result = generateInviteUrl(baseUrl, householdId);

			expect(result).toBe('https://example.com/invite/550e8400-e29b-41d4-a716-446655440000');
		});
	});

	describe('isValidHouseholdName', () => {
		it('should return false for empty string', () => {
			const result = isValidHouseholdName('');
			expect(result).toBe(false);
		});

		it('should return false for whitespace only', () => {
			const result = isValidHouseholdName('   ');
			expect(result).toBe(false);
		});

		it('should return false for string with less than 3 characters', () => {
			const result = isValidHouseholdName('ab');
			expect(result).toBe(false);
		});

		it('should return true for string with exactly 3 characters', () => {
			const result = isValidHouseholdName('abc');
			expect(result).toBe(true);
		});

		it('should return true for string with more than 3 characters', () => {
			const result = isValidHouseholdName('Smith Family');
			expect(result).toBe(true);
		});

		it('should trim whitespace before validation', () => {
			const result = isValidHouseholdName('  ab  ');
			expect(result).toBe(false);
		});

		it('should validate after trimming', () => {
			const result = isValidHouseholdName('  abc  ');
			expect(result).toBe(true);
		});
	});

	describe('isValidAdminName', () => {
		it('should return false for empty string', () => {
			const result = isValidAdminName('');
			expect(result).toBe(false);
		});

		it('should return false for whitespace only', () => {
			const result = isValidAdminName('   ');
			expect(result).toBe(false);
		});

		it('should return false for string with less than 3 characters', () => {
			const result = isValidAdminName('Jo');
			expect(result).toBe(false);
		});

		it('should return true for string with exactly 3 characters', () => {
			const result = isValidAdminName('Joe');
			expect(result).toBe(true);
		});

		it('should return true for string with more than 3 characters', () => {
			const result = isValidAdminName('John Smith');
			expect(result).toBe(true);
		});

		it('should trim whitespace before validation', () => {
			const result = isValidAdminName('  Jo  ');
			expect(result).toBe(false);
		});

		it('should validate after trimming', () => {
			const result = isValidAdminName('  Joe  ');
			expect(result).toBe(true);
		});
	});

	describe('isValidEmail', () => {
		it('should return false for empty string', () => {
			const result = isValidEmail('');
			expect(result).toBe(false);
		});

		it('should return false for string without @ symbol', () => {
			const result = isValidEmail('testexample.com');
			expect(result).toBe(false);
		});

		it('should return false for string without domain', () => {
			const result = isValidEmail('test@');
			expect(result).toBe(false);
		});

		it('should return false for string without local part', () => {
			const result = isValidEmail('@example.com');
			expect(result).toBe(false);
		});

		it('should return false for string without TLD', () => {
			const result = isValidEmail('test@example');
			expect(result).toBe(false);
		});

		it('should return true for valid email', () => {
			const result = isValidEmail('test@example.com');
			expect(result).toBe(true);
		});

		it('should return true for email with subdomain', () => {
			const result = isValidEmail('test@mail.example.com');
			expect(result).toBe(true);
		});

		it('should return true for email with plus sign', () => {
			const result = isValidEmail('test+label@example.com');
			expect(result).toBe(true);
		});

		it('should return true for email with dots in local part', () => {
			const result = isValidEmail('first.last@example.com');
			expect(result).toBe(true);
		});

		it('should return false for email with spaces', () => {
			const result = isValidEmail('test @example.com');
			expect(result).toBe(false);
		});

		it('should trim whitespace before validation', () => {
			const result = isValidEmail('  test@example.com  ');
			expect(result).toBe(true);
		});

		it('should return false for multiple @ symbols', () => {
			const result = isValidEmail('test@@example.com');
			expect(result).toBe(false);
		});
	});

	// Note: readFileAsDataURL tests are omitted here because they require browser APIs (FileReader)
	// These should be tested in a browser environment using Svelte component tests or E2E tests
});

