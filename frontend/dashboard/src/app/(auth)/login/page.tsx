'use client'

import React, { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  Lock,
  Mail,
  Building2,
  Eye,
  EyeOff,
  AlertCircle,
  Loader2,
  ShieldCheck,
  Zap,
  ArrowLeft,
} from 'lucide-react'
import { api } from '@/lib/api-client'
import { useAuthStore } from '@/store/auth-store'
import { cn } from '@/lib/utils'

// ─── Field wrapper ────────────────────────────────────────────────────────────

function Field({
  id,
  label,
  icon,
  error,
  children,
}: {
  id: string
  label: string
  icon: React.ReactNode
  error?: string
  children: React.ReactNode
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
        {label}
      </label>
      <div className="relative">
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--text-muted)' }}>
          {icon}
        </span>
        {children}
      </div>
      {error && (
        <p className="mt-1.5 flex items-center gap-1 text-xs text-red-600">
          <AlertCircle size={11} className="flex-shrink-0" />
          {error}
        </p>
      )}
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

const loginSchema = z.object({
  email:    z.string().min(1, 'Email is required').email('Enter a valid email'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  tenantId: z.string().min(1, 'Tenant ID is required'),
})

type LoginFormData = z.infer<typeof loginSchema>

export default function LoginPage() {
  const router   = useRouter()
  const setAuth  = useAuthStore((s) => s.setAuth)
  const setLoading = useAuthStore((s) => s.setLoading)

  const [serverError, setServerError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '', tenantId: '' },
  })

  const onSubmit = async (data: LoginFormData) => {
    setServerError(null)
    setLoading(true)
    try {
      const response = await api.auth.login(data)
      setAuth(response)
      router.push('/overview')
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? 'Invalid credentials. Please try again.'
      setServerError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4" style={{ background: 'var(--bg-body)' }}>

      <div className="w-full max-w-[420px]">

        {/* Back to home */}
        <div className="mb-6">
          <Link href="/" className="inline-flex items-center gap-1.5 text-xs transition-colors hover:opacity-80" style={{ color: 'var(--text-muted)' }}>
            <ArrowLeft size={13} />
            Back to home
          </Link>
        </div>

        {/* Logo + Brand */}
        <div className="mb-8 flex flex-col items-center gap-4 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-600">
            <ShieldCheck size={24} className="text-white" />
          </div>
          <div>
          <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>BBSS Platform</h1>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>Blockchain Banking Security System</p>
          </div>
        </div>

        {/* Card */}
        <div className="rounded-2xl p-8 shadow-sm" style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}>
          <p className="mb-6 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Sign in to your workspace</p>

          {/* Server error */}
          {serverError && (
            <div className="mb-5 flex items-start gap-2.5 rounded-lg bg-red-50 p-3.5 text-sm text-red-700" style={{ border: '1px solid #FECACA' }}>
              <AlertCircle size={15} className="mt-0.5 flex-shrink-0" />
              <span>{serverError}</span>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            {/* Tenant ID */}
            <Field id="tenantId" label="Tenant ID" icon={<Building2 size={15} />} error={errors.tenantId?.message}>
              <input
                id="tenantId"
                type="text"
                autoComplete="organization"
                placeholder="e.g. tenant-abc123"
                className="input-base pl-9"
                {...register('tenantId')}
              />
            </Field>

            {/* Email */}
            <Field id="email" label="Email Address" icon={<Mail size={15} />} error={errors.email?.message}>
              <input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@company.com"
                className="input-base pl-9"
                {...register('email')}
              />
            </Field>

            {/* Password */}
            <Field id="password" label="Password" icon={<Lock size={15} />} error={errors.password?.message}>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="••••••••"
                className="input-base pl-9 pr-10"
                {...register('password')}
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 transition-colors hover:opacity-80"
              style={{ color: 'var(--text-muted)' }}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </Field>

            {/* Submit */}
            <button
              type="submit"
              disabled={isSubmitting}
              className={cn('btn-primary mt-2 w-full py-2.5 text-sm font-semibold', isSubmitting && 'opacity-70 cursor-not-allowed')}
            >
              {isSubmitting ? (
                <><Loader2 size={15} className="animate-spin" /> Authenticating…</>
              ) : (
                <><Lock size={15} /> Sign In Securely</>
              )}
            </button>
          </form>

          <div className="mt-6 flex items-center justify-center gap-1.5 text-xs" style={{ color: 'var(--text-muted)' }}>
            <Zap size={11} />
            <span>AES-256 encryption · Blockchain audit trail</span>
          </div>
        </div>

        <p className="mt-5 text-center text-[11px]" style={{ color: 'var(--text-muted)' }}>
          BBSS v1.0 — Enterprise Security Platform
        </p>
      </div>
    </div>
  )
}
