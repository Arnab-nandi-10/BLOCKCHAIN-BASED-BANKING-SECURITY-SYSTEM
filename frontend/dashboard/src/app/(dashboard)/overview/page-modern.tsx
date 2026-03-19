'use client'

import React from 'react'
import {
  ArrowLeftRight,
  ShieldCheck,
  ShieldX,
  AlertTriangle,
  Activity,
  TrendingUp,
  Clock,
} from 'lucide-react'
import { useTransactionStats, useTransactions } from '@/hooks/use-transactions'
import { useAuditSummary } from '@/hooks/use-audit'
import { MetricCard } from '@/components/ui/metric-card'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatCurrency, formatDate, formatNumber } from '@/lib/utils'
import type { Transaction, TransactionStatus } from '@/types'

// ══════════════════════════════════════════════════════════════════════════════
// Modern Dashboard Overview Page
// Clean, professional security intelligence center
// Inspired by Stripe, Vercel, Cloudflare dashboards
// ══════════════════════════════════════════════════════════════════════════════

function statusVariant(status: TransactionStatus): 'success' | 'danger' | 'warning' | 'info' | 'default' {
  switch (status) {
    case 'COMPLETED':
    case 'VERIFIED':
      return 'success'
    case 'BLOCKED':
    case 'FAILED':
      return 'danger'
    case 'FRAUD_HOLD':
      return 'warning'
    case 'SUBMITTED':
    case 'PENDING_FRAUD_CHECK':
      return 'info'
    default:
      return 'default'
  }
}

export default function OverviewPage() {
  const { data: stats, isLoading: statsLoading } = useTransactionStats()
  const { data: recentPage, isLoading: recentLoading } = useTransactions({ size: 6, sort: 'createdAt,desc' })
  const { data: auditSummary, isLoading: auditLoading } = useAuditSummary()

  const recentTxs: Transaction[] = recentPage?.content ?? []

  const totalTransactions =
    (stats?.totalSubmitted ?? 0) + (stats?.totalCompleted ?? 0) + (stats?.totalVerified ?? 0)

  return (
    <div className="space-y-8 animate-slide-up">
      {/* Page Header */}
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
            Overview
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Real-time blockchain banking security metrics
          </p>
        </div>
        
        <div className="flex items-center gap-2">
          {/* Live indicator */}
          <div className="flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-1.5 dark:bg-emerald-950/30">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
            </span>
            <span className="text-xs font-medium text-emerald-700 dark:text-emerald-400">
              Live
            </span>
          </div>
        </div>
      </div>

      {/* Key Metrics Grid */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          title="Total Transactions"
          value={statsLoading ? '—' : formatNumber(totalTransactions)}
          subtitle="All-time submitted"
          icon={<ArrowLeftRight className="h-5 w-5" />}
          color="blue"
          trend={{ value: 12, isPositive: true, label: 'vs last week' }}
          isLoading={statsLoading}
        />
        <MetricCard
          title="Verified"
          value={statsLoading ? '—' : formatNumber(stats?.totalVerified ?? 0)}
          subtitle="Blockchain confirmed"
          icon={<ShieldCheck className="h-5 w-5" />}
          color="emerald"
          trend={{ value: 8, isPositive: true, label: 'vs last week' }}
          isLoading={statsLoading}
        />
        <MetricCard
          title="Blocked"
          value={statsLoading ? '—' : formatNumber(stats?.totalBlocked ?? 0)}
          subtitle="Fraud prevention"
          icon={<ShieldX className="h-5 w-5" />}
          color="rose"
          trend={{ value: 3, isPositive: false, label: 'vs last week' }}
          isLoading={statsLoading}
        />
        <MetricCard
          title="Fraud Hold"
          value={statsLoading ? '—' : formatNumber(stats?.totalFraudHold ?? 0)}
          subtitle="Pending review"
          icon={<AlertTriangle className="h-5 w-5" />}
          color="amber"
          trend={{ value: 5, isPositive: false, label: 'vs last week' }}
          isLoading={statsLoading}
        />
      </div>

      {/* Audit Summary */}
      {!auditLoading && auditSummary && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[
            { label: 'Audit Entries', value: auditSummary.totalEntries, color: 'slate' as const },
            { label: 'On Blockchain', value: auditSummary.committedToBlockchain, color: 'emerald' as const },
            { label: 'Pending', value: auditSummary.pending, color: 'amber' as const },
            { label: 'Failed', value: auditSummary.failed, color: 'rose' as const },
          ].map(({ label, value, color }) => {
            const colorClasses = {
              slate: 'border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900',
              emerald: 'border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950/30',
              amber: 'border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/30',
              rose: 'border-rose-200 bg-rose-50 dark:border-rose-900 dark:bg-rose-950/30',
            }
            return (
              <div key={label} className={`rounded-xl border p-4 text-center ${colorClasses[color]}`}>
                <p className="text-2xl font-semibold text-slate-900 dark:text-slate-100">{formatNumber(value)}</p>
                <p className="mt-1 text-xs text-slate-600 dark:text-slate-400">{label}</p>
              </div>
            )
          })}
        </div>
      )}

      {/* Recent Transactions */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Recent Transactions</CardTitle>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                Latest activity from the platform
              </p>
            </div>
            <Button variant="ghost" size="sm">
              View all
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {recentLoading ? (
            <div className="space-y-3">
              {[...Array(3)].map((_, i) => (
                <div key={i} className="flex items-center gap-4 py-3">
                  <div className="h-10 w-10 animate-pulse rounded-full bg-slate-200 dark:bg-slate-800" />
                  <div className="flex-1 space-y-2">
                    <div className="h-4 w-32 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                    <div className="h-3 w-48 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                  </div>
                  <div className="h-8 w-20 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                </div>
              ))}
            </div>
          ) : recentTxs.length === 0 ? (
            <div className="py-12 text-center">
              <Activity className="mx-auto h-12 w-12 text-slate-300 dark:text-slate-700" />
              <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">
                No transactions yet
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {recentTxs.map((tx) => (
                <div key={tx.transactionId} className="flex items-center gap-4 py-4 first:pt-0 last:pb-0">
                  {/* Icon */}
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-800">
                    <ArrowLeftRight className="h-5 w-5 text-slate-600 dark:text-slate-400" />
                  </div>

                  {/* Details */}
                  <div className="flex-1 overflow-hidden">
                    <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                      {tx.transactionId}
                    </p>
                    <div className="mt-0.5 flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                      <Clock className="h-3 w-3" />
                      {formatDate(tx.createdAt)}
                    </div>
                  </div>

                  {/* Amount */}
                  <div className="text-right">
                    <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                      {formatCurrency(tx.amount)}
                    </p>
                    <Badge variant={statusVariant(tx.status)} size="sm" className="mt-1">
                      {tx.status}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
