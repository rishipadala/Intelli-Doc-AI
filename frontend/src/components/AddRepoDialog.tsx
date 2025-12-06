import { useState } from 'react';
import { useRepoStore } from '@/stores/repoStore';
import { repoAPI } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Plus, Loader2, GitBranch } from 'lucide-react';
import { toast } from 'sonner';

export function AddRepoDialog() {
  const [open, setOpen] = useState(false);
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const { addRepository } = useRepoStore();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url) return;

    setLoading(true);
    try {
      const response = await repoAPI.create(url);
      addRepository(response.data);
      toast.success('Repository added successfully!');
      setOpen(false);
      setUrl('');
    } catch (error: any) {
      console.error('Failed to add repo:', error);
      
      // Robust error message extraction
      let errorMsg = 'Failed to add repository';
      
      if (error.response?.data?.message) {
        // 1. Check for specific backend error message (e.g., 409 Duplicate)
        errorMsg = error.response.data.message;
      } else if (typeof error.response?.data === 'string') {
        // 2. Fallback if backend sends a raw string
        errorMsg = error.response.data;
      }
                       
      toast.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="neon" className="gap-2 shadow-lg hover:shadow-accent/20">
          <Plus className="h-4 w-4" />
          Add Repository
        </Button>
      </DialogTrigger>
      <DialogContent className="glass-strong border-white/10 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-xl">
            <GitBranch className="h-5 w-5 text-accent" />
            Add Repository
          </DialogTitle>
          <DialogDescription>
            Enter the URL of a public GitHub repository to start analyzing.
          </DialogDescription>
        </DialogHeader>
        
        <form onSubmit={handleSubmit} className="space-y-4 mt-4">
          <div className="space-y-2">
            <Input
              placeholder="https://github.com/username/repo"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              className="bg-black/20 border-white/10 focus:border-accent"
            />
          </div>
          
          <div className="flex justify-end gap-3">
            <Button 
              type="button" 
              variant="ghost" 
              onClick={() => setOpen(false)}
              disabled={loading}
            >
              Cancel
            </Button>
            <Button 
              type="submit" 
              variant="neon"
              disabled={loading || !url}
            >
              {loading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Analyzing...
                </>
              ) : (
                'Start Analysis'
              )}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}