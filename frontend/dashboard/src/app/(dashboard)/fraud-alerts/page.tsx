'use client'

import React, { useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import * as Select from '@radix-ui/react-select'
import {
  ShieldAlert, AlertTriangle, Flame, AlertCircle,
  Search, X, ChevronDown, Check, ChevronLeft, ChevronRight, Download, Loader2,
  Activity, Link2, Server, Wifi, WifiOff, RefreshCw,
} from 'lucide-react'
import { api } from '@/lib/api-client'
import { Badge } from '@/components/ui/badge'
import { LoadingSpinner } from '@/components/ui/loading-spinner'
import { formatDate, truncate, formatNumber, downloadCsv } from '@/lib/utils'
import { useAuthStore } from '@/store/auth-store'
import { useBlockchainServiceHealth, useFabricHealth } from '@/hooks/use-blockchain'
import { useFraudAlerts, useFraudAlertSummary, useFraudHealth } from '@/hooks/use-fraud'
import type { FraudAlert, RiskLevel, FraudAlertParams } from '@/types'

type BadgeVariant = 'success' | 'danger' | 'warning' | 'info' | 'default'

function riskVariant(level: RiskLevel): BadgeVariant {
  switch (level) {
    case 'LOW':    return 'success'
    case 'MEDIUM': return 'warning'
    default:       return 'danger'
  }
}

function riskBarColor(level: RiskLevel): string {
  switch (level) {
    case 'LOW':      return '#10b981'
    case 'MEDIUM':   return '#f59e0b'
    case 'HIGH':     return '#f97316'
    case 'CRITICAL': return '#ef4444'
    default:         return '#64748b'
  }
}

function scoreToPercent(score: number): number {
  const normalized = score <= 1 ? score * 100 : score
  return Math.max(0, Math.min(100, normalized))
}

function riskIcon(level: RiskLevel, size = 14) {
  switch (level) {
    case 'LOW':      return <AlertCircle size={size} style={{ color: '#34d399' }} />
    case 'MEDIUM':   return <AlertTriangle size={size} style={{ color: '#fbbf24' }} />
    case 'HIGH':     return <ShieldAlert size={size} style={{ color: '#f97316' }} />
    case 'CRITICAL': return <Flame size={size} style={{ color: '#f87171' }} />
    default:         return null
  }
}

const RISK_OPTIONS = [
  { label: 'All Risk Levels', value: 'ALL' },
  { label: 'Low',             value: 'LOW' },
  { label: 'Medium',          value: 'MEDIUM' },
  { label: 'High',            value: 'HIGH' },
  { label: 'Critical',        value: 'CRITICAL' },
]

const DECISION_OPTIONS = [
  { label: 'All Decisions', value: 'ALL' },
  { label: 'Allow', value: 'ALLOW' },
  { label: 'Monitor', value: 'MONITOR' },
  { label: 'Hold', value: 'HOLD' },
  { label: 'Block', value: 'BLOCK' },
]

const panelStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
}

function BlockchainStatusCard() {
  const tenantId = useAuthStore((s) => s.tenantId)
  const {
    data: serviceHealth,
    isFetching: isServiceFetching,
    isError: isServiceError,
    refetch: refetchService,
    dataUpdatedAt: serviceUpdatedAt,
  } = useBlockchainServiceHealth()

  const {
    data: fabricHealth,
    isFetching: isFabricFetching,
    isError: isFabricError,
    refetch: refetchFabric,
    dataUpdatedAt: fabricUpdatedAt,
  } = useFabricHealth()

  const isFetching = isServiceFetching || isFabricFetching
  const isError = isServiceError || isFabricError

  const effectiveColor = (eff?: string) => {
    switch (eff) {
      case 'UP':      return { text: '#34d399', bg: 'rgba(16,185,129,0.1)', border: 'rgba(16,185,129,0.2)' }
      case 'DEGRADED': return { text: '#fbbf24', bg: 'rgba(245,158,11,0.1)', border: 'rgba(245,158,11,0.2)' }
      default:        return { text: '#f87171', bg: 'rgba(239,68,68,0.1)',   border: 'rgba(239,68,68,0.2)'  }
    }
  }

  const clr = effectiveColor(fabricHealth?.effectiveHealth ?? serviceHealth?.status)
  const lastUpdatedAt = Math.max(serviceUpdatedAt ?? 0, fabricUpdatedAt ?? 0)

  const handleRefresh = () => {
    void refetchService()
    void refetchFabric()
  }

  return (
    <section className="rounded-2xl p-4" style={panelStyle}>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl" style={{ background: clr.bg, color: clr.text }}>
            <Link2 size={16} />
          </div>
          <div>
            <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Blockchain visibility</p>
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
              Current tenant: {tenantId ?? '—'} · service + Fabric health from blockchain-service
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={handleRefresh}
          disabled={isFetching}
          className="btn-secondary text-xs font-medium"
        >
          {isFetching ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
          {isFetching ? 'Checking…' : 'Check now'}
        </button>
      </div>

      {isError ? (
        <div className="mb-4 flex items-start gap-2 rounded-xl border border-amber-500/20 bg-amber-50 px-4 py-3 text-sm text-amber-700">
          <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
          <span>Unable to reach one or more blockchain health endpoints right now.</span>
        </div>
      ) : null}

      {serviceHealth || fabricHealth ? (
        <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          {[
            { label: 'Service Status', value: serviceHealth?.status ?? 'DOWN', icon: <Wifi size={13} /> },
            { label: 'Service Mode', value: serviceHealth?.mode?.replace(/_/g, ' ') ?? '—', icon: <Server size={13} /> },
            { label: 'Fabric Channel', value: fabricHealth?.channel ?? '—', icon: <Activity size={13} /> },
            { label: 'Peer Endpoint', value: fabricHealth?.peerEndpoint ?? '—', icon: <Link2 size={13} /> },
          ].map(({ label, value, icon }) => (
            <div key={label} className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
              <div className="mb-1.5 flex items-center gap-1.5" style={{ color: 'var(--text-muted)' }}>
                {icon}
                <p className="text-[9px] font-semibold uppercase tracking-wider">{label}</p>
              </div>
              <p className="truncate font-mono text-xs" style={{ color: 'var(--text-secondary)' }}>{value}</p>
            </div>
          ))}
        </div>
      ) : !isFetching ? (
        <div className="rounded-xl px-4 py-3 text-center text-sm" style={{ color: 'var(--text-muted)', background: 'var(--bg-subtle)' }}>
          Click "Check now" to fetch the live blockchain status.
        </div>
      ) : (
        <div className="skeleton h-20 rounded-xl" />
      )}

      {(serviceHealth || fabricHealth) && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold"
            style={{ background: clr.bg, border: `1px solid ${clr.border}`, color: clr.text }}
          >
            {fabricHealth?.effectiveHealth === 'UP' || serviceHealth?.status?.toUpperCase() === 'UP'
              ? <Wifi size={9} />
              : <WifiOff size={9} />}
            {fabricHealth?.effectiveHealth ?? serviceHealth?.status ?? 'DOWN'}
          </span>
          <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
            {fabricHealth?.message ?? serviceHealth?.message ?? 'Blockchain health checks are running.'}
          </span>
          {lastUpdatedAt > 0 ? (
            <span className="ml-auto text-[10px]" style={{ color: 'var(--text-muted)' }}>
              Updated {new Date(lastUpdatedAt).toLocaleTimeString()}
            </span>
          ) : null}
        </div>
      )}
    </section>
  )
}

function FraudServiceStatusCard() {
  const {
    data: fraudHealth,
    isFetching,
    isError,
    refetch,
    dataUpdatedAt,
  } = useFraudHealth()

  const healthColor = (status?: string) => {
    const normalized = (status ?? '').toUpperCase()
    if (normalized === 'UP' || normalized === 'OK' || normalized === 'HEALTHY') {
      return { text: '#34d399', bg: 'rgba(16,185,129,0.1)', border: 'rgba(16,185,129,0.2)' }
    }
    if (normalized === 'DEGRADED' || normalized === 'WARNING' || normalized === 'WARN') {
      return { text: '#fbbf24', bg: 'rgba(245,158,11,0.1)', border: 'rgba(245,158,11,0.2)' }
    }
    return { text: '#f87171', bg: 'rgba(239,68,68,0.1)', border: 'rgba(239,68,68,0.2)' }
  }

  const clr = healthColor(fraudHealth?.status)

  return (
    <section className="rounded-2xl p-4" style={panelStyle}>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl" style={{ background: clr.bg, color: clr.text }}>
            <ShieldAlert size={16} />
          </div>
          <div>
            <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Fraud service visibility</p>
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
              Live model and service readiness from fraud-service
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void refetch()}
          disabled={isFetching}
          className="btn-secondary text-xs font-medium"
        >
          {isFetching ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
          {isFetching ? 'Checking…' : 'Check now'}
        </button>
      </div>

      {isError ? (
        <div className="mb-4 flex items-start gap-2 rounded-xl border border-amber-500/20 bg-amber-50 px-4 py-3 text-sm text-amber-700">
          <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
          <span>Unable to reach the fraud health endpoint right now.</span>
        </div>
      ) : null}

      {fraudHealth ? (
        <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          {[
            { label: 'Status', value: fraudHealth.status, icon: <Wifi size={13} /> },
            { label: 'Service', value: fraudHealth.service, icon: <Server size={13} /> },
            { label: 'Version', value: fraudHealth.version, icon: <Activity size={13} /> },
            { label: 'Model Loaded', value: fraudHealth.modelLoaded ? 'Yes' : 'No', icon: <ShieldAlert size={13} /> },
          ].map(({ label, value, icon }) => (
            <div key={label} className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
              <div className="mb-1.5 flex items-center gap-1.5" style={{ color: 'var(--text-muted)' }}>
                {icon}
                <p className="text-[9px] font-semibold uppercase tracking-wider">{label}</p>
              </div>
              <p className="truncate font-mono text-xs" style={{ color: 'var(--text-secondary)' }}>{value}</p>
            </div>
          ))}
        </div>
      ) : !isFetching ? (
        <div className="rounded-xl px-4 py-3 text-center text-sm" style={{ color: 'var(--text-muted)', background: 'var(--bg-subtle)' }}>
          Click "Check now" to fetch the live fraud status.
        </div>
      ) : (
        <div className="skeleton h-20 rounded-xl" />
      )}

      {fraudHealth && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold"
            style={{ background: clr.bg, border: `1px solid ${clr.border}`, color: clr.text }}
          >
            {fraudHealth.status.toUpperCase() === 'UP' ? <Wifi size={9} /> : <WifiOff size={9} />}
            {fraudHealth.status}
          </span>
          <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
            Mode: {fraudHealth.mode ?? 'N/A'} · Fallback {fraudHealth.fallbackEnabled ? 'enabled' : 'disabled'}
          </span>
          {dataUpdatedAt ? (
            <span className="ml-auto text-[10px]" style={{ color: 'var(--text-muted)' }}>
              Updated {new Date(dataUpdatedAt).toLocaleTimeString()}
            </span>
          ) : null}
        </div>
      )}
    </section>
  )
}

function FraudDetailDialog({ alert, open, onClose }: {
  alert: FraudAlert | null; open: boolean; onClose: () => void
}) {
  if (!alert) return null
  return (
    <Dialog.Root open={open} onOpenChange={(v) => !v && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/30" />
        <Dialog.Content
          className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg max-h-[85vh] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl shadow-2xl outline-none animate-slide-up"
          style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
        >
          <div className="flex items-start justify-between px-6 py-5" style={{ borderBottom: '1px solid var(--border)' }}>
            <div className="flex items-center gap-2">
              {riskIcon(alert.riskLevel, 16)}
              <div>
                <Dialog.Title className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>Fraud Analysis</Dialog.Title>
                <p className="font-mono text-[11px]" style={{ color: 'var(--text-muted)' }}>{truncate(alert.transactionId, 12, 6)}</p>
              </div>
            </div>
            <Dialog.Close asChild>
              <button className="rounded-lg p-1.5 transition-colors hover:bg-[var(--bg-subtle)]" style={{ color: 'var(--text-muted)' }}>
                <X size={16} />
              </button>
            </Dialog.Close>
          </div>

          <div className="px-6 py-5 space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-xl p-4 text-center" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                <p className="text-3xl font-bold" style={{ color: 'var(--text-primary)' }}>{scoreToPercent(alert.score).toFixed(1)}%</p>
                <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>Fraud Score</p>
                <div className="mt-3 h-1.5 w-full rounded-full" style={{ background: 'var(--border)' }}>
                    <div className="h-1.5 rounded-full" style={{ width: `${scoreToPercent(alert.score)}%`, background: riskBarColor(alert.riskLevel) }} />
                </div>
              </div>
              <div className="flex flex-col items-center justify-center gap-2 rounded-xl p-4" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                {riskIcon(alert.riskLevel, 20)}
                <Badge variant={riskVariant(alert.riskLevel)} size="md">{alert.riskLevel} RISK</Badge>
                <Badge variant={alert.decision === 'BLOCK' ? 'danger' : alert.decision === 'HOLD' ? 'warning' : alert.decision === 'MONITOR' ? 'info' : 'success'} size="sm">
                  {alert.decision}
                </Badge>
              </div>
            </div>

            <div>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Triggered Rules</p>
              <div className="flex flex-wrap gap-1.5">
                {alert.triggeredRules.map((rule) => (
                  <span key={rule} className="rounded-full px-3 py-1 text-[11px] bg-amber-50 border border-amber-200 text-amber-700">
                    {rule}
                  </span>
                ))}
              </div>
            </div>

            <div className="rounded-xl p-4" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
              <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Recommendation</p>
              <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>{alert.recommendation}</p>
            </div>

            {alert.explanations.length > 0 ? (
              <div className="rounded-xl p-4" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Explanations</p>
                <div className="space-y-1.5 text-sm" style={{ color: 'var(--text-secondary)' }}>
                  {alert.explanations.map((explanation) => (
                    <p key={explanation}>{explanation}</p>
                  ))}
                </div>
              </div>
            ) : null}

            <div className="space-y-2">
              {[
                { k: 'Transaction ID', v: truncate(alert.transactionId, 8, 4), mono: true },
                { k: 'Review Required', v: alert.reviewRequired ? 'Yes' : 'No', mono: false },
                { k: 'Detected At',    v: formatDate(alert.detectedAt), mono: false },
              ].map(({ k, v, mono }) => (
                <div key={k} className="flex items-center justify-between rounded-xl px-4 py-2.5" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                  <span className="text-sm" style={{ color: 'var(--text-muted)' }}>{k}</span>
                  <span className={mono ? 'font-mono text-[11px]' : 'text-sm'} style={{ color: 'var(--text-secondary)' }}>{v}</span>
                </div>
              ))}
            </div>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

export default function FraudAlertsPage() {
  const [page,      setPage]      = useState(0)
  const [riskLevel, setRiskLevel] = useState('ALL')
  const [decision,  setDecision]  = useState('ALL')
  const [fromDate,  setFromDate]  = useState('')
  const [toDate,    setToDate]    = useState('')
  const [search,    setSearch]    = useState('')
  const [selected,  setSelected]  = useState<FraudAlert | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  const params: FraudAlertParams = {
    page, size: 15,
    ...(riskLevel !== 'ALL' && { riskLevel }),
    ...(decision !== 'ALL' && { decision }),
    ...(fromDate  && { fromDate }),
    ...(toDate    && { toDate }),
    ...(search    && { search }),
  }

  const { data, isLoading, isFetching } = useFraudAlerts(params)
  const { data: summary } = useFraudAlertSummary(params)

  const alerts        = data?.content    ?? []
  const totalPages    = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  const [isExporting, setIsExporting] = useState(false)

  const handleExportCsv = async () => {
    setIsExporting(true)
    try {
      const all = await api.fraud.getAlerts({ ...params, page: 0, size: 1000 })
      downloadCsv(
        (all.content ?? []).map((a) => ({
          transactionId:  a.transactionId,
          riskLevel:      a.riskLevel,
          score:          a.score,
          recommendation: a.recommendation ?? '',
          triggeredRules: (a.triggeredRules ?? []).join('; '),
          detectedAt:     a.detectedAt,
        })),
        `fraud-alerts-${new Date().toISOString().slice(0, 10)}.csv`
      )
    } finally {
      setIsExporting(false)
    }
  }

  const totalAlerts = summary?.totalAlerts ?? totalElements
  const highAlerts = summary?.riskLevelCounts?.HIGH ?? 0
  const criticalAlerts = summary?.riskLevelCounts?.CRITICAL ?? 0
  const reviewRequiredCount = summary?.reviewRequiredCount ?? 0

  return (
    <div className="animate-fade-in space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Fraud Alerts</h2>
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>{formatNumber(totalAlerts)} total alerts detected</p>
        </div>
        <button
          onClick={handleExportCsv}
          disabled={isExporting || isLoading}
          className="btn-secondary flex items-center gap-2 text-sm disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {isExporting ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
          {isExporting ? 'Exporting…' : 'Export CSV'}
        </button>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <FraudServiceStatusCard />
        <BlockchainStatusCard />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[
          { icon: <ShieldAlert size={18} />,   value: totalAlerts,    label: 'Total Alerts',    iconBg: '#EFF6FF', iconColor: '#2563EB', border: '#BFDBFE'   },
          { icon: <AlertTriangle size={18} />, value: highAlerts,     label: 'HIGH Alerts',     iconBg: '#FFFBEB', iconColor: '#D97706', border: '#FDE68A'   },
          { icon: <Flame size={18} />,          value: criticalAlerts, label: 'CRITICAL Alerts', iconBg: '#FEF2F2', iconColor: '#DC2626', border: '#FECACA'  },
          { icon: <AlertCircle size={18} />,    value: reviewRequiredCount, label: 'Review Required', iconBg: '#F8FAFC', iconColor: '#475569', border: '#CBD5E1'  },
        ].map(({ icon, value, label, iconBg, iconColor, border }) => (
          <div key={label} className="rounded-2xl p-4" style={{ background: 'var(--bg-surface)', border: `1px solid ${border}` }}>
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl flex-shrink-0" style={{ background: iconBg, color: iconColor }}>{icon}</div>
              <div>
                <p className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>{value}</p>
                <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{label}</p>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-3 rounded-2xl p-4" style={panelStyle}>
        <div className="relative min-w-48 flex-1">
          <Search size={13} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2" style={{ color: 'var(--text-muted)' }} />
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0) }}
            placeholder="Search TX ID or recommendation…"
            className="input-base pl-9"
          />
        </div>

        <Select.Root value={riskLevel} onValueChange={(v) => { setRiskLevel(v); setPage(0) }}>
          <Select.Trigger className="flex min-w-44 items-center justify-between gap-2 rounded-xl px-3 py-2.5 text-sm outline-none"
            style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
            <Select.Value placeholder="All Risk Levels" />
            <ChevronDown size={13} style={{ color: 'var(--text-muted)' }} />
          </Select.Trigger>
          <Select.Portal>
            <Select.Content className="z-50 overflow-hidden rounded-2xl shadow-2xl"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}>
              <Select.Viewport className="p-1.5">
                {RISK_OPTIONS.map((opt) => (
                  <Select.Item key={opt.value} value={opt.value}
                    className="flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm outline-none data-[highlighted]:bg-[var(--bg-subtle)]"
                    style={{ color: 'var(--text-secondary)' }}>
                    <Select.ItemIndicator><Check size={11} className="text-blue-600" /></Select.ItemIndicator>
                    <Select.ItemText>{opt.label}</Select.ItemText>
                  </Select.Item>
                ))}
              </Select.Viewport>
            </Select.Content>
          </Select.Portal>
        </Select.Root>

        <Select.Root value={decision} onValueChange={(v) => { setDecision(v); setPage(0) }}>
          <Select.Trigger className="flex min-w-40 items-center justify-between gap-2 rounded-xl px-3 py-2.5 text-sm outline-none"
            style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
            <Select.Value placeholder="All Decisions" />
            <ChevronDown size={13} style={{ color: 'var(--text-muted)' }} />
          </Select.Trigger>
          <Select.Portal>
            <Select.Content className="z-50 overflow-hidden rounded-2xl shadow-2xl"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}>
              <Select.Viewport className="p-1.5">
                {DECISION_OPTIONS.map((opt) => (
                  <Select.Item key={opt.value} value={opt.value}
                    className="flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm outline-none data-[highlighted]:bg-[var(--bg-subtle)]"
                    style={{ color: 'var(--text-secondary)' }}>
                    <Select.ItemIndicator><Check size={11} className="text-blue-600" /></Select.ItemIndicator>
                    <Select.ItemText>{opt.label}</Select.ItemText>
                  </Select.Item>
                ))}
              </Select.Viewport>
            </Select.Content>
          </Select.Portal>
        </Select.Root>

        <input type="date" value={fromDate} onChange={(e) => { setFromDate(e.target.value); setPage(0) }} className="input-base w-auto" />
        <input type="date" value={toDate}   onChange={(e) => { setToDate(e.target.value); setPage(0) }}   className="input-base w-auto" />

        {(riskLevel !== 'ALL' || decision !== 'ALL' || fromDate || toDate || search) && (
          <button onClick={() => { setRiskLevel('ALL'); setDecision('ALL'); setFromDate(''); setToDate(''); setSearch(''); setPage(0) }}
            className="flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm transition-colors hover:bg-[var(--bg-subtle)]"
            style={{ color: 'var(--text-muted)' }}>
            <X size={13} /> Reset
          </button>
        )}
      </div>

      <div className="relative overflow-x-auto rounded-2xl" style={panelStyle}>
        {isFetching && !isLoading && (
          <div className="absolute right-4 top-4 z-10"><LoadingSpinner size="sm" /></div>
        )}
        <table className="data-table min-w-full">
          <thead>
            <tr>{['Transaction ID', 'Risk Level', 'Fraud Score', 'Triggered Rules', 'Recommendation', 'Detected At'].map((h) => <th key={h}>{h}</th>)}</tr>
          </thead>
          <tbody>
            {isLoading
              ? Array.from({ length: 8 }).map((_, i) => (
                  <tr key={i}>{Array.from({ length: 6 }).map((__, j) => <td key={j}><div className="skeleton h-4 rounded" /></td>)}</tr>
                ))
              : alerts.length === 0
                  ? <tr><td colSpan={6} className="py-16 text-center text-sm" style={{ color: 'var(--text-muted)' }}>No fraud alerts found for this tenant yet.</td></tr>
              : alerts.map((alert) => (
                  <tr key={`${alert.transactionId}-${alert.detectedAt}`}
                    onClick={() => { setSelected(alert); setDialogOpen(true) }}
                    className="cursor-pointer">
                    <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-muted)' }} title={alert.transactionId}>{truncate(alert.transactionId, 8, 4)}</span></td>
                    <td><div className="flex items-center gap-2">{riskIcon(alert.riskLevel)}<Badge variant={riskVariant(alert.riskLevel)} size="sm">{alert.riskLevel}</Badge></div></td>
                    <td>
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 w-20 rounded-full" style={{ background: 'var(--bg-subtle)' }}>
                          <div className="h-1.5 rounded-full" style={{ width: `${scoreToPercent(alert.score)}%`, background: riskBarColor(alert.riskLevel) }} />
                        </div>
                        <span className="text-[11px] font-mono" style={{ color: 'var(--text-muted)' }}>{scoreToPercent(alert.score).toFixed(1)}%</span>
                      </div>
                    </td>
                    <td>
                      <div className="flex flex-wrap gap-1 max-w-xs">
                        {alert.triggeredRules.slice(0, 3).map((rule) => (
                          <span key={rule} className="rounded-full px-2 py-0.5 text-[10px] bg-slate-100 border border-slate-200 text-slate-700">
                            {rule}
                          </span>
                        ))}
                        {alert.triggeredRules.length > 3 && (
                          <span className="rounded-full px-2 py-0.5 text-[10px]" style={{ background: 'var(--bg-subtle)', color: 'var(--text-muted)' }}>
                            +{alert.triggeredRules.length - 3}
                          </span>
                        )}
                      </div>
                    </td>
                    <td><p className="max-w-xs truncate text-[11px]" style={{ color: 'var(--text-secondary)' }}>{alert.recommendation}</p></td>
                    <td><span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{formatDate(alert.detectedAt, 'MMM dd HH:mm')}</span></td>
                  </tr>
                ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between gap-4">
          <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>Page {page + 1} of {totalPages}</p>
          <div className="flex items-center gap-1.5">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
              className="flex items-center gap-1 rounded-xl px-3 py-1.5 text-[11px] font-medium transition-all disabled:cursor-not-allowed disabled:opacity-40"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
              <ChevronLeft size={13} /> Prev
            </button>
            <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
              className="flex items-center gap-1 rounded-xl px-3 py-1.5 text-[11px] font-medium transition-all disabled:cursor-not-allowed disabled:opacity-40"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
              Next <ChevronRight size={13} />
            </button>
          </div>
        </div>
      )}

      <FraudDetailDialog alert={selected} open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </div>
  )
}
