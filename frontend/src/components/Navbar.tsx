import { Link } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/ui/button';
import { FileCode, LogOut, User } from 'lucide-react';

export const Navbar = () => {
  const { user, logout, isAuthenticated } = useAuthStore();

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 transition-all duration-300 ease-out animate-fade-in">
      {/* Enhanced glass effect with smoother backdrop */}
      <div className="absolute inset-0 bg-card/70 backdrop-blur-xl border-b border-border/30 shadow-lg shadow-black/5" />
      <div className="absolute inset-0 bg-gradient-to-b from-card/90 via-card/70 to-card/50" />
      {/* Subtle bottom glow */}
      <div className="absolute bottom-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-accent/20 to-transparent" />
      
      <div className="relative container mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          <Link 
            to="/" 
            className="flex items-center gap-2.5 group -ml-1 transition-all duration-300 hover:opacity-90"
          >
            <div className="relative flex-shrink-0 transition-transform duration-300 group-hover:scale-110">
              <FileCode className="h-7 w-7 text-accent transition-all duration-300 group-hover:text-accent/90" />
              <div className="absolute inset-0 blur-lg bg-accent/30 animate-glow-pulse opacity-60 group-hover:opacity-80 transition-opacity duration-300" />
            </div>
            <span className="text-xl font-bold text-gradient tracking-tight transition-all duration-300 group-hover:opacity-90">
              Intelli-Doc AI
            </span>
          </Link>

          <div className="flex items-center gap-3">
            {isAuthenticated ? (
              <>
                <div className="flex items-center gap-2 text-muted-foreground px-3 py-1.5 rounded-lg transition-all duration-300 hover:bg-card/50 hover:text-foreground">
                  <User className="h-4 w-4 flex-shrink-0 transition-transform duration-300" />
                  <span className="text-sm font-medium hidden sm:inline transition-colors duration-300">
                    {user?.username}
                  </span>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={logout}
                  className="text-muted-foreground hover:text-destructive gap-2 transition-all duration-300 hover:bg-destructive/10"
                >
                  <LogOut className="h-4 w-4 transition-transform duration-300 group-hover:rotate-12" />
                  <span className="hidden sm:inline">Logout</span>
                </Button>
              </>
            ) : (
              <Link to="/auth" className="transition-transform duration-300 hover:scale-105 active:scale-95">
                <Button variant="neon" size="sm" className="transition-all duration-300">
                  Get Started
                </Button>
              </Link>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
};
