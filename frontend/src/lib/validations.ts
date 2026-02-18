import { z } from 'zod';

// --- Auth Schemas ---

export const loginSchema = z.object({
    email: z
        .string()
        .min(1, 'Email is required.')
        .email('Please enter a valid email address.'),
    password: z
        .string()
        .min(1, 'Password is required.'),
});

export const signupSchema = z.object({
    username: z
        .string()
        .min(3, 'Username must be at least 3 characters.')
        .max(50, 'Username cannot exceed 50 characters.'),
    email: z
        .string()
        .min(1, 'Email is required.')
        .email('Please enter a valid email address.'),
    password: z
        .string()
        .min(6, 'Password must be at least 6 characters.')
        .max(100, 'Password cannot exceed 100 characters.'),
});

export type LoginFormData = z.infer<typeof loginSchema>;
export type SignupFormData = z.infer<typeof signupSchema>;

// --- Repository Schemas ---

export const repoUrlSchema = z.object({
    url: z
        .string()
        .min(1, 'Repository URL is required.')
        .url('Please enter a valid URL.')
        .regex(
            /^https:\/\/github\.com\/[\w.-]+\/[\w.-]+/,
            'Must be a valid GitHub repository URL (https://github.com/owner/repo).'
        ),
});

export type RepoUrlFormData = z.infer<typeof repoUrlSchema>;
