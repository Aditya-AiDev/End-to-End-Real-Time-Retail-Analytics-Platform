/**
 * FunnelChart.jsx — Customer Journey Funnel
 * ===========================================
 * TECHNIQUE: Custom SVG funnel using Recharts BarChart (horizontal)
 * WHY Recharts: zero-config, React-native, no d3 required. BarChart with
 *   horizontal layout visually reads as a funnel with drop-off annotations.
 *
 * Stages: Entry → Zone Visit → Billing Queue → Purchase
 * Drop-off % displayed on each bar — immediately actionable.
 */

import React from 'react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, Cell, ResponsiveContainer, LabelList } from 'recharts';

const COLORS = ['#6366f1','#8b5cf6','#a78bfa','#c4b5fd'];

export function FunnelChart({ funnel }) {
  if (!funnel) return <div style={{ color:'#9ca3af' }}>Loading funnel…</div>;

  const data = [
    { stage:'Entry',         count: funnel.entryCount,         drop: null },
    { stage:'Zone Visit',    count: funnel.zoneVisitCount,     drop: funnel.entryToZoneDropPct },
    { stage:'Billing Queue', count: funnel.billingQueueCount,  drop: funnel.zoneToBillingDropPct },
    { stage:'Purchase',      count: funnel.purchaseCount,      drop: funnel.billingToPurchaseDropPct },
  ];

  return (
    <div style={{ background:'#fff', borderRadius:8, padding:20, boxShadow:'0 1px 4px rgba(0,0,0,0.1)' }}>
      <div style={{ fontSize:14, fontWeight:600, marginBottom:12, color:'#374151' }}>
        Customer Funnel
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={data} layout="vertical" margin={{ left:10, right:60 }}>
          <XAxis type="number" hide />
          <YAxis type="category" dataKey="stage" width={100} tick={{ fontSize:12 }} />
          <Tooltip formatter={(v) => [v, 'Visitors']} />
          <Bar dataKey="count" radius={4}>
            {data.map((_, i) => <Cell key={i} fill={COLORS[i]} />)}
            <LabelList dataKey="count" position="right" style={{ fontSize:12, fill:'#374151' }} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <div style={{ display:'flex', gap:8, flexWrap:'wrap', marginTop:8 }}>
        {data.filter(d => d.drop != null).map(d => (
          <div key={d.stage} style={{ fontSize:11, color:'#ef4444', background:'#fef2f2',
               borderRadius:4, padding:'2px 8px' }}>
            ↓ {d.drop}% drop before {d.stage}
          </div>
        ))}
      </div>
    </div>
  );
}
