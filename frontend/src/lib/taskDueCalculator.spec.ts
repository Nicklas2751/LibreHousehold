import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
	addInterval,
	checkIsDone,
	getDisplayDueDate,
	getLastDueDate,
	getNextDueDateAfterToday
} from './taskDueCalculator';
import type { Task } from '../generated-sources/openapi';

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
				dueDate: undefined as unknown as Date,
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

		it.each([
			{
				desc: 'firstDueDateToday_returnsToday',
				dueDate: '2025-11-22',
				unit: 'days',
				interval: 1,
				expected: '2025-11-22'
			},
			{
				desc: 'daily_returnsToday',
				dueDate: '2025-11-20',
				unit: 'days',
				interval: 1,
				expected: '2025-11-22'
			},
			{
				desc: 'weekly_returnsToday',
				dueDate: '2025-11-01',
				unit: 'weeks',
				interval: 1,
				expected: '2025-11-22'
			},
			{
				desc: 'monthly_returnsToday',
				dueDate: '2025-09-22',
				unit: 'months',
				interval: 1,
				expected: '2025-11-22'
			},
			{
				desc: 'yearly_returnsToday',
				dueDate: '2023-11-22',
				unit: 'years',
				interval: 1,
				expected: '2025-11-22'
			},
			{
				desc: 'everyThreeDays_returnsLastDueDateBeforeToday',
				dueDate: '2025-11-15',
				unit: 'days',
				interval: 3,
				expected: '2025-11-21'
			},
			{
				desc: 'dueDateWithTimeComponent_ignoresTime',
				dueDate: '2025-11-20T23:59:59Z',
				unit: 'days',
				interval: 1,
				expected: '2025-11-22'
			}
		])('$desc', ({ dueDate, unit, interval, expected }) => {
			// given
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date(dueDate),
				recurring: true,
				recurrenceUnit: unit as Task['recurrenceUnit'],
				recurrenceInterval: interval
			};
			// when
			const result = getLastDueDate(task);
			// then
			expect(result?.toISOString().slice(0, 10)).toBe(expected);
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
				dueDate: undefined as unknown as Date,
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

		it.each([
			{
				desc: 'firstDueDateInFuture_returnsFirstDueDate',
				dueDate: '2025-11-25',
				unit: 'days',
				interval: 1,
				expected: '2025-11-25'
			},
			{
				desc: 'daily_returnsTomorrow',
				dueDate: '2025-11-20',
				unit: 'days',
				interval: 1,
				expected: '2025-11-23'
			},
			{
				desc: 'weekly_returnsNextWeek',
				dueDate: '2025-11-15',
				unit: 'weeks',
				interval: 1,
				expected: '2025-11-29'
			},
			{
				desc: 'monthly_returnsNextMonth',
				dueDate: '2025-09-22',
				unit: 'months',
				interval: 1,
				expected: '2025-12-22'
			},
			{
				desc: 'yearly_returnsNextYear',
				dueDate: '2024-11-22',
				unit: 'years',
				interval: 1,
				expected: '2026-11-22'
			},
			{
				desc: 'everyThreeDays_returnsNextDueDateAfterToday',
				dueDate: '2025-11-15',
				unit: 'days',
				interval: 3,
				expected: '2025-11-24'
			},
			{
				desc: 'dueDateWithTimeComponent_ignoresTime',
				dueDate: '2025-11-20T10:30:00Z',
				unit: 'days',
				interval: 1,
				expected: '2025-11-23'
			}
		])('$desc', ({ dueDate, unit, interval, expected }) => {
			// given
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date(dueDate),
				recurring: true,
				recurrenceUnit: unit as Task['recurrenceUnit'],
				recurrenceInterval: interval
			};
			// when
			const result = getNextDueDateAfterToday(task);
			// then
			expect(result?.toISOString().slice(0, 10)).toBe(expected);
		});
	});

	describe('checkIsDone', () => {
		it('no_done_date_returns_false', () => {
			// given
			const task: Task = {
				id: '1',
				title: 'Test Task',
				dueDate: new Date('2025-11-20'),
				done: undefined
			};
			// when / then
			expect(checkIsDone(task)).toBe(false);
		});

		describe('non-recurring task', () => {
			it('done_set_returns_true', () => {
				// given
				const task: Task = {
					id: '1',
					title: 'Test Task',
					dueDate: new Date('2025-11-22'),
					recurring: false,
					done: new Date('2025-11-22')
				};
				// when / then
				expect(checkIsDone(task)).toBe(true);
			});

			it('done_set_with_time_component_returns_true', () => {
				// given
				const task: Task = {
					id: '1',
					title: 'Test Task',
					dueDate: new Date('2025-11-20'),
					recurring: false,
					done: new Date('2025-11-22T23:59:59Z')
				};
				// when / then
				expect(checkIsDone(task)).toBe(true);
			});
		});

		describe('recurring task', () => {
			// The backend advances dueDate by one interval when the task is marked done, so the
			// completed occurrence is dueDate - interval. The task counts as done on the day it was
			// done and while today is closer to the completed occurrence than to the next one.
			// today (mocked) = 2025-11-22

			it('no_recurrenceUnit_returns_false', () => {
				// given
				const task: Task = {
					id: '1',
					title: 'Test Task',
					dueDate: new Date('2025-11-23'),
					recurring: true,
					recurrenceUnit: undefined,
					recurrenceInterval: 1,
					done: new Date('2025-11-22')
				};
				// when / then
				expect(checkIsDone(task)).toBe(false);
			});

			it('no_recurrenceInterval_returns_false', () => {
				// given
				const task: Task = {
					id: '1',
					title: 'Test Task',
					dueDate: new Date('2025-11-23'),
					recurring: true,
					recurrenceUnit: 'days',
					recurrenceInterval: undefined,
					done: new Date('2025-11-22')
				};
				// when / then
				expect(checkIsDone(task)).toBe(false);
			});

			it.each([
				// dueDate is the advanced next occurrence, the completed occurrence is dueDate - interval
				{
					desc: 'daily_doneToday_returns_true',
					dueDate: '2025-11-23',
					unit: 'days',
					interval: 1,
					done: '2025-11-22',
					expected: true
				},
				{
					desc: 'daily_doneYesterdayAndNextDueToday_returns_false',
					dueDate: '2025-11-22',
					unit: 'days',
					interval: 1,
					done: '2025-11-21',
					expected: false
				},
				{
					desc: 'weekly_doneToday_returns_true',
					dueDate: '2025-11-29',
					unit: 'weeks',
					interval: 1,
					done: '2025-11-22',
					expected: true
				},
				{
					desc: 'monthly_doneBeforeCompletedDueDate_returns_true',
					dueDate: '2025-12-26',
					unit: 'months',
					interval: 1,
					done: '2025-11-20',
					expected: true
				},
				{
					desc: 'weekly_todayCloserToCompletedThanToNextDueDate_returns_true',
					dueDate: '2025-11-27',
					unit: 'weeks',
					interval: 1,
					done: '2025-11-20',
					expected: true
				},
				{
					desc: 'weekly_todayCloserToNextThanToCompletedDueDate_returns_false',
					dueDate: '2025-11-24',
					unit: 'weeks',
					interval: 1,
					done: '2025-11-17',
					expected: false
				},
				{
					desc: 'everyTwoDays_todayHalfwayBetweenDueDates_returns_false',
					dueDate: '2025-11-23',
					unit: 'days',
					interval: 2,
					done: '2025-11-21',
					expected: false
				},
				{
					desc: 'weekly_overdueAndDoneToday_returns_true',
					dueDate: '2025-11-17',
					unit: 'weeks',
					interval: 1,
					done: '2025-11-22',
					expected: true
				}
			])('$desc', ({ dueDate, unit, interval, done, expected }) => {
				// given
				const task: Task = {
					id: '1',
					title: 'Test Task',
					dueDate: new Date(dueDate),
					recurring: true,
					recurrenceUnit: unit as Task['recurrenceUnit'],
					recurrenceInterval: interval,
					done: new Date(done)
				};
				// when / then
				expect(checkIsDone(task)).toBe(expected);
			});

			it('done_date_time_component_ignored_returns_true', () => {
				// given — dueDate=23.11 (daily, advanced), done has time component
				const task: Task = {
					id: '1',
					title: 'Test Task',
					dueDate: new Date('2025-11-23'),
					recurring: true,
					recurrenceUnit: 'days',
					recurrenceInterval: 1,
					done: new Date('2025-11-22T23:59:59Z')
				};
				// when / then
				expect(checkIsDone(task)).toBe(true);
			});
		});
	});

	// today (mocked) = 2025-11-22
	describe('getDisplayDueDate', () => {
		it('no_dueDate_returns_undefined', () => {
			// given
			const task = { id: '1', title: 'Test' } as Task;
			// when / then
			expect(getDisplayDueDate(task)).toBeUndefined();
		});

		it('nonRecurring_returns_dueDate', () => {
			// given
			const task: Task = {
				id: '1',
				title: 'Test',
				dueDate: new Date('2025-11-25'),
				recurring: false
			};
			// when
			const result = getDisplayDueDate(task);
			// then
			expect(result?.toISOString().slice(0, 10)).toBe('2025-11-25');
		});

		it('recurringNotDone_returns_advancedDueDate', () => {
			// given — dueDate not yet advanced (task not done), due in future
			const task: Task = {
				id: '1',
				title: 'Test',
				dueDate: new Date('2025-11-25'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1,
				done: undefined
			};
			// when
			const result = getDisplayDueDate(task);
			// then
			expect(result?.toISOString().slice(0, 10)).toBe('2025-11-25');
		});

		it('recurringDoneThisPeriod_returns_previousDueDate', () => {
			// given — dueDate advanced to 23.11 (backend), today(22.11) <= prev(22.11) → done
			const task: Task = {
				id: '1',
				title: 'Test',
				dueDate: new Date('2025-11-23'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1,
				done: new Date('2025-11-22')
			};
			// when
			const result = getDisplayDueDate(task);
			// then — should show old due date (22.11), not the advanced one (23.11)
			expect(result?.toISOString().slice(0, 10)).toBe('2025-11-22');
		});

		it('recurringDonePreviousPeriodExpired_returns_advancedDueDate', () => {
			// given — dueDate=22.11, prev=21.11, today(22) > 21 → no longer done
			const task: Task = {
				id: '1',
				title: 'Test',
				dueDate: new Date('2025-11-22'),
				recurring: true,
				recurrenceUnit: 'days',
				recurrenceInterval: 1,
				done: new Date('2025-11-21')
			};
			// when
			const result = getDisplayDueDate(task);
			// then — task is no longer done, show the current (advanced) dueDate
			expect(result?.toISOString().slice(0, 10)).toBe('2025-11-22');
		});
	});
});
