/**
 * Dashboard.jsx — Main store intelligence dashboard
 * ===================================================
 * Layout: header → store selector → KPI cards → 3-column grid (funnel | heatmap | anomalies)
 *
 * WHY store selector: supports multi-store. Switching storeId re-fetches
 *   all data via the useStore hook.
 *
 * The dashboard auto-refreshes via WebSocket events (no manual refresh needed).
 * A "Last updated" timestamp shows data freshness.
 */

import React, { useState } from 'react';
import { useStore } from '../hooks/useStore';
import { MetricsCards } from '../components/MetricsCards';
import { FunnelChart } from '../components/FunnelChart';
import { HeatmapView } from '../components/HeatmapView';
import { AnomalyPanel } from '../components/AnomalyPanel';
import { Toaster } from 'react-hot-toast';

const STORES = ['ST1076', 'STORE_BLR_002'];

export function Dashboard() {
  const [storeId, setStoreId] = useState(STORES[0]);
  const { metrics, funnel, heatmap, anomalies, loading, error, refresh } = useStore(storeId);

  return (
    <div style={{ minHeight:'100vh', background:'#f9fafb', fontFamily:'system-ui,sans-serif' }}>
      <Toaster position="top-right" />

      {/* Header */}
      <div style={{ background:'#6d28d9', color:'#fff', padding:'12px 24px',
                    display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <div style={{ fontSize:18, fontWeight:700 }}>💄 Purplle Store Intelligence</div>
        <div style={{ display:'flex', alignItems:'center', gap:12 }}>
          <select value={storeId} onChange={e => setStoreId(e.target.value)}
            style={{ padding:'4px 8px', borderRadius:4, fontSize:13, border:'none',
                     background:'rgba(255,255,255,0.2)', color:'#fff' }}>
            {STORES.map(s => <option key={s} value={s} style={{ color:'#000' }}>{s}</option>)}
          </select>
          <button onClick={refresh}
            style={{ padding:'4px 12px', borderRadius:4, fontSize:12, border:'none',
                     background:'rgba(255,255,255,0.2)', color:'#fff', cursor:'pointer' }}>
            ↻ Refresh
          </button>
        </div>
      </div>

      <div style={{ padding:24 }}>
        {/* Status bar */}
        <div style={{ marginBottom:16, display:'flex', alignItems:'center', gap:12 }}>
          <span style={{ fontSize:13, color:'#6b7280' }}>
            Store: <strong>{storeId}</strong>
          </span>
          {error && <span style={{ color:'#ef4444', fontSize:12 }}>⚠ {error}</span>}
          {loading && <span style={{ color:'#6b7280', fontSize:12 }}>Loading…</span>}
          {anomalies.filter(a => a.severity === 'CRITICAL').length > 0 && (
            <span style={{ background:'#fef2f2', color:'#dc2626', fontSize:12,
                           padding:'2px 10px', borderRadius:4, fontWeight:600 }}>
              🔴 {anomalies.filter(a => a.severity === 'CRITICAL').length} Critical Alert(s)
            </span>
          )}
        </div>

        {/* KPI Cards */}
        <MetricsCards metrics={metrics} />

        {/* Main grid */}
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr 1fr', gap:16 }}>
          <FunnelChart funnel={funnel} />
          <HeatmapView heatmap={heatmap} />
          <AnomalyPanel anomalies={anomalies} />
        </div>

        {/* Zone dwell table */}
        {metrics?.avgDwellByZone && Object.keys(metrics.avgDwellByZone).length > 0 && (
          <div style={{ background:'#fff', borderRadius:8, padding:20,
                        boxShadow:'0 1px 4px rgba(0,0,0,0.1)', marginTop:16 }}>
            <div style={{ fontSize:14, fontWeight:600, color:'#374151', marginBottom:12 }}>
              Avg Dwell by Zone
            </div>
            <table style={{ width:'100%', fontSize:13, borderCollapse:'collapse' }}>
              <thead>
                <tr style={{ borderBottom:'1px solid #e5e7eb' }}>
                  <th style={{ textAlign:'left', padding:'4px 8px', color:'#6b7280' }}>Zone</th>
                  <th style={{ textAlign:'right', padding:'4px 8px', color:'#6b7280' }}>Avg Dwell</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(metrics.avgDwellByZone).map(([zone, ms]) => (
                  <tr key={zone} style={{ borderBottom:'1px solid #f3f4f6' }}>
                    <td style={{ padding:'6px 8px', color:'#374151' }}>{zone}</td>
                    <td style={{ padding:'6px 8px', textAlign:'right', color:'#6366f1', fontWeight:600 }}>
                      {Math.round(ms / 1000)}s
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
