'use client'

import React, { useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import * as Select from '@radix-ui/react-select'
import {
  BookOpen, Link2, Clock, XCircle, CheckCircle2, X,
  ChevronDown, Check, ChevronLeft, ChevronRight,
  Search, Loader2, Database, ShieldCheck, Download,
} from 'lucide-react'
import { useAuditEntries, useAuditSummary } from '@/hooks/use-audit'
import { api } from '@/lib/api-client'
import { Badge } from '@/components/ui/badge'
import { LoadingSpinner } from '@/components/ui/loading-spinner'
import { formatDate, truncate, formatNumber, prettyJson, downloadCsv } from '@/lib/utils'
import type { AuditEntry, AuditStatus, AuditListParams } from '@/types'

const ENTITY_TYPE_OPTIONS = [
  { label: 'All Entity Types', value: 'ALL' },
  { label: 'Transaction',      value: 'TRANSACTION' },
  { label: 'User',             value: 'USER' },
  { label: 'Tenant',           value: 'TENANT' },
  { label: 'Auth',             value: 'AUTH' },
  { label: 'Account',          value: 'ACCOUNT' },
]

const ACTION_OPTIONS = [
  { label: 'All Actions', value: 'ALL' },
  { label: 'CREATE',      value: 'CREATE' },
  { label: 'UPDATE',      value: 'UPDATE' },
  { label: 'DELETE',      value: 'DELETE' },
  { label: 'LOGIN',       value: 'LOGIN' },
  { label: 'LOGOUT',      value: 'LOGOUT' },
  { label: 'APPROVE',     value: 'APPROVE' },
  { label: 'REJECT',      value: 'REJECT' },
]

function auditStatusIcon(status: AuditStatus) {
  switch (status) {
    case 'COMMITTED': return <CheckCircle2 size={13} style={{ color: '#34d399' }} />
    case 'PENDING':   return <Loader2 size={13} className="animate-spin" style={{ color: '#fbbf24' }} />
    case 'FAILED':    return <XCircle size={13} style={{ color: '#f87171' }} />
    default:          return null
  }
}

function auditStatusVariant(status: AuditStatus): 'success' | 'warning' | 'danger' | 'default' {
  switch (status) {
    case 'COMMITTED': return 'success'
    case 'PENDING':   return 'warning'
    case 'FAILED':    return 'danger'
    default:          return 'default'
  }
}

function entityTypeBadgeVariant(type: string): 'info' | 'success' | 'warning' | 'purple' | 'default' {
  switch (type.toUpperCase()) {
    case 'TRANSACTION': return 'info'
    case 'USER':        return 'success'
    case 'TENANT':      return 'purple'
    case 'AUTH':        return 'warning'
    default:            return 'default'
  }
}

const panelStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
}

const dropdownStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  backdropFilter: 'blur(24px)',
}

function StyledSelect({ value, onValueChange, placeholder, options, minWidth = '44' }: {
  value: string
  onValueChange: (v: string) => void
  placeholder: string
  options: Array<{ label: string; value: string }>
  minWidth?: string
}) {
  return (
    <Select.Root value={value} onValueChange={onValueChange}>
      <Select.Trigger className={`flex min-w-${minWidth} items-center justify-between gap-2 rounded-xl px-3 py-2.5 text-sm outline-none`}
        style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
        <Select.Value placeholder={placeholder} />
        <ChevronDown size={13} style={{ color: 'var(--text-muted)' }} />
      </Select.Trigger>
      <Select.Portal>
        <Select.Content className="z-50 overflow-hidden rounded-2xl shadow-2xl" style={dropdownStyle}>
          <Select.Viewport className="p-1.5">
            {options.map((opt) => (
              <Select.Item key={opt.value} value={opt.value}
                className="flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm outline-none data-[highlighted]:bg-[var(--bg-subtle)]"
                style={{ color: 'var(--text-secondary)' }}>
                <Select.ItemIndicator><Check size={11} className="text-primary-400" /></Select.ItemIndicator>
                <Select.ItemText>{opt.label}</Select.ItemText>
              </Select.Item>
            ))}
          </Select.Viewport>
        </Select.Content>
      </Select.Portal>
    </Select.Root>
  )
}

function AuditDetailDialog({ entry, open, onClose }: {
  entry: AuditEntry | null; open: boolean; onClose: () => void
}) {
  if (!entry) return null

  const rows = [
    { label: 'Audit ID',    value: <span className="font-mono text-[11px]">{entry.auditId}</span> },
    { label: 'Entity Type', value: <Badge variant={entityTypeBadgeVariant(entry.entityType)} size="sm">{entry.entityType}</Badge> },
    { label: 'Entity ID',   value: <span className="font-mono text-[11px]">{entry.entityId}</span> },
    { label: 'Action',      value: <span className="font-semibold text-sm" style={{ color: 'var(--text-primary)' }}>{entry.action}</span> },
    { label: 'Actor ID',    value: <span className="font-mono text-[11px]">{entry.actorId}</span> },
    { label: 'Actor Type',  value: entry.actorType },
    { label: 'IP Address',  value: entry.ipAddress ?? '—' },
    { label: 'Occurred At', value: formatDate(entry.occurredAt) },
    { label: 'Status',      value: <div className="flex items-center gap-1.5">{auditStatusIcon(entry.status)}<Badge variant={auditStatusVariant(entry.status)} size="sm">{entry.status}</Badge></div> },
    { label: 'Tenant ID',   value: entry.tenantId },
  ]

  return (
    <Dialog.Root open={open} onOpenChange={(v) => !v && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/30 backdrop-blur-sm" />
        <Dialog.Content
          className="fixed z-50 w-full max-w-2xl max-h-[88vh] overflow-y-auto rounded-2xl outline-none"
          style={{ 
            background: 'var(--bg-surface)', 
            border: '1px solid var(--border)',
            boxShadow: '0 20px 60px rgba(0,0,0,0.15)',
            position: 'fixed',
            left: '50%',
            top: '50%',
            transform: 'translate(-50%, -50%)',
          }}
        >
          <div className="flex items-start justify-between px-6 py-5" style={{ borderBottom: '1px solid var(--border)' }}>
            <div>
              <Dialog.Title className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>Audit Entry Detail</Dialog.Title>
              <p className="mt-0.5 font-mono text-[11px]" style={{ color: 'var(--text-muted)' }}>{truncate(entry.auditId, 12, 6)}</p>
            </div>
            <Dialog.Close asChild>
              <button className="rounded-lg p-1.5 transition-colors hover:bg-[var(--bg-subtle)]" style={{ color: 'var(--text-muted)' }}>
                <X size={16} />
              </button>
            </Dialog.Close>
          </div>

          <div className="px-6 py-5 space-y-4">
            <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
              {rows.map(({ label, value }) => (
                <div key={label} className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                  <p className="text-[9px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>{label}</p>
                  <div className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>{value}</div>
                </div>
              ))}
            </div>

            {/* Blockchain proof */}
            <div className="rounded-xl p-4" style={{ background: 'rgba(99,102,241,0.05)', border: '1px solid rgba(99,102,241,0.15)' }}>
              <div className="flex items-center gap-2 mb-3">
                <Link2 size={14} className="text-primary-400" />
                <p className="text-sm font-semibold text-primary-300">Blockchain Immutability Proof</p>
                {entry.status === 'COMMITTED' ? (
                  <span className="ml-auto flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold"
                    style={{ background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.2)', color: '#34d399' }}>
                    <ShieldCheck size={9} /> Committed
                  </span>
                ) : (
                  <span className="ml-auto flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px]"
                    style={{ background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.2)', color: '#fbbf24' }}>
                    <Clock size={9} /> {entry.status}
                  </span>
                )}
              </div>
              <div className="space-y-1.5 text-xs">
                {[{ k: 'TX Hash', v: entry.blockchainTxId ?? '—' }, { k: 'Block Number', v: entry.blockNumber?.toString() ?? '—' }].map(({ k, v }) => (
                  <div key={k} className="flex items-start gap-3">
                    <span className="w-24 flex-shrink-0" style={{ color: 'var(--text-muted)' }}>{k}</span>
                    <span className="font-mono break-all" style={{ color: 'var(--text-secondary)' }}>{v}</span>
                  </div>
                ))}
              </div>
            </div>

            {entry.payload && (
              <div>
                <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Event Payload</p>
                <pre className="overflow-x-auto rounded-xl p-4 text-[11px] font-mono leading-relaxed"
                  style={{ background: 'rgba(0,0,0,0.5)', border: '1px solid rgba(255,255,255,0.07)', color: '#34d399' }}>
                  {prettyJson(entry.payload)}
                </pre>
              </div>
            )}
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

export default function AuditTrailPage() {
  const [page,       setPage]       = useState(0)
  const [entityType, setEntityType] = useState('ALL')
  const [action,     setAction]     = useState('ALL')
  const [fromDate,   setFromDate]   = useState('')
  const [toDate,     setToDate]     = useState('')
  const [search,     setSearch]     = useState('')
  const [selected,   setSelected]   = useState<AuditEntry | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  const params: AuditListParams = {
    page, size: 15, sort: 'occurredAt,desc',
    ...(entityType !== 'ALL' && { entityType }),
    ...(action !== 'ALL'     && { action }),
    ...(fromDate   && { fromDate }),
    ...(toDate     && { toDate }),
    ...(search     && { search }),
  }

  const { data, isLoading, isFetching } = useAuditEntries(params)
  const { data: summary } = useAuditSummary()

  const entries       = data?.content    ?? []
  const totalPages    = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0
  const hasFilters    = !!(entityType !== 'ALL' || action !== 'ALL' || fromDate || toDate || search)

  const [isExporting, setIsExporting] = useState(false)

  const handleExportCsv = async () => {
    setIsExporting(true)
    try {
      const all = await api.audit.list({ ...params, page: 0, size: 1000 })
      downloadCsv(
        (all.content ?? []).map((e) => ({
          auditId:    e.auditId,
          entityType: e.entityType,
          entityId:   e.entityId,
          action:     e.action,
          actorId:    e.actorId,
          ipAddress:  e.ipAddress ?? '',
          status:     e.status,
          blockchainTxId: e.blockchainTxId ?? '',
          occurredAt: e.occurredAt,
        })),
        `audit-trail-${new Date().toISOString().slice(0, 10)}.csv`
      )
    } finally {
      setIsExporting(false)
    }
  }

  const handleReset = () => { setEntityType('ALL'); setAction('ALL'); setFromDate(''); setToDate(''); setSearch(''); setPage(0) }

  return (
    <div className="animate-fade-in space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Audit Trail</h2>
            <span className="flex items-center gap-1 rounded-full px-2.5 py-1 text-[10px] font-semibold"
              style={{ background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.2)', color: '#34d399' }}>
              <ShieldCheck size={10} /> Immutable
            </span>
          </div>
          <p className="mt-0.5 text-sm" style={{ color: 'var(--text-muted)' }}>
            Every event cryptographically recorded on-chain
          </p>
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

      {summary && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[
            { label: 'Total Entries',   value: summary.totalEntries,          icon: <Database size={16} />,   iconBg: 'var(--bg-subtle)',  iconColor: 'var(--text-secondary)', border: 'var(--border)' },
            { label: 'On Blockchain',   value: summary.committedToBlockchain, icon: <Link2 size={16} />,      iconBg: 'rgba(16,185,129,0.1)',    iconColor: '#34d399',               border: 'rgba(16,185,129,0.15)'  },
            { label: 'Pending Commit',  value: summary.pending,              icon: <Loader2 size={16} className="animate-spin" />, iconBg: 'rgba(245,158,11,0.1)', iconColor: '#fbbf24', border: 'rgba(245,158,11,0.15)'  },
            { label: 'Failed',          value: summary.failed,               icon: <XCircle size={16} />,    iconBg: 'rgba(239,68,68,0.1)',     iconColor: '#f87171',               border: 'rgba(239,68,68,0.15)'   },
          ].map(({ label, value, icon, iconBg, iconColor, border }) => (
            <div key={label} className="rounded-2xl p-4" style={{ background: 'var(--bg-surface)', border: `1px solid ${border}` }}>
              <div className="flex h-8 w-8 items-center justify-center rounded-xl mb-3" style={{ background: iconBg, color: iconColor }}>{icon}</div>
              <p className="text-2xl font-bold" style={{ color: iconColor }}>{formatNumber(value)}</p>
              <p className="mt-0.5 text-[11px]" style={{ color: 'var(--text-muted)' }}>{label}</p>
            </div>
          ))}
        </div>
      )}

      <div className="flex flex-wrap gap-3 rounded-2xl p-4" style={panelStyle}>
        <div className="relative min-w-48">
          <Search size={13} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2" style={{ color: 'var(--text-muted)' }} />
          <input type="text" placeholder="Search entity ID…" value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0) }}
            className="input-base pl-9" />
        </div>
        <StyledSelect value={entityType} onValueChange={(v) => { setEntityType(v); setPage(0) }} placeholder="All Entity Types" options={ENTITY_TYPE_OPTIONS} />
        <StyledSelect value={action}     onValueChange={(v) => { setAction(v);     setPage(0) }} placeholder="All Actions"      options={ACTION_OPTIONS}      minWidth="36" />
        <input type="date" value={fromDate} onChange={(e) => { setFromDate(e.target.value); setPage(0) }} className="input-base w-auto" />
        <input type="date" value={toDate}   onChange={(e) => { setToDate(e.target.value);   setPage(0) }} className="input-base w-auto" />
        {hasFilters && (
          <button onClick={handleReset} className="flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm transition-colors hover:bg-[var(--bg-subtle)]" style={{ color: 'var(--text-muted)' }}>
            <X size={13} /> Reset
          </button>
        )}
      </div>

      <div className="relative overflow-x-auto rounded-2xl" style={panelStyle}>
        {isFetching && !isLoading && <div className="absolute right-4 top-4 z-10"><LoadingSpinner size="sm" /></div>}
        <table className="data-table min-w-full">
          <thead>
            <tr>{['Audit ID', 'Entity Type', 'Entity ID', 'Action', 'Actor', 'IP Address', 'Chain TX', 'Status', 'Occurred At'].map((h) => <th key={h}>{h}</th>)}</tr>
          </thead>
          <tbody>
            {isLoading
              ? Array.from({ length: 10 }).map((_, i) => (
                  <tr key={i}>{Array.from({ length: 9 }).map((__, j) => <td key={j}><div className="skeleton h-4 rounded" /></td>)}</tr>
                ))
              : entries.length === 0
              ? <tr><td colSpan={9} className="py-16 text-center text-sm" style={{ color: 'var(--text-muted)' }}>No audit entries found.</td></tr>
              : entries.map((entry) => (
                  <tr key={entry.auditId} onClick={() => { setSelected(entry); setDialogOpen(true) }} className="cursor-pointer">
                    <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-muted)' }} title={entry.auditId}>{truncate(entry.auditId, 8, 4)}</span></td>
                    <td><Badge variant={entityTypeBadgeVariant(entry.entityType)} size="sm">{entry.entityType}</Badge></td>
                    <td><span className="font-mono text-[11px]">{truncate(entry.entityId, 6, 4)}</span></td>
                    <td><span className="text-[11px] font-semibold" style={{ color: 'var(--text-primary)' }}>{entry.action}</span></td>
                    <td><span className="font-mono text-[11px]">{truncate(entry.actorId, 6, 4)}</span></td>
                    <td><span className="text-[11px]">{entry.ipAddress ?? '—'}</span></td>
                    <td>
                      {entry.blockchainTxId ? (
                        <div className="flex items-center gap-1">
                          <Link2 size={10} style={{ color: '#34d399' }} />
                          <span className="font-mono text-[11px]" style={{ color: '#34d399' }} title={entry.blockchainTxId}>{truncate(entry.blockchainTxId, 5, 3)}</span>
                        </div>
                      ) : <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>—</span>}
                    </td>
                    <td>
                      <div className="flex items-center gap-1.5">
                        {auditStatusIcon(entry.status)}
                        <Badge variant={auditStatusVariant(entry.status)} size="sm">{entry.status}</Badge>
                      </div>
                    </td>
                    <td><span className="text-[11px]">{formatDate(entry.occurredAt, 'MMM dd HH:mm')}</span></td>
                  </tr>
                ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between gap-4">
          <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>Page {page + 1} of {totalPages} — {formatNumber(totalElements)} entries</p>
          <div className="flex items-center gap-1.5">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
              className="flex items-center gap-1 rounded-xl px-3 py-1.5 text-[11px] font-medium disabled:cursor-not-allowed disabled:opacity-40 transition-all"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
              <ChevronLeft size={13} /> Prev
            </button>
            <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
              className="flex items-center gap-1 rounded-xl px-3 py-1.5 text-[11px] font-medium disabled:cursor-not-allowed disabled:opacity-40 transition-all"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
              Next <ChevronRight size={13} />
            </button>
          </div>
        </div>
      )}

      <AuditDetailDialog entry={selected} open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </div>
  )
}
