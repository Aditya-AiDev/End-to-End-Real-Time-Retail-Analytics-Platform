/**
 * api.js — Axios HTTP client + WebSocket helpers
 * =================================================
 * WHY axios: interceptors let us inject X-Trace-Id on every request,
 *   centralising the tracing requirement without touching each component.
 *
 * WHY separate api.js: all endpoint URLs in one place. Backend URL changes
 *   need editing only here (or set REACT_APP_API_URL env var).
 */

import axios from 'axios';

const BASE = process.env.REACT_APP_API_URL || '';

const http = axios.create({ baseURL: BASE });

// Inject a trace ID on every request
http.interceptors.request.use(cfg => {
  cfg.headers['X-Trace-Id'] = crypto.randomUUID();
  return cfg;
});

export const api = {
  getMetrics:   (storeId) => http.get(`/api/v1/stores/${storeId}/metrics`).then(r => r.data),
  getFunnel:    (storeId) => http.get(`/api/v1/stores/${storeId}/funnel`).then(r => r.data),
  getHeatmap:   (storeId) => http.get(`/api/v1/stores/${storeId}/heatmap`).then(r => r.data),
  getAnomalies: (storeId) => http.get(`/api/v1/stores/${storeId}/anomalies`).then(r => r.data),
  getHealth:    ()         => http.get('/health').then(r => r.data),
};
