import { useTheme } from 'next-themes';
import { useEffect, useState } from 'react';
import { Moon, Sun } from 'lucide-react';

export function ThemeToggle() {
    const { theme, setTheme } = useTheme();
    const [mounted, setMounted] = useState(false);

    // Avoid hydration mismatch — only render after mount
    useEffect(() => setMounted(true), []);

    if (!mounted) {
        return (
            <button className="p-2 rounded-lg bg-white/5 border border-transparent" aria-label="Toggle theme">
                <div className="h-4 w-4" />
            </button>
        );
    }

    const isDark = theme === 'dark';

    return (
        <button
            onClick={() => setTheme(isDark ? 'light' : 'dark')}
            className="relative p-2 rounded-lg cursor-pointer transition-all duration-300 hover:bg-card/50 border border-transparent hover:border-accent/20 group"
            title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
            aria-label="Toggle theme"
        >
            {/* Sun icon — visible in dark mode (clicking switches to light) */}
            <Sun
                className={`h-4 w-4 transition-all duration-500 absolute inset-0 m-auto
          ${isDark
                        ? 'rotate-0 scale-100 opacity-100 text-amber-400 group-hover:text-amber-300 group-hover:rotate-45'
                        : 'rotate-90 scale-0 opacity-0'
                    }`}
            />

            {/* Moon icon — visible in light mode (clicking switches to dark) */}
            <Moon
                className={`h-4 w-4 transition-all duration-500
          ${isDark
                        ? '-rotate-90 scale-0 opacity-0'
                        : 'rotate-0 scale-100 opacity-100 text-indigo-500 group-hover:text-indigo-400 group-hover:-rotate-12'
                    }`}
            />
        </button>
    );
}
