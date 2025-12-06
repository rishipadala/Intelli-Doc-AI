import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:8080/ws';

export const useRepoWebSocket = (repoId: string | undefined, onStatusUpdate: (status: string) => void) => {
  const clientRef = useRef<Client | null>(null);
  
  // 🔥 FIX: Store the latest callback in a ref to avoid stale closures
  const onStatusUpdateRef = useRef(onStatusUpdate);

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
            // 🔥 FIX: Always call the LATEST version of the function
            onStatusUpdateRef.current(payload.status);
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
  }, [repoId]);
};