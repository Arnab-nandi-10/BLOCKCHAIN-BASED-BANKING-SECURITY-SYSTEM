'use client'

import React, { useState, useCallback } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import * as Select from '@radix-ui/react-select'
import {
  Search, X, ExternalLink, Download, ChevronLeft, ChevronRight,
  ChevronDown, Check, Link2, Eye, Loader2, AlertTriangle,
} from 'lucide-react'
import { useTransactions } from '@/hooks/use-transactions'
import { api } from '@/lib/api-client'
import { Badge } from '@/components/ui/badge'
import { LoadingSpinner } from '@/components/ui/loading-spinner'
import { formatCurrency, formatDate, truncate, formatNumber, prettyJson, downloadCsv } from '@/lib/utils'
import type { Transaction, TransactionStatus, TransactionListParams } from '@/types'

// ─── Constants ────────────────────────────────────────────────────────────────

const STATUS_OPTIONS = [
  { label: 'All Statuses',        value: 'ALL' },
  { label: 'Submitted',           value: 'SUBMITTED' },
  { label: 'Pending Fraud Check', value: 'PENDING_FRAUD_CHECK' },
  { label: 'Verified',            value: 'VERIFIED' },
  { label: 'Fraud Hold',          value: 'FRAUD_HOLD' },
  { label: 'Blocked',             value: 'BLOCKED' },
  { label: 'Completed',           value: 'COMPLETED' },
  { label: 'Failed',              value: 'FAILED' },
]

function statusVariant(status: TransactionStatus): 'success' | 'danger' | 'warning' | 'info' | 'default' {
  switch (status) {
    case 'COMPLETED': case 'VERIFIED':           return 'success'
    case 'BLOCKED':   case 'FAILED':             return 'danger'
    case 'FRAUD_HOLD':                           return 'warning'
    case 'SUBMITTED': case 'PENDING_FRAUD_CHECK': return 'info'
    default:                                     return 'default'
  }
}

function fraudBarColor(score: number): string {
  if (score >= 0.8) return '#ef4444'
  if (score >= 0.6) return '#f97316'
  if (score >= 0.4) return '#f59e0b'
  return '#10b981'
}

// ─── Shared panel style ────────────────────────────────────────────────────────

const panelStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
}

// ─── Transaction Detail Dialog ────────────────────────────────────────────────

function TransactionDetailDialog({ tx, open, onClose }: {
  tx: Transaction | null; open: boolean; onClose: () => void
}) {
  if (!tx) return null

  const rows: Array<{ label: string; value: React.ReactNode }> = [
    { label: 'Transaction ID',  value: <span className="font-mono text-[11px]">{tx.transactionId}</span> },
    { label: 'Tenant ID',       value: tx.tenantId },
    { label: 'Type',            value: <Badge variant="info" size="sm">{tx.type}</Badge> },
    { label: 'Status',          value: <Badge variant={statusVariant(tx.status)} size="sm">{tx.status.replace(/_/g, ' ')}</Badge> },
    { label: 'From Account',    value: <span className="font-mono text-[11px]">{tx.fromAccount}</span> },
    { label: 'To Account',      value: <span className="font-mono text-[11px]">{tx.toAccount}</span> },
    { label: 'Amount',          value: <span className="font-semibold">{formatCurrency(tx.amount, tx.currency)}</span> },
    { label: 'Currency',        value: tx.currency },
    { label: 'Fraud Score',     value: (
      <div className="flex items-center gap-2">
        <div className="h-1.5 w-28 rounded-full" style={{ background: 'rgba(255,255,255,0.1)' }}>
          <div className="h-1.5 rounded-full transition-all" style={{ width: `${(tx.fraudScore * 100).toFixed(1)}%`, background: fraudBarColor(tx.fraudScore) }} />
        </div>
        <span className="font-mono text-[11px]">{(tx.fraudScore * 100).toFixed(1)}%</span>
      </div>
    )},
    { label: 'Risk Level',      value: tx.fraudRiskLevel
        ? <Badge variant={tx.fraudRiskLevel === 'CRITICAL' ? 'danger' : tx.fraudRiskLevel === 'HIGH' ? 'warning' : 'default'} size="sm">{tx.fraudRiskLevel}</Badge>
        : '—' },
    { label: 'Created At',      value: formatDate(tx.createdAt) },
    { label: 'Completed At',    value: tx.completedAt ? formatDate(tx.completedAt) : '—' },
  ]

  return (
    <Dialog.Root open={open} onOpenChange={(v) => !v && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/30 backdrop-blur-sm" />
        <Dialog.Content
          className="fixed z-50 w-[95vw] max-w-3xl max-h-[85vh] overflow-y-auto rounded-2xl outline-none"
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
          {/* Header with gradient accent */}
          <div className="sticky top-0 z-10 flex items-start justify-between px-6 py-5 bg-gradient-to-r from-blue-600/20 to-indigo-600/20" style={{ borderBottom: '1px solid var(--border)', background: 'linear-gradient(135deg, rgba(37,99,235,0.08) 0%, rgba(79,70,229,0.08) 100%)' }}>
            <div>
              <Dialog.Title className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                Transaction Details
              </Dialog.Title>
              <p className="mt-1 font-mono text-[11px]" style={{ color: 'var(--text-muted)' }}>{truncate(tx.transactionId, 14, 8)}</p>
            </div>
            <Dialog.Close asChild>
              <button className="flex-shrink-0 rounded-lg p-2 transition-all hover:bg-[var(--bg-subtle)]" style={{ color: 'var(--text-muted)' }}>
                <X size={18} />
              </button>
            </Dialog.Close>
          </div>

          <div className="px-6 py-6 space-y-5">
            {/* Main transaction info grid */}
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {rows.slice(0, 8).map(({ label, value }) => (
                <div key={label} className="rounded-xl px-4 py-3.5 transition-all hover:border-blue-500/30" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                  <p className="text-[9px] font-bold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>{label}</p>
                  <div className="mt-2 text-[13px] font-medium" style={{ color: 'var(--text-primary)' }}>{value}</div>
                </div>
              ))}
            </div>

            {/* Risk & Blockchain section */}
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {/* Fraud Score section */}
              <div className="rounded-xl p-4 border border-orange-500/20" style={{ background: 'linear-gradient(135deg, rgba(217,119,6,0.06) 0%, rgba(245,158,11,0.06) 100%)' }}>
                <div className="flex items-center gap-2 mb-4">
                  <AlertTriangle size={14} style={{ color: '#D97706' }} />
                  <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Fraud Assessment</p>
                </div>
                <div className="space-y-3">
                  {rows.slice(8, 10).map(({ label, value }) => (
                    <div key={label}>
                      <p className="text-[9px] font-bold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>{label}</p>
                      <div className="mt-1 text-[13px] font-medium" style={{ color: 'var(--text-primary)' }}>{value}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Blockchain proof */}
              <div className="rounded-xl p-4 border border-indigo-500/20" style={{ background: 'linear-gradient(135deg, rgba(99,102,241,0.06) 0%, rgba(59,130,246,0.06) 100%)' }}>
                <div className="flex items-center gap-2 mb-4">
                  <Link2 size={14} style={{ color: '#3B82F6' }} />
                  <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Blockchain Proof</p>
                  {tx.blockchainTxId ? (
                    <span className="ml-auto flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold"
                      style={{ background: 'rgba(16,185,129,0.15)', border: '1px solid rgba(16,185,129,0.3)', color: '#10b981' }}>
                      <Check size={10} /> Verified
                    </span>
                  ) : (
                    <span className="ml-auto rounded-full px-2 py-0.5 text-[10px] font-medium"
                      style={{ background: 'var(--bg-subtle)', color: 'var(--text-muted)' }}>Pending</span>
                  )}
                </div>
                <div className="space-y-2.5 text-xs">
                  {[
                    { k: 'TX Hash', v: tx.blockchainTxId ?? '—' },
                    { k: 'Block #', v: tx.blockNumber?.toString() ?? '—' },
                  ].map(({ k, v }) => (
                    <div key={k} className="flex items-start gap-2">
                      <span className="w-16 flex-shrink-0 font-semibold" style={{ color: 'var(--text-muted)' }}>{k}</span>
                      <span className="font-mono text-[11px] break-all" style={{ color: 'var(--text-secondary)' }}>{v}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Timestamps */}
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3 pt-2">
              {rows.slice(10).map(({ label, value }) => (
                <div key={label} className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                  <p className="text-[9px] font-bold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>{label}</p>
                  <div className="mt-1 text-[12px]" style={{ color: 'var(--text-secondary)' }}>{value}</div>
                </div>
              ))}
            </div>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function TransactionsPage() {
  const [page,     setPage]     = useState(0)
  const [search,   setSearch]   = useState('')
  const [status,   setStatus]   = useState('ALL')
  const [fromDate, setFromDate] = useState('')
  const [toDate,   setToDate]   = useState('')
  const [selectedTx, setSelectedTx] = useState<Transaction | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  const params: TransactionListParams = {
    page, size: 15, sort: 'createdAt,desc',
    ...(status !== 'ALL' && { status }),
    ...(fromDate  && { fromDate }),
    ...(toDate    && { toDate }),
    ...(search    && { search }),
  }

  const { data, isLoading, isFetching } = useTransactions(params)
  const transactions  = data?.content    ?? []
  const totalPages    = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  const openDetail = useCallback((tx: Transaction) => { setSelectedTx(tx); setDialogOpen(true) }, [])

  const [isExporting, setIsExporting] = useState(false)

  const handleExportCsv = async () => {
    setIsExporting(true)
    try {
      const all = await api.transactions.list({ ...params, page: 0, size: 1000 })
      downloadCsv(
        (all.content ?? []).map((t) => ({
          transactionId:  t.transactionId,
          fromAccount:    t.fromAccount,
          toAccount:      t.toAccount,
          amount:         t.amount,
          currency:       t.currency,
          type:           t.type,
          status:         t.status,
          fraudScore:     t.fraudScore,
          blockchainTxId: t.blockchainTxId ?? '',
          blockNumber:    t.blockNumber ?? '',
          createdAt:      t.createdAt,
        })),
        `transactions-${new Date().toISOString().slice(0, 10)}.csv`
      )
    } finally {
      setIsExporting(false)
    }
  }

  const hasFilters = !!(search || (status !== 'ALL') || fromDate || toDate)

  return (
    <div className="animate-fade-in space-y-6">

      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Transactions</h2>
          <p className="mt-0.5 text-sm" style={{ color: 'var(--text-muted)' }}>
            {formatNumber(totalElements)} total records
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

      {/* Filters */}
      <div className="flex flex-wrap gap-3 rounded-2xl p-4" style={panelStyle}>
        <div className="relative flex-1 min-w-44">
          <Search size={13} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2" style={{ color: 'var(--text-muted)' }} />
          <input
            type="text"
            placeholder="Search TX ID or account…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0) }}
            className="input-base pl-9"
          />
        </div>

        <Select.Root value={status} onValueChange={(v) => { setStatus(v); setPage(0) }}>
          <Select.Trigger className="flex min-w-44 items-center justify-between gap-2 rounded-xl px-3 py-2.5 text-sm outline-none transition-all"
            style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
            <Select.Value placeholder="All Statuses" />
            <ChevronDown size={13} style={{ color: 'var(--text-muted)' }} />
          </Select.Trigger>
          <Select.Portal>
            <Select.Content className="z-50 overflow-hidden rounded-2xl shadow-2xl"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}>
              <Select.Viewport className="p-1.5">
                {STATUS_OPTIONS.map((opt) => (
                  <Select.Item key={opt.value} value={opt.value}
                    className="flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm outline-none transition-colors data-[highlighted]:bg-[var(--bg-subtle)]"
                    style={{ color: 'var(--text-secondary)' }}>
                    <Select.ItemIndicator>
                      <Check size={11} style={{ color: 'var(--color-primary)' }} />
                    </Select.ItemIndicator>
                    <Select.ItemText>{opt.label}</Select.ItemText>
                  </Select.Item>
                ))}
              </Select.Viewport>
            </Select.Content>
          </Select.Portal>
        </Select.Root>

        <input type="date" value={fromDate}
          onChange={(e) => { setFromDate(e.target.value); setPage(0) }}
          className="input-base w-auto"
        />
        <input type="date" value={toDate}
          onChange={(e) => { setToDate(e.target.value); setPage(0) }}
          className="input-base w-auto"
        />

        {hasFilters && (
          <button
            onClick={() => { setSearch(''); setStatus('ALL'); setFromDate(''); setToDate(''); setPage(0) }}
            className="flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm transition-colors hover:bg-[var(--bg-subtle)]"
            style={{ color: 'var(--text-muted)' }}
          >
            <X size={13} /> Reset
          </button>
        )}
      </div>

      {/* Table */}
      <div className="relative rounded-2xl overflow-hidden overflow-x-auto" style={panelStyle}>
        {isFetching && !isLoading && (
          <div className="absolute right-4 top-4 z-10">
            <LoadingSpinner size="sm" />
          </div>
        )}
        <table className="data-table min-w-full">
          <colgroup>
            <col style={{ width: '120px' }} />{/* TX ID */}
            <col style={{ width: '130px' }} />{/* From */}
            <col style={{ width: '130px' }} />{/* To */}
            <col style={{ width: '130px' }} />{/* Amount */}
            <col style={{ width: '90px' }}  />{/* Type */}
            <col style={{ width: '105px' }} />{/* Status */}
            <col style={{ width: '160px' }} />{/* Fraud Score */}
            <col style={{ width: '90px' }}  />{/* Chain TX */}
            <col style={{ width: '110px' }} />{/* Created */}
            <col style={{ width: '70px' }}  />{/* Actions */}
          </colgroup>
          <thead>
            <tr>
              {['TX ID', 'From', 'To', 'Amount', 'Type', 'Status', 'Fraud Score', 'Chain TX', 'Created', ''].map((h) => (
                <th key={h}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading
              ? Array.from({ length: 10 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 10 }).map((__, j) => (
                      <td key={j}><div className="skeleton h-4 rounded" /></td>
                    ))}
                  </tr>
                ))
              : transactions.length === 0
              ? (
                <tr>
                  <td colSpan={10} className="py-16 text-center text-sm" style={{ color: 'var(--text-muted)' }}>
                    No transactions match your filters.
                  </td>
                </tr>
              )
              : transactions.map((tx) => (
                  <tr key={tx.transactionId}>
                    <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-muted)' }} title={tx.transactionId}>{truncate(tx.transactionId, 8, 4)}</span></td>
                    <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-secondary)' }}>{truncate(tx.fromAccount, 6, 4)}</span></td>
                    <td><span className="font-mono text-[11px]" style={{ color: 'var(--text-secondary)' }}>{truncate(tx.toAccount, 6, 4)}</span></td>
                    <td><span className="font-medium text-sm" style={{ color: 'var(--text-primary)' }}>{formatCurrency(tx.amount, tx.currency)}</span></td>
                    <td><Badge variant="default" size="sm">{tx.type}</Badge></td>
                    <td><Badge variant={statusVariant(tx.status)} size="sm">{tx.status.replace(/_/g, ' ')}</Badge></td>
                    <td>
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 w-16 flex-shrink-0 rounded-full" style={{ background: 'var(--bg-subtle)' }}>
                          <div className="h-1.5 rounded-full" style={{ width: `${(tx.fraudScore * 100).toFixed(1)}%`, background: fraudBarColor(tx.fraudScore) }} />
                        </div>
                        <span className="text-[11px] font-mono" style={{ color: 'var(--text-muted)' }}>{(tx.fraudScore * 100).toFixed(1)}%</span>
                      </div>
                    </td>
                    <td>
                      {tx.blockchainTxId ? (
                        <div className="flex items-center gap-1">
                          <ExternalLink size={10} style={{ color: 'var(--color-primary)' }} className="flex-shrink-0" />
                          <span className="font-mono text-[11px] cursor-pointer transition-colors" style={{ color: 'var(--color-primary)' }} title={tx.blockchainTxId}>
                            {truncate(tx.blockchainTxId, 5, 3)}
                          </span>
                        </div>
                      ) : (
                        <span className="text-[11px]" style={{ color: 'var(--border)' }}>—</span>
                      )}
                    </td>
                    <td><span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{formatDate(tx.createdAt, 'MMM dd HH:mm')}</span></td>
                    <td>
                      <button
                        onClick={() => openDetail(tx)}
                        className="flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-[11px] transition-all hover:bg-[var(--bg-subtle)]"
                        style={{ border: '1px solid var(--border)', color: 'var(--text-secondary)' }}
                      >
                        <Eye size={11} /> View
                      </button>
                    </td>
                  </tr>
                ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between gap-4">
          <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
            Page {page + 1} of {totalPages} — {formatNumber(totalElements)} records
          </p>
          <div className="flex items-center gap-1.5">
            <PagBtn onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
              <ChevronLeft size={13} /> Prev
            </PagBtn>
            {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
              const start = Math.max(0, Math.min(page - 2, totalPages - 5))
              const p = start + i
              return (
                <button key={p} onClick={() => setPage(p)}
                  className="w-8 rounded-xl py-1.5 text-[11px] font-medium transition-all"
                  style={p === page
                    ? { background: 'linear-gradient(135deg, #EFF6FF 0%, #F0F9FF 100%)', border: '1px solid #BFDBFE', color: '#2563EB' }
                    : { background: 'var(--bg-subtle)', border: '1px solid var(--border)', color: 'var(--text-muted)' }}
                >
                  {p + 1}
                </button>
              )
            })}
            <PagBtn onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}>
              Next <ChevronRight size={13} />
            </PagBtn>
          </div>
        </div>
      )}

      <TransactionDetailDialog tx={selectedTx} open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </div>
  )
}

function PagBtn({ children, onClick, disabled }: { children: React.ReactNode; onClick: () => void; disabled: boolean }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="flex items-center gap-1 rounded-xl px-3 py-1.5 text-[11px] font-medium transition-all disabled:cursor-not-allowed disabled:opacity-40 bg-white"
      style={{ border: '1px solid #E2E8F0', color: '#64748B' }}
    >
      {children}
    </button>
  )
}
