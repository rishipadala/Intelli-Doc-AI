import { useState, useMemo } from 'react';
import { cn } from '@/lib/utils';
import { Checkbox } from '@/components/ui/checkbox';
import { ChevronRight, ChevronDown, File, Folder, FolderOpen } from 'lucide-react';

interface FileTreeProps {
  files: string[];
  selectedFiles: Set<string>;
  onSelectionChange: (files: Set<string>) => void;
}

interface TreeNode {
  name: string;
  path: string;
  isFile: boolean;
  children: TreeNode[];
}

function buildTree(files: string[]): TreeNode[] {
  const root: TreeNode[] = [];

  files.forEach((filePath) => {
    const parts = filePath.split('/');
    let currentLevel = root;

    parts.forEach((part, index) => {
      const isFile = index === parts.length - 1;
      const currentPath = parts.slice(0, index + 1).join('/');
      
      let existingNode = currentLevel.find((n) => n.name === part);
      
      if (!existingNode) {
        existingNode = {
          name: part,
          path: currentPath,
          isFile,
          children: [],
        };
        currentLevel.push(existingNode);
      }
      
      currentLevel = existingNode.children;
    });
  });

  return root;
}

interface TreeNodeItemProps {
  node: TreeNode;
  level: number;
  selectedFiles: Set<string>;
  onToggle: (path: string, isFile: boolean) => void;
  expandedFolders: Set<string>;
  toggleFolder: (path: string) => void;
}

const TreeNodeItem = ({ node, level, selectedFiles, onToggle, expandedFolders, toggleFolder }: TreeNodeItemProps) => {
  const isExpanded = expandedFolders.has(node.path);
  const isSelected = selectedFiles.has(node.path);

  const getAllFilePaths = (n: TreeNode): string[] => {
    if (n.isFile) return [n.path];
    return n.children.flatMap(getAllFilePaths);
  };

  const childFiles = getAllFilePaths(node);
  const selectedChildCount = childFiles.filter((f) => selectedFiles.has(f)).length;
  const isPartiallySelected = !node.isFile && selectedChildCount > 0 && selectedChildCount < childFiles.length;

  return (
    <div>
      <div
        className={cn(
          'flex items-center gap-2 py-1.5 px-2 rounded-md cursor-pointer transition-colors',
          'hover:bg-accent/10',
          isSelected && node.isFile && 'bg-accent/20'
        )}
        style={{ paddingLeft: `${level * 16 + 8}px` }}
        onClick={() => {
          if (node.isFile) {
            onToggle(node.path, true);
          } else {
            toggleFolder(node.path);
          }
        }}
      >
        {!node.isFile && (
          <span className="text-muted-foreground">
            {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          </span>
        )}
        {node.isFile ? (
          <File className="h-4 w-4 text-accent/70" />
        ) : isExpanded ? (
          <FolderOpen className="h-4 w-4 text-accent" />
        ) : (
          <Folder className="h-4 w-4 text-accent" />
        )}
        <span className={cn('text-sm flex-1', node.isFile ? 'text-foreground' : 'text-foreground font-medium')}>
          {node.name}
        </span>
        {node.isFile && (
          <Checkbox
            checked={isSelected}
            onCheckedChange={() => onToggle(node.path, true)}
            onClick={(e) => e.stopPropagation()}
            className="border-muted-foreground data-[state=checked]:bg-accent data-[state=checked]:border-accent"
          />
        )}
        {!node.isFile && childFiles.length > 0 && (
          <span className="text-xs text-muted-foreground">
            {selectedChildCount}/{childFiles.length}
          </span>
        )}
      </div>
      {!node.isFile && isExpanded && (
        <div>
          {node.children
            .sort((a, b) => {
              if (a.isFile === b.isFile) return a.name.localeCompare(b.name);
              return a.isFile ? 1 : -1;
            })
            .map((child) => (
              <TreeNodeItem
                key={child.path}
                node={child}
                level={level + 1}
                selectedFiles={selectedFiles}
                onToggle={onToggle}
                expandedFolders={expandedFolders}
                toggleFolder={toggleFolder}
              />
            ))}
        </div>
      )}
    </div>
  );
};

export const FileTree = ({ files, selectedFiles, onSelectionChange }: FileTreeProps) => {
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  
  const tree = useMemo(() => buildTree(files), [files]);

  const toggleFolder = (path: string) => {
    setExpandedFolders((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  };

  const onToggle = (path: string, isFile: boolean) => {
    if (!isFile) return;
    
    const next = new Set(selectedFiles);
    if (next.has(path)) {
      next.delete(path);
    } else {
      next.add(path);
    }
    onSelectionChange(next);
  };

  return (
    <div className="glass rounded-lg p-3 max-h-[400px] overflow-y-auto">
      {tree
        .sort((a, b) => {
          if (a.isFile === b.isFile) return a.name.localeCompare(b.name);
          return a.isFile ? 1 : -1;
        })
        .map((node) => (
          <TreeNodeItem
            key={node.path}
            node={node}
            level={0}
            selectedFiles={selectedFiles}
            onToggle={onToggle}
            expandedFolders={expandedFolders}
            toggleFolder={toggleFolder}
          />
        ))}
    </div>
  );
};
