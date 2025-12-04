import {type Writable, writable} from "svelte/store";
import {Configuration, type Task, TasksApi, type TaskUpdate} from "../../generated-sources/openapi";
import {addToast} from "./toastStore";
import {Toast} from "$lib/toast";

export const tasks: Writable<Task[]> = writable([]);

const apiConfig = new Configuration({basePath: '/api'});
const api = new TasksApi(apiConfig);

export const addTask = async (householdId: string, task: Task): Promise<Task> => {
    let savedTask = await api.createTask({householdId: householdId, task: task});
    tasks.update((all) => [savedTask, ...all]);
    return savedTask;
};

export const loadTasks = async (householdId: string): Promise<void> => {
    tasks.set(await api.getTasks({householdId: householdId}));
}

export const updateTaskDoneStatus = async (householdId: string, taskId: string, doneDate: Date | null): Promise<void> => {
    // Optimistic update: Update the store immediately
    let previousTasks: Task[] = [];
    tasks.update((all) => {
        previousTasks = [...all];
        return all.map((task) =>
            task.id === taskId ? {...task, done: doneDate ?? undefined} : task
        );
    });

    try {
        const taskUpdate: TaskUpdate = {
            done: doneDate ?? undefined
        };
        await api.updateTask({householdId, taskId, taskUpdate});
    } catch (error) {
        // Rollback on error
        tasks.set(previousTasks);
        addToast(new Toast("Failed to update task status", "error", 5000));
        console.error("Failed to update task done status:", error);
    }
}
