import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useRepoStore, Repository } from '@/stores/repoStore';

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';

/**
 * A single shared WebSocket connection for the Dashboard.
 * Instead of each RepoCard opening its own connection (N connections),
 * this hook opens ONE connection and subscribes to updates for ALL active repos.
 */
export const useDashboardWebSocket = (repositories: Repository[]) => {
  const clientRef = useRef<Client | null>(null);
  const subscribedReposRef = useRef<Set<string>>(new Set());
  const { updateRepository } = useRepoStore();

  useEffect(() => {
    // Get repos that are in an active/processing state
    const activeRepos = repositories.filter((r) =>
      ['QUEUED', 'PROCESSING', 'ANALYZING_CODE', 'GENERATING_README'].includes(r.status)
    );

    // No active repos → no need for a WebSocket connection
    if (activeRepos.length === 0) {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
        subscribedReposRef.current.clear();
      }
      return;
    }

    // Create connection if not already open
    if (!clientRef.current) {
      const client = new Client({
        webSocketFactory: () => new SockJS(WS_URL),
        debug: (str) => console.log('STOMP (Dashboard): ' + str),
        reconnectDelay: 5000,
        onConnect: () => {
          console.log('Dashboard WS connected');
          // Subscribe to all active repos
          activeRepos.forEach((repo) => {
            subscribeToRepo(client, repo.id);
          });
        },
      });

      client.activate();
      clientRef.current = client;
    } else if (clientRef.current.connected) {
      // Connection already open — subscribe to any new active repos
      activeRepos.forEach((repo) => {
        if (!subscribedReposRef.current.has(repo.id)) {
          subscribeToRepo(clientRef.current!, repo.id);
        }
      });
    }

    // Cleanup on unmount
    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
        subscribedReposRef.current.clear();
      }
    };
  }, [repositories]);

  const subscribeToRepo = (client: Client, repoId: string) => {
    if (subscribedReposRef.current.has(repoId)) return;

    client.subscribe(`/topic/repo/${repoId}`, (message) => {
      if (message.body) {
        const payload = JSON.parse(message.body);
        if (payload.type === 'STATUS_UPDATE' || payload.status) {
          updateRepository(repoId, { status: payload.status });
        }
      }
    });

    subscribedReposRef.current.add(repoId);
    console.log(`Subscribed to repo: ${repoId}`);
  };
};
