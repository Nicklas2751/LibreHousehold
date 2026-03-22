import { writable } from 'svelte/store';
import {
    Configuration,
    StatisticsApi,
    type StatisticsResponse,
    GetStatisticsPeriodEnum,
} from '../../generated-sources/openapi';

export { GetStatisticsPeriodEnum };
export type StatisticsPeriod = typeof GetStatisticsPeriodEnum[keyof typeof GetStatisticsPeriodEnum];

export const statistics = writable<StatisticsResponse | null>(null);
export const statisticsLoading = writable<boolean>(false);
export const statisticsError = writable<string | null>(null);

const apiConfig = new Configuration({ basePath: '/api' });
const api = new StatisticsApi(apiConfig);

export const loadStatistics = async (
    householdId: string,
    period: StatisticsPeriod
): Promise<void> => {
    statisticsLoading.set(true);
    statisticsError.set(null);
    try {
        const result = await api.getStatistics({ householdId, period });
        statistics.set(result);
    } catch (e) {
        statisticsError.set('Statistiken konnten nicht geladen werden.');
        console.error('Failed to load statistics:', e);
    } finally {
        statisticsLoading.set(false);
    }
};
