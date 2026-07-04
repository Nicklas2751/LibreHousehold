import { type Writable, writable } from 'svelte/store';
import { type Task, type TaskEdit, TasksApi, type TaskUpdate } from '../../generated-sources/openapi';
import { addToast } from './toastStore';
import { Toast } from '$lib/toast';
import { createApiConfig } from '$lib/api';

export const tasks: Writable<Task[]> = writable([]);

const api = new TasksApi(createApiConfig());

export const addTask = async (householdId: string, task: Task): Promise<Task> => {
	try {
		const savedTask = await api.createTask({ householdId: householdId, task: task });
		tasks.update((all) => [savedTask, ...all]);
		return savedTask;
	} catch (error) {
		addToast(new Toast('Failed to create task', 'error', 5000));
		throw error;
	}
};

export const loadTasks = async (householdId: string): Promise<void> => {
	tasks.set(await api.getTasks({ householdId: householdId }));
};

export const editTask = async (
	householdId: string,
	taskId: string,
	taskEdit: TaskEdit
): Promise<void> => {
	let previousTasks: Task[] = [];
	tasks.update((all) => {
		previousTasks = [...all];
		return all.map((t) => (t.id === taskId ? { ...t, ...taskEdit } : t));
	});

	try {
		const updated = await api.editTask({ householdId, taskId, taskEdit });
		tasks.update((all) => all.map((t) => (t.id === taskId ? updated : t)));
	} catch (error) {
		tasks.set(previousTasks);
		addToast(new Toast('Failed to edit task', 'error', 5000));
		console.error('Failed to edit task:', error);
	}
};

export const deleteTask = async (householdId: string, taskId: string): Promise<void> => {
	let previousTasks: Task[] = [];
	tasks.update((all) => {
		previousTasks = [...all];
		return all.filter((t) => t.id !== taskId);
	});

	try {
		await api.deleteTask({ householdId, taskId });
	} catch (error) {
		tasks.set(previousTasks);
		addToast(new Toast('Failed to delete task', 'error', 5000));
		console.error('Failed to delete task:', error);
	}
};

export const updateTaskDoneStatus = async (
	householdId: string,
	taskId: string,
	doneDate: Date | null
): Promise<void> => {
	// Optimistic update: Update the store immediately
	let previousTasks: Task[] = [];
	tasks.update((all) => {
		previousTasks = [...all];
		return all.map((task) =>
			task.id === taskId ? { ...task, done: doneDate ?? undefined } : task
		);
	});

	try {
		const taskUpdate: TaskUpdate = {
			done: doneDate ?? undefined
		};
		const updated = await api.updateTask({ householdId, taskId, taskUpdate });
		tasks.update((all) => all.map((t) => (t.id === taskId ? updated : t)));
	} catch (error) {
		// Rollback on error
		tasks.set(previousTasks);
		addToast(new Toast('Failed to update task status', 'error', 5000));
		console.error('Failed to update task done status:', error);
	}
};
