import { useEffect, useState } from 'react';
import { useRepoStore } from '@/stores/repoStore';
import { repoAPI } from '@/lib/api';
import { Navbar } from '@/components/Navbar';
import { RepoCard } from '@/components/RepoCard';
import { RepoCardSkeleton } from '@/components/RepoCardSkeleton';
import { AddRepoDialog } from '@/components/AddRepoDialog';
import { GitBranch, Sparkles } from 'lucide-react';
import { toast } from 'sonner';

export default function Dashboard() {
  const { repositories, setRepositories } = useRepoStore();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchRepos = async () => {
      try {
        const response = await repoAPI.getMyRepos();
        setRepositories(response.data);
      } catch (error: any) {
        toast.error('Failed to fetch repositories');
      } finally {
        setLoading(false);
      }
    };

    fetchRepos();
  }, [setRepositories]);

  return (
    <div className="min-h-screen gradient-mesh">
      <Navbar />
      
      <main className="container mx-auto px-4 pt-24 pb-12">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8 animate-fade-in">
          <div>
            <h1 className="text-3xl font-bold text-foreground mb-2">Your Repositories</h1>
            <p className="text-muted-foreground">Manage and generate documentation for your projects</p>
          </div>
          <AddRepoDialog />
        </div>

        {/* Repository Grid */}
        {loading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div
                key={i}
                className="animate-fade-in"
                style={{ animationDelay: `${i * 100}ms` }}
              >
                <RepoCardSkeleton />
              </div>
            ))}
          </div>
        ) : repositories.length === 0 ? (
          <div className="glass rounded-2xl p-12 text-center animate-fade-in">
            <div className="relative inline-flex items-center justify-center mb-6">
              <GitBranch className="h-16 w-16 text-accent" />
              <div className="absolute inset-0 blur-2xl bg-accent/20" />
            </div>
            <h2 className="text-xl font-semibold text-foreground mb-2">No repositories yet</h2>
            <p className="text-muted-foreground mb-6 max-w-md mx-auto">
              Add your first repository to start generating AI-powered documentation.
            </p>
            <div className="flex items-center justify-center gap-2 text-sm text-accent">
              <Sparkles className="h-4 w-4" />
              <span>Click "Add Repository" to get started</span>
            </div>
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {repositories.map((repo, index) => (
              <div
                key={repo.id}
                className="animate-fade-in"
                style={{ animationDelay: `${index * 100}ms` }}
              >
                <RepoCard repo={repo} />
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
