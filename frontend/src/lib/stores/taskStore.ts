import {type Writable, writable} from "svelte/store";
import {Configuration, type Task, TasksApi} from "../../generated-sources/openapi";

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