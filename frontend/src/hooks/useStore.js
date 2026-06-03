/**
 * useStore.js — Custom hook for store data
 * ==========================================
 * WHY a custom hook: keeps all data-fetching and WebSocket logic out of
 *   UI components. Components just call useStore(storeId) and get data.
 *
 * POLLING + WEBSOCKET strategy:
 *   - Initial load: fetch all 4 endpoints in parallel.
 *   - WebSocket /topic/events/{storeId}: fires when pipeline ingests new events
 *     → triggers a metrics + funnel refresh.
 *   - WebSocket /topic/anomalies/{storeId}: fires when AnomalyService detects
 *     something → shows a toast notification.
 *   - Fallback polling: every 30 seconds even if WebSocket is down.
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { api } from '../services/api';
import toast from 'react-hot-toast';

const BASE_WS = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export function useStore(storeId) {
  const [metrics,   setMetrics]   = useState(null);
  const [funnel,    setFunnel]    = useState(null);
  const [heatmap,   setHeatmap]   = useState(null);
  const [anomalies, setAnomalies] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState(null);
  const stompRef = useRef(null);

  const refresh = useCallback(async () => {
    if (!storeId) return;
    try {
      const [m, f, h, a] = await Promise.all([
        api.getMetrics(storeId),
        api.getFunnel(storeId),
        api.getHeatmap(storeId),
        api.getAnomalies(storeId),
      ]);
      setMetrics(m); setFunnel(f); setHeatmap(h); setAnomalies(a);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [storeId]);

  // Initial load + 30-second fallback poll
  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 30_000);
    return () => clearInterval(interval);
  }, [refresh]);

  // WebSocket
  useEffect(() => {
    if (!storeId) return;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${BASE_WS}/ws`),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/events/${storeId}`, () => refresh());
        client.subscribe(`/topic/anomalies/${storeId}`, msg => {
          const alerts = JSON.parse(msg.body);
          setAnomalies(alerts);
          alerts.filter(a => a.severity === 'CRITICAL')
                .forEach(a => toast.error(a.description, { duration: 8000 }));
        });
      },
    });
    client.activate();
    stompRef.current = client;
    return () => client.deactivate();
  }, [storeId, refresh]);

  return { metrics, funnel, heatmap, anomalies, loading, error, refresh };
}
