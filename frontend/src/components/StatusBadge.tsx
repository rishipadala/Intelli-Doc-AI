import { cn } from '@/lib/utils';
import { RepoStatus } from '@/stores/repoStore';

interface StatusBadgeProps {
  status: RepoStatus;
  isPolling?: boolean;
}

const statusConfig: Record<RepoStatus, { label: string; color: string }> = {
  QUEUED: { label: 'Queued', color: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30' },
  PROCESSING: { label: 'Processing', color: 'bg-blue-500/20 text-blue-400 border-blue-500/30' },
  ANALYZING_CODE: { label: 'Analyzing', color: 'bg-purple-500/20 text-purple-400 border-purple-500/30' },
  ANALYSIS_COMPLETED: { label: 'Ready', color: 'bg-accent/20 text-accent border-accent/30' },
  GENERATING_README: { label: 'Generating', color: 'bg-orange-500/20 text-orange-400 border-orange-500/30' },
  COMPLETED: { label: 'Completed', color: 'bg-green-500/20 text-green-400 border-green-500/30' },
  FAILED: { label: 'Failed', color: 'bg-red-500/20 text-red-400 border-red-500/30' },
};

export const StatusBadge = ({ status, isPolling }: StatusBadgeProps) => {
  const config = statusConfig[status];

  return (
    <div className={cn(
      'inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-medium border',
      config.color
    )}>
      {isPolling && (
        <span className="relative flex h-2 w-2">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-accent opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-accent" />
        </span>
      )}
      {config.label}
    </div>
  );
};
