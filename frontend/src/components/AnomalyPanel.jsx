/**
 * AnomalyPanel.jsx — Active Anomaly Alerts
 * ==========================================
 * Shows CRITICAL anomalies prominently (red) and WARN/INFO below.
 * Each anomaly shows: type badge, description, and suggested action.
 * WHY suggested actions: store staff need immediate guidance, not just an alert.
 */

import React from 'react';

const SEV_STYLE = {
  CRITICAL: { bg:'#fef2f2', border:'#fca5a5', badge:'#dc2626', label:'🔴 CRITICAL' },
  WARN:     { bg:'#fffbeb', border:'#fcd34d', badge:'#d97706', label:'🟡 WARN' },
  INFO:     { bg:'#eff6ff', border:'#93c5fd', badge:'#2563eb', label:'🔵 INFO' },
};

export function AnomalyPanel({ anomalies }) {
  const sorted = [...(anomalies || [])].sort((a, b) => {
    const order = { CRITICAL:0, WARN:1, INFO:2 };
    return (order[a.severity] || 2) - (order[b.severity] || 2);
  });

  return (
    <div style={{ background:'#fff', borderRadius:8, padding:20, boxShadow:'0 1px 4px rgba(0,0,0,0.1)' }}>
      <div style={{ fontSize:14, fontWeight:600, color:'#374151', marginBottom:12 }}>
        Active Anomalies {sorted.length > 0 && <span style={{ color:'#ef4444' }}>({sorted.length})</span>}
      </div>
      {sorted.length === 0 ? (
        <div style={{ color:'#22c55e', fontSize:13 }}>✓ No active anomalies</div>
      ) : (
        sorted.map(a => {
          const s = SEV_STYLE[a.severity] || SEV_STYLE.INFO;
          return (
            <div key={a.anomalyId} style={{ background:s.bg, border:`1px solid ${s.border}`,
                 borderRadius:6, padding:12, marginBottom:8 }}>
              <div style={{ display:'flex', gap:8, alignItems:'center', marginBottom:4 }}>
                <span style={{ fontSize:11, background:s.badge, color:'#fff',
                               padding:'1px 6px', borderRadius:4 }}>{s.label}</span>
                <span style={{ fontSize:11, color:'#6b7280' }}>{a.anomalyType}</span>
              </div>
              <div style={{ fontSize:13, color:'#1f2937', marginBottom:4 }}>{a.description}</div>
              <div style={{ fontSize:12, color:'#6b7280' }}>
                <strong>Action:</strong> {a.suggestedAction}
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}
