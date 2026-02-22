import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { repoAPI } from '@/lib/api';
import {
    Dialog,
    DialogContent,
    DialogTitle,
} from '@/components/ui/dialog';
import { Search, FileText, GitBranch, Loader2, Sparkles, ArrowRight } from 'lucide-react';

interface SearchResult {
    documentationId: string;
    repositoryId: string;
    repositoryName: string;
    filePath: string;
    snippet: string;
    score: number;
}

interface SearchDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
}

export function SearchDialog({ open, onOpenChange }: SearchDialogProps) {
    const navigate = useNavigate();
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<SearchResult[]>([]);
    const [loading, setLoading] = useState(false);
    const [searched, setSearched] = useState(false);
    const [selectedIndex, setSelectedIndex] = useState(0);
    const inputRef = useRef<HTMLInputElement>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const resultsRef = useRef<HTMLDivElement>(null);

    // Reset state when dialog opens
    useEffect(() => {
        if (open) {
            setQuery('');
            setResults([]);
            setSearched(false);
            setSelectedIndex(0);
            setTimeout(() => inputRef.current?.focus(), 100);
        }
    }, [open]);

    // Debounced search
    const performSearch = useCallback(async (searchQuery: string) => {
        if (searchQuery.trim().length < 2) {
            setResults([]);
            setSearched(false);
            return;
        }

        setLoading(true);
        setSearched(true);
        try {
            const response = await repoAPI.searchDocs(searchQuery.trim());
            setResults(response.data || []);
            setSelectedIndex(0);
        } catch (error) {
            console.error('Search failed:', error);
            setResults([]);
        } finally {
            setLoading(false);
        }
    }, []);

    // Handle input change with debounce
    const handleInputChange = (value: string) => {
        setQuery(value);
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => performSearch(value), 300);
    };

    // Navigate to result
    const openResult = (result: SearchResult) => {
        onOpenChange(false);
        const filePath = result.filePath.replace(/\\/g, '/');
        navigate(`/repo/${result.repositoryId}?file=${encodeURIComponent(filePath)}`);
    };

    // Keyboard navigation
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            setSelectedIndex((prev) => Math.min(prev + 1, results.length - 1));
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setSelectedIndex((prev) => Math.max(prev - 1, 0));
        } else if (e.key === 'Enter' && results.length > 0) {
            e.preventDefault();
            openResult(results[selectedIndex]);
        }
    };

    // Auto-scroll selected item into view
    useEffect(() => {
        if (resultsRef.current) {
            const selected = resultsRef.current.children[selectedIndex] as HTMLElement;
            selected?.scrollIntoView({ block: 'nearest' });
        }
    }, [selectedIndex]);

    // Highlight matching text in snippet
    const highlightMatch = (text: string, q: string) => {
        if (!q || q.length < 2) return text;
        const regex = new RegExp(`(${q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
        const parts = text.split(regex);
        return parts.map((part, i) =>
            regex.test(part) ? (
                <mark key={i} className="bg-accent/30 text-accent rounded px-0.5">
                    {part}
                </mark>
            ) : (
                part
            )
        );
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-2xl p-0 gap-0 bg-card/95 backdrop-blur-2xl border-accent/20 shadow-2xl shadow-accent/10 overflow-hidden">
                <DialogTitle className="sr-only">Search Documentation</DialogTitle>

                {/* Search Input */}
                <div className="flex items-center gap-3 px-5 py-4 border-b border-white/10">
                    <Search className="h-5 w-5 text-accent shrink-0" />
                    <input
                        ref={inputRef}
                        value={query}
                        onChange={(e) => handleInputChange(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="Search across all documentation..."
                        className="flex-1 bg-transparent text-foreground text-base placeholder:text-muted-foreground outline-none"
                        autoComplete="off"
                        spellCheck={false}
                    />
                    {loading && <Loader2 className="h-4 w-4 animate-spin text-accent" />}
                    <kbd className="hidden sm:inline-flex items-center gap-1 rounded border border-white/10 bg-white/5 px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground">
                        ESC
                    </kbd>
                </div>

                {/* Results Area */}
                <div className="max-h-[400px] overflow-y-auto custom-scrollbar" ref={resultsRef}>
                    {/* Loading skeleton */}
                    {loading && results.length === 0 && (
                        <div className="p-3 space-y-2">
                            {[1, 2, 3].map((i) => (
                                <div key={i} className="px-4 py-3 rounded-lg bg-white/5 animate-pulse">
                                    <div className="h-4 bg-white/10 rounded w-1/3 mb-2" />
                                    <div className="h-3 bg-white/5 rounded w-2/3" />
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Results */}
                    {!loading && results.length > 0 && (
                        <div className="p-2 space-y-0.5">
                            {results.map((result, index) => (
                                <button
                                    key={`${result.documentationId}-${index}`}
                                    onClick={() => openResult(result)}
                                    onMouseEnter={() => setSelectedIndex(index)}
                                    className={`w-full text-left px-4 py-3 rounded-lg transition-all duration-150 group
                    ${index === selectedIndex
                                            ? 'bg-accent/15 border border-accent/20 shadow-[0_0_15px_rgba(var(--neon-cyan),0.1)]'
                                            : 'hover:bg-white/5 border border-transparent'
                                        }`}
                                >
                                    {/* Result header */}
                                    <div className="flex items-center gap-2 mb-1.5">
                                        <GitBranch className="h-3.5 w-3.5 text-accent/70 shrink-0" />
                                        <span className="text-xs font-semibold text-accent/90 truncate">
                                            {result.repositoryName}
                                        </span>
                                        <ArrowRight className="h-3 w-3 text-muted-foreground/50" />
                                        <div className="flex items-center gap-1.5 min-w-0">
                                            {result.filePath.includes('README') ? (
                                                <Sparkles className="h-3 w-3 text-yellow-400 shrink-0" />
                                            ) : (
                                                <FileText className="h-3 w-3 text-muted-foreground shrink-0" />
                                            )}
                                            <span className="text-sm font-medium text-foreground truncate">
                                                {result.filePath}
                                            </span>
                                        </div>
                                    </div>

                                    {/* Snippet */}
                                    {result.snippet && (
                                        <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed pl-5">
                                            {highlightMatch(result.snippet, query)}
                                        </p>
                                    )}
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Empty state */}
                    {!loading && searched && results.length === 0 && (
                        <div className="py-12 px-6 text-center">
                            <div className="relative inline-flex items-center justify-center mb-4">
                                <Search className="h-10 w-10 text-muted-foreground/30" />
                                <div className="absolute inset-0 blur-xl bg-accent/10" />
                            </div>
                            <p className="text-sm text-muted-foreground">
                                No documentation found for "<span className="text-foreground font-medium">{query}</span>"
                            </p>
                            <p className="text-xs text-muted-foreground/60 mt-1">
                                Try different keywords or shorter search terms
                            </p>
                        </div>
                    )}

                    {/* Initial hint */}
                    {!loading && !searched && (
                        <div className="py-10 px-6 text-center">
                            <div className="relative inline-flex items-center justify-center mb-3">
                                <Sparkles className="h-8 w-8 text-accent/40" />
                                <div className="absolute inset-0 blur-xl bg-accent/10" />
                            </div>
                            <p className="text-sm text-muted-foreground">
                                Search for functions, classes, or patterns across all your docs
                            </p>
                            <p className="text-xs text-muted-foreground/50 mt-1">
                                Type at least 2 characters to start searching
                            </p>
                        </div>
                    )}
                </div>

                {/* Footer with hints */}
                {results.length > 0 && (
                    <div className="flex items-center gap-4 px-5 py-2.5 border-t border-white/10 text-[10px] text-muted-foreground/60">
                        <span className="flex items-center gap-1">
                            <kbd className="rounded border border-white/10 bg-white/5 px-1 py-0.5 font-mono">↑↓</kbd>
                            navigate
                        </span>
                        <span className="flex items-center gap-1">
                            <kbd className="rounded border border-white/10 bg-white/5 px-1 py-0.5 font-mono">↵</kbd>
                            open
                        </span>
                        <span className="flex items-center gap-1">
                            <kbd className="rounded border border-white/10 bg-white/5 px-1 py-0.5 font-mono">esc</kbd>
                            close
                        </span>
                        <span className="ml-auto">{results.length} result{results.length !== 1 ? 's' : ''}</span>
                    </div>
                )}
            </DialogContent>
        </Dialog>
    );
}
