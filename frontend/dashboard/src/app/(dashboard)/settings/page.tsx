'use client'

import React, { useState } from 'react'
import {
  User, ShieldCheck, Key, Bell, Database,
  ChevronRight, Check, Info,
} from 'lucide-react'
import { useAuthStore } from '@/store/auth-store'

export default function SettingsPage() {
  const user     = useAuthStore((s) => s.user)
  const tenantId = useAuthStore((s) => s.tenantId)
  const [configured, setConfigured] = useState<string[]>([])

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

  return (
    <div className="animate-fade-in max-w-2xl space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Settings</h2>
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Account and system configuration</p>
      </div>

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
        <p>BBSS Dashboard v1.0.0</p>
        <p>Blockchain Banking Security System</p>
        <p>Next.js 14 · React 18 · TypeScript 5</p>
      </div>
    </div>
  )
}
