/**
 * HeatmapView.jsx — Zone Traffic Heatmap
 * ========================================
 * TECHNIQUE: CSS colour interpolation from green → amber → red based on
 *   normalized score (0–100) from the API. Each zone is a coloured block.
 *
 * WHY not a spatial image overlay: We don't have the store floor plan image.
 *   This bar-based heatmap conveys the same information (which zones are hot)
 *   without requiring a floor plan. An image overlay can be added later.
 *
 * dataConfidence flag: if < 20 sessions, a "Low data" badge is shown so
 *   store managers don't act on statistically insignificant data.
 */

import React from 'react';

function score2color(score) {
  if (score >= 70) return '#ef4444';  // red   = hot
  if (score >= 40) return '#f59e0b';  // amber = medium
  return '#22c55e';                    // green = cool
}

export function HeatmapView({ heatmap }) {
  if (!heatmap) return <div style={{ color:'#9ca3af' }}>Loading heatmap…</div>;

  const sorted = [...(heatmap.zones || [])].sort((a, b) => b.normalizedScore - a.normalizedScore);

  return (
    <div style={{ background:'#fff', borderRadius:8, padding:20, boxShadow:'0 1px 4px rgba(0,0,0,0.1)' }}>
      <div style={{ display:'flex', justifyContent:'space-between', marginBottom:12 }}>
        <span style={{ fontSize:14, fontWeight:600, color:'#374151' }}>Zone Heatmap</span>
        {!heatmap.dataConfidence &&
          <span style={{ fontSize:11, background:'#fef3c7', color:'#92400e',
                         padding:'2px 8px', borderRadius:4 }}>Low data</span>}
      </div>

      {sorted.length === 0 ? (
        <div style={{ color:'#9ca3af', fontSize:13 }}>No zone data yet</div>
      ) : (
        sorted.map(z => (
          <div key={z.zoneId} style={{ marginBottom:10 }}>
            <div style={{ display:'flex', justifyContent:'space-between', fontSize:12, marginBottom:3 }}>
              <span style={{ color:'#374151' }}>{z.zoneName}</span>
              <span style={{ color:'#6b7280' }}>{z.visitCount} visits · {Math.round(z.avgDwellMs/1000)}s avg</span>
            </div>
            <div style={{ height:16, borderRadius:4, background:'#f3f4f6', overflow:'hidden' }}>
              <div style={{ width:`${z.normalizedScore}%`, height:'100%',
                            background: score2color(z.normalizedScore),
                            transition:'width 0.6s ease' }} />
            </div>
          </div>
        ))
      )}

      <div style={{ display:'flex', gap:12, marginTop:12, fontSize:11, color:'#6b7280' }}>
        <span>🟢 Cool (&lt;40)</span>
        <span>🟡 Medium (40–70)</span>
        <span>🔴 Hot (&gt;70)</span>
      </div>
    </div>
  );
}
