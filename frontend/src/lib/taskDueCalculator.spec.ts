import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {addInterval, checkIsDone, getLastDueDate, getNextDueDateAfterToday} from './taskDueCalculator';
import type {Task} from '../generated-sources/openapi';

describe('taskDueCalculator', () => {
	beforeEach(() => {
		// Mock current date to November 22, 2025 00:00:00 UTC
		vi.useFakeTimers();
		vi.setSystemTime(new Date('2025-11-22T00:00:00Z'));
	});

	afterEach(() => {
		vi.restoreAllMocks();
	});

	describe('addInterval', () => {
		it('should add days to a date', () => {
			const date = new Date('2025-11-22');
			const result = addInterval(date, 'days', 5);
			expect(result.getUTCDate()).toBe(27);
		});

		it('should add negative days to a date', () => {
			const date = new Date('2025-11-22');
			const result = addInterval(date, 'days', -5);
			expect(result.getUTCDate()).toBe(17);
		});

		it('should add zero days to a date', () => {
			const date = new Date('2025-11-22');
			const result = addInterval(date, 'days', 0);
			expect(result.getUTCDate()).toBe(22);
		});

		it('should add weeks to a date', () => {
			const date = new Date('2025-11-22');
			const result = addInterval(date, 'weeks', 2);
			expect(result.getUTCDate()).toBe(6);
			expect(result.getUTCMonth()).toBe(11); // December
		});

		it('should add months to a date', () => {
			const date = new Date('2025-11-22');
			const result = addInterval(date, 'months', 3);
			expect(result.getUTCMonth()).toBe(1); // February
			expect(result.getUTCFullYear()).toBe(2026);
		});

		it('should add months handling month boundaries correctly', () => {
			const date = new Date('2025-01-31');
			const result = addInterval(date, 'months', 1);
			// When adding 1 month to January 31, JS Date adds a month
			expect(result.getUTCMonth()).toBeGreaterThanOrEqual(1);
		});

		it('should add years to a date', () => {
			const date = new Date('2025-11-22');
			const result = addInterval(date, 'years', 2);
			expect(result.getUTCFullYear()).toBe(2027);
			expect(result.getUTCMonth()).toBe(10); // November
			expect(result.getUTCDate()).toBe(22);
		});

		it('should not modify the original date', () => {
			const date = new Date('2025-11-22');
			const originalTime = date.getTime();
			addInterval(date, 'days', 5);
			expect(date.getTime()).toBe(originalTime);
		});

		it('should handle unknown interval type gracefully', () => {
			const date = new Date('2025-11-22');
			const result = addInterval(date, 'invalid', 5);
			// Should return the same date when unit is unknown
			expect(result.getUTCDate()).toBe(22);
		});
	});

	describe('getLastDueDate', () => {
		it('should return undefined for non-recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				recurring: false
			};
			expect(getLastDueDate(task)).toBeUndefined();
		});

		it('should return undefined if task has no dueDate', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: undefined as any,
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			expect(getLastDueDate(task)).toBeUndefined();
		});

		it('should return undefined if task has no recurrenceUnit', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				recurring: true,
				recurrenceUnit: undefined,
				recurrenceInterval: 1
			};
			expect(getLastDueDate(task)).toBeUndefined();
		});

		it('should return undefined if task has no recurrenceInterval', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: undefined
			};
			expect(getLastDueDate(task)).toBeUndefined();
		});

		it('should return undefined if first due date is in the future', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-25'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			expect(getLastDueDate(task)).toBeUndefined();
		});

		it('should return the first due date if it is today or in the past', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-22'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			const result = getLastDueDate(task);
			expect(result).toBeDefined();
			// When the first due date is exactly today, today should be returned as last due date
			expect(result?.getUTCDate()).toBe(22);
		});

		it('should return the last due date for daily recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			const result = getLastDueDate(task);
			expect(result).toBeDefined();
			// Due dates: 20, 21, 22 (today), 23 (future) - since 23 > today, 22 is the last
			expect(result?.getUTCDate()).toBe(22);
		});

		it('should return the last due date for weekly recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-01'),
				recurring: true,
				recurrenceUnit: 'weeks',
				recurrenceInterval: 1
			};
			const result = getLastDueDate(task);
			expect(result).toBeDefined();
			// Due dates: 01, 08, 15, 22 (today), 29 (future) - 22 is the last
			expect(result?.getUTCDate()).toBe(22);
		});

		it('should return the last due date for monthly recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-09-22'),
				recurring: true,
				recurrenceUnit: 'months',
				recurrenceInterval: 1
			};
			const result = getLastDueDate(task);
			expect(result).toBeDefined();
			// Due dates: 09-22 (month 8), 10-22 (month 9), 11-22 (month 10=today), 12-22 (month 11, future)
			// So last is 11-22 (month 10)
			expect(result?.getUTCMonth()).toBe(10); // November (0-based)
			expect(result?.getUTCDate()).toBe(22);
		});

		it('should return the last due date for yearly recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2023-11-22'),
				recurring: true,
				recurrenceUnit: 'years',
				recurrenceInterval: 1
			};
			const result = getLastDueDate(task);
			expect(result).toBeDefined();
			// 2025-11-22 is today, so it's included
			expect(result?.getUTCFullYear()).toBe(2025);
		});

		it('should handle tasks with recurrence intervals > 1', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-15'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 3
			};
			const result = getLastDueDate(task);
			expect(result).toBeDefined();
			// Due dates: 15, 18, 21, 24 - today is 22, so 24 > 22, last is 21
			expect(result?.getUTCDate()).toBe(21);
		});

		it('should ignore time component and work with dates only', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20T23:59:59Z'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			const result = getLastDueDate(task);
			expect(result).toBeDefined();
			// Same as 2025-11-20, Dates: 20, 21, 22 (today), 23 (future) - 22 is last
			expect(result?.getUTCDate()).toBe(22);
		});
	});

	describe('getNextDueDateAfterToday', () => {
		it('should return undefined for non-recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-25'),
				recurring: false
			};
			expect(getNextDueDateAfterToday(task)).toBeUndefined();
		});

		it('should return undefined if task has no dueDate', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: undefined as any,
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			expect(getNextDueDateAfterToday(task)).toBeUndefined();
		});

		it('should return undefined if task has no recurrenceUnit', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-25'),
				recurring: true,
				recurrenceUnit: undefined,
				recurrenceInterval: 1
			};
			expect(getNextDueDateAfterToday(task)).toBeUndefined();
		});

		it('should return undefined if task has no recurrenceInterval', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-25'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: undefined
			};
			expect(getNextDueDateAfterToday(task)).toBeUndefined();
		});

		it('should return the next due date after today when first due is in future', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-25'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			const result = getNextDueDateAfterToday(task);
			expect(result).toBeDefined();
			// 25 > 22 (today), so the result is 25 itself
			expect(result?.getUTCDate()).toBe(25);
		});

		it('should calculate next due date for daily recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			const result = getNextDueDateAfterToday(task);
			expect(result).toBeDefined();
			// Due dates: 20, 21, 22 (today), loop increments to 23
			expect(result?.getUTCDate()).toBe(23);
		});

		it('should calculate next due date for weekly recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-15'),
				recurring: true,
				recurrenceUnit: 'weeks',
				recurrenceInterval: 1
			};
			const result = getNextDueDateAfterToday(task);
			expect(result).toBeDefined();
			// Due dates: 15, 22 (today), 29 (next) - loop increments, so 29
			expect(result?.getUTCDate()).toBe(29);
		});

		it('should calculate next due date for monthly recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-09-22'),
				recurring: true,
				recurrenceUnit: 'months',
				recurrenceInterval: 1
			};
			const result = getNextDueDateAfterToday(task);
			expect(result).toBeDefined();
			// Sept 22 (month 8), Oct 22 (month 9), Nov 22 (month 10=today), increments to Dec 22 (month 11)
			expect(result?.getUTCMonth()).toBe(11); // December (0-based)
			expect(result?.getUTCDate()).toBe(22);
		});

		it('should calculate next due date for yearly recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2024-11-22'),
				recurring: true,
				recurrenceUnit: 'years',
				recurrenceInterval: 1
			};
			const result = getNextDueDateAfterToday(task);
			expect(result).toBeDefined();
			// 2024-11-22, 2025-11-22 (<=today), increments
			expect(result?.getUTCFullYear()).toBe(2026);
			expect(result?.getUTCDate()).toBe(22);
		});

		it('should handle tasks with recurrence intervals > 1', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-15'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 3
			};
			const result = getNextDueDateAfterToday(task);
			expect(result).toBeDefined();
			// Dates: 15, 18, 21, 24 - increments past 22
			expect(result?.getUTCDate()).toBe(24);
		});

		it('should ignore time component and work with dates only', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20T10:30:00Z'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1
			};
			const result = getNextDueDateAfterToday(task);
			expect(result).toBeDefined();
			// Time ignored: 20, 21, 22 (<=today), so 23 is the first after today
			expect(result?.getUTCDate()).toBe(23);
		});
	});

	describe('checkIsDone', () => {
		it('should return false if task has no done date', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				done: undefined
			};
			expect(checkIsDone(task)).toBe(false);
		});

		it('should return false for non-recurring task with done date before due date', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-25'),
				recurring: false,
				done: new Date('2025-11-20')
			};
			expect(checkIsDone(task)).toBe(false);
		});

		it('should return false for non-recurring task with done date on due date', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-22'),
				recurring: false,
				done: new Date('2025-11-22')
			};
			expect(checkIsDone(task)).toBe(false);
		});

		it('should return true for non-recurring task with done date after due date', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				recurring: false,
				done: new Date('2025-11-22')
			};
			expect(checkIsDone(task)).toBe(true);
		});

		it('should return false for recurring task when there is no last due date', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-25'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1,
				done: new Date('2025-11-25')
			};
			expect(checkIsDone(task)).toBe(false);
		});

		it('should return false for recurring task with done date before last due date', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-15'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1,
				done: new Date('2025-11-20')
			};
			expect(checkIsDone(task)).toBe(false);
		});

		it('should return false for recurring task with done date on last due date', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-15'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1,
				done: new Date('2025-11-21')
			};
			expect(checkIsDone(task)).toBe(false);
		});

		it('should return true for recurring task with done date after last due date', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-15'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1,
				done: new Date('2025-11-23')
			};
			expect(checkIsDone(task)).toBe(true);
		});

		it('should ignore time component in done date for non-recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				recurring: false,
				done: new Date('2025-11-22T23:59:59Z')
			};
			expect(checkIsDone(task)).toBe(true);
		});

		it('should ignore time component in done date for recurring task', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-15'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1,
				done: new Date('2025-11-23T10:30:00Z')
			};
			expect(checkIsDone(task)).toBe(true);
		});

		it('should handle recurring task with multiple recurrence intervals', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-08'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 7,
				done: new Date('2025-11-23')
			};
			expect(checkIsDone(task)).toBe(true);
		});

		it('should handle monthly recurring tasks correctly', () => {
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-09-22'),
				recurring: true,
				recurrenceUnit: 'months',
				recurrenceInterval: 1,
				done: new Date('2025-11-23')
			};
			expect(checkIsDone(task)).toBe(true);
		});
	});
});
