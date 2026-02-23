import { useNavigate } from 'react-router-dom';
import { Repository } from '@/stores/repoStore';
import { StatusBadge } from './StatusBadge';
import { Card, CardContent } from '@/components/ui/card';
import { GitBranch, ExternalLink, Clock } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

interface RepoCardProps {
  repo: Repository;
}

export const RepoCard = ({ repo }: RepoCardProps) => {
  const navigate = useNavigate();

  // Safe Date Parsing Function
  const getRelativeTime = (dateString?: string | null) => {
    if (!dateString) return 'Just now';
    try {
      // Backend sends UTC time without 'Z' suffix — append it so JS parses as UTC
      const utcDate = dateString.endsWith('Z') ? dateString : dateString + 'Z';
      const date = new Date(utcDate);
      if (isNaN(date.getTime())) return 'Unknown date';
      return formatDistanceToNow(date, { addSuffix: true });
    } catch (error) {
      console.error("Date formatting error:", error);
      return 'Unknown date';
    }
  };

  const repoName = repo.url.split('/').slice(-2).join('/').replace('.git', '');

  // Logic to decide if the badge should show a spinner
  const isActiveState = ['QUEUED', 'PROCESSING', 'ANALYZING_CODE', 'GENERATING_README'].includes(repo.status);

  return (
    <Card
      className="glass group cursor-pointer transition-all duration-300 hover:neon-glow-sm hover:border-accent/50"
      onClick={() => navigate(`/repo/${repo.id}`)}
    >
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-2">
              <GitBranch className="h-4 w-4 text-accent shrink-0" />
              <h3 className="font-semibold text-foreground truncate group-hover:text-accent transition-colors">
                {repoName}
              </h3>
            </div>
            <p className="text-xs text-muted-foreground truncate mb-3">
              {repo.url}
            </p>
            <div className="flex items-center gap-3 text-xs text-muted-foreground">
              <div className="flex items-center gap-1">
                <Clock className="h-3 w-3" />
                {getRelativeTime(repo.lastAnalyzedAt)}
              </div>
            </div>
          </div>
          <div className="flex flex-col items-end gap-2">
            <StatusBadge status={repo.status} isPolling={isActiveState} />
            <ExternalLink className="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
};