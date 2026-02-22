import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { authAPI } from '@/lib/api';
import { FileCode, Loader2, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';

export default function GitHubCallback() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const { setAuth } = useAuthStore();
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const code = searchParams.get('code');
        const errorParam = searchParams.get('error');

        if (errorParam) {
            setError('GitHub authorization was denied.');
            return;
        }

        if (!code) {
            setError('No authorization code received from GitHub.');
            return;
        }

        const exchangeCode = async () => {
            try {
                // 1. Send code to backend
                const response = await authAPI.githubLogin(code);
                const { token, id, username, email } = response.data;

                if (!token) {
                    throw new Error('No token returned from server');
                }

                // 2. Store auth state
                setAuth(token, { id, username, email });

                // 3. Fetch full user profile
                const userResponse = await authAPI.getMe();
                setAuth(token, userResponse.data);

                toast.success(`Welcome, ${userResponse.data.username || username}!`);
                navigate('/dashboard');
            } catch (err: any) {
                const message = err.response?.data?.message || 'GitHub authentication failed';
                setError(message);
                toast.error(message);
            }
        };

        exchangeCode();
    }, [searchParams, navigate, setAuth]);

    if (error) {
        return (
            <div className="min-h-screen gradient-mesh flex items-center justify-center px-4">
                <div className="glass-strong rounded-2xl p-8 neon-border max-w-md w-full text-center animate-fade-in">
                    <div className="inline-flex items-center justify-center gap-3 mb-6">
                        <AlertCircle className="h-10 w-10 text-red-400" />
                    </div>
                    <h2 className="text-xl font-bold text-foreground mb-2">Authentication Failed</h2>
                    <p className="text-muted-foreground mb-6">{error}</p>
                    <Button variant="neon" onClick={() => navigate('/auth')}>
                        Back to Login
                    </Button>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen gradient-mesh flex items-center justify-center px-4">
            <div className="glass-strong rounded-2xl p-8 neon-border max-w-md w-full text-center animate-fade-in">
                <div className="inline-flex items-center justify-center gap-3 mb-6">
                    <div className="relative">
                        <FileCode className="h-10 w-10 text-accent" />
                        <div className="absolute inset-0 blur-xl bg-accent/40" />
                    </div>
                </div>
                <h2 className="text-xl font-bold text-foreground mb-2">Signing in with GitHub...</h2>
                <p className="text-muted-foreground mb-6">Please wait while we verify your account</p>
                <Loader2 className="h-8 w-8 animate-spin text-accent mx-auto" />
            </div>
        </div>
    );
}
