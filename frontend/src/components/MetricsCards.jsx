/**
 * MetricsCards.jsx — KPI summary cards
 * ======================================
 * WHY 4 top-level KPIs: Unique Visitors, Conversion Rate, Avg Dwell, Queue Depth
 *   are the most actionable at a glance. Everything else is drill-down.
 * Colour coding: green (good), amber (warning), red (critical) — no ambiguity.
 */

import React from 'react';

function Card({ title, value, sub, color }) {
  const colors = { green:'#16a34a', amber:'#d97706', red:'#dc2626', blue:'#2563eb' };
  return (
    <div style={{ background:'#fff', borderRadius:8, padding:'16px 20px',
                  boxShadow:'0 1px 4px rgba(0,0,0,0.1)', flex:1, minWidth:160 }}>
      <div style={{ fontSize:12, color:'#6b7280', marginBottom:4 }}>{title}</div>
      <div style={{ fontSize:28, fontWeight:700, color: colors[color] || '#111' }}>{value}</div>
      {sub && <div style={{ fontSize:11, color:'#9ca3af', marginTop:4 }}>{sub}</div>}
    </div>
  );
}

export function MetricsCards({ metrics }) {
  if (!metrics) return <div style={{ color:'#9ca3af' }}>Loading metrics…</div>;

  const { uniqueVisitors, conversionRate, avgDwellSeconds, queueDepth, abandonmentRate } = metrics;
  const convPct   = (conversionRate * 100).toFixed(1);
  const dwellMin  = Math.round(avgDwellSeconds / 60);
  const abPct     = (abandonmentRate * 100).toFixed(0);

  return (
    <div style={{ display:'flex', gap:16, flexWrap:'wrap', marginBottom:24 }}>
      <Card title="Unique Visitors (Today)"  value={uniqueVisitors}       color="blue" />
      <Card title="Conversion Rate"          value={`${convPct}%`}        color={conversionRate > 0.1 ? 'green' : 'amber'}
            sub={`${abPct}% queue abandonment`} />
      <Card title="Avg Dwell Time"           value={`${dwellMin} min`}    color="blue" />
      <Card title="Billing Queue Depth"      value={queueDepth}           color={queueDepth >= 15 ? 'red' : queueDepth >= 8 ? 'amber' : 'green'}
            sub={queueDepth >= 8 ? '⚠ High queue' : 'Normal'} />
    </div>
  );
}
