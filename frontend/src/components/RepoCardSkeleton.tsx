import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

export const RepoCardSkeleton = () => {
  return (
    <Card className="glass">
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0 space-y-3">
            {/* Icon and Title */}
            <div className="flex items-center gap-2">
              <Skeleton className="h-4 w-4 rounded shrink-0 bg-muted/50" />
              <Skeleton className="h-5 w-32 bg-muted/50" />
            </div>
            {/* URL */}
            <Skeleton className="h-3 w-full bg-muted/40" />
            {/* Time */}
            <div className="flex items-center gap-1.5">
              <Skeleton className="h-3 w-3 rounded-full bg-muted/40" />
              <Skeleton className="h-3 w-20 bg-muted/40" />
            </div>
          </div>
          {/* Status Badge and Icon */}
          <div className="flex flex-col items-end gap-2">
            <Skeleton className="h-6 w-20 rounded-full bg-muted/50" />
            <Skeleton className="h-4 w-4 rounded bg-muted/40" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

