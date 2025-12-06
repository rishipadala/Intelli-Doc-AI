import { useEffect, useState } from 'react'; // Removed useRef
import { useParams, useNavigate } from 'react-router-dom';
import { useRepoStore } from '@/stores/repoStore';
import { repoAPI } from '@/lib/api';
import { Navbar } from '@/components/Navbar';
import { StatusBadge } from '@/components/StatusBadge';
import { MarkdownPreview } from '@/components/MarkdownPreview';
import { Button } from '@/components/ui/button';
import { 
  ArrowLeft, 
  GitBranch, 
  Loader2, 
  Sparkles,
  FileText,
  Book,
  Code2,
  Copy,
  Download,
  Trash2 
} from 'lucide-react';
import { toast } from 'sonner';
import { useRepoWebSocket } from '@/hooks/useRepoWebSocket'; // <--- 1. Import Hook

export default function RepoDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { currentRepo, setCurrentRepo, updateRepository } = useRepoStore();
  
  const [loading, setLoading] = useState(true);
  
  // Local state for the IDE view
  const [files, setFiles] = useState<string[]>([]);
  const [activeFile, setActiveFile] = useState<string | null>(null);
  const [activeContent, setActiveContent] = useState<string>('');
  const [docList, setDocList] = useState<any[]>([]); 
  
  const [generating, setGenerating] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // 2. 🔥 WEB SOCKET HOOK
  // This listens for updates and refreshes data instantly
  useRepoWebSocket(id, (newStatus) => {
    if (currentRepo && newStatus !== currentRepo.status) {
      // Update Store & Local State
      updateRepository(id!, { status: newStatus as any });
      setCurrentRepo({ ...currentRepo, status: newStatus as any });

      // Trigger actions based on completion
      if (newStatus === 'ANALYSIS_COMPLETED') {
        toast.success('Analysis Done! Files ready.');
        fetchAllDocs();
      } else if (newStatus === 'COMPLETED') {
        toast.success('Master README Created!');
        fetchAllDocs();
      }
    }
  });

  // 3. Initial Load (Keep this to load data when page first opens)
  useEffect(() => {
    const fetchRepo = async () => {
      if (!id) return;
      try {
        const statusRes = await repoAPI.getStatus(id);
        const reposRes = await repoAPI.getMyRepos();
        const repo = reposRes.data.find((r: any) => r.id === id);
        
        if (repo) {
          const updatedRepo = { ...repo, status: statusRes.data.status };
          setCurrentRepo(updatedRepo);
          
          if (['ANALYSIS_COMPLETED', 'COMPLETED', 'GENERATING_README'].includes(updatedRepo.status)) {
            fetchAllDocs();
          }
        }
      } catch (error) {
        toast.error('Failed to load repo');
        navigate('/dashboard');
      } finally {
        setLoading(false);
      }
    };
    fetchRepo();
    return () => setCurrentRepo(null);
  }, [id]);

  // 4. Fetch Docs Logic (Unchanged)
  const fetchAllDocs = async () => {
    if (!id) return;
    try {
      const docsRes = await repoAPI.getDocumentation(id);
      let allDocs = docsRes.data || [];

      try {
        const readmeRes = await repoAPI.getReadme(id);
        if (readmeRes.data) {
           const readmeContent = readmeRes.data.content || readmeRes.data;
           allDocs = allDocs.filter((d: any) => !d.filePath.includes('README_GENERATED'));
           allDocs.unshift({ filePath: 'README_GENERATED.md', content: readmeContent });
        }
      } catch (e) { }

      const normalizedDocs = allDocs.map((doc: any) => ({
        ...doc,
        filePath: doc.filePath.replace(/\\/g, '/')
      }));

      setDocList(normalizedDocs);
      const filePaths = normalizedDocs.map((d: any) => d.filePath);
      setFiles(filePaths);

      if (!activeFile && normalizedDocs.length > 0) {
        selectFile(normalizedDocs[0].filePath, normalizedDocs);
      } else if (activeFile) {
        // Refresh content if active file was re-fetched
        const currentDoc = normalizedDocs.find((d: any) => d.filePath === activeFile);
        if (currentDoc) setActiveContent(currentDoc.content);
      }

    } catch (error) {
      console.error("Error fetching docs", error);
    }
  };

  const selectFile = (path: string, currentDocs = docList) => {
    setActiveFile(path);
    const doc = currentDocs.find((d: any) => d.filePath === path);
    if (doc) setActiveContent(doc.content);
  };

  // 5. Actions (Unchanged)
  const handleGenerateReadme = async () => {
    if (!id) return;
    setGenerating(true);
    try {
      await repoAPI.generateReadme(id);
      const newStatus = 'GENERATING_README';
      updateRepository(id, { status: newStatus });
      if (currentRepo) setCurrentRepo({ ...currentRepo, status: newStatus });
      toast.info('AI is writing the Master README...');
    } catch (error) {
      toast.error('Failed to start generation');
    } finally {
      setGenerating(false);
    }
  };

  const handleDelete = async () => {
    if (!id || !currentRepo) return;
    
    if (!window.confirm(`Are you sure you want to delete "${currentRepo.name}"? This cannot be undone.`)) {
      return;
    }

    setDeleting(true);
    try {
      await repoAPI.delete(id);
      toast.success('Repository deleted successfully');
      navigate('/dashboard');
    } catch (error) {
      toast.error('Failed to delete repository');
      setDeleting(false);
    }
  };

  const copyContent = () => {
    if (!activeContent) return;
    navigator.clipboard.writeText(activeContent);
    toast.success('Copied to clipboard!');
  };

  const downloadContent = () => {
    if (!activeContent || !activeFile) return;
    const blob = new Blob([activeContent], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = activeFile.split('/').pop() || 'document.md';
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Downloaded file');
  };

  if (loading || !currentRepo) {
    return (
      <div className="min-h-screen gradient-mesh flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  return (
    <div className="min-h-screen gradient-mesh flex flex-col">
      <Navbar />
      
      <main className="flex-1 container mx-auto px-4 pt-24 pb-8 flex flex-col h-[calc(100vh-20px)]">
        
        {/* Header Section */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6 shrink-0">
          <div className="flex items-center gap-3 overflow-hidden">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <GitBranch className="h-5 w-5 text-accent shrink-0" />
                <h1 className="text-xl font-bold truncate">{currentRepo.name}</h1>
              </div>
              <p className="text-xs text-muted-foreground truncate">{currentRepo.url}</p>
            </div>
            {/* Pass isActive status directly to badge */}
            <StatusBadge 
              status={currentRepo.status} 
              isPolling={['QUEUED', 'PROCESSING', 'ANALYZING_CODE', 'GENERATING_README'].includes(currentRepo.status)} 
            />
          </div>

          <div className="flex gap-2 shrink-0">
            {['ANALYSIS_COMPLETED', 'COMPLETED'].includes(currentRepo.status) && (
              <Button 
                variant={currentRepo.status === 'COMPLETED' ? "outline" : "neon"} 
                onClick={handleGenerateReadme} 
                disabled={generating || currentRepo.status === 'GENERATING_README'}
              >
                {generating ? <Loader2 className="animate-spin mr-2" /> : <Sparkles className="mr-2 h-4 w-4" />}
                {currentRepo.status === 'COMPLETED' ? 'Regenerate Readme' : 'Generate Master README'}
              </Button>
            )}

            <Button 
              variant="destructive" 
              size="icon" 
              onClick={handleDelete}
              disabled={deleting}
              title="Delete Repository"
            >
              {deleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
            </Button>
          </div>
        </div>

        {/* --- MAIN IDE LAYOUT --- */}
        <div className="flex-1 flex gap-0 overflow-hidden glass rounded-xl border border-white/10 shadow-2xl">
          
          {/* LEFT SIDEBAR: FILE LIST */}
          <div className="w-72 bg-black/20 border-r border-white/10 flex flex-col">
            <div className="p-4 border-b border-white/10">
              <h3 className="text-xs font-bold text-muted-foreground uppercase tracking-wider flex items-center gap-2">
                <FileText className="h-3 w-3" /> Project Files
              </h3>
            </div>
            
            <div className="flex-1 overflow-y-auto p-2 space-y-1">
              {files.map((file) => {
                const isReadme = file.includes('README_GENERATED');
                const isActive = activeFile === file;
                return (
                  <button
                    key={file}
                    onClick={() => selectFile(file)}
                    className={`w-full text-left px-3 py-2.5 rounded-md text-sm truncate transition-all flex items-center gap-2 ${
                      isActive 
                        ? 'bg-accent/20 text-accent border border-accent/20 shadow-[0_0_10px_rgba(var(--neon-cyan),0.2)]' 
                        : 'text-gray-400 hover:bg-white/5 hover:text-white'
                    }`}
                  >
                    {isReadme ? <Sparkles className="h-4 w-4 text-yellow-400" /> : <Code2 className="h-4 w-4 opacity-70" />}
                    <span className="truncate">{isReadme ? 'MASTER README' : file}</span>
                  </button>
                );
              })}
              {files.length === 0 && (
                <div className="p-8 text-center flex flex-col items-center">
                  <p className="text-xs text-muted-foreground">Analyzing structure...</p>
                </div>
              )}
            </div>
          </div>

          {/* RIGHT SIDE: PREVIEW */}
          <div className="flex-1 flex flex-col bg-[#0d1117] overflow-hidden">
             {activeFile ? (
               <div className="flex-1 overflow-y-auto custom-scrollbar">
                 <div className="max-w-4xl mx-auto p-8">
                   <div className="mb-6 pb-4 border-b border-white/10 flex items-center justify-between">
                     <div className="flex items-center gap-2">
                       {activeFile.includes('README') ? <Book className="h-6 w-6 text-yellow-400" /> : <Code2 className="h-6 w-6 text-accent" />}
                       <h2 className="text-2xl font-bold text-white truncate max-w-md">{activeFile}</h2>
                     </div>
                     
                     <div className="flex gap-2">
                        <Button variant="outline" size="sm" onClick={copyContent}>
                          <Copy className="h-4 w-4 mr-2" /> Copy
                        </Button>
                        <Button variant="outline" size="sm" onClick={downloadContent}>
                          <Download className="h-4 w-4 mr-2" /> Download
                        </Button>
                     </div>
                   </div>
                   <MarkdownPreview content={activeContent} />
                 </div>
               </div>
             ) : (
               <div className="flex-1 flex flex-col items-center justify-center text-muted-foreground p-8 text-center">
                 <div className="w-16 h-16 bg-white/5 rounded-full flex items-center justify-center mb-4">
                   <Sparkles className="h-8 w-8 text-white/20" />
                 </div>
                 <p>Select a file from the sidebar to view its documentation.</p>
               </div>
             )}
          </div>

        </div>
      </main>
    </div>
  );
}