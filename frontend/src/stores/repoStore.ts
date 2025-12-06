import { create } from 'zustand';

export type RepoStatus = 
  | 'QUEUED' 
  | 'PROCESSING' 
  | 'ANALYZING_CODE' 
  | 'ANALYSIS_COMPLETED' 
  | 'GENERATING_README' 
  | 'COMPLETED' 
  | 'FAILED';

export interface Repository {
  id: string;
  url: string;
  name: string;
  status: RepoStatus;
  fileTree?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Documentation {
  id: string;
  filePath: string;
  content: string;
  repositoryId: string;
}

interface RepoState {
  repositories: Repository[];
  currentRepo: Repository | null;
  setRepositories: (repos: Repository[]) => void;
  addRepository: (repo: Repository) => void;
  updateRepository: (id: string, updates: Partial<Repository>) => void;
  setCurrentRepo: (repo: Repository | null) => void;

}

export const useRepoStore = create<RepoState>((set, get) => ({
  repositories: [],
  currentRepo: null,
  
  setRepositories: (repos) => set({ repositories: repos }),
  
  addRepository: (repo) => set((state) => ({ 
    repositories: [repo, ...state.repositories] 
  })),
  
  updateRepository: (id, updates) => set((state) => ({
    repositories: state.repositories.map((r) => 
      r.id === id ? { ...r, ...updates } : r
    ),
    currentRepo: state.currentRepo?.id === id 
      ? { ...state.currentRepo, ...updates } 
      : state.currentRepo,
  })),
  
  setCurrentRepo: (repo) => set({ currentRepo: repo }),
  
}));
