import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get } from 'svelte/store';
import type { Task, TaskEdit } from '../../generated-sources/openapi';

const mockCreateTask = vi.hoisted(() => vi.fn());
const mockUpdateTask = vi.hoisted(() => vi.fn());
const mockEditTask = vi.hoisted(() => vi.fn());
const mockDeleteTask = vi.hoisted(() => vi.fn());

vi.mock('../../generated-sources/openapi', () => ({
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	Configuration: vi.fn(function (this: any) {}),
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	TasksApi: vi.fn(function (this: any) {
		return {
			createTask: mockCreateTask,
			updateTask: mockUpdateTask,
			editTask: mockEditTask,
			deleteTask: mockDeleteTask,
			getTasks: vi.fn()
		};
	})
}));

const mockAddToast = vi.hoisted(() => vi.fn());
vi.mock('./toastStore', () => ({
	addToast: mockAddToast
}));

import { tasks, addTask, updateTaskDoneStatus, editTask, deleteTask } from './taskStore';

describe('taskStore', () => {
	beforeEach(() => {
		tasks.set([]);
		vi.clearAllMocks();
	});

	describe('addTask', () => {
		it('apiSuccess_addsTaskToStoreAndReturns', async () => {
			// given
			const task: Task = { id: 'task-1', title: 'Clean', dueDate: new Date('2024-07-01') };
			mockCreateTask.mockResolvedValueOnce(task);

			// when
			const result = await addTask('household-1', task);

			// then
			expect(result).toEqual(task);
			expect(get(tasks)).toEqual([task]);
		});

		it('apiError_showsErrorToastAndDoesNotUpdateStore', async () => {
			// given
			const task: Task = { id: 'task-1', title: 'Clean', dueDate: new Date('2024-07-01') };
			mockCreateTask.mockRejectedValueOnce(new Error('Network error'));

			// when / then
			await expect(addTask('household-1', task)).rejects.toThrow('Network error');
			expect(mockAddToast).toHaveBeenCalledOnce();
			expect(get(tasks)).toEqual([]);
		});
	});

	describe('updateTaskDoneStatus', () => {
		it('apiSuccess_updatesStoreWithServerResponse', async () => {
			// given — server may modify other fields (e.g. advancing dueDate for recurring tasks)
			const taskId = 'task-1';
			const originalTask: Task = {
				id: taskId,
				title: 'Clean',
				dueDate: new Date('2024-07-01'),
				recurring: true,
				recurrenceUnit: 'weeks',
				recurrenceInterval: 1
			};
			const serverUpdatedTask: Task = {
				...originalTask,
				done: new Date('2024-07-05'),
				dueDate: new Date('2024-07-08')
			};
			tasks.set([originalTask]);
			mockUpdateTask.mockResolvedValueOnce(serverUpdatedTask);

			// when
			await updateTaskDoneStatus('household-1', taskId, new Date('2024-07-05'));

			// then
			expect(get(tasks)).toEqual([serverUpdatedTask]);
		});

		it('apiError_rollsBackToOriginalStateAndShowsToast', async () => {
			// given
			const taskId = 'task-1';
			const originalTask: Task = { id: taskId, title: 'Clean', dueDate: new Date('2024-07-01') };
			tasks.set([originalTask]);
			mockUpdateTask.mockRejectedValueOnce(new Error('Server error'));

			// when
			await updateTaskDoneStatus('household-1', taskId, new Date('2024-07-05'));

			// then
			expect(get(tasks)).toEqual([originalTask]);
			expect(mockAddToast).toHaveBeenCalledOnce();
		});
	});

	describe('editTask', () => {
		it('apiSuccess_updatesStoreWithServerResponse', async () => {
			// given
			const taskId = 'task-1';
			const originalTask: Task = {
				id: taskId,
				title: 'Old title',
				dueDate: new Date('2024-07-01')
			};
			const edit: TaskEdit = { title: 'New title', dueDate: new Date('2024-08-01') };
			const serverUpdatedTask: Task = {
				id: taskId,
				title: 'New title',
				dueDate: new Date('2024-08-01')
			};
			tasks.set([originalTask]);
			mockEditTask.mockResolvedValueOnce(serverUpdatedTask);

			// when
			await editTask('household-1', taskId, edit);

			// then
			expect(get(tasks)).toEqual([serverUpdatedTask]);
		});

		it('apiError_rollsBackToOriginalStateAndShowsToast', async () => {
			// given
			const taskId = 'task-1';
			const originalTask: Task = {
				id: taskId,
				title: 'Old title',
				dueDate: new Date('2024-07-01')
			};
			const edit: TaskEdit = { title: 'New title', dueDate: new Date('2024-08-01') };
			tasks.set([originalTask]);
			mockEditTask.mockRejectedValueOnce(new Error('Server error'));

			// when
			await editTask('household-1', taskId, edit);

			// then
			expect(get(tasks)).toEqual([originalTask]);
			expect(mockAddToast).toHaveBeenCalledOnce();
		});
	});

	describe('deleteTask', () => {
		it('apiSuccess_removesTaskFromStore', async () => {
			// given
			const taskId = 'task-1';
			const task: Task = { id: taskId, title: 'Clean', dueDate: new Date('2024-07-01') };
			const otherTask: Task = { id: 'task-2', title: 'Cook', dueDate: new Date('2024-07-02') };
			tasks.set([task, otherTask]);
			mockDeleteTask.mockResolvedValueOnce(undefined);

			// when
			await deleteTask('household-1', taskId);

			// then
			expect(get(tasks)).toEqual([otherTask]);
		});

		it('apiError_rollsBackToOriginalStateAndShowsToast', async () => {
			// given
			const taskId = 'task-1';
			const task: Task = { id: taskId, title: 'Clean', dueDate: new Date('2024-07-01') };
			tasks.set([task]);
			mockDeleteTask.mockRejectedValueOnce(new Error('Server error'));

			// when
			await deleteTask('household-1', taskId);

			// then
			expect(get(tasks)).toEqual([task]);
			expect(mockAddToast).toHaveBeenCalledOnce();
		});
	});
});
