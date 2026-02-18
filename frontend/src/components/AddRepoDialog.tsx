import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useRepoStore } from '@/stores/repoStore';
import { repoAPI } from '@/lib/api';
import { repoUrlSchema, RepoUrlFormData } from '@/lib/validations';
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
  const [loading, setLoading] = useState(false);
  const { addRepository } = useRepoStore();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<RepoUrlFormData>({
    resolver: zodResolver(repoUrlSchema),
    defaultValues: { url: '' },
  });

  const onSubmit = async (data: RepoUrlFormData) => {
    setLoading(true);
    try {
      const response = await repoAPI.create(data.url);
      addRepository(response.data);
      toast.success('Repository added successfully!');
      setOpen(false);
      reset();
    } catch (error: any) {
      console.error('Failed to add repo:', error);

      // Robust error message extraction
      let errorMsg = 'Failed to add repository';

      if (error.response?.data?.message) {
        errorMsg = error.response.data.message;
      } else if (typeof error.response?.data === 'string') {
        errorMsg = error.response.data;
      }

      toast.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenChange = (isOpen: boolean) => {
    setOpen(isOpen);
    if (!isOpen) reset();
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
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

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 mt-4">
          <div className="space-y-2">
            <Input
              placeholder="https://github.com/username/repo"
              {...register('url')}
              className={`bg-black/20 border-white/10 focus:border-accent ${errors.url ? 'border-red-500 focus:border-red-500' : ''}`}
            />
            {errors.url && (
              <p className="text-sm text-red-500">{errors.url.message}</p>
            )}
          </div>

          <div className="flex justify-end gap-3">
            <Button
              type="button"
              variant="ghost"
              onClick={() => handleOpenChange(false)}
              disabled={loading}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="neon"
              disabled={loading}
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