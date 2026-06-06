import type { Task } from '../generated-sources/openapi';

function isTaskValidForDueCalculation(task: Task) {
	return !task.recurring || !task.dueDate || !task.recurrenceUnit || !task.recurrenceInterval;
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
	// @ts-expect-error TS2345
	let nextDueDate = addInterval(currentDueDate, task.recurrenceUnit, task.recurrenceInterval);
	while (nextDueDate <= today) {
		currentDueDate = nextDueDate;
		// @ts-expect-error TS2345
		nextDueDate = addInterval(nextDueDate, task.recurrenceUnit, task.recurrenceInterval);
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
		// @ts-expect-error TS2345
		currentDueDate = addInterval(currentDueDate, task.recurrenceUnit, task.recurrenceInterval);
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
			newDate.setUTCDate(newDate.getUTCDate() + interval * 7);
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
 * Returns the due date to display for a task.
 *
 * For a recurring task that is still within its "done" period, returns the previous due date
 * (dueDate - interval) so the UI reflects when the task was actually due, not the next occurrence.
 */
export function getDisplayDueDate(task: Task): Date | undefined {
	if (!task.dueDate) return undefined;
	if (task.recurring && checkIsDone(task) && task.recurrenceUnit && task.recurrenceInterval) {
		return addInterval(new Date(task.dueDate), task.recurrenceUnit as string, -task.recurrenceInterval);
	}
	return new Date(task.dueDate);
}

/**
 * Checks if a task is considered done.
 *
 * For recurring tasks the backend advances dueDate by one interval when done is set,
 * so the previous due date is dueDate - interval. The task counts as done while today
 * is still on or before that previous due date.
 *
 * @param task The task to check.
 * @returns True if the task is done, false otherwise.
 */
export function checkIsDone(task: Task): boolean {
	if (!task.done) return false;
	if (!task.recurring) return true;

	if (!task.recurrenceUnit || !task.recurrenceInterval || !task.dueDate) return false;

	const previousDueDate = addInterval(new Date(task.dueDate), task.recurrenceUnit as string, -task.recurrenceInterval);
	previousDueDate.setUTCHours(0, 0, 0, 0);

	const today = new Date();
	today.setUTCHours(0, 0, 0, 0);

	return today <= previousDueDate;
}
