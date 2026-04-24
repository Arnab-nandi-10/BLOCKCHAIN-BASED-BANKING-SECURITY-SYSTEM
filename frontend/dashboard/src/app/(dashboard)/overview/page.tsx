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
  Server,
  Wifi,
  WifiOff,
} from 'lucide-react'
import { useTransactions, useTransactionStats } from '@/hooks/use-transactions'
import { useAuditSummary } from '@/hooks/use-audit'
import { useBlockchainServiceHealth } from '@/hooks/use-blockchain'
import { useFraudHealth } from '@/hooks/use-fraud'
import { StatCard } from '@/components/ui/stat-card'
import { Badge } from '@/components/ui/badge'
import { formatCurrency, formatDate, truncate, formatAccountNumber, formatNumber } from '@/lib/utils'
import type { RiskLevel, Transaction, TransactionStatus } from '@/types'

const riskLevels: RiskLevel[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

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

function healthColor(status?: string) {
  const normalized = (status ?? '').toUpperCase()
  if (normalized === 'UP' || normalized === 'OK' || normalized === 'HEALTHY') {
    return {
      text: '#16A34A',
      bg: 'rgba(16,185,129,0.1)',
      border: 'rgba(16,185,129,0.2)',
    }
  }

  if (normalized === 'DEGRADED' || normalized === 'WARNING' || normalized === 'WARN') {
    return {
      text: '#D97706',
      bg: 'rgba(245,158,11,0.1)',
      border: 'rgba(245,158,11,0.2)',
    }
  }

  return {
    text: '#DC2626',
    bg: 'rgba(239,68,68,0.1)',
    border: 'rgba(239,68,68,0.2)',
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

export default function OverviewPage() {
  const statsRange = React.useMemo(() => {
    const end = new Date()
    const start = new Date()
    start.setDate(end.getDate() - 6)
    return {
      fromDate: start.toISOString().slice(0, 10),
      toDate: end.toISOString().slice(0, 10),
    }
  }, [])

  const { data: transactionStats, isLoading: statsLoading } = useTransactionStats(statsRange)
  const { data: recentTransactionsPage, isLoading: recentTransactionsLoading } = useTransactions({
    size: 8,
    sort: 'createdAt,desc',
  })
  const { data: auditSummary, isLoading: auditLoading } = useAuditSummary()
  const { data: blockchainServiceHealth, isLoading: blockchainServiceLoading } = useBlockchainServiceHealth()
  const { data: fraudServiceHealth, isLoading: fraudServiceLoading } = useFraudHealth()

  const recentTxs: Transaction[] = recentTransactionsPage?.content ?? []
  const totalTransactions = transactionStats?.totalTransactions ?? 0
  const hasTransactions = totalTransactions > 0

  const transactionCounts = {
    verified: transactionStats?.totalVerified ?? 0,
    blocked: transactionStats?.totalBlocked ?? 0,
    fraudHold: transactionStats?.totalFraudHold ?? 0,
    completed: transactionStats?.totalCompleted ?? 0,
    failed: transactionStats?.totalFailed ?? 0,
    submitted: transactionStats?.totalSubmitted ?? 0,
  }

  const volumeData = (transactionStats?.dailyVolume ?? []).map((point) => ({
    day: point.label,
    count: point.transactionCount,
    amount: point.totalAmount,
  }))

  const totalRiskCount = riskLevels.reduce((sum, level) => (
    sum + Number(transactionStats?.fraudRiskDistribution?.[level] ?? 0)
  ), 0)

  // Only compute pie slices when there is real data; avoids showing equal-25%
  // placeholder wedges when all counts are zero.
  const hasFraudDistribution = totalRiskCount > 0
  const fraudDistribution = riskLevels.map((level, index) => ({
    name: level,
    value: hasFraudDistribution
      ? Number((((transactionStats?.fraudRiskDistribution?.[level] ?? 0) / totalRiskCount) * 100).toFixed(1))
      : 0,
    raw: Number(transactionStats?.fraudRiskDistribution?.[level] ?? 0),
    color: ['#10b981', '#f59e0b', '#f97316', '#ef4444'][index],
  }))

  const blockchainTone = healthColor(blockchainServiceHealth?.status)
  const fraudTone = healthColor(fraudServiceHealth?.status)

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
          isLoading={statsLoading}
        />
        <StatCard
          title="Verified"
          value={statsLoading ? '—' : formatNumber(transactionCounts.verified)}
          subtitle="Blockchain confirmed"
          icon={<ShieldCheck size={20} />}
          color="green"
          isLoading={statsLoading}
        />
        <StatCard
          title="Blocked"
          value={statsLoading ? '—' : formatNumber(transactionCounts.blocked)}
          subtitle="Fraud prevention"
          icon={<ShieldX size={20} />}
          color="red"
          isLoading={statsLoading}
        />
        <StatCard
          title="Fraud Hold"
          value={statsLoading ? '—' : formatNumber(transactionCounts.fraudHold)}
          subtitle="Pending review"
          icon={<AlertTriangle size={20} />}
          color="amber"
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

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div
          className="rounded-2xl p-4"
          style={{ background: 'var(--bg-surface)', border: `1px solid ${blockchainTone.border}` }}
        >
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Server size={15} style={{ color: blockchainTone.text }} />
              <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                Blockchain Service Health
              </p>
            </div>
            <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold"
              style={{ background: blockchainTone.bg, border: `1px solid ${blockchainTone.border}`, color: blockchainTone.text }}>
              {(blockchainServiceHealth?.status ?? '').toUpperCase() === 'UP' ? <Wifi size={9} /> : <WifiOff size={9} />}
              {blockchainServiceLoading ? 'Checking…' : (blockchainServiceHealth?.status ?? 'DOWN')}
            </span>
          </div>
          <div className="space-y-1 text-xs" style={{ color: 'var(--text-muted)' }}>
            <p>Mode: <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>{blockchainServiceHealth?.mode?.replace(/_/g, ' ') ?? '—'}</span></p>
            <p>{blockchainServiceHealth?.message ?? 'Waiting for blockchain service health response.'}</p>
          </div>
        </div>

        <div
          className="rounded-2xl p-4"
          style={{ background: 'var(--bg-surface)', border: `1px solid ${fraudTone.border}` }}
        >
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertTriangle size={15} style={{ color: fraudTone.text }} />
              <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                Fraud Service Health
              </p>
            </div>
            <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold"
              style={{ background: fraudTone.bg, border: `1px solid ${fraudTone.border}`, color: fraudTone.text }}>
              {(fraudServiceHealth?.status ?? '').toUpperCase() === 'UP' ? <Wifi size={9} /> : <WifiOff size={9} />}
              {fraudServiceLoading ? 'Checking…' : (fraudServiceHealth?.status ?? 'DOWN')}
            </span>
          </div>
          <div className="space-y-1 text-xs" style={{ color: 'var(--text-muted)' }}>
            <p>Service: <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>{fraudServiceHealth?.service ?? 'fraud-service'}</span></p>
            <p>Version: <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>{fraudServiceHealth?.version ?? '—'}</span></p>
            <p>Mode: <span className="font-mono" style={{ color: 'var(--text-secondary)' }}>{fraudServiceHealth?.mode ?? '—'}</span></p>
          </div>
        </div>
      </div>

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
            hasTransactions ? (
            <ResponsiveContainer width="100%" height={200}>
                <AreaChart data={volumeData} margin={{ top: 2, right: 8, left: -16, bottom: 0 }}>
                  <defs>
                    <linearGradient id="gradCount" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#2563EB" stopOpacity={0.14} />
                      <stop offset="95%" stopColor="#2563EB" stopOpacity={0}    />
                    </linearGradient>
                    <linearGradient id="gradAmount" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#0EA5E9" stopOpacity={0.14} />
                      <stop offset="95%" stopColor="#0EA5E9" stopOpacity={0}   />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                  <XAxis dataKey="day" tick={{ fill: '#64748B', fontSize: 11 }} axisLine={false} tickLine={false} />
                  {/* Left axis — transaction count */}
                  <YAxis yAxisId="left"  tick={{ fill: '#64748B', fontSize: 11 }} axisLine={false} tickLine={false} width={28} />
                  {/* Right axis — amount (abbreviated) */}
                  <YAxis yAxisId="right" orientation="right" tick={{ fill: '#64748B', fontSize: 11 }} axisLine={false} tickLine={false} width={40}
                    tickFormatter={(v: number) => v >= 1_000_000 ? `$${(v/1_000_000).toFixed(1)}M` : v >= 1_000 ? `$${(v/1_000).toFixed(0)}K` : `$${v}`}
                  />
                  <Tooltip content={<ChartTooltip />} />
                  <Area yAxisId="left"  type="monotone" dataKey="count"  name="Transactions" stroke="#2563EB" strokeWidth={2} fill="url(#gradCount)"  dot={false} activeDot={{ r: 4, fill: '#2563EB', strokeWidth: 0 }} />
                  <Area yAxisId="right" type="monotone" dataKey="amount" name="Amount ($)"   stroke="#0EA5E9" strokeWidth={2} fill="url(#gradAmount)" dot={false} activeDot={{ r: 4, fill: '#0EA5E9', strokeWidth: 0 }} />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-[200px] items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-950/40 dark:text-slate-400">
                No transactions found for the selected tenant.
              </div>
            )
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
            hasTransactions && hasFraudDistribution ? (
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
                    formatter={(value, name, props) => [
                      `${value}% (${(props.payload as typeof fraudDistribution[0]).raw} txns)`, ''
                    ]}
                    contentStyle={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: '12px', fontSize: '12px' }}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-[200px] items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-950/40 dark:text-slate-400">
                No fraud data available yet.
              </div>
            )
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
          <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>Last {Math.min(8, totalTransactions)}</span>
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
              {recentTransactionsLoading
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
                     <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-muted)' }} title={tx.fromAccount}>{formatAccountNumber(tx.fromAccount)}</span></td>
                      <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-secondary)' }} title={tx.toAccount}>{formatAccountNumber(tx.toAccount)}</span></td>
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
      {!statsLoading && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          {[
            { icon: <CheckCircle2 size={18} />, value: transactionCounts.completed, label: 'Completed',  color: '#16A34A', iconBg: '#F0FDF4' },
            { icon: <ShieldX size={18} />,       value: transactionCounts.failed,   label: 'Failed',     color: '#DC2626', iconBg: '#FEF2F2' },
            { icon: <Clock size={18} />,          value: transactionCounts.submitted, label: 'Submitted', color: '#D97706', iconBg: '#FFFBEB' },
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
