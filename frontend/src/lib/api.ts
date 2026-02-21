import axios from 'axios';
import { useAuthStore } from '@/stores/authStore';

const API_BASE_URL = 'http://localhost:8080/api';

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add JWT token
api.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().token;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/auth';
    }
    return Promise.reject(error);
  }
);

// Auth API
export const authAPI = {
  signup: (data: { username: string; email: string; password: string }) =>
    api.post('/auth/signup', data),

  login: (data: { email: string; password: string }) =>
    api.post('/auth/login', data),

  getMe: () => api.get('/users/me'),
};

// Repository API
export const repoAPI = {
  create: (url: string) =>
    api.post('/repositories', { url }),

  getMyRepos: () =>
    api.get('/repositories/my-repos'),

  getStatus: (id: string) =>
    api.get(`/repositories/${id}/status`),

  getDocumentation: (id: string) =>
    api.get(`/repositories/${id}/documentation`),

  generateReadme: (id: string) =>
    api.post(`/repositories/${id}/generate-readme`),

  getReadme: (id: string) =>
    api.get(`/repositories/${id}/readme`),

  delete: (id: string) =>
    api.delete(`/repositories/${id}`),

  updateReadme: (id: string, content: string) =>
    api.put(`/repositories/${id}/readme`, { content }),
};
