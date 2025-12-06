import type {Task} from '../generated-sources/openapi';
import {checkIsDone} from './taskDueCalculator';

export const TaskFilterType = {
    ALL: '',
    ASSIGNED_TO_ME: 'assigned_to_me',
    PENDING: 'pending',
    COMPLETED: 'completed'
} as const;

export type TaskFilterType = (typeof TaskFilterType)[keyof typeof TaskFilterType];

/**
 * Filters tasks based on the specified filter type.
 *
 * @param tasks - The list of tasks to filter
 * @param filterType - The type of filter to apply
 * @param currentUserId - The ID of the current user (required for ASSIGNED_TO_ME filter)
 * @returns The filtered list of tasks
 */
export function filterTasks(
    tasks: Task[],
    filterType: TaskFilterType,
    currentUserId: string | undefined
): Task[] {
    switch (filterType) {
        case TaskFilterType.ALL:
            return tasks;

        case TaskFilterType.ASSIGNED_TO_ME:
            if (!currentUserId) {
                return [];
            }
            return tasks.filter(task => task.assignedTo === currentUserId);

        case TaskFilterType.PENDING:
            return tasks.filter(task => !checkIsDone(task));

        case TaskFilterType.COMPLETED:
            return tasks.filter(task => checkIsDone(task));

        default:
            return tasks;
    }
}

