import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { authAPI, api } from '@/lib/api';
import { Navbar } from '@/components/Navbar';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ArrowLeft, Loader2, Mail, Lock, User, Save, Shield } from 'lucide-react';
import { toast } from 'sonner';

export default function Profile() {
    const navigate = useNavigate();
    const { user, setUser, token } = useAuthStore();

    const [username, setUsername] = useState(user?.username || '');
    const [email, setEmail] = useState(user?.email || '');
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [saving, setSaving] = useState(false);

    // Sync state when user loads
    useEffect(() => {
        if (user) {
            setUsername(user.username);
            setEmail(user.email);
        }
    }, [user]);

    const handleSaveProfile = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!user) return;

        setSaving(true);
        try {
            const payload: Record<string, string> = {};

            if (username !== user.username) payload.username = username;
            if (email !== user.email) payload.email = email;
            if (newPassword.trim()) payload.password = newPassword;

            if (Object.keys(payload).length === 0) {
                toast.info('No changes to save.');
                setSaving(false);
                return;
            }

            const response = await api.put(`/users/${user.id}`, payload);
            setUser(response.data);
            setNewPassword('');
            setCurrentPassword('');
            toast.success('Profile updated successfully!');
        } catch (error: any) {
            const message = error.response?.data?.message || 'Failed to update profile';
            toast.error(message);
        } finally {
            setSaving(false);
        }
    };

    if (!user) {
        return (
            <div className="min-h-screen gradient-mesh flex items-center justify-center">
                <Loader2 className="h-8 w-8 animate-spin text-accent" />
            </div>
        );
    }

    return (
        <div className="min-h-screen gradient-mesh">
            <Navbar />

            <main className="container mx-auto px-4 pt-24 pb-12 max-w-2xl">
                {/* Header */}
                <div className="flex items-center gap-3 mb-8 animate-fade-in">
                    <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
                        <ArrowLeft className="h-5 w-5" />
                    </Button>
                    <div>
                        <h1 className="text-3xl font-bold text-foreground">Profile Settings</h1>
                        <p className="text-muted-foreground text-sm">Manage your account details</p>
                    </div>
                </div>

                {/* Profile Card */}
                <div className="glass-strong rounded-2xl p-8 neon-border animate-fade-in" style={{ animationDelay: '100ms' }}>
                    {/* Avatar Section */}
                    <div className="flex items-center gap-4 mb-8 pb-6 border-b border-white/10">
                        <div className="h-16 w-16 rounded-full bg-accent/20 flex items-center justify-center ring-2 ring-accent/30">
                            <span className="text-2xl font-bold text-accent">
                                {user.username.charAt(0).toUpperCase()}
                            </span>
                        </div>
                        <div>
                            <h2 className="text-xl font-semibold text-foreground">{user.username}</h2>
                            <p className="text-sm text-muted-foreground">{user.email}</p>
                        </div>
                    </div>

                    <form onSubmit={handleSaveProfile} className="space-y-6">
                        {/* Username */}
                        <div className="space-y-2">
                            <Label htmlFor="profile-username" className="text-foreground flex items-center gap-2">
                                <User className="h-4 w-4 text-muted-foreground" />
                                Username
                            </Label>
                            <Input
                                id="profile-username"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                placeholder="Your username"
                            />
                        </div>

                        {/* Email */}
                        <div className="space-y-2">
                            <Label htmlFor="profile-email" className="text-foreground flex items-center gap-2">
                                <Mail className="h-4 w-4 text-muted-foreground" />
                                Email
                            </Label>
                            <Input
                                id="profile-email"
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                placeholder="your@email.com"
                            />
                        </div>

                        {/* Divider */}
                        <div className="pt-2 pb-2">
                            <div className="flex items-center gap-3">
                                <Shield className="h-4 w-4 text-muted-foreground" />
                                <span className="text-sm font-medium text-muted-foreground">Change Password</span>
                                <div className="flex-1 h-px bg-white/10" />
                            </div>
                            <p className="text-xs text-muted-foreground mt-1 ml-7">Leave blank to keep your current password</p>
                        </div>

                        {/* New Password */}
                        <div className="space-y-2">
                            <Label htmlFor="new-password" className="text-foreground flex items-center gap-2">
                                <Lock className="h-4 w-4 text-muted-foreground" />
                                New Password
                            </Label>
                            <Input
                                id="new-password"
                                type="password"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                placeholder="••••••••"
                            />
                            {newPassword && newPassword.length < 6 && (
                                <p className="text-sm text-red-500">Password must be at least 6 characters</p>
                            )}
                        </div>

                        {/* Save Button */}
                        <div className="pt-4">
                            <Button
                                type="submit"
                                variant="neon"
                                className="w-full"
                                disabled={saving || (newPassword.length > 0 && newPassword.length < 6)}
                            >
                                {saving ? (
                                    <>
                                        <Loader2 className="h-4 w-4 animate-spin mr-2" />
                                        Saving...
                                    </>
                                ) : (
                                    <>
                                        <Save className="h-4 w-4 mr-2" />
                                        Save Changes
                                    </>
                                )}
                            </Button>
                        </div>
                    </form>
                </div>
            </main>
        </div>
    );
}
