import type {Task} from "../generated-sources/openapi";

function isTaskValidForDueCalculation(task: Task) {
    return !task.recurring || !task.dueDate || !task.recurrenceUnit || task.recurrenceInterval === undefined;
}

/**
 * Returns the last due date of the recurring task that is not in the future.
 * @param task The recurring task to calculate the last due date for.
 * @returns The last due date that is not in the future, or undefined if there is none.
 */
export function getLastDueDate(task: Task): Date | undefined {
    if (isTaskValidForDueCalculation(task)) {
        return undefined;
    }

    const today = new Date();
    today.setUTCHours(0, 0, 0, 0);

    let currentDueDate = new Date(task.dueDate);
    currentDueDate.setUTCHours(0, 0, 0, 0);

    // If the first due date is already after today, there's no due date before today
    if (currentDueDate > today) {
        return undefined;
    }

    // Calculate all due dates until we reach or pass today
    let nextDueDate = addInterval(currentDueDate, task.recurrenceUnit!, task.recurrenceInterval!);
    while (nextDueDate <= today) {
        currentDueDate = nextDueDate;
        nextDueDate = addInterval(nextDueDate, task.recurrenceUnit!, task.recurrenceInterval!);
    }

    return currentDueDate;
}

/**
 * Returns the next due date of the recurring task that is after today.
 * @param task The recurring task to calculate the next due date for.
 * @returns The next due date after today, or undefined if there is none.
 */
export function getNextDueDateAfterToday(task: Task): Date | undefined {
    if (isTaskValidForDueCalculation(task)) {
        return undefined;
    }

    const today = new Date();
    today.setUTCHours(0, 0, 0, 0);

    let currentDueDate = new Date(task.dueDate);
    currentDueDate.setUTCHours(0, 0, 0, 0);

    // Calculate all due dates until we find one after today
    while (currentDueDate <= today) {
        currentDueDate = addInterval(currentDueDate, task.recurrenceUnit!, task.recurrenceInterval!);
    }

    return currentDueDate;
}

/**
 * Adds the specified interval to a date.
 * @param date The date to add the interval to.
 * @param unit The unit of the interval (days, weeks, months, years).
 * @param interval The number of units to add.
 * @returns A new date with the interval added.
 */
export function addInterval(date: Date, unit: string, interval: number): Date {
    const newDate = new Date(date);

    switch (unit) {
        case 'days':
            newDate.setUTCDate(newDate.getUTCDate() + interval);
            break;
        case 'weeks':
            newDate.setUTCDate(newDate.getUTCDate() + (interval * 7));
            break;
        case 'months':
            newDate.setUTCMonth(newDate.getUTCMonth() + interval);
            break;
        case 'years':
            newDate.setUTCFullYear(newDate.getUTCFullYear() + interval);
            break;
    }

    return newDate;
}

/**
 * Checks if a task is considered done.
 *
 * For non-recurring tasks:
 * - The task is done if it has a done date that is after the due date.
 *
 * For recurring tasks:
 * - The task is done if it has a done date that is after the last due date.
 * - If there is no last due date, the task is not done.
 *
 * @param task The task to check.
 * @returns True if the task is done, false otherwise.
 */
export function checkIsDone(task: Task): boolean {
    // If there's no done date, the task is not done
    if (!task.done) {
        return false;
    }

    // Normalize done date to ignore time
    const doneDate = new Date(task.done);
    doneDate.setUTCHours(0, 0, 0, 0);

    if (!task.recurring) {
        // For non-recurring tasks, check if done date is after the due date
        const dueDate = new Date(task.dueDate);
        dueDate.setUTCHours(0, 0, 0, 0);
        return doneDate > dueDate;
    }

    // For recurring tasks, check if done date is after the last due date
    const lastDueDateBeforeToday = getLastDueDate(task);

    // If there's no last due date, the task is not done
    if (!lastDueDateBeforeToday) {
        return false;
    }

    return doneDate > lastDueDateBeforeToday;
}

