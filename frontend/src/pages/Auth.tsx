import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuthStore } from '@/stores/authStore';
import { authAPI } from '@/lib/api';
import { loginSchema, signupSchema, otpSchema, LoginFormData, SignupFormData, OtpFormData } from '@/lib/validations';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { FileCode, Loader2, Mail, Lock, User, ArrowLeft, ShieldCheck, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';

type AuthMode = 'login' | 'signup' | 'verify-otp';

export default function Auth() {
  const [mode, setMode] = useState<AuthMode>('login');
  const [loading, setLoading] = useState(false);
  const [otpEmail, setOtpEmail] = useState('');
  const [otpDigits, setOtpDigits] = useState<string[]>(['', '', '', '', '', '']);
  const [resendCooldown, setResendCooldown] = useState(0);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

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

  const otpForm = useForm<OtpFormData>({
    resolver: zodResolver(otpSchema),
  });

  // Cooldown timer for resend OTP
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setTimeout(() => setResendCooldown(resendCooldown - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendCooldown]);

  // Focus first OTP input when entering verify mode
  useEffect(() => {
    if (mode === 'verify-otp') {
      setTimeout(() => inputRefs.current[0]?.focus(), 100);
    }
  }, [mode]);

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
      // Check if this is an email-not-verified error
      if (error.response?.status === 403 && error.response?.data?.error === 'EMAIL_NOT_VERIFIED') {
        setOtpEmail(data.email);
        setMode('verify-otp');
        setResendCooldown(60);
        toast.info('Please verify your email first. A new OTP has been sent.');
        return;
      }
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
      // Switch to OTP verification mode
      setOtpEmail(data.email);
      setMode('verify-otp');
      setResendCooldown(60);
      toast.success('Account created! Please check your email for the verification code.');
    } catch (error: any) {
      const message = error.response?.data?.message || error.response?.data?.error || 'Authentication failed';
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  const handleOtpDigitChange = useCallback((index: number, value: string) => {
    // Only allow digits
    const digit = value.replace(/\D/g, '').slice(-1);

    setOtpDigits(prev => {
      const newDigits = [...prev];
      newDigits[index] = digit;

      // Auto-submit when all 6 digits are filled
      const fullOtp = newDigits.join('');
      if (fullOtp.length === 6 && newDigits.every(d => d !== '')) {
        otpForm.setValue('otp', fullOtp);
        // Trigger submit after state updates
        setTimeout(() => {
          otpForm.handleSubmit(handleVerifyOtp)();
        }, 100);
      }

      return newDigits;
    });

    // Auto-focus next input
    if (digit && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  }, []);

  const handleOtpKeyDown = useCallback((index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !otpDigits[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
      setOtpDigits(prev => {
        const newDigits = [...prev];
        newDigits[index - 1] = '';
        return newDigits;
      });
    }
  }, [otpDigits]);

  const handleOtpPaste = useCallback((e: React.ClipboardEvent) => {
    e.preventDefault();
    const pasteData = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (pasteData.length > 0) {
      const newDigits = [...otpDigits];
      for (let i = 0; i < 6; i++) {
        newDigits[i] = pasteData[i] || '';
      }
      setOtpDigits(newDigits);

      // Focus the next empty input or the last one
      const nextEmptyIdx = newDigits.findIndex(d => d === '');
      const focusIdx = nextEmptyIdx === -1 ? 5 : nextEmptyIdx;
      inputRefs.current[focusIdx]?.focus();

      // Auto-submit if complete
      if (pasteData.length === 6) {
        otpForm.setValue('otp', pasteData);
        setTimeout(() => {
          otpForm.handleSubmit(handleVerifyOtp)();
        }, 100);
      }
    }
  }, [otpDigits]);

  const handleVerifyOtp = async (data: OtpFormData) => {
    setLoading(true);
    try {
      await authAPI.verifyOtp({ email: otpEmail, otp: data.otp });
      toast.success('Email verified successfully! Please log in.');
      setMode('login');
      setOtpDigits(['', '', '', '', '', '']);
      loginForm.reset();
    } catch (error: any) {
      const message = error.response?.data?.error || error.response?.data?.message || 'Verification failed';
      toast.error(message);
      // Clear OTP inputs on error
      setOtpDigits(['', '', '', '', '', '']);
      setTimeout(() => inputRefs.current[0]?.focus(), 100);
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    if (resendCooldown > 0) return;
    setLoading(true);
    try {
      await authAPI.resendOtp({ email: otpEmail });
      setResendCooldown(60);
      toast.success('A new OTP has been sent to your email.');
    } catch (error: any) {
      const message = error.response?.data?.error || error.response?.data?.message || 'Failed to resend OTP';
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  const switchMode = (newMode: AuthMode) => {
    setMode(newMode);
    loginForm.clearErrors();
    signupForm.clearErrors();
    otpForm.clearErrors();
    setOtpDigits(['', '', '', '', '', '']);
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

      <div className="flex items-center justify-center min-h-screen px-3 sm:px-4 py-12">
        <div className="w-full max-w-md animate-fade-in relative z-10">
          {/* Logo */}
          <div className="text-center mb-6 sm:mb-8">
            <div className="inline-flex items-center justify-center gap-2 sm:gap-3 mb-4">
              <div className="relative">
                <FileCode className="h-10 w-10 sm:h-12 sm:w-12 text-accent" />
                <div className="absolute inset-0 blur-xl bg-accent/40" />
              </div>
              <span className="text-2xl sm:text-3xl font-bold text-gradient">Intelli-Doc AI</span>
            </div>
            <p className="text-muted-foreground text-sm sm:text-base">AI-Powered Code Documentation</p>
          </div>

          {/* Auth Card */}
          <div className="glass-strong rounded-2xl p-5 sm:p-8 neon-border">

            {/* OTP Verification Mode */}
            {mode === 'verify-otp' ? (
              <div className="space-y-6">
                {/* Header */}
                <div className="text-center">
                  <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-accent/10 border border-accent/20 mb-4">
                    <ShieldCheck className="h-8 w-8 text-accent" />
                  </div>
                  <h2 className="text-xl font-semibold text-foreground mb-2">Verify Your Email</h2>
                  <p className="text-sm text-muted-foreground">
                    We've sent a 6-digit code to{' '}
                    <span className="text-accent font-medium">{otpEmail}</span>
                  </p>
                </div>

                {/* OTP Input - 6 individual boxes */}
                <form onSubmit={otpForm.handleSubmit(handleVerifyOtp)} className="space-y-6">
                  <div className="flex justify-center gap-2 sm:gap-3" onPaste={handleOtpPaste}>
                    {otpDigits.map((digit, index) => (
                      <input
                        key={index}
                        ref={(el) => { inputRefs.current[index] = el; }}
                        type="text"
                        inputMode="numeric"
                        maxLength={1}
                        value={digit}
                        onChange={(e) => handleOtpDigitChange(index, e.target.value)}
                        onKeyDown={(e) => handleOtpKeyDown(index, e)}
                        className="w-11 h-14 sm:w-12 sm:h-16 text-center text-xl sm:text-2xl font-bold rounded-xl bg-card/50 border border-border/50 text-foreground focus:border-accent focus:ring-2 focus:ring-accent/20 outline-none transition-all duration-200"
                        autoComplete="one-time-code"
                      />
                    ))}
                  </div>

                  {otpForm.formState.errors.otp && (
                    <p className="text-sm text-red-500 text-center">{otpForm.formState.errors.otp.message}</p>
                  )}

                  <Button type="submit" variant="neon" className="w-full" disabled={loading || otpDigits.some(d => d === '')}>
                    {loading ? (
                      <>
                        <Loader2 className="h-4 w-4 animate-spin" />
                        Verifying...
                      </>
                    ) : (
                      <>
                        <ShieldCheck className="h-4 w-4" />
                        Verify Email
                      </>
                    )}
                  </Button>
                </form>

                {/* Resend & Back */}
                <div className="flex flex-col items-center gap-3">
                  <button
                    type="button"
                    onClick={handleResendOtp}
                    disabled={resendCooldown > 0 || loading}
                    className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-accent transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <RotateCcw className={`h-3.5 w-3.5 ${resendCooldown > 0 ? '' : 'group-hover:animate-spin'}`} />
                    {resendCooldown > 0
                      ? `Resend code in ${resendCooldown}s`
                      : "Didn't receive a code? Resend"}
                  </button>

                  <button
                    type="button"
                    onClick={() => switchMode('login')}
                    className="text-sm text-muted-foreground hover:text-foreground transition-colors"
                  >
                    ← Back to Sign In
                  </button>
                </div>
              </div>
            ) : (
              <>
                {/* Login / Signup Tabs */}
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

                {/* GitHub OAuth Button */}
                <button
                  type="button"
                  onClick={() => {
                    const clientId = 'Ov23liI4IUjAZ1jLsx3W';
                    const redirectUri = `${window.location.origin}/auth/callback`;
                    const scope = 'user:email';
                    window.location.href = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=${scope}`;
                  }}
                  className="w-full flex items-center justify-center gap-3 px-4 py-2.5 rounded-lg border border-border/50 bg-card/50 text-foreground hover:bg-card hover:border-accent/50 transition-all duration-300 group"
                >
                  <svg className="h-5 w-5 transition-transform duration-300 group-hover:scale-110" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
                  </svg>
                  <span className="text-sm font-medium">Sign in with GitHub</span>
                </button>

                {/* Divider */}
                <div className="flex items-center gap-3 my-6">
                  <div className="flex-1 h-px bg-border/50" />
                  <span className="text-xs text-muted-foreground uppercase tracking-wider">or continue with email</span>
                  <div className="flex-1 h-px bg-border/50" />
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
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
