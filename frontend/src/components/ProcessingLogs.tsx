import { useEffect, useMemo, useRef, type ElementType } from 'react';
import { useProgressLogStore, ProgressLog } from '@/stores/progressLogStore';
import {
    GitBranch,
    Search,
    Brain,
    Cpu,
    Save,
    CheckCircle2,
    AlertCircle,
    Zap,
    Database
} from 'lucide-react';

// Map step identifiers to icons and colors
const stepConfig: Record<string, { icon: ElementType; color: string; glowColor: string }> = {
    INIT: { icon: Zap, color: 'text-cyan-400', glowColor: 'shadow-cyan-500/30' },
    CLONE: { icon: GitBranch, color: 'text-blue-400', glowColor: 'shadow-blue-500/30' },
    SCAN: { icon: Search, color: 'text-violet-400', glowColor: 'shadow-violet-500/30' },
    ARCHITECT: { icon: Brain, color: 'text-purple-400', glowColor: 'shadow-purple-500/30' },
    PROCESS: { icon: Cpu, color: 'text-amber-400', glowColor: 'shadow-amber-500/30' },
    CACHE: { icon: Database, color: 'text-emerald-400', glowColor: 'shadow-emerald-500/30' },
    AI_GENERATE: { icon: Brain, color: 'text-pink-400', glowColor: 'shadow-pink-500/30' },
    SAVE: { icon: Save, color: 'text-green-400', glowColor: 'shadow-green-500/30' },
    COMPLETE: { icon: CheckCircle2, color: 'text-green-400', glowColor: 'shadow-green-500/30' },
    ERROR: { icon: AlertCircle, color: 'text-red-400', glowColor: 'shadow-red-500/30' },
};

const defaultStepConfig = { icon: Zap, color: 'text-gray-400', glowColor: 'shadow-gray-500/30' };

function formatTimestamp(ts: string): string {
    try {
        const date = new Date(ts);
        return date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        });
    } catch {
        return '';
    }
}

interface ProcessingLogsProps {
    repoId: string;
    repoStatus: string;
}

export const ProcessingLogs = ({ repoId, repoStatus }: ProcessingLogsProps) => {
    const rawLogs = useProgressLogStore((s) => s.logs[repoId]);
    const logs = useMemo(() => rawLogs || [], [rawLogs]);
    const scrollRef = useRef<HTMLDivElement>(null);

    // Auto-scroll to bottom when new logs arrive
    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [logs.length]);

    const isActive = ['QUEUED', 'PROCESSING', 'ANALYZING_CODE', 'GENERATING_README'].includes(repoStatus);

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            {/* Terminal Header */}
            <div className="flex items-center gap-3 px-5 py-3 bg-black/40 border-b border-white/5">
                <div className="flex gap-1.5">
                    <div className="w-3 h-3 rounded-full bg-red-500/80" />
                    <div className="w-3 h-3 rounded-full bg-yellow-500/80" />
                    <div className="w-3 h-3 rounded-full bg-green-500/80" />
                </div>
                <span className="text-xs font-mono text-muted-foreground">
                    intellidoc — live processing logs
                </span>
                {isActive && (
                    <span className="ml-auto flex items-center gap-2 text-xs text-cyan-400">
                        <span className="relative flex h-2 w-2">
                            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75" />
                            <span className="relative inline-flex rounded-full h-2 w-2 bg-cyan-400" />
                        </span>
                        LIVE
                    </span>
                )}
            </div>

            {/* Log Entries */}
            <div
                ref={scrollRef}
                className="flex-1 overflow-y-auto p-4 space-y-1 font-mono text-sm custom-scrollbar"
            >
                {logs.length === 0 && isActive && (
                    <div className="flex items-center gap-3 text-muted-foreground animate-pulse py-8 justify-center">
                        <Zap className="h-5 w-5 text-cyan-400" />
                        <span>Waiting for processing to begin...</span>
                    </div>
                )}

                {logs.map((log: ProgressLog, index: number) => {
                    const config = stepConfig[log.step] || defaultStepConfig;
                    const Icon = config.icon;
                    const isLatest = index === logs.length - 1 && isActive;

                    return (
                        <div
                            key={index}
                            className={`
                flex items-start gap-3 px-3 py-2 rounded-lg transition-all duration-300
                animate-log-slide-in
                ${isLatest ? 'bg-white/[0.03]' : ''}
                ${log.step === 'COMPLETE' ? 'bg-green-500/[0.05] border border-green-500/10' : ''}
                ${log.step === 'ERROR' ? 'bg-red-500/[0.05] border border-red-500/10' : ''}
              `}
                            style={{ animationDelay: `${Math.min(index * 50, 200)}ms` }}
                        >
                            {/* Icon */}
                            <div className={`mt-0.5 shrink-0 ${config.color}`}>
                                <Icon className="h-4 w-4" />
                            </div>

                            {/* Message */}
                            <div className="flex-1 min-w-0">
                                <span className={`${log.step === 'COMPLETE' ? 'text-green-400 font-semibold' :
                                    log.step === 'ERROR' ? 'text-red-400 font-semibold' :
                                        'text-gray-300'
                                    }`}>
                                    {log.message}
                                </span>
                            </div>

                            {/* Timestamp */}
                            <span className="text-[10px] text-muted-foreground tabular-nums shrink-0 mt-0.5">
                                {formatTimestamp(log.timestamp)}
                            </span>
                        </div>
                    );
                })}

                {/* Blinking cursor when active */}
                {isActive && logs.length > 0 && (
                    <div className="flex items-center gap-2 px-3 py-2">
                        <span className="inline-block w-2 h-4 bg-cyan-400 animate-terminal-blink" />
                    </div>
                )}
            </div>

            {/* Status bar at the bottom */}
            <div className="px-4 py-2 bg-black/40 border-t border-white/5 flex items-center justify-between">
                <span className="text-[10px] text-muted-foreground font-mono">
                    {logs.length} event{logs.length !== 1 ? 's' : ''} logged
                </span>
                <span className="text-[10px] text-muted-foreground font-mono">
                    {isActive ? 'Processing...' : repoStatus === 'COMPLETED' ? '✓ Done' : repoStatus}
                </span>
            </div>
        </div>
    );
};
