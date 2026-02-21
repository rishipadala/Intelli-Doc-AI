import { create } from 'zustand';

export interface ProgressLog {
    step: string;
    message: string;
    timestamp: string;
}

interface ProgressLogState {
    logs: Record<string, ProgressLog[]>; // keyed by repoId
    addLog: (repoId: string, log: ProgressLog) => void;
    clearLogs: (repoId: string) => void;
    getLogsForRepo: (repoId: string) => ProgressLog[];
}

export const useProgressLogStore = create<ProgressLogState>((set, get) => ({
    logs: {},

    addLog: (repoId, log) =>
        set((state) => ({
            logs: {
                ...state.logs,
                [repoId]: [...(state.logs[repoId] || []), log],
            },
        })),

    clearLogs: (repoId) =>
        set((state) => ({
            logs: {
                ...state.logs,
                [repoId]: [],
            },
        })),

    getLogsForRepo: (repoId) => get().logs[repoId] || [],
}));
