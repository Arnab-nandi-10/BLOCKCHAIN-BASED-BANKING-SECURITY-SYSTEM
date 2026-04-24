'use client'

import React, { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  User, ShieldCheck, Key, Bell, Database,
  ChevronRight, Check, Info, RefreshCw, Loader2, AlertTriangle,
  Link2, Activity, Server, Wifi, WifiOff,
} from 'lucide-react'
import { api } from '@/lib/api-client'
import { useAuthStore } from '@/store/auth-store'
import { useBlockchainServiceHealth, useFabricHealth } from '@/hooks/use-blockchain'
import { Badge } from '@/components/ui/badge'

function BlockchainHealthPanel() {
  const { data: serviceHealth, isFetching: isServiceFetching, isError: isServiceError, refetch: refetchService, dataUpdatedAt: serviceUpdatedAt } = useBlockchainServiceHealth()
  const { data: fabricHealth, isFetching: isFabricFetching, isError: isFabricError, refetch: refetchFabric, dataUpdatedAt: fabricUpdatedAt } = useFabricHealth()

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
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-xl" style={{ background: clr.bg, color: clr.text }}>
            <Activity size={16} />
          </div>
          <div>
            <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Blockchain Infrastructure</p>
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>Live blockchain-service and Hyperledger Fabric connectivity status</p>
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
            { label: 'Service Mode',   value: serviceHealth?.mode?.replace(/_/g, ' ') ?? '—', icon: <Server size={13} /> },
            { label: 'Channel',        value: fabricHealth?.channel ?? '—', icon: <Link2 size={13} /> },
            { label: 'Peer Endpoint',  value: fabricHealth?.peerEndpoint ?? '—', icon: <Database size={13} /> },
          ].map(({ label, value, icon }) => (
            <div key={label} className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
              <div className="flex items-center gap-1.5 mb-1.5" style={{ color: 'var(--text-muted)' }}>
                {icon}
                <p className="text-[9px] font-semibold uppercase tracking-wider">{label}</p>
              </div>
              <p className="font-mono text-xs truncate" style={{ color: 'var(--text-secondary)' }}>{value}</p>
            </div>
          ))}
        </div>
      ) : !isFetching ? (
        <div className="rounded-xl px-4 py-3 text-sm text-center" style={{ color: 'var(--text-muted)', background: 'var(--bg-subtle)' }}>
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
            {fabricHealth?.effectiveHealth === 'UP' || serviceHealth?.status?.toUpperCase() === 'UP' ? <Wifi size={9} /> : <WifiOff size={9} />}
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
    </div>
  )
}

export default function SettingsPage() {
  const user     = useAuthStore((s) => s.user)
  const tenantId = useAuthStore((s) => s.tenantId)
  const syncUserProfile = useAuthStore((s) => s.syncUserProfile)
  const [configured, setConfigured] = useState<string[]>([])

  const {
    data: serverProfile,
    isFetching,
    isError,
    error,
    refetch,
    dataUpdatedAt,
  } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => api.auth.getMe(),
    enabled: Boolean(user),
    staleTime: 0,
    refetchOnWindowFocus: false,
    retry: 1,
  })

  useEffect(() => {
    if (serverProfile) {
      syncUserProfile(serverProfile)
    }
  }, [serverProfile, syncUserProfile])

  const handleConfigure = (label: string) => {
    setConfigured((prev) =>
      prev.includes(label) ? prev : [...prev, label]
    )
  }

  const profileFields = [
    { label: 'Full Name',  value: user ? `${user.firstName} ${user.lastName}` : '—' },
    { label: 'Email',      value: user?.email ?? '—' },
    { label: 'User ID',    value: user?.id ?? '—' },
    { label: 'Tenant ID',  value: tenantId ?? '—' },
    { label: 'Roles',      value: user?.roles.map((r) => r.replace('ROLE_', '')).join(', ') ?? '—' },
  ]

  const securityItems = [
    {
      icon:  <Key size={16} />,
      label: 'Change Password',
      desc:  'Update your authentication credentials',
      color: '#818cf8',
      bg:    'rgba(99,102,241,0.1)',
    },
    {
      icon:  <Bell size={16} />,
      label: 'Notifications',
      desc:  'Configure fraud alert delivery preferences',
      color: '#fbbf24',
      bg:    'rgba(245,158,11,0.1)',
    },
    {
      icon:  <Database size={16} />,
      label: 'Audit Log Access',
      desc:  'Review your own activity log',
      color: '#34d399',
      bg:    'rgba(16,185,129,0.1)',
    },
  ]

  const sessionFields = [
    { label: 'Server Name', value: serverProfile ? `${serverProfile.firstName} ${serverProfile.lastName}` : '—' },
    { label: 'Server Email', value: serverProfile?.email ?? '—' },
    { label: 'Roles', value: serverProfile?.roles.map((r) => r.replace('ROLE_', '')).join(', ') ?? '—' },
    { label: 'Last Sync', value: dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleString() : '—' },
  ]

  return (
    <div className="animate-fade-in max-w-2xl space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Settings</h2>
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Account and system configuration</p>
      </div>

      {/* Session sync */}
      <section
        className="rounded-2xl p-5"
        style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded-xl" style={{ background: 'rgba(59,130,246,0.1)', color: '#60a5fa' }}>
              <ShieldCheck size={16} />
            </div>
            <div>
              <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Session Verification</p>
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>Refreshes the displayed profile from the backend /me endpoint</p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => refetch()}
            disabled={isFetching}
            className="btn-secondary text-xs font-medium"
          >
            {isFetching ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
            {isFetching ? 'Syncing…' : 'Sync from backend'}
          </button>
        </div>

        {isError ? (
          <div className="mb-4 flex items-start gap-2 rounded-xl border border-amber-500/20 bg-amber-50 px-4 py-3 text-sm text-amber-700">
            <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
            <span>{(error as Error)?.message ?? 'Unable to verify the session right now.'}</span>
          </div>
        ) : null}

        <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
          {sessionFields.map(({ label, value }) => (
            <div key={label} className="rounded-xl px-4 py-3" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
              <p className="text-[9px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>{label}</p>
              <p className="mt-1 truncate font-mono text-sm" style={{ color: 'var(--text-secondary)' }}>{value}</p>
            </div>
          ))}
        </div>

        <div className="mt-4 flex items-center gap-2 text-xs" style={{ color: 'var(--text-muted)' }}>
          <Check size={12} style={{ color: serverProfile ? '#34d399' : 'var(--text-muted)' }} />
          <span>{serverProfile ? 'Backend session is verified and synced.' : 'Waiting for backend profile verification.'}</span>
        </div>
      </section>

      {/* Profile card */}
      <section
        className="rounded-2xl p-5"
        style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
      >
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-xl" style={{ background: 'rgba(99,102,241,0.1)', color: '#818cf8' }}>
            <User size={16} />
          </div>
          <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Profile Information</p>
        </div>

        <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
          {profileFields.map(({ label, value }) => (
            <div key={label} className="rounded-xl px-4 py-3"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}>
              <p className="text-[9px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>{label}</p>
              <p className="mt-1 truncate font-mono text-sm" style={{ color: 'var(--text-secondary)' }}>{value}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Security settings */}
      <section
        className="rounded-2xl p-5"
        style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
      >
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-xl" style={{ background: 'rgba(16,185,129,0.1)', color: '#34d399' }}>
            <ShieldCheck size={16} />
          </div>
          <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Security</p>
        </div>

        <div className="space-y-2">
          {securityItems.map(({ icon, label, desc, color, bg }) => {
            const done = configured.includes(label)
            return (
              <div key={label}
                className="flex items-center justify-between rounded-xl px-4 py-3 transition-colors"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border)' }}
              >
                <div className="flex items-center gap-3">
                  <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-xl" style={{ background: bg, color }}>
                    {icon}
                  </div>
                  <div>
                    <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{label}</p>
                    <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{desc}</p>
                  </div>
                </div>
                <button
                  onClick={() => handleConfigure(label)}
                  className="flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-medium transition-all"
                  style={done
                    ? { background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.2)', color: '#34d399' }
                    : { background: 'var(--bg-subtle)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}
                >
                  {done ? <><Check size={11} /> Done</> : <>Configure <ChevronRight size={11} /></>}
                </button>
              </div>
            )
          })}
        </div>
      </section>

      {/* Blockchain Status */}
      <section
        className="rounded-2xl p-5"
        style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
      >
        <BlockchainHealthPanel />
      </section>

      {/* Info notice */}
      <div className="flex items-start gap-3 rounded-2xl px-4 py-3.5"
        style={{ background: 'rgba(99,102,241,0.05)', border: '1px solid rgba(99,102,241,0.15)' }}>
        <Info size={15} className="mt-0.5 flex-shrink-0 text-primary-400" />
        <p className="text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
          Full configuration workflows are managed via the backend admin API.
          These controls trigger the corresponding service endpoints when the backend is running.
        </p>
      </div>

      {/* Version */}
      <div className="space-y-0.5 text-[11px]" style={{ color: 'var(--text-muted)' }}>
        <p>Civic Savings Dashboard v1.0.0</p>
        <p>Blockchain Banking Security System</p>
        <p>Next.js 14 · React 18 · TypeScript 5</p>
      </div>
    </div>
  )
}
