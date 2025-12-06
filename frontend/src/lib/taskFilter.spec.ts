import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {filterTasks, TaskFilterType} from './taskFilter';
import type {Task} from '../generated-sources/openapi';

describe('taskFilter', () => {
    beforeEach(() => {
        // Mock current date to December 4, 2025 00:00:00 UTC
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2025-12-04T00:00:00Z'));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    // Helper to create a task with minimal required fields
    const createTask = (overrides: Partial<Task> = {}): Task => ({
        id: 'task-1',
        title: 'Test Task',
        dueDate: new Date('2025-12-01'),
        ...overrides
    });

    describe('ALL filter', () => {
        it('should return all tasks when filter is ALL', () => {
            const tasks: Task[] = [
                createTask({id: 'task-1', title: 'Task 1'}),
                createTask({id: 'task-2', title: 'Task 2'}),
                createTask({id: 'task-3', title: 'Task 3'})
            ];

            const result = filterTasks(tasks, TaskFilterType.ALL, 'user-1');

            expect(result).toHaveLength(3);
            expect(result).toEqual(tasks);
        });

        it('should return empty array when no tasks exist', () => {
            const result = filterTasks([], TaskFilterType.ALL, 'user-1');

            expect(result).toHaveLength(0);
            expect(result).toEqual([]);
        });
    });

    describe('ASSIGNED_TO_ME filter', () => {
        it('should return only tasks assigned to current user', () => {
            const currentUserId = 'user-1';
            const tasks: Task[] = [
                createTask({id: 'task-1', assignedTo: 'user-1'}),
                createTask({id: 'task-2', assignedTo: 'user-2'}),
                createTask({id: 'task-3', assignedTo: 'user-1'})
            ];

            const result = filterTasks(tasks, TaskFilterType.ASSIGNED_TO_ME, currentUserId);

            expect(result).toHaveLength(2);
            expect(result.map(t => t.id)).toEqual(['task-1', 'task-3']);
        });

        it('should return empty array when no tasks assigned to user', () => {
            const currentUserId = 'user-1';
            const tasks: Task[] = [
                createTask({id: 'task-1', assignedTo: 'user-2'}),
                createTask({id: 'task-2', assignedTo: 'user-3'})
            ];

            const result = filterTasks(tasks, TaskFilterType.ASSIGNED_TO_ME, currentUserId);

            expect(result).toHaveLength(0);
        });

        it('should return empty array when currentUserId is undefined', () => {
            const tasks: Task[] = [
                createTask({id: 'task-1', assignedTo: 'user-1'}),
                createTask({id: 'task-2', assignedTo: 'user-2'})
            ];

            const result = filterTasks(tasks, TaskFilterType.ASSIGNED_TO_ME, undefined);

            expect(result).toHaveLength(0);
        });

        it('should not include tasks without assignedTo', () => {
            const currentUserId = 'user-1';
            const tasks: Task[] = [
                createTask({id: 'task-1', assignedTo: 'user-1'}),
                createTask({id: 'task-2', assignedTo: undefined})
            ];

            const result = filterTasks(tasks, TaskFilterType.ASSIGNED_TO_ME, currentUserId);

            expect(result).toHaveLength(1);
            expect(result[0].id).toBe('task-1');
        });
    });

    describe('PENDING filter', () => {
        it('should return only tasks that are not done', () => {
            const tasks: Task[] = [
                createTask({id: 'task-1', done: undefined}),
                createTask({id: 'task-2', done: new Date('2025-12-02')}), // done after due date
                createTask({id: 'task-3', done: undefined})
            ];

            const result = filterTasks(tasks, TaskFilterType.PENDING, 'user-1');

            expect(result).toHaveLength(2);
            expect(result.map(t => t.id)).toEqual(['task-1', 'task-3']);
        });

        it('should include recurring tasks that need to be done again', () => {
            // Recurring task: weekly, started Nov 1, done on Nov 8 (after Nov 1 due)
            // Current date: Dec 4 -> last due date is Dec 4 (or Nov 27)
            // Done date Nov 8 is before Dec 4, so task needs to be done again
            const recurringTask = createTask({
                id: 'recurring-task',
                dueDate: new Date('2025-11-01'),
                recurring: true,
                recurrenceUnit: 'weeks',
                recurrenceInterval: 1,
                done: new Date('2025-11-08') // Done after Nov 1, but before current period
            });

            const result = filterTasks([recurringTask], TaskFilterType.PENDING, 'user-1');

            expect(result).toHaveLength(1);
            expect(result[0].id).toBe('recurring-task');
        });

        it('should return all tasks when none are done', () => {
            const tasks: Task[] = [
                createTask({id: 'task-1', done: undefined}),
                createTask({id: 'task-2', done: undefined}),
                createTask({id: 'task-3', done: undefined})
            ];

            const result = filterTasks(tasks, TaskFilterType.PENDING, 'user-1');

            expect(result).toHaveLength(3);
        });
    });

    describe('COMPLETED filter', () => {
        it('should return only tasks that are done', () => {
            const tasks: Task[] = [
                createTask({id: 'task-1', done: undefined}),
                createTask({id: 'task-2', dueDate: new Date('2025-12-01'), done: new Date('2025-12-02')}),
                createTask({id: 'task-3', done: undefined})
            ];

            const result = filterTasks(tasks, TaskFilterType.COMPLETED, 'user-1');

            expect(result).toHaveLength(1);
            expect(result[0].id).toBe('task-2');
        });

        it('should include recurring tasks that are done for current period', () => {
            // Recurring task: weekly, started Nov 27
            // Current date: Dec 4 -> last due date is Dec 4
            // Done date Dec 5 is after Dec 4, so task is done for this period
            const recurringTask = createTask({
                id: 'recurring-task',
                dueDate: new Date('2025-11-27'),
                recurring: true,
                recurrenceUnit: 'weeks',
                recurrenceInterval: 1,
                done: new Date('2025-12-05') // Done after the last due date (Dec 4)
            });

            const result = filterTasks([recurringTask], TaskFilterType.COMPLETED, 'user-1');

            expect(result).toHaveLength(1);
            expect(result[0].id).toBe('recurring-task');
        });

        it('should return empty array when no tasks are done', () => {
            const tasks: Task[] = [
                createTask({id: 'task-1', done: undefined}),
                createTask({id: 'task-2', done: undefined})
            ];

            const result = filterTasks(tasks, TaskFilterType.COMPLETED, 'user-1');

            expect(result).toHaveLength(0);
        });
    });

    describe('unknown filter type', () => {
        it('should return all tasks for unknown filter type', () => {
            const tasks: Task[] = [
                createTask({id: 'task-1'}),
                createTask({id: 'task-2'})
            ];

            const result = filterTasks(tasks, 'unknown' as TaskFilterType, 'user-1');

            expect(result).toHaveLength(2);
        });
    });
});

