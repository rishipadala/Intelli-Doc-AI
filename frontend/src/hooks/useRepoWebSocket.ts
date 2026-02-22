import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useProgressLogStore } from '@/stores/progressLogStore';

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';

export const useRepoWebSocket = (repoId: string | undefined, onStatusUpdate: (status: string) => void) => {
  const clientRef = useRef<Client | null>(null);

  // Store the latest callback in a ref to avoid stale closures
  const onStatusUpdateRef = useRef(onStatusUpdate);
  const addLog = useProgressLogStore((s) => s.addLog);

  // Update the ref whenever the callback changes (e.g. when currentRepo updates)
  useEffect(() => {
    onStatusUpdateRef.current = onStatusUpdate;
  }, [onStatusUpdate]);

  useEffect(() => {
    if (!repoId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      debug: (str) => console.log('STOMP: ' + str),
      reconnectDelay: 5000,
      onConnect: () => {
        console.log(`Connected to WS for Repo: ${repoId}`);

        client.subscribe(`/topic/repo/${repoId}`, (message) => {
          if (message.body) {
            const payload = JSON.parse(message.body);

            if (payload.type === 'PROGRESS_LOG') {
              // Push log event to the progress log store
              addLog(repoId, {
                step: payload.step,
                message: payload.message,
                timestamp: payload.timestamp,
              });
            } else if (payload.type === 'STATUS_UPDATE' || payload.status) {
              // STATUS_UPDATE or legacy format (backward compatible)
              onStatusUpdateRef.current(payload.status);
            }
          }
        });
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, [repoId, addLog]);
};