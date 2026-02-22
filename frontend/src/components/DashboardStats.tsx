import { GitBranch, CheckCircle2, FileText, Clock } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

interface DashboardStatsProps {
    stats: {
        totalRepos: number;
        analyzedRepos: number;
        totalFilesDocumented: number;
        lastAnalysisAt: string | null;
    } | null;
    loading: boolean;
}

const statCards = [
    {
        key: 'totalRepos',
        label: 'Total Repos',
        icon: GitBranch,
        color: 'text-accent',
        glowColor: 'bg-accent/20',
        borderColor: 'border-accent/20',
    },
    {
        key: 'analyzedRepos',
        label: 'Analyzed',
        icon: CheckCircle2,
        color: 'text-emerald-400',
        glowColor: 'bg-emerald-400/20',
        borderColor: 'border-emerald-400/20',
    },
    {
        key: 'totalFilesDocumented',
        label: 'Files Documented',
        icon: FileText,
        color: 'text-primary',
        glowColor: 'bg-primary/20',
        borderColor: 'border-primary/20',
    },
    {
        key: 'lastAnalysisAt',
        label: 'Last Analysis',
        icon: Clock,
        color: 'text-amber-400',
        glowColor: 'bg-amber-400/20',
        borderColor: 'border-amber-400/20',
    },
] as const;

export function DashboardStats({ stats, loading }: DashboardStatsProps) {
    const getDisplayValue = (key: string): string => {
        if (!stats) return '—';
        if (key === 'lastAnalysisAt') {
            if (!stats.lastAnalysisAt) return 'Never';
            try {
                return formatDistanceToNow(new Date(stats.lastAnalysisAt), { addSuffix: true });
            } catch {
                return 'Unknown';
            }
        }
        const val = stats[key as keyof typeof stats];
        if (typeof val === 'number') return val.toLocaleString();
        return String(val ?? '—');
    };

    return (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-8">
            {statCards.map((card, index) => {
                const Icon = card.icon;
                return (
                    <div
                        key={card.key}
                        className={`glass rounded-xl p-4 relative overflow-hidden transition-all duration-300 hover:border-opacity-60 animate-fade-in ${card.borderColor}`}
                        style={{ animationDelay: `${index * 80}ms` }}
                    >
                        {/* Subtle background glow */}
                        <div className={`absolute -top-4 -right-4 w-20 h-20 ${card.glowColor} rounded-full blur-2xl opacity-40`} />

                        <div className="relative">
                            <div className="flex items-center gap-2 mb-2">
                                <div className={`p-1.5 rounded-md bg-white/5 ${card.color}`}>
                                    <Icon className="h-3.5 w-3.5" />
                                </div>
                                <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                                    {card.label}
                                </span>
                            </div>

                            {loading ? (
                                <div className="h-8 w-16 bg-white/5 rounded animate-pulse mt-1" />
                            ) : (
                                <p className={`text-2xl font-bold text-foreground tracking-tight ${card.key === 'lastAnalysisAt' ? 'text-base mt-1' : ''
                                    }`}>
                                    {getDisplayValue(card.key)}
                                </p>
                            )}
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
