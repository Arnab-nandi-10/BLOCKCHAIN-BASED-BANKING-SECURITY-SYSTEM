'use client'

import React from 'react'
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts'
import {
  ArrowLeftRight,
  ShieldCheck,
  ShieldX,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Activity,
} from 'lucide-react'
import { useTransactionStats, useTransactions } from '@/hooks/use-transactions'
import { useAuditSummary } from '@/hooks/use-audit'
import { StatCard } from '@/components/ui/stat-card'
import { Badge } from '@/components/ui/badge'
import { formatCurrency, formatDate, truncate, formatNumber } from '@/lib/utils'
import type { Transaction, TransactionStatus } from '@/types'

// ─── Mock chart data ──────────────────────────────────────────────────────────

const volumeData = [
  { day: 'Mon', count: 45,  amount: 125_000 },
  { day: 'Tue', count: 62,  amount: 185_000 },
  { day: 'Wed', count: 38,  amount: 92_000  },
  { day: 'Thu', count: 71,  amount: 210_000 },
  { day: 'Fri', count: 89,  amount: 267_000 },
  { day: 'Sat', count: 23,  amount: 45_000  },
  { day: 'Sun', count: 15,  amount: 32_000  },
]

const fraudDistribution = [
  { name: 'LOW',      value: 65, color: '#10b981' },
  { name: 'MEDIUM',   value: 20, color: '#f59e0b' },
  { name: 'HIGH',     value: 10, color: '#f97316' },
  { name: 'CRITICAL', value: 5,  color: '#ef4444' },
]

function statusVariant(status: TransactionStatus): 'success' | 'danger' | 'warning' | 'info' | 'default' {
  switch (status) {
    case 'COMPLETED':
    case 'VERIFIED':             return 'success'
    case 'BLOCKED':
    case 'FAILED':               return 'danger'
    case 'FRAUD_HOLD':           return 'warning'
    case 'SUBMITTED':
    case 'PENDING_FRAUD_CHECK':  return 'info'
    default:                     return 'default'
  }
}

// ─── Chart tooltip ─────────────────────────────────────────────────────────────

const ChartTooltip = ({ active, payload, label }: {
  active?: boolean
  payload?: Array<{ name: string; value: number; color: string }>
  label?: string
}) => {
  if (!active || !payload?.length) return null
  return (
    <div
      className="rounded-xl p-3 text-xs shadow-2xl"
      style={{
        background: 'rgba(13,17,23,0.97)',
        border: '1px solid rgba(255,255,255,0.09)',
        backdropFilter: 'blur(20px)',
      }}
    >
      <p className="mb-2 font-semibold" style={{ color: 'var(--text-primary)' }}>{label}</p>
      {payload.map((p) => (
        <div key={p.name} className="flex items-center gap-2" style={{ color: 'var(--text-secondary)' }}>
          <span className="inline-block h-2 w-2 rounded-full" style={{ background: p.color }} />
          <span>{p.name}:</span>
          <span className="font-semibold" style={{ color: p.color }}>
            {p.name === 'Amount ($)' ? `$${formatNumber(p.value)}` : p.value}
          </span>
        </div>
      ))}
    </div>
  )
}

// ─── Section label ─────────────────────────────────────────────────────────────

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <p className="text-[10px] font-semibold uppercase tracking-[0.12em]" style={{ color: 'var(--text-muted)' }}>
      {children}
    </p>
  )
}

// ─── Page ──────────────────────────────────────────────────────────────────────

export default function OverviewPage() {
  const { data: stats,       isLoading: statsLoading   } = useTransactionStats()
  const { data: recentPage,  isLoading: recentLoading  } = useTransactions({ size: 8, sort: 'createdAt,desc' })
  const { data: auditSummary, isLoading: auditLoading  } = useAuditSummary()

  const recentTxs: Transaction[] = recentPage?.content ?? []

  const totalTransactions =
    (stats?.totalSubmitted ?? 0) + (stats?.totalCompleted ?? 0) + (stats?.totalVerified ?? 0)

  return (
    <div className="animate-fade-in space-y-6">

      {/* ── Page heading ───────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
            System Overview
          </h2>
          <p className="mt-0.5 text-sm" style={{ color: 'var(--text-muted)' }}>
            Real-time blockchain banking security metrics
          </p>
        </div>
        <div className="flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-medium"
          style={{ background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.15)', color: '#34d399' }}>
          <div className="dot-live" />
          Live
        </div>
      </div>

      {/* ── KPI Cards ──────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title="Total Transactions"
          value={statsLoading ? '—' : formatNumber(totalTransactions)}
          subtitle="All-time submitted"
          icon={<ArrowLeftRight size={20} />}
          color="blue"
          trend={{ value: 12, isPositive: true }}
          isLoading={statsLoading}
        />
        <StatCard
          title="Verified"
          value={statsLoading ? '—' : formatNumber(stats?.totalVerified ?? 0)}
          subtitle="Blockchain confirmed"
          icon={<ShieldCheck size={20} />}
          color="green"
          trend={{ value: 8, isPositive: true }}
          isLoading={statsLoading}
        />
        <StatCard
          title="Blocked"
          value={statsLoading ? '—' : formatNumber(stats?.totalBlocked ?? 0)}
          subtitle="Fraud prevention"
          icon={<ShieldX size={20} />}
          color="red"
          trend={{ value: 3, isPositive: false }}
          isLoading={statsLoading}
        />
        <StatCard
          title="Fraud Hold"
          value={statsLoading ? '—' : formatNumber(stats?.totalFraudHold ?? 0)}
          subtitle="Pending review"
          icon={<AlertTriangle size={20} />}
          color="amber"
          trend={{ value: 5, isPositive: false }}
          isLoading={statsLoading}
        />
      </div>

      {/* ── Audit summary strip ────────────────────────────────────────────── */}
      {!auditLoading && auditSummary && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: 'Total Audit Entries',      value: auditSummary.totalEntries           ?? 0, color: '#0F172A'   },
            { label: 'Committed to Blockchain',  value: auditSummary.committedToBlockchain  ?? 0, color: '#16A34A'               },
            { label: 'Pending',                  value: auditSummary.pending                ?? 0, color: '#D97706'               },
            { label: 'Failed',                   value: auditSummary.failed                 ?? 0, color: '#DC2626'               },
          ].map(({ label, value, color }) => (
            <div
              key={label}
              className="rounded-2xl p-4 text-center"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
            >
              <p className="text-2xl font-bold" style={{ color }}>{formatNumber(value)}</p>
              <p className="mt-1 text-[11px]" style={{ color: 'var(--text-muted)' }}>{label}</p>
            </div>
          ))}
        </div>
      )}

      {/* ── Charts ─────────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
        {/* Transaction Volume */}
        <div
          className="xl:col-span-2 rounded-2xl p-5"
          style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
        >
          <div className="mb-5 flex items-center justify-between">
            <div>
              <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Transaction Volume</p>
              <p className="text-[10px] font-semibold uppercase tracking-[0.12em] mt-1" style={{ color: 'var(--text-muted)' }}>Last 7 days</p>
            </div>
            <Activity size={16} style={{ color: 'var(--text-muted)' }} />
          </div>
          {statsLoading ? (
            <div className="skeleton h-[200px] rounded-xl" />
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <AreaChart data={volumeData} margin={{ top: 2, right: 4, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="gradCount" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%"  stopColor="#2563EB" stopOpacity={0.12} />
                    <stop offset="95%" stopColor="#2563EB" stopOpacity={0}    />
                  </linearGradient>
                  <linearGradient id="gradAmount" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%"  stopColor="#0EA5E9" stopOpacity={0.12} />
                    <stop offset="95%"  stopColor="#0EA5E9" stopOpacity={0}   />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" vertical={false} />
                <XAxis dataKey="day" tick={{ fill: '#64748B', fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#64748B', fontSize: 11 }} axisLine={false} tickLine={false} width={32} />
                <Tooltip content={<ChartTooltip />} />
                <Area type="monotone" dataKey="count"  name="Transactions" stroke="#2563EB" strokeWidth={2} fill="url(#gradCount)"  dot={false} activeDot={{ r: 4, fill: '#2563EB', strokeWidth: 0 }} />
                <Area type="monotone" dataKey="amount" name="Amount ($)"   stroke="#0EA5E9" strokeWidth={2} fill="url(#gradAmount)" dot={false} activeDot={{ r: 4, fill: '#0EA5E9', strokeWidth: 0 }} />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Fraud Distribution */}
        <div
          className="rounded-2xl p-5"
          style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
        >
          <div className="mb-5">
            <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Fraud Distribution</p>
            <p className="text-[10px] font-semibold uppercase tracking-[0.12em] mt-1" style={{ color: 'var(--text-muted)' }}>By risk level</p>
          </div>
          {statsLoading ? (
            <div className="skeleton h-[200px] rounded-xl" />
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie data={fraudDistribution} cx="50%" cy="44%" innerRadius={52} outerRadius={76} paddingAngle={3} dataKey="value">
                  {fraudDistribution.map((entry) => (
                    <Cell key={entry.name} fill={entry.color} strokeWidth={0} />
                  ))}
                </Pie>
                <Legend
                  iconType="circle"
                  iconSize={7}
                  formatter={(value) => (
                    <span style={{ color: '#64748B', fontSize: '11px' }}>{value}</span>
                  )}
                />
                <Tooltip
                  formatter={(value) => [`${value}%`, '']}
                  contentStyle={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: '12px', fontSize: '12px' }}
                />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* ── Recent Transactions ────────────────────────────────────────────── */}
      <div
        className="rounded-2xl overflow-hidden"
        style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
      >
          <div className="flex items-center justify-between px-5 py-4" style={{ borderBottom: '1px solid var(--border)' }}>
          <div>
            <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Recent Transactions</p>
            <p className="text-[10px] font-semibold uppercase tracking-[0.12em] mt-1" style={{ color: 'var(--text-muted)' }}>Latest activity</p>
          </div>
          <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>Last 8</span>
        </div>

        <div className="overflow-x-auto">
          <table className="data-table min-w-full">
            <thead>
              <tr>
                {['TX ID', 'From', 'To', 'Amount', 'Status', 'Time'].map((h) => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {recentLoading
                ? Array.from({ length: 6 }).map((_, i) => (
                    <tr key={i}>
                      {Array.from({ length: 6 }).map((__, j) => (
                        <td key={j}>
                          <div className="skeleton h-4 w-full rounded" />
                        </td>
                      ))}
                    </tr>
                  ))
                : recentTxs.length === 0
                ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-sm" style={{ color: 'var(--text-muted)' }}>
                      No transactions found
                    </td>
                  </tr>
                )
                : recentTxs.map((tx) => (
                    <tr key={tx.transactionId}>
                      <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-muted)' }}>{truncate(tx.transactionId, 8, 4)}</span></td>
                      <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-secondary)' }}>{truncate(tx.fromAccount, 6, 4)}</span></td>
                      <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-secondary)' }}>{truncate(tx.toAccount, 6, 4)}</span></td>
                      <td><span className="font-medium text-sm" style={{ color: 'var(--text-primary)' }}>{formatCurrency(tx.amount, tx.currency)}</span></td>
                      <td><Badge variant={statusVariant(tx.status)} size="sm">{tx.status.replace(/_/g, ' ')}</Badge></td>
                      <td><span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{formatDate(tx.createdAt, 'MMM dd HH:mm')}</span></td>
                    </tr>
                  ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* ── Stats summary row ──────────────────────────────────────────────── */}
      {!statsLoading && stats && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          {[
            { icon: <CheckCircle2 size={18} />, value: stats.totalCompleted, label: 'Completed',  color: '#16A34A', iconBg: '#F0FDF4' },
            { icon: <ShieldX size={18} />,       value: stats.totalFailed,   label: 'Failed',     color: '#DC2626', iconBg: '#FEF2F2' },
            { icon: <Clock size={18} />,          value: stats.totalSubmitted, label: 'Submitted', color: '#D97706', iconBg: '#FFFBEB' },
          ].map(({ icon, value, label, color, iconBg }) => (
            <div
              key={label}
              className="flex items-center gap-3.5 rounded-2xl p-4"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
            >
              <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl" style={{ background: iconBg, color }}>
                {icon}
              </div>
              <div>
                <p className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{formatNumber(value)}</p>
                <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{label}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
