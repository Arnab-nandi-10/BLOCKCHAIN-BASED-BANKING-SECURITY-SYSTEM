'use client'

import React, { useRef, useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { AlertCircle, Check, Download, FileUp, Loader2, Plus, ShieldAlert, Upload, X } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { cn, downloadCsv } from '@/lib/utils'
import { transactionKeys, useSubmitTransaction } from '@/hooks/use-transactions'
import { Badge } from '@/components/ui/badge'
import { useAuthStore } from '@/store/auth-store'
import type { FraudScoreRequest, FraudScoreResponse, SubmitTransactionRequest, Transaction, TransactionType } from '@/types'

const TRANSACTION_TYPES: Array<{ label: string; value: TransactionType }> = [
  { label: 'Transfer', value: 'TRANSFER' },
  { label: 'Payment', value: 'PAYMENT' },
  { label: 'Withdrawal', value: 'WITHDRAWAL' },
  { label: 'Deposit', value: 'DEPOSIT' },
]

const MANUAL_FORM_INITIAL = {
  fromAccount: '',
  toAccount: '',
  amount: '',
  currency: 'USD',
  type: 'TRANSFER' as TransactionType,
  metadata: '',
}

type ManualFormState = typeof MANUAL_FORM_INITIAL

type ParsedUploadRow = {
  lineNumber: number
  values: Record<string, string>
  request?: SubmitTransactionRequest
  error?: string
}

const CSV_TEMPLATE = [
  {
    fromAccount: '4820019342',
    toAccount: '7631004589',
    amount: 1500.25,
    currency: 'USD',
    type: 'TRANSFER',
    metadata: '{"reference":"INV-1042","batch":"april"}',
  },
]

function getErrorMessage(error: unknown, fallback = 'Something went wrong') {
  const possibleMessage = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  if (possibleMessage) return possibleMessage
  if (error instanceof Error && error.message) return error.message
  return fallback
}

function normalizeHeader(header: string) {
  return header.trim().toLowerCase().replace(/[\s_-]+/g, '')
}

function parseMetadata(raw: string): Record<string, string> {
  const trimmed = raw.trim()
  if (!trimmed) return {}

  const parsed = JSON.parse(trimmed)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('metadata must be a JSON object')
  }

  return Object.fromEntries(
    Object.entries(parsed as Record<string, unknown>).map(([key, value]) => [
      key,
      typeof value === 'string' ? value : JSON.stringify(value),
    ])
  )
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = []
  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  let currentRow: string[] = []
  let currentValue = ''
  let inQuotes = false

  for (let index = 0; index < normalized.length; index += 1) {
    const char = normalized[index]
    const nextChar = normalized[index + 1]

    if (inQuotes) {
      if (char === '"') {
        if (nextChar === '"') {
          currentValue += '"'
          index += 1
        } else {
          inQuotes = false
        }
      } else {
        currentValue += char
      }
      continue
    }

    if (char === '"') {
      inQuotes = true
      continue
    }

    if (char === ',') {
      currentRow.push(currentValue.trim())
      currentValue = ''
      continue
    }

    if (char === '\n') {
      currentRow.push(currentValue.trim())
      if (currentRow.some((cell) => cell.trim() !== '')) {
        rows.push(currentRow)
      }
      currentRow = []
      currentValue = ''
      continue
    }

    currentValue += char
  }

  if (currentValue.length > 0 || currentRow.length > 0) {
    currentRow.push(currentValue.trim())
    if (currentRow.some((cell) => cell.trim() !== '')) {
      rows.push(currentRow)
    }
  }

  if (rows[0]?.[0]?.startsWith('\ufeff')) {
    rows[0][0] = rows[0][0].slice(1)
  }

  return rows
}

function isTransactionType(value: string): value is TransactionType {
  return TRANSACTION_TYPES.some((option) => option.value === value)
}

function parseTransactionCsv(text: string): ParsedUploadRow[] {
  const parsedRows = parseCsv(text)
  if (parsedRows.length <= 1) return []

  const headers = parsedRows[0].map(normalizeHeader)
  const columnIndex = new Map(headers.map((header, index) => [header, index]))
  const rows: ParsedUploadRow[] = []

  for (let rowIndex = 1; rowIndex < parsedRows.length; rowIndex += 1) {
    const cells = parsedRows[rowIndex]
    const lineNumber = rowIndex + 1
    const getValue = (columnName: string) => {
      const column = columnIndex.get(columnName)
      return column === undefined ? '' : (cells[column] ?? '').trim()
    }

    const values = {
      fromAccount: getValue('fromaccount'),
      toAccount: getValue('toaccount'),
      amount: getValue('amount'),
      currency: getValue('currency'),
      type: getValue('type'),
      metadata: getValue('metadata'),
    }

    const errors: string[] = []
    if (!values.fromAccount) errors.push('fromAccount is required')
    if (!values.toAccount) errors.push('toAccount is required')

    const amount = Number(values.amount)
    if (!Number.isFinite(amount) || amount <= 0) {
      errors.push('amount must be a positive number')
    }

    const currency = values.currency.trim().toUpperCase()
    if (!/^[A-Z]{3}$/.test(currency)) {
      errors.push('currency must be a 3-letter ISO code')
    }

    const type = values.type.trim().toUpperCase()
    if (!isTransactionType(type)) {
      errors.push(`type must be one of ${TRANSACTION_TYPES.map((option) => option.value).join(', ')}`)
    }

    let metadata: Record<string, string> | undefined
    if (values.metadata) {
      try {
        metadata = parseMetadata(values.metadata)
      } catch (error) {
        errors.push(getErrorMessage(error, 'metadata must be a valid JSON object'))
      }
    }

    if (errors.length > 0) {
      rows.push({
        lineNumber,
        values,
        error: errors.join('; '),
      })
      continue
    }

    rows.push({
      lineNumber,
      values,
      request: {
        fromAccount: values.fromAccount,
        toAccount: values.toAccount,
        amount,
        currency,
        type: type as TransactionType,
        ...(metadata && Object.keys(metadata).length > 0 ? { metadata } : {}),
      },
    })
  }

  return rows
}

function downloadTemplate() {
  downloadCsv(CSV_TEMPLATE, 'transaction-upload-template.csv')
}

function createPreviewTransactionId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `preview-${crypto.randomUUID()}`
  }

  return `preview-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

const SUBMIT_RECOVERY_ATTEMPTS = 6
const SUBMIT_RECOVERY_DELAY_MS = 750

type HeaderBag = {
  get?: (name: string) => unknown
  [key: string]: unknown
}

function readHeaderValue(headers: unknown, key: string) {
  if (!headers || typeof headers !== 'object') {
    return null
  }

  const bag = headers as HeaderBag
  const values = [bag[key], bag[key.toLowerCase()], bag[key.toUpperCase()]]

  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }

  if (typeof bag.get === 'function') {
    const value = bag.get(key)
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }

  return null
}

function getRequestIdFromSubmitError(error: unknown) {
  const maybeError = error as {
    config?: { headers?: unknown }
    response?: { headers?: unknown; data?: { requestId?: unknown } }
  }

  return (
    readHeaderValue(maybeError.config?.headers, 'X-Request-ID') ??
    readHeaderValue(maybeError.response?.headers, 'X-Request-ID') ??
    (typeof maybeError.response?.data?.requestId === 'string' && maybeError.response.data.requestId.trim()
      ? maybeError.response.data.requestId.trim()
      : null)
  )
}

function isRecoverableSubmitError(error: unknown) {
  const maybeError = error as {
    code?: string
    message?: string
    response?: { status?: number }
  }

  const message = maybeError.message?.toLowerCase() ?? ''
  return (
    maybeError.response?.status === 503 ||
    maybeError.response?.status === 504 ||
    maybeError.code === 'ECONNABORTED' ||
    message.includes('timeout')
  )
}

function delay(ms: number) {
  return new Promise<void>((resolve) => {
    setTimeout(resolve, ms)
  })
}

async function recoverSubmittedTransaction(requestId: string) {
  for (let attempt = 0; attempt < SUBMIT_RECOVERY_ATTEMPTS; attempt += 1) {
    try {
      const page = await api.transactions.list({
        search: requestId,
        size: 20,
        sort: 'createdAt,desc',
      })

      const recovered = page.content.find((transaction) => transaction.correlationId === requestId)
      if (recovered) {
        return recovered
      }
    } catch {
      // Ignore lookup errors here; a later attempt may still reconcile the transaction.
    }

    if (attempt < SUBMIT_RECOVERY_ATTEMPTS - 1) {
      await delay(SUBMIT_RECOVERY_DELAY_MS)
    }
  }

  return null
}

async function submitTransactionWithRecovery(
  submitRequest: SubmitTransactionRequest,
  submitFn: (request: SubmitTransactionRequest) => Promise<Transaction>
) {
  try {
    const transaction = await submitFn(submitRequest)
    return { transaction, recovered: false as const }
  } catch (error) {
    if (isRecoverableSubmitError(error)) {
      const requestId = getRequestIdFromSubmitError(error)
      if (requestId) {
        const recovered = await recoverSubmittedTransaction(requestId)
        if (recovered) {
          return { transaction: recovered, recovered: true as const, requestId }
        }
      }
    }

    throw error
  }
}

function fraudRiskVariant(level?: string) {
  switch (level) {
    case 'CRITICAL': return 'danger'
    case 'HIGH':     return 'warning'
    case 'MEDIUM':   return 'info'
    case 'LOW':      return 'success'
    default:         return 'default'
  }
}

function decisionVariant(decision?: string) {
  switch (decision) {
    case 'ALLOW':  return 'success'
    case 'MONITOR': return 'info'
    case 'HOLD':   return 'warning'
    case 'BLOCK':  return 'danger'
    default:       return 'default'
  }
}

function normalizeFraudScore(score: number) {
  return Math.max(0, Math.min(100, score <= 1 ? score * 100 : score))
}

function fraudBarColor(score: number): string {
  const normalized = score <= 1 ? score : score / 100
  if (normalized >= 0.8) return '#ef4444'
  if (normalized >= 0.6) return '#f97316'
  if (normalized >= 0.4) return '#f59e0b'
  return '#10b981'
}

function Field({
  label,
  hint,
  error,
  children,
  required = false,
}: {
  label: string
  hint?: string
  error?: string
  required?: boolean
  children: React.ReactNode
}) {
  return (
    <div>
      <div className="mb-1.5 flex items-end justify-between gap-3">
        <label className="text-xs font-semibold uppercase tracking-[0.14em]" style={{ color: 'var(--text-muted)' }}>
          {label}
          {required ? <span className="ml-1 text-red-500">*</span> : null}
        </label>
        {hint ? <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>{hint}</span> : null}
      </div>
      {children}
      {error ? (
        <p className="mt-1.5 flex items-start gap-1.5 text-xs text-red-600">
          <AlertCircle size={12} className="mt-0.5 flex-shrink-0" />
          <span>{error}</span>
        </p>
      ) : null}
    </div>
  )
}

function DialogShell({
  title,
  description,
  children,
  widthClass = 'max-w-3xl',
}: {
  title: string
  description: string
  children: React.ReactNode
  widthClass?: string
}) {
  return (
    <Dialog.Portal>
      <Dialog.Overlay className="fixed inset-0 z-40 bg-black/35 backdrop-blur-sm" />
      <Dialog.Content
        className={cn(
          'fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[95vw] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl outline-none',
          widthClass,
        )}
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          boxShadow: '0 24px 80px rgba(0,0,0,0.18)',
        }}
      >
        <div
          className="flex items-start justify-between gap-4 px-6 py-5"
          style={{
            background: 'linear-gradient(135deg, rgba(37,99,235,0.08) 0%, rgba(79,70,229,0.08) 100%)',
            borderBottom: '1px solid var(--border)',
          }}
        >
          <div>
            <Dialog.Title className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
              {title}
            </Dialog.Title>
            <Dialog.Description className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
              {description}
            </Dialog.Description>
          </div>
          <Dialog.Close asChild>
            <button
              type="button"
              className="rounded-lg p-2 transition-colors hover:bg-[var(--bg-subtle)]"
              style={{ color: 'var(--text-muted)' }}
              aria-label="Close dialog"
            >
              <X size={18} />
            </button>
          </Dialog.Close>
        </div>

        <div className="px-6 py-6">{children}</div>
      </Dialog.Content>
    </Dialog.Portal>
  )
}

export function TransactionActions() {
  const queryClient = useQueryClient()
  const submitMutation = useSubmitTransaction()
  const tenantId = useAuthStore((s) => s.tenantId)

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState<ManualFormState>(MANUAL_FORM_INITIAL)
  const [createError, setCreateError] = useState<string | null>(null)
  const [createSuccess, setCreateSuccess] = useState<string | null>(null)
  const [createRecoveryBusy, setCreateRecoveryBusy] = useState(false)
  const [fraudPreview, setFraudPreview] = useState<FraudScoreResponse | null>(null)
  const [fraudPreviewError, setFraudPreviewError] = useState<string | null>(null)
  const [fraudPreviewBusy, setFraudPreviewBusy] = useState(false)

  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploadFileName, setUploadFileName] = useState('')
  const [uploadRows, setUploadRows] = useState<ParsedUploadRow[]>([])
  const [uploadBusy, setUploadBusy] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null)
  const [uploadProgress, setUploadProgress] = useState({ done: 0, total: 0 })
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const validUploadRows = uploadRows.filter((row): row is ParsedUploadRow & { request: SubmitTransactionRequest } => Boolean(row.request))
  const invalidUploadRows = uploadRows.filter((row) => row.error)

  const resetCreateDialog = () => {
    setCreateForm(MANUAL_FORM_INITIAL)
    setCreateError(null)
    setCreateSuccess(null)
    setCreateRecoveryBusy(false)
    setFraudPreview(null)
    setFraudPreviewError(null)
    setFraudPreviewBusy(false)
  }

  const resetUploadDialog = () => {
    setUploadFileName('')
    setUploadRows([])
    setUploadBusy(false)
    setUploadError(null)
    setUploadSuccess(null)
    setUploadProgress({ done: 0, total: 0 })
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  const handleCreateSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setCreateError(null)
    setCreateSuccess(null)
    setCreateRecoveryBusy(true)

    try {
      const { submitRequest } = buildCreatePayload()
      const { transaction, recovered } = await submitTransactionWithRecovery(
        submitRequest,
        (request) => submitMutation.mutateAsync(request)
      )

      if (recovered) {
        await queryClient.invalidateQueries({ queryKey: transactionKeys.lists() })
        await queryClient.invalidateQueries({ queryKey: transactionKeys.stats() })
      }

      setCreateSuccess(
        recovered
          ? `Transaction ${transaction.transactionId} was confirmed after a timeout recovery.`
          : `Transaction ${transaction.transactionId} submitted successfully.`
      )
      setCreateForm({
        ...MANUAL_FORM_INITIAL,
        currency: createForm.currency.trim().toUpperCase(),
        type: createForm.type,
      })
      setFraudPreview(null)
      setFraudPreviewError(null)
    } catch (error) {
      const requestId = getRequestIdFromSubmitError(error)
      setCreateError(
        requestId
          ? `We could not confirm the transaction before the gateway timed out. Request ID ${requestId} can be used to trace it, and it may still appear in the list shortly.`
          : getErrorMessage(error, 'Failed to submit transaction.')
      )
    } finally {
      setCreateRecoveryBusy(false)
    }
  }

  const buildCreatePayload = () => {
    const fromAccount = createForm.fromAccount.trim()
    const toAccount = createForm.toAccount.trim()
    const currency = createForm.currency.trim().toUpperCase()
    const amount = Number(createForm.amount)

    if (!fromAccount || !toAccount) {
      throw new Error('From account and to account are required.')
    }

    if (!Number.isFinite(amount) || amount <= 0) {
      throw new Error('Amount must be a positive number.')
    }

    if (!/^[A-Z]{3}$/.test(currency)) {
      throw new Error('Currency must be a 3-letter ISO code.')
    }

    let metadata: Record<string, string> | undefined
    if (createForm.metadata.trim()) {
      metadata = parseMetadata(createForm.metadata)
    }

    const submitRequest: SubmitTransactionRequest = {
      fromAccount,
      toAccount,
      amount,
      currency,
      type: createForm.type,
      ...(metadata && Object.keys(metadata).length > 0 ? { metadata } : {}),
    }

    const resolvedTenantId = tenantId?.trim()
    if (!resolvedTenantId) {
      throw new Error('Tenant ID is required to preview fraud risk.')
    }

    const fraudRequest: FraudScoreRequest = {
      transactionId: createPreviewTransactionId(),
      tenantId: resolvedTenantId,
      fromAccount,
      toAccount,
      amount,
      currency,
      transactionType: createForm.type,
      ...(metadata && Object.keys(metadata).length > 0 ? { metadata } : {}),
    }

    return { submitRequest, fraudRequest }
  }

  const handleFraudPreview = async () => {
    setFraudPreviewError(null)
    setFraudPreviewBusy(true)

    try {
      const { fraudRequest } = buildCreatePayload()
      const preview = await api.fraud.getScore(fraudRequest)
      setFraudPreview(preview)
    } catch (error) {
      setFraudPreview(null)
      setFraudPreviewError(getErrorMessage(error, 'Failed to preview fraud risk.'))
    } finally {
      setFraudPreviewBusy(false)
    }
  }

  const handleUploadChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return

    setUploadError(null)
    setUploadSuccess(null)
    setUploadBusy(false)
    setUploadProgress({ done: 0, total: 0 })

    try {
      const parsedRows = parseTransactionCsv(await file.text())
      setUploadFileName(file.name)
      setUploadRows(parsedRows)

      if (parsedRows.length === 0) {
        setUploadError('No transaction rows were found. Include a header row and at least one data row.')
        return
      }

      const invalidCount = parsedRows.filter((row) => row.error).length
      if (invalidCount > 0) {
        setUploadError(`${invalidCount} row${invalidCount === 1 ? '' : 's'} need attention and will be skipped during import.`)
      }
    } catch (error) {
      setUploadRows([])
      setUploadFileName(file.name)
      setUploadError(getErrorMessage(error, 'Failed to read the CSV file.'))
    }
  }

  const handleUploadSubmit = async () => {
    setUploadError(null)
    setUploadSuccess(null)

    if (validUploadRows.length === 0) {
      setUploadError('Add at least one valid transaction row before importing.')
      return
    }

    setUploadBusy(true)
    setUploadProgress({ done: 0, total: validUploadRows.length })

    const failures: string[] = []
    let importedCount = 0

    try {
      for (let index = 0; index < validUploadRows.length; index += 1) {
        const row = validUploadRows[index]
        try {
          await submitTransactionWithRecovery(row.request, (request) => api.transactions.submit(request))
          importedCount += 1
          setUploadProgress({ done: index + 1, total: validUploadRows.length })
        } catch (error) {
          failures.push(`Line ${row.lineNumber}: ${getErrorMessage(error, 'Submission failed')}`)
          setUploadProgress({ done: index + 1, total: validUploadRows.length })
        }
      }

      await queryClient.invalidateQueries({ queryKey: transactionKeys.lists() })
      await queryClient.invalidateQueries({ queryKey: transactionKeys.stats() })

      if (importedCount > 0) {
        setUploadSuccess(`Imported ${importedCount} transaction${importedCount === 1 ? '' : 's'} from ${uploadFileName || 'the CSV file'}.`)
      }

      if (failures.length > 0) {
        setUploadError(failures.join(' '))
      } else if (importedCount > 0) {
        setUploadError(null)
      }
    } finally {
      setUploadBusy(false)
    }
  }

  const previewRows = uploadRows.slice(0, 5)
  const previewCount = uploadRows.length

  return (
    <div className="flex flex-wrap items-center gap-2">
      <button
        type="button"
        onClick={() => {
          resetCreateDialog()
          setCreateOpen(true)
        }}
        className="btn-secondary"
      >
        <Plus size={14} />
        New Transaction
      </button>

      <button
        type="button"
        onClick={() => {
          resetUploadDialog()
          setUploadOpen(true)
        }}
        className="btn-secondary"
      >
        <Upload size={14} />
        Upload CSV
      </button>

      <Dialog.Root
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open)
          if (!open) {
            resetCreateDialog()
          }
        }}
      >
        <DialogShell
          title="Create transaction"
          description="Submit a single transaction directly into the transaction pipeline."
        >
          <form className="space-y-5" onSubmit={handleCreateSubmit}>
            {createError ? (
              <div className="flex items-start gap-2 rounded-xl border border-red-500/20 bg-red-50 px-4 py-3 text-sm text-red-700">
                <AlertCircle size={15} className="mt-0.5 flex-shrink-0" />
                <span>{createError}</span>
              </div>
            ) : null}

            {createSuccess ? (
              <div className="flex items-start gap-2 rounded-xl border border-emerald-500/20 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                <Check size={15} className="mt-0.5 flex-shrink-0" />
                <span>{createSuccess}</span>
              </div>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2">
              <Field label="From Account" required hint="10–12 digit account number">
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]{10,12}"
                  maxLength={12}
                  value={createForm.fromAccount}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, fromAccount: event.target.value }))}
                  placeholder="4820019342"
                  className="input-base"
                />
              </Field>

              <Field label="To Account" required hint="10–12 digit account number">
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]{10,12}"
                  maxLength={12}
                  value={createForm.toAccount}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, toAccount: event.target.value }))}
                  placeholder="7631004589"
                  className="input-base"
                />
              </Field>

              <Field label="Amount" required hint="Positive decimal">
                <input
                  type="number"
                  inputMode="decimal"
                  min="0"
                  step="0.01"
                  value={createForm.amount}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, amount: event.target.value }))}
                  placeholder="1000.00"
                  className="input-base"
                />
              </Field>

              <Field label="Currency" required hint="ISO 4217 code">
                <input
                  type="text"
                  maxLength={3}
                  value={createForm.currency}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, currency: event.target.value.toUpperCase() }))}
                  placeholder="USD"
                  className="input-base uppercase"
                />
              </Field>

              <Field label="Transaction Type" required hint="How the transfer should be categorized">
                <select
                  value={createForm.type}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, type: event.target.value as TransactionType }))}
                  className="input-base"
                >
                  {TRANSACTION_TYPES.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Field>
            </div>

            <Field label="Metadata" hint="Optional JSON object for downstream audit or enrichment">
              <textarea
                value={createForm.metadata}
                onChange={(event) => setCreateForm((prev) => ({ ...prev, metadata: event.target.value }))}
                placeholder='{"reference":"INV-1042","batch":"april"}'
                className="input-base min-h-[96px] resize-y font-mono text-[12px]"
              />
            </Field>

            <div className="rounded-2xl border p-4" style={{ background: 'var(--bg-subtle)', borderColor: 'var(--border)' }}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <ShieldAlert size={14} style={{ color: 'var(--color-primary)' }} />
                  <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Fraud risk preview</p>
                </div>
                {fraudPreview ? (
                  <div className="flex flex-wrap gap-2">
                    <Badge variant={fraudRiskVariant(fraudPreview.riskLevel)} size="sm">{fraudPreview.riskLevel}</Badge>
                    {fraudPreview.decision ? <Badge variant={decisionVariant(fraudPreview.decision)} size="sm">{fraudPreview.decision}</Badge> : null}
                    {fraudPreview.shouldBlock ? <Badge variant="danger" size="sm">Block</Badge> : null}
                    {fraudPreview.reviewRequired ? <Badge variant="warning" size="sm">Review</Badge> : null}
                  </div>
                ) : null}
              </div>

              {fraudPreviewBusy ? (
                <div className="mt-4 flex items-center gap-2 text-sm" style={{ color: 'var(--text-secondary)' }}>
                  <Loader2 size={14} className="animate-spin" />
                  Scoring transaction with the fraud engine…
                </div>
              ) : fraudPreviewError ? (
                <div className="mt-4 rounded-xl border border-amber-500/20 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                  {fraudPreviewError}
                </div>
              ) : fraudPreview ? (
                <div className="mt-4 grid gap-3 md:grid-cols-2">
                  <div className="rounded-xl p-4" style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}>
                    <p className="text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Fraud score</p>
                    <p className="mt-2 text-3xl font-bold" style={{ color: 'var(--text-primary)' }}>{normalizeFraudScore(fraudPreview.score).toFixed(1)}%</p>
                    <div className="mt-3 h-1.5 w-full rounded-full" style={{ background: 'var(--bg-subtle)' }}>
                      <div className="h-1.5 rounded-full transition-all" style={{ width: `${normalizeFraudScore(fraudPreview.score)}%`, background: fraudBarColor(fraudPreview.score <= 1 ? fraudPreview.score : fraudPreview.score / 100) }} />
                    </div>
                    <p className="mt-2 text-xs" style={{ color: 'var(--text-muted)' }}>
                      Transaction preview ID: <span className="font-mono">{fraudPreview.transactionId}</span>
                    </p>
                  </div>

                  <div className="rounded-xl p-4" style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}>
                    <p className="text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Engine details</p>
                    <div className="mt-2 space-y-2 text-xs" style={{ color: 'var(--text-secondary)' }}>
                      <div className="flex items-center justify-between gap-3">
                        <span>Model version</span>
                        <span className="font-mono">{fraudPreview.modelVersion ?? '—'}</span>
                      </div>
                      <div className="flex items-center justify-between gap-3">
                        <span>Processing time</span>
                        <span className="font-mono">{fraudPreview.processingTimeMs ? `${fraudPreview.processingTimeMs.toFixed(0)} ms` : '—'}</span>
                      </div>
                      <div className="flex items-center justify-between gap-3">
                        <span>Fallback mode</span>
                        <span className="font-mono">{fraudPreview.fallbackUsed ? 'Yes' : 'No'}</span>
                      </div>
                    </div>
                  </div>

                  <div className="rounded-xl p-4 md:col-span-2" style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}>
                    <p className="text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Triggered rules</p>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {fraudPreview.triggeredRules.length > 0 ? (
                        fraudPreview.triggeredRules.map((rule) => (
                          <span
                            key={rule}
                            className="rounded-full px-2.5 py-1 text-[11px]"
                            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}
                          >
                            {rule}
                          </span>
                        ))
                      ) : (
                        <span className="text-xs" style={{ color: 'var(--text-muted)' }}>No explicit rules were triggered.</span>
                      )}
                    </div>
                    <p className="mt-3 text-sm" style={{ color: 'var(--text-secondary)' }}>
                      {fraudPreview.recommendation}
                    </p>
                  </div>
                </div>
              ) : (
                <p className="mt-4 text-xs" style={{ color: 'var(--text-muted)' }}>
                  Run a fraud check before submitting to see the backend score, decision, and recommendation.
                </p>
              )}

              <div className="mt-4 flex flex-wrap justify-end gap-2">
                <button
                  type="button"
                  className={cn('btn-secondary', fraudPreviewBusy && 'cursor-not-allowed opacity-70')}
                  onClick={handleFraudPreview}
                  disabled={fraudPreviewBusy || submitMutation.isPending || createRecoveryBusy}
                >
                  {fraudPreviewBusy ? <Loader2 size={14} className="animate-spin" /> : <ShieldAlert size={14} />}
                  {fraudPreviewBusy ? 'Scoring…' : fraudPreview ? 'Re-check fraud risk' : 'Check fraud risk'}
                </button>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-end gap-2 pt-1">
              <Dialog.Close asChild>
                <button type="button" className="btn-secondary">
                  Cancel
                </button>
              </Dialog.Close>
              <button type="submit" className={cn('btn-primary', (submitMutation.isPending || createRecoveryBusy) && 'cursor-not-allowed opacity-70')} disabled={submitMutation.isPending || createRecoveryBusy}>
                {submitMutation.isPending || createRecoveryBusy ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
                {submitMutation.isPending || createRecoveryBusy ? 'Submitting…' : 'Submit Transaction'}
              </button>
            </div>
          </form>
        </DialogShell>
      </Dialog.Root>

      <Dialog.Root
        open={uploadOpen}
        onOpenChange={(open) => {
          setUploadOpen(open)
          if (!open) {
            resetUploadDialog()
          }
        }}
      >
        <DialogShell
          title="Upload transactions"
          description="Import a CSV file, review the parsed rows, and submit them through the existing transaction pipeline."
          widthClass="max-w-4xl"
        >
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2 rounded-xl border px-4 py-3 text-sm" style={{ background: 'var(--bg-subtle)', borderColor: 'var(--border)' }}>
              <div className="flex items-center gap-2" style={{ color: 'var(--text-secondary)' }}>
                <FileUp size={14} />
                <span>
                  CSV columns: <span className="font-mono">fromAccount</span>, <span className="font-mono">toAccount</span>, <span className="font-mono">amount</span>, <span className="font-mono">currency</span>, <span className="font-mono">type</span>, <span className="font-mono">metadata</span> (optional)
                </span>
              </div>
              <button type="button" className="btn-secondary ml-auto" onClick={downloadTemplate}>
                <Download size={14} />
                Download Template
              </button>
            </div>

            {uploadError ? (
              <div className="flex items-start gap-2 rounded-xl border border-amber-500/20 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                <AlertCircle size={15} className="mt-0.5 flex-shrink-0" />
                <span>{uploadError}</span>
              </div>
            ) : null}

            {uploadSuccess ? (
              <div className="flex items-start gap-2 rounded-xl border border-emerald-500/20 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                <Check size={15} className="mt-0.5 flex-shrink-0" />
                <span>{uploadSuccess}</span>
              </div>
            ) : null}

            <div className="grid gap-4 md:grid-cols-[1fr_auto] md:items-end">
              <Field label="CSV File" required hint="Pick a comma-separated file from your computer">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".csv,text/csv"
                  onChange={handleUploadChange}
                  className="input-base file:mr-4 file:rounded-lg file:border-0 file:bg-[var(--bg-subtle)] file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-[var(--text-secondary)]"
                />
              </Field>

              <button type="button" className="btn-secondary" onClick={() => fileInputRef.current?.click()}>
                <FileUp size={14} />
                Choose file
              </button>
            </div>

            {uploadFileName ? (
              <div className="flex flex-wrap items-center gap-2 text-xs" style={{ color: 'var(--text-muted)' }}>
                <span className="rounded-full px-2.5 py-1" style={{ background: 'var(--bg-subtle)' }}>
                  Selected: {uploadFileName}
                </span>
                <span className="rounded-full px-2.5 py-1" style={{ background: 'var(--bg-subtle)' }}>
                  Parsed rows: {previewCount}
                </span>
                <span className="rounded-full px-2.5 py-1" style={{ background: 'var(--bg-subtle)' }}>
                  Ready to import: {validUploadRows.length}
                </span>
              </div>
            ) : null}

            {invalidUploadRows.length > 0 ? (
              <div className="space-y-2 rounded-xl border border-amber-500/20 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                <p className="font-semibold">Validation issues</p>
                <ul className="space-y-1 text-xs">
                  {invalidUploadRows.slice(0, 5).map((row) => (
                    <li key={row.lineNumber}>
                      Line {row.lineNumber}: {row.error}
                    </li>
                  ))}
                </ul>
                {invalidUploadRows.length > 5 ? <p className="text-xs">…and {invalidUploadRows.length - 5} more</p> : null}
              </div>
            ) : null}

            {previewRows.length > 0 ? (
              <div className="overflow-hidden rounded-2xl border" style={{ borderColor: 'var(--border)' }}>
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y" style={{ borderColor: 'var(--border)' }}>
                    <thead style={{ background: 'var(--bg-subtle)' }}>
                      <tr className="text-left text-[10px] font-bold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>
                        <th className="px-4 py-3">Line</th>
                        <th className="px-4 py-3">From</th>
                        <th className="px-4 py-3">To</th>
                        <th className="px-4 py-3">Amount</th>
                        <th className="px-4 py-3">Type</th>
                        <th className="px-4 py-3">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y" style={{ borderColor: 'var(--border)' }}>
                      {previewRows.map((row) => {
                        const isValid = Boolean(row.request)
                        return (
                          <tr key={row.lineNumber} style={{ color: 'var(--text-secondary)' }}>
                            <td className="px-4 py-3 text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>{row.lineNumber}</td>
                            <td className="px-4 py-3 font-mono text-xs">{row.values.fromAccount || '—'}</td>
                            <td className="px-4 py-3 font-mono text-xs">{row.values.toAccount || '—'}</td>
                            <td className="px-4 py-3 text-xs">{row.values.amount || '—'}</td>
                            <td className="px-4 py-3 text-xs">{row.values.type || '—'}</td>
                            <td className="px-4 py-3 text-xs">
                              <span
                                className="inline-flex items-center rounded-full px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wider"
                                style={{
                                  background: isValid ? 'rgba(16,185,129,0.12)' : 'rgba(245,158,11,0.12)',
                                  color: isValid ? '#059669' : '#B45309',
                                }}
                              >
                                {isValid ? 'Ready' : 'Needs attention'}
                              </span>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : null}

            <div className="flex flex-wrap items-center justify-end gap-2 pt-1">
              <Dialog.Close asChild>
                <button type="button" className="btn-secondary" disabled={uploadBusy}>
                  Close
                </button>
              </Dialog.Close>
              <button
                type="button"
                className={cn('btn-primary', uploadBusy && 'cursor-not-allowed opacity-70')}
                disabled={uploadBusy || validUploadRows.length === 0}
                onClick={handleUploadSubmit}
              >
                {uploadBusy ? <Loader2 size={14} className="animate-spin" /> : <Upload size={14} />}
                {uploadBusy
                  ? `Importing ${uploadProgress.done}/${uploadProgress.total}`
                  : `Import ${validUploadRows.length} Transaction${validUploadRows.length === 1 ? '' : 's'}`}
              </button>
            </div>
          </div>
        </DialogShell>
      </Dialog.Root>
    </div>
  )
}
