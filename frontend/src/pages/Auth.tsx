import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuthStore } from '@/stores/authStore';
import { authAPI } from '@/lib/api';
import { loginSchema, signupSchema, LoginFormData, SignupFormData } from '@/lib/validations';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { FileCode, Loader2, Mail, Lock, User, ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';

type AuthMode = 'login' | 'signup';

export default function Auth() {
  const [mode, setMode] = useState<AuthMode>('login');
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  const loginForm = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  const signupForm = useForm<SignupFormData>({
    resolver: zodResolver(signupSchema),
    defaultValues: { username: '', email: '', password: '' },
  });

  const handleLogin = async (data: LoginFormData) => {
    setLoading(true);
    try {
      const response = await authAPI.login({
        email: data.email,
        password: data.password,
      });

      const token = response.data.token || response.data.jwt || response.data.accessToken;
      if (!token) {
        throw new Error('No token returned from server');
      }

      setAuth(token, null);
      const userResponse = await authAPI.getMe();
      setAuth(token, userResponse.data);
      toast.success('Welcome back!');
      navigate('/dashboard');
    } catch (error: any) {
      const message = error.response?.data?.message || error.response?.data?.error || 'Authentication failed';
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (data: SignupFormData) => {
    setLoading(true);
    try {
      await authAPI.signup({
        username: data.username,
        email: data.email,
        password: data.password,
      });
      toast.success('Account created! Please log in.');
      setMode('login');
      loginForm.reset();
    } catch (error: any) {
      const message = error.response?.data?.message || error.response?.data?.error || 'Authentication failed';
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  const switchMode = (newMode: AuthMode) => {
    setMode(newMode);
    loginForm.clearErrors();
    signupForm.clearErrors();
  };

  return (
    <div className="min-h-screen gradient-mesh relative">
      {/* Background effects */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-accent/5 rounded-full blur-3xl animate-float" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-primary/5 rounded-full blur-3xl animate-float" style={{ animationDelay: '3s' }} />
      </div>

      {/* Back to Home Button - Top Left */}
      <div className="absolute top-6 left-6 z-20">
        <Link
          to="/"
          className="inline-flex items-center gap-2 px-4 py-2 text-sm text-muted-foreground hover:text-foreground transition-colors rounded-lg hover:bg-card/50 group"
        >
          <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-1" />
          <span>Back to Home</span>
        </Link>
      </div>

      <div className="flex items-center justify-center min-h-screen px-4 py-12">
        <div className="w-full max-w-md animate-fade-in relative z-10">
          {/* Logo */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center gap-3 mb-4">
              <div className="relative">
                <FileCode className="h-12 w-12 text-accent" />
                <div className="absolute inset-0 blur-xl bg-accent/40" />
              </div>
              <span className="text-3xl font-bold text-gradient">Intelli-Doc AI</span>
            </div>
            <p className="text-muted-foreground">AI-Powered Code Documentation</p>
          </div>

          {/* Auth Card */}
          <div className="glass-strong rounded-2xl p-8 neon-border">
            <div className="flex mb-8">
              <button
                type="button"
                onClick={() => switchMode('login')}
                className={`flex-1 py-2 text-sm font-medium transition-colors ${mode === 'login'
                    ? 'text-accent border-b-2 border-accent'
                    : 'text-muted-foreground border-b border-border hover:text-foreground'
                  }`}
              >
                Sign In
              </button>
              <button
                type="button"
                onClick={() => switchMode('signup')}
                className={`flex-1 py-2 text-sm font-medium transition-colors ${mode === 'signup'
                    ? 'text-accent border-b-2 border-accent'
                    : 'text-muted-foreground border-b border-border hover:text-foreground'
                  }`}
              >
                Create Account
              </button>
            </div>

            {/* Login Form */}
            {mode === 'login' && (
              <form onSubmit={loginForm.handleSubmit(handleLogin)} className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="login-email" className="text-foreground">Email</Label>
                  <div className="relative">
                    <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="login-email"
                      type="email"
                      placeholder="john@example.com"
                      {...loginForm.register('email')}
                      className={`pl-10 ${loginForm.formState.errors.email ? 'border-red-500 focus:border-red-500' : ''}`}
                    />
                  </div>
                  {loginForm.formState.errors.email && (
                    <p className="text-sm text-red-500">{loginForm.formState.errors.email.message}</p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="login-password" className="text-foreground">Password</Label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="login-password"
                      type="password"
                      placeholder="••••••••"
                      {...loginForm.register('password')}
                      className={`pl-10 ${loginForm.formState.errors.password ? 'border-red-500 focus:border-red-500' : ''}`}
                    />
                  </div>
                  {loginForm.formState.errors.password && (
                    <p className="text-sm text-red-500">{loginForm.formState.errors.password.message}</p>
                  )}
                </div>

                <Button type="submit" variant="neon" className="w-full" disabled={loading}>
                  {loading ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Signing in...
                    </>
                  ) : (
                    'Sign In'
                  )}
                </Button>
              </form>
            )}

            {/* Signup Form */}
            {mode === 'signup' && (
              <form onSubmit={signupForm.handleSubmit(handleSignup)} className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="signup-username" className="text-foreground">Username</Label>
                  <div className="relative">
                    <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="signup-username"
                      placeholder="johndoe"
                      {...signupForm.register('username')}
                      className={`pl-10 ${signupForm.formState.errors.username ? 'border-red-500 focus:border-red-500' : ''}`}
                    />
                  </div>
                  {signupForm.formState.errors.username && (
                    <p className="text-sm text-red-500">{signupForm.formState.errors.username.message}</p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="signup-email" className="text-foreground">Email</Label>
                  <div className="relative">
                    <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="signup-email"
                      type="email"
                      placeholder="john@example.com"
                      {...signupForm.register('email')}
                      className={`pl-10 ${signupForm.formState.errors.email ? 'border-red-500 focus:border-red-500' : ''}`}
                    />
                  </div>
                  {signupForm.formState.errors.email && (
                    <p className="text-sm text-red-500">{signupForm.formState.errors.email.message}</p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="signup-password" className="text-foreground">Password</Label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="signup-password"
                      type="password"
                      placeholder="••••••••"
                      {...signupForm.register('password')}
                      className={`pl-10 ${signupForm.formState.errors.password ? 'border-red-500 focus:border-red-500' : ''}`}
                    />
                  </div>
                  {signupForm.formState.errors.password && (
                    <p className="text-sm text-red-500">{signupForm.formState.errors.password.message}</p>
                  )}
                </div>

                <Button type="submit" variant="neon" className="w-full" disabled={loading}>
                  {loading ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Creating account...
                    </>
                  ) : (
                    'Create Account'
                  )}
                </Button>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
