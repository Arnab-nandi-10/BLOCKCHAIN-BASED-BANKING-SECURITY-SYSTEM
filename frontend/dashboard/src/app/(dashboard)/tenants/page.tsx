'use client'

import React, { useState } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import * as Select from '@radix-ui/react-select'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  Building2, Plus, Eye, EyeOff, RefreshCw, CheckCircle2,
  Ban, Trash2, X, ChevronDown, ChevronRight, Check, ShieldX, Loader2,
  AlertTriangle, Copy,
} from 'lucide-react'
import { api } from '@/lib/api-client'
import { useAuthStore, selectIsSuperAdmin } from '@/store/auth-store'
import { Badge } from '@/components/ui/badge'
import { formatDate, truncate, formatNumber } from '@/lib/utils'
import type { Tenant, TenantStatus, TenantPlan, TenantListParams, CreateTenantRequest } from '@/types'

const tenantKeys = {
  all:  ['tenants'] as const,
  list: (p: TenantListParams) => ['tenants', 'list', p] as const,
}

type BadgeVariant = 'success' | 'danger' | 'warning' | 'info' | 'default' | 'purple' | 'gray'

function statusVariant(status: TenantStatus): BadgeVariant {
  switch (status) {
    case 'ACTIVE':    return 'success'
    case 'SUSPENDED': return 'warning'
    case 'PENDING':   return 'info'
    case 'DELETED':   return 'danger'
    default:          return 'default'
  }
}

function planVariant(plan: TenantPlan): BadgeVariant {
  switch (plan) {
    case 'STARTER':      return 'gray'
    case 'PROFESSIONAL': return 'info'
    case 'ENTERPRISE':   return 'purple'
    default:             return 'default'
  }
}

const createTenantSchema = z.object({
  name:       z.string().min(2, 'Name must be at least 2 characters'),
  adminEmail: z.string().email('Enter a valid email'),
  plan:       z.enum(['STARTER', 'PROFESSIONAL', 'ENTERPRISE']),
  webhookUrl: z.string().trim().url('Enter a valid webhook URL').or(z.literal('')),
})

type CreateTenantForm = z.infer<typeof createTenantSchema>

const panelStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
}

const dialogStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  backdropFilter: 'blur(32px)',
}

// ─── Create Dialog ─────────────────────────────────────────────────────────────

function CreateTenantDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)

  const { register, handleSubmit, reset, setValue, watch, formState: { errors, isSubmitting } } =
    useForm<CreateTenantForm>({
      resolver: zodResolver(createTenantSchema),
      defaultValues: { name: '', adminEmail: '', plan: 'STARTER', webhookUrl: '' },
    })

  const selectedPlan = watch('plan')

  const createMutation = useMutation({
    mutationFn: (req: CreateTenantRequest) => api.tenants.create(req),
    onSuccess: () => { qc.invalidateQueries({ queryKey: tenantKeys.all }); reset(); onClose() },
    onError: (err: unknown) => {
      setServerError((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to create tenant.')
    },
  })

  return (
    <Dialog.Root open={open} onOpenChange={(v) => !v && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/75 backdrop-blur-md" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl shadow-2xl outline-none animate-slide-up" style={dialogStyle}>
          <div className="flex items-center justify-between px-6 py-5" style={{ borderBottom: '1px solid var(--border)' }}>
            <Dialog.Title className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>Create New Tenant</Dialog.Title>
            <Dialog.Close asChild>
              <button className="rounded-lg p-1.5 transition-colors hover:bg-[var(--bg-subtle)]" style={{ color: 'var(--text-muted)' }}><X size={16} /></button>
            </Dialog.Close>
          </div>

          <form onSubmit={handleSubmit((d) => {
            setServerError(null)
            const webhookUrl = d.webhookUrl.trim()
            createMutation.mutate({
              name: d.name,
              adminEmail: d.adminEmail,
              plan: d.plan,
              ...(webhookUrl ? { webhookUrl } : {}),
            })
          })} className="px-6 py-5 space-y-4">
            {serverError && (
              <div className="flex items-center gap-2 rounded-xl px-3 py-2.5 text-sm" style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#fca5a5' }}>
                <AlertTriangle size={13} /> {serverError}
              </div>
            )}

            {[
              { id: 'name',       label: 'Tenant Name',  type: 'text',  placeholder: 'Acme Bank',             error: errors.name?.message },
              { id: 'adminEmail', label: 'Admin Email',  type: 'email', placeholder: 'admin@acmebank.com',    error: errors.adminEmail?.message },
            ].map(({ id, label, type, placeholder, error }) => (
              <div key={id}>
                <label className="mb-1.5 block text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>{label}</label>
                <input type={type} placeholder={placeholder} className="input-base" {...register(id as 'name' | 'adminEmail')} />
                {error && <p className="mt-1 text-xs text-red-400">{error}</p>}
              </div>
            ))}

            <div>
              <label className="mb-1.5 block text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Webhook URL</label>
              <input
                type="url"
                placeholder="https://example.com/webhooks/bbss"
                className="input-base"
                {...register('webhookUrl')}
              />
              {errors.webhookUrl && <p className="mt-1 text-xs text-red-400">{errors.webhookUrl.message}</p>}
            </div>

            <div>
              <label className="mb-1.5 block text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Plan</label>
              <Select.Root value={selectedPlan} onValueChange={(v) => setValue('plan', v as TenantPlan)}>
                <Select.Trigger className="flex w-full items-center justify-between gap-2 rounded-xl px-3 py-2.5 text-sm outline-none"
                  style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
                  <Select.Value />
                  <ChevronDown size={13} style={{ color: 'var(--text-muted)' }} />
                </Select.Trigger>
                <Select.Portal>
                  <Select.Content className="z-[60] overflow-hidden rounded-2xl shadow-2xl" style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', backdropFilter: 'blur(24px)' }}>
                    <Select.Viewport className="p-1.5">
                      {(['STARTER', 'PROFESSIONAL', 'ENTERPRISE'] as TenantPlan[]).map((p) => (
                        <Select.Item key={p} value={p}
                          className="flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm outline-none data-[highlighted]:bg-[var(--bg-subtle)]"
                          style={{ color: 'var(--text-secondary)' }}>
                          <Select.ItemIndicator><Check size={11} className="text-primary-400" /></Select.ItemIndicator>
                          <Select.ItemText><Badge variant={planVariant(p)} size="sm">{p}</Badge></Select.ItemText>
                        </Select.Item>
                      ))}
                    </Select.Viewport>
                  </Select.Content>
                </Select.Portal>
              </Select.Root>
            </div>

            <div className="flex justify-end gap-3 pt-1">
              <Dialog.Close asChild>
                <button type="button" className="btn-secondary px-4 py-2 text-sm">Cancel</button>
              </Dialog.Close>
              <button type="submit" disabled={isSubmitting || createMutation.isPending}
                className="btn-primary px-4 py-2 text-sm font-semibold disabled:opacity-60 disabled:cursor-not-allowed">
                {(isSubmitting || createMutation.isPending) && <Loader2 size={13} className="animate-spin" />}
                Create Tenant
              </button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function parseTenantConfigJson(raw: string): Record<string, string> {
  const trimmed = raw.trim()
  if (!trimmed) return {}

  const parsed = JSON.parse(trimmed)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Configuration must be a JSON object.')
  }

  return Object.fromEntries(
    Object.entries(parsed as Record<string, unknown>).map(([key, value]) => [
      key,
      typeof value === 'string' ? value : JSON.stringify(value),
    ])
  )
}

function TenantConfigDialog({
  tenant,
  open,
  onClose,
}: {
  tenant: Tenant | null
  open: boolean
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [configText, setConfigText] = useState('{}')
  const [serverError, setServerError] = useState<string | null>(null)

  const updateMutation = useMutation({
    mutationFn: (config: Record<string, string>) => {
      if (!tenant) throw new Error('No tenant selected')
      return api.tenants.updateConfig(tenant.tenantId, config)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: tenantKeys.all })
      onClose()
    },
    onError: (err: unknown) => {
      setServerError((err as { response?: { data?: { message?: string } }})?.response?.data?.message ?? 'Failed to update tenant configuration.')
    },
  })

  React.useEffect(() => {
    if (!open || !tenant) return
    setConfigText(JSON.stringify(tenant.config ?? {}, null, 2))
    setServerError(null)
  }, [open, tenant])

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!tenant) return

    try {
      const parsed = parseTenantConfigJson(configText)
      setServerError(null)
      updateMutation.mutate(parsed)
    } catch (error) {
      setServerError((error as Error).message ?? 'Configuration must be valid JSON.')
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={(v) => !v && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/75 backdrop-blur-md" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-full max-w-2xl -translate-x-1/2 -translate-y-1/2 rounded-2xl shadow-2xl outline-none animate-slide-up" style={dialogStyle}>
          <div className="flex items-center justify-between px-6 py-5" style={{ borderBottom: '1px solid var(--border)' }}>
            <div>
              <Dialog.Title className="text-base font-semibold" style={{ color: 'var(--text-primary)' }}>Tenant Configuration</Dialog.Title>
              <Dialog.Description className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
                Update the backend config map for this tenant.
              </Dialog.Description>
            </div>
            <Dialog.Close asChild>
              <button className="rounded-lg p-1.5 transition-colors hover:bg-[var(--bg-subtle)]" style={{ color: 'var(--text-muted)' }}><X size={16} /></button>
            </Dialog.Close>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5 px-6 py-5">
            {tenant && (
              <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                <div className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                  <p className="text-[9px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Monthly limit</p>
                  <p className="mt-1 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{formatNumber(tenant.monthlyTransactionLimit)}</p>
                </div>
                <div className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                  <p className="text-[9px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Max users</p>
                  <p className="mt-1 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{formatNumber(tenant.maxUsers)}</p>
                </div>
                <div className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
                  <p className="text-[9px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Webhook URL</p>
                  <p className="mt-1 truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{tenant.webhookUrl ?? '—'}</p>
                </div>
              </div>
            )}

            {serverError && (
              <div className="flex items-center gap-2 rounded-xl px-3 py-2.5 text-sm" style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#fca5a5' }}>
                <AlertTriangle size={13} /> {serverError}
              </div>
            )}

            <div>
              <label className="mb-1.5 block text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Config JSON</label>
              <textarea
                value={configText}
                onChange={(event) => setConfigText(event.target.value)}
                className="input-base min-h-[240px] font-mono text-[12px]"
                placeholder='{"fraudLimit":"7500","dailyProcessingWindow":"04:00-16:00"}'
              />
              <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
                Values are stored as strings; nested objects will be stringified.
              </p>
            </div>

            <div className="flex justify-end gap-3 pt-1">
              <Dialog.Close asChild>
                <button type="button" className="btn-secondary px-4 py-2 text-sm" disabled={updateMutation.isPending}>Cancel</button>
              </Dialog.Close>
              <button type="submit" disabled={updateMutation.isPending}
                className="btn-primary px-4 py-2 text-sm font-semibold disabled:opacity-60 disabled:cursor-not-allowed">
                {updateMutation.isPending && <Loader2 size={13} className="animate-spin" />}
                Save Configuration
              </button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

// ─── Tenant Card ───────────────────────────────────────────────────────────────

function TenantCard({ tenant, onConfigure }: { tenant: Tenant; onConfigure: () => void }) {
  const qc = useQueryClient()
  const [showApiKey, setShowApiKey] = useState(false)
  const [copied,     setCopied]     = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const opts = (key: string) => ({
    onSuccess: () => { qc.invalidateQueries({ queryKey: tenantKeys.all }); setActionError(null) },
    onError:   (err: unknown) => setActionError((err as { response?: { data?: { message?: string } }})?.response?.data?.message ?? `Failed to ${key}.`),
  })

  const activateMut = useMutation({ mutationFn: () => api.tenants.activate(tenant.tenantId),        ...opts('activate') })
  const suspendMut  = useMutation({ mutationFn: () => api.tenants.suspend(tenant.tenantId),          ...opts('suspend') })
  const deleteMut   = useMutation({ mutationFn: () => api.tenants.delete(tenant.tenantId),           ...opts('delete') })
  const regenMut    = useMutation({ mutationFn: () => api.tenants.regenerateApiKey(tenant.tenantId), ...opts('regenerate API key') })

  const isBusy = activateMut.isPending || suspendMut.isPending || deleteMut.isPending || regenMut.isPending

  const handleCopy = () =>
    navigator.clipboard.writeText(tenant.apiKey).then(() => { setCopied(true); setTimeout(() => setCopied(false), 2000) })

  return (
    <div className="rounded-2xl p-5 space-y-4 transition-all duration-200"
      style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
      onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.borderColor = 'rgba(99,102,241,0.2)' }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.borderColor = 'var(--border)' }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl" style={{ background: 'rgba(99,102,241,0.1)', color: '#818cf8' }}>
            <Building2 size={18} />
          </div>
          <div>
            <h3 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{tenant.name}</h3>
            <p className="font-mono text-[11px]" style={{ color: 'var(--text-muted)' }}>{tenant.tenantId}</p>
          </div>
        </div>
        <div className="flex items-center gap-1.5 flex-shrink-0">
          <Badge variant={planVariant(tenant.plan)} size="sm">{tenant.plan}</Badge>
          <Badge variant={statusVariant(tenant.status)} size="sm">{tenant.status}</Badge>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 text-xs">
        <div>
          <p style={{ color: 'var(--text-muted)' }}>Admin Email</p>
          <p className="truncate mt-0.5" style={{ color: 'var(--text-secondary)' }}>{tenant.adminEmail}</p>
        </div>
        <div>
          <p style={{ color: 'var(--text-muted)' }}>Created</p>
          <p className="mt-0.5" style={{ color: 'var(--text-secondary)' }}>{formatDate(tenant.createdAt, 'MMM dd, yyyy')}</p>
        </div>
        <div>
          <p style={{ color: 'var(--text-muted)' }}>Webhook URL</p>
          <p className="truncate mt-0.5" style={{ color: 'var(--text-secondary)' }}>{tenant.webhookUrl ?? '—'}</p>
        </div>
        <div>
          <p style={{ color: 'var(--text-muted)' }}>Plan Limits</p>
          <p className="mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            {formatNumber(tenant.monthlyTransactionLimit)} tx / {formatNumber(tenant.maxUsers)} users
          </p>
        </div>
      </div>

      <div className="rounded-xl p-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
        <div className="flex items-center justify-between mb-1.5">
          <p className="text-[9px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>API Key</p>
          <div className="flex items-center gap-1">
            <button onClick={handleCopy} className="rounded-lg p-1 transition-colors hover:bg-[var(--bg-subtle)]" style={{ color: 'var(--text-muted)' }} title="Copy API key">
              <Copy size={11} />
            </button>
            <button onClick={() => setShowApiKey((v) => !v)} className="rounded-lg p-1 transition-colors hover:bg-[var(--bg-subtle)]" style={{ color: 'var(--text-muted)' }}>
              {showApiKey ? <EyeOff size={11} /> : <Eye size={11} />}
            </button>
          </div>
        </div>
        <p className="font-mono text-[11px]" style={{ color: 'var(--text-secondary)' }}>
          {showApiKey ? tenant.apiKey : `${tenant.apiKey.slice(0, 8)}${'•'.repeat(16)}${tenant.apiKey.slice(-4)}`}
          {copied && <span className="ml-2" style={{ color: '#34d399' }}>Copied!</span>}
        </p>
      </div>

      {actionError && (
        <p className="flex items-center gap-1.5 text-xs" style={{ color: '#f87171' }}>
          <AlertTriangle size={11} />{actionError}
        </p>
      )}

      <div className="flex flex-wrap gap-1.5">
        {tenant.status !== 'ACTIVE' && tenant.status !== 'DELETED' && (
          <button onClick={() => activateMut.mutate()} disabled={isBusy}
            className="flex items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-[11px] transition-colors disabled:opacity-50"
            style={{ background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.2)', color: '#34d399' }}>
            {activateMut.isPending ? <Loader2 size={10} className="animate-spin" /> : <CheckCircle2 size={10} />} Activate
          </button>
        )}
        {tenant.status === 'ACTIVE' && (
          <button onClick={() => suspendMut.mutate()} disabled={isBusy}
            className="flex items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-[11px] transition-colors disabled:opacity-50"
            style={{ background: 'rgba(245,158,11,0.08)', border: '1px solid rgba(245,158,11,0.2)', color: '#fbbf24' }}>
            {suspendMut.isPending ? <Loader2 size={10} className="animate-spin" /> : <Ban size={10} />} Suspend
          </button>
        )}
        <button onClick={() => regenMut.mutate()} disabled={isBusy}
          className="flex items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-[11px] transition-colors disabled:opacity-50"
          style={{ background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.2)', color: '#a5b4fc' }}>
          {regenMut.isPending ? <Loader2 size={10} className="animate-spin" /> : <RefreshCw size={10} />} Regen Key
        </button>
        <button onClick={onConfigure} disabled={isBusy}
          className="flex items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-[11px] transition-colors disabled:opacity-50"
          style={{ background: 'rgba(14,165,233,0.08)', border: '1px solid rgba(14,165,233,0.2)', color: '#67e8f9' }}>
          <ChevronRight size={10} /> Config
        </button>
        {tenant.status !== 'DELETED' && (
          <button onClick={() => { if (window.confirm(`Delete "${tenant.name}"? This cannot be undone.`)) deleteMut.mutate() }}
            disabled={isBusy}
            className="flex items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-[11px] transition-colors disabled:opacity-50"
            style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#f87171' }}>
            {deleteMut.isPending ? <Loader2 size={10} className="animate-spin" /> : <Trash2 size={10} />} Delete
          </button>
        )}
      </div>
    </div>
  )
}

// ─── Page ──────────────────────────────────────────────────────────────────────

export default function TenantsPage() {
  const isSuperAdmin = useAuthStore(selectIsSuperAdmin)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [configTenant, setConfigTenant] = useState<Tenant | null>(null)
  const [page,         setPage]         = useState(0)
  const [statusFilter, setStatusFilter] = useState('ALL')

  const params: TenantListParams = { page, size: 12, ...(statusFilter !== 'ALL' && { status: statusFilter }) }

  const { data, isLoading } = useQuery({
    queryKey: tenantKeys.list(params),
    queryFn:  () => api.tenants.list(params),
    enabled:  isSuperAdmin,
    placeholderData: (prev) => prev,
  })

  const tenants       = data?.content     ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages    = data?.totalPages   ?? 0

  if (!isSuperAdmin) {
    return (
      <div className="flex h-96 flex-col items-center justify-center gap-4 animate-fade-in">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl" style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.2)' }}>
          <ShieldX size={30} style={{ color: '#f87171' }} />
        </div>
        <div className="text-center">
          <h2 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>Access Denied</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
            This page requires the{' '}
            <span className="rounded-full px-2 py-0.5 font-mono text-xs" style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#f87171' }}>
              ROLE_SUPER_ADMIN
            </span>{' '}
            role.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="animate-fade-in space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Tenants</h2>
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>{formatNumber(totalElements)} registered tenant{totalElements !== 1 ? 's' : ''}</p>
        </div>
        <button onClick={() => setCreateDialogOpen(true)} className="btn-primary text-sm font-semibold">
          <Plus size={14} /> New Tenant
        </button>
      </div>

      <div className="flex flex-wrap gap-3 rounded-2xl p-4" style={panelStyle}>
        <Select.Root value={statusFilter} onValueChange={(v) => { setStatusFilter(v); setPage(0) }}>
          <Select.Trigger className="flex min-w-44 items-center justify-between gap-2 rounded-xl px-3 py-2.5 text-sm outline-none"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
            <Select.Value placeholder="All Statuses" />
            <ChevronDown size={13} style={{ color: 'var(--text-muted)' }} />
          </Select.Trigger>
          <Select.Portal>
            <Select.Content className="z-50 overflow-hidden rounded-2xl shadow-2xl"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', backdropFilter: 'blur(24px)' }}>
              <Select.Viewport className="p-1.5">
                {[
                  { label: 'All Statuses', value: 'ALL' },
                  { label: 'Active',       value: 'ACTIVE' },
                  { label: 'Suspended',    value: 'SUSPENDED' },
                  { label: 'Pending',      value: 'PENDING' },
                  { label: 'Deleted',      value: 'DELETED' },
                ].map((opt) => (
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
        {statusFilter !== 'ALL' && (
          <button onClick={() => { setStatusFilter('ALL'); setPage(0) }}
            className="flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm transition-colors hover:bg-[var(--bg-subtle)]"
            style={{ color: 'var(--text-muted)' }}>
            <X size={13} /> Reset
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="rounded-2xl p-5 space-y-4" style={panelStyle}>
              <div className="flex items-center gap-3">
                <div className="skeleton h-10 w-10 rounded-xl" />
                <div className="flex-1 space-y-1.5">
                  <div className="skeleton h-4 w-32 rounded-lg" />
                  <div className="skeleton h-3 w-24 rounded" />
                </div>
              </div>
              <div className="skeleton h-16 rounded-xl" />
              <div className="skeleton h-10 rounded-xl" />
              <div className="flex gap-1.5">
                <div className="skeleton h-7 w-20 rounded-xl" />
                <div className="skeleton h-7 w-20 rounded-xl" />
              </div>
            </div>
          ))}
        </div>
      ) : tenants.length === 0 ? (
        <div className="flex h-64 flex-col items-center justify-center gap-3 rounded-2xl" style={panelStyle}>
          <Building2 size={28} style={{ color: 'var(--text-muted)' }} />
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>No tenants found.</p>
          <button onClick={() => setCreateDialogOpen(true)}
            className="flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-sm"
            style={{ background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.2)', color: '#a5b4fc' }}>
            <Plus size={13} /> Create your first tenant
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
          {tenants.map((tenant) => (
            <TenantCard
              key={tenant.tenantId}
              tenant={tenant}
              onConfigure={() => setConfigTenant(tenant)}
            />
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
            className="rounded-xl px-3 py-1.5 text-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
            Previous
          </button>
          <span className="text-sm" style={{ color: 'var(--text-muted)' }}>Page {page + 1} / {totalPages}</span>
          <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
            className="rounded-xl px-3 py-1.5 text-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
            Next
          </button>
        </div>
      )}

      <CreateTenantDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />
      <TenantConfigDialog
        tenant={configTenant}
        open={Boolean(configTenant)}
        onClose={() => setConfigTenant(null)}
      />
    </div>
  )
}
