import { useState, useRef, useEffect, useCallback } from 'react';
import { MarkdownPreview } from './MarkdownPreview';
import { Button } from '@/components/ui/button';
import { Eye, Pencil, Columns2 } from 'lucide-react';
import { cn } from '@/lib/utils';

type ViewMode = 'write' | 'preview' | 'split';

interface MarkdownEditorProps {
    initialContent: string;
    onSave: (content: string) => void;
    onCancel: () => void;
    saving?: boolean;
}

export const MarkdownEditor = ({ initialContent, onSave, onCancel, saving }: MarkdownEditorProps) => {
    const [content, setContent] = useState(initialContent);
    const [viewMode, setViewMode] = useState<ViewMode>('split');
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Track if content has been modified
    const hasChanges = content !== initialContent;

    // Focus textarea on mount
    useEffect(() => {
        if (textareaRef.current && viewMode !== 'preview') {
            textareaRef.current.focus();
        }
    }, []);

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        // Ctrl/Cmd + S to save
        if ((e.ctrlKey || e.metaKey) && e.key === 's') {
            e.preventDefault();
            if (hasChanges) onSave(content);
        }
        // Escape to cancel
        if (e.key === 'Escape') {
            e.preventDefault();
            onCancel();
        }
        // Tab to insert spaces instead of switching focus
        if (e.key === 'Tab') {
            e.preventDefault();
            const textarea = textareaRef.current;
            if (!textarea) return;
            const start = textarea.selectionStart;
            const end = textarea.selectionEnd;
            const newContent = content.substring(0, start) + '  ' + content.substring(end);
            setContent(newContent);
            // restore cursor position after state update
            requestAnimationFrame(() => {
                textarea.selectionStart = textarea.selectionEnd = start + 2;
            });
        }
    }, [content, hasChanges, onSave, onCancel]);

    return (
        <div className="flex flex-col h-full">
            {/* Toolbar */}
            <div className="flex items-center justify-between px-4 py-2 border-b border-white/10 bg-black/30 shrink-0">
                <div className="flex items-center gap-1">
                    {/* View mode toggle buttons */}
                    <div className="flex bg-black/40 rounded-lg p-0.5 border border-white/5">
                        <button
                            onClick={() => setViewMode('write')}
                            className={cn(
                                'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all',
                                viewMode === 'write'
                                    ? 'bg-accent/20 text-accent shadow-[0_0_8px_rgba(var(--neon-cyan),0.15)]'
                                    : 'text-gray-400 hover:text-white hover:bg-white/5'
                            )}
                        >
                            <Pencil className="h-3 w-3" />
                            Write
                        </button>
                        <button
                            onClick={() => setViewMode('split')}
                            className={cn(
                                'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all',
                                viewMode === 'split'
                                    ? 'bg-accent/20 text-accent shadow-[0_0_8px_rgba(var(--neon-cyan),0.15)]'
                                    : 'text-gray-400 hover:text-white hover:bg-white/5'
                            )}
                        >
                            <Columns2 className="h-3 w-3" />
                            Split
                        </button>
                        <button
                            onClick={() => setViewMode('preview')}
                            className={cn(
                                'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all',
                                viewMode === 'preview'
                                    ? 'bg-accent/20 text-accent shadow-[0_0_8px_rgba(var(--neon-cyan),0.15)]'
                                    : 'text-gray-400 hover:text-white hover:bg-white/5'
                            )}
                        >
                            <Eye className="h-3 w-3" />
                            Preview
                        </button>
                    </div>

                    {hasChanges && (
                        <span className="ml-3 text-xs text-yellow-400/80 flex items-center gap-1">
                            <span className="w-1.5 h-1.5 rounded-full bg-yellow-400 animate-pulse" />
                            Unsaved changes
                        </span>
                    )}
                </div>

                <div className="flex items-center gap-2">
                    <span className="text-[10px] text-gray-500 mr-2 hidden sm:inline">
                        Ctrl+S to save · Esc to cancel
                    </span>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onCancel}
                        disabled={saving}
                        className="text-gray-400 hover:text-white"
                    >
                        Cancel
                    </Button>
                    <Button
                        variant="neon"
                        size="sm"
                        onClick={() => onSave(content)}
                        disabled={saving || !hasChanges}
                    >
                        {saving ? (
                            <span className="flex items-center gap-2">
                                <span className="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                Saving...
                            </span>
                        ) : (
                            'Save Changes'
                        )}
                    </Button>
                </div>
            </div>

            {/* Editor Area */}
            <div className="flex-1 flex overflow-hidden">
                {/* Write pane */}
                {viewMode !== 'preview' && (
                    <div className={cn(
                        'flex flex-col overflow-hidden',
                        viewMode === 'split' ? 'w-1/2 border-r border-white/10' : 'w-full'
                    )}>
                        <div className="px-3 py-1.5 text-[10px] text-gray-500 uppercase tracking-wider border-b border-white/5 bg-black/20 shrink-0">
                            Markdown
                        </div>
                        <textarea
                            ref={textareaRef}
                            value={content}
                            onChange={(e) => setContent(e.target.value)}
                            onKeyDown={handleKeyDown}
                            spellCheck={false}
                            className={cn(
                                'flex-1 w-full resize-none p-4 bg-transparent text-gray-200',
                                'font-mono text-sm leading-relaxed',
                                'focus:outline-none placeholder:text-gray-600',
                                'custom-scrollbar'
                            )}
                            placeholder="Write your markdown here..."
                        />
                    </div>
                )}

                {/* Preview pane */}
                {viewMode !== 'write' && (
                    <div className={cn(
                        'flex flex-col overflow-hidden',
                        viewMode === 'split' ? 'w-1/2' : 'w-full'
                    )}>
                        <div className="px-3 py-1.5 text-[10px] text-gray-500 uppercase tracking-wider border-b border-white/5 bg-black/20 shrink-0">
                            Preview
                        </div>
                        <div className="flex-1 overflow-y-auto custom-scrollbar p-4">
                            {content.trim() ? (
                                <MarkdownPreview content={content} />
                            ) : (
                                <div className="flex items-center justify-center h-full text-gray-500 text-sm">
                                    Nothing to preview
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};
