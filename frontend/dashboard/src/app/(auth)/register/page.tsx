'use client'

import React, { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  ArrowLeft,
  AlertCircle,
  Building2,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  Mail,
  ShieldCheck,
  User,
  Zap,
} from 'lucide-react'
import { api } from '@/lib/api-client'
import { useAuthStore } from '@/store/auth-store'
import { cn } from '@/lib/utils'

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
      {error ? (
        <p className="mt-1.5 flex items-center gap-1 text-xs text-red-600">
          <AlertCircle size={11} className="flex-shrink-0" />
          {error}
        </p>
      ) : null}
    </div>
  )
}

const registerSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
  tenantId: z.string().min(1, 'Tenant ID is required'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  confirmPassword: z.string().min(8, 'Please confirm your password'),
}).refine((data) => data.password === data.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Passwords do not match',
})

type RegisterFormData = z.infer<typeof registerSchema>

export default function RegisterPage() {
  const router = useRouter()
  const setAuth = useAuthStore((s) => s.setAuth)
  const setLoading = useAuthStore((s) => s.setLoading)

  const [serverError, setServerError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      firstName: '',
      lastName: '',
      email: '',
      tenantId: '',
      password: '',
      confirmPassword: '',
    },
  })

  const onSubmit = async (data: RegisterFormData) => {
    setServerError(null)
    setLoading(true)

    try {
      const response = await api.auth.register({
        firstName: data.firstName,
        lastName: data.lastName,
        email: data.email,
        tenantId: data.tenantId,
        password: data.password,
      })

      setAuth(response)
      router.push('/overview')
    } catch (error: unknown) {
      const message =
        (error as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Unable to create account. Please try again.'
      setServerError(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4" style={{ background: 'var(--bg-body)' }}>
      <div className="w-full max-w-[480px]">
        <div className="mb-6">
          <Link href="/" className="inline-flex items-center gap-1.5 text-xs transition-colors hover:opacity-80" style={{ color: 'var(--text-muted)' }}>
            <ArrowLeft size={13} />
            Back to home
          </Link>
        </div>

        <div className="mb-8 flex flex-col items-center gap-4 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-600">
            <ShieldCheck size={24} className="text-white" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>Create your Civic Savings account</h1>
            <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>Register a new admin workspace and start managing secure banking flows.</p>
          </div>
        </div>

        <div className="rounded-2xl p-8 shadow-sm" style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}>
          <p className="mb-6 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Create your workspace account</p>

          {serverError ? (
            <div className="mb-5 flex items-start gap-2.5 rounded-lg bg-red-50 p-3.5 text-sm text-red-700" style={{ border: '1px solid #FECACA' }}>
              <AlertCircle size={15} className="mt-0.5 flex-shrink-0" />
              <span>{serverError}</span>
            </div>
          ) : null}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field id="firstName" label="First Name" icon={<User size={15} />} error={errors.firstName?.message}>
                <input
                  id="firstName"
                  type="text"
                  autoComplete="given-name"
                  placeholder="Ava"
                  className="input-base pl-9"
                  {...register('firstName')}
                />
              </Field>

              <Field id="lastName" label="Last Name" icon={<User size={15} />} error={errors.lastName?.message}>
                <input
                  id="lastName"
                  type="text"
                  autoComplete="family-name"
                  placeholder="Patel"
                  className="input-base pl-9"
                  {...register('lastName')}
                />
              </Field>
            </div>

            <Field id="tenantId" label="Tenant ID" icon={<Building2 size={15} />} error={errors.tenantId?.message}>
              <input
                id="tenantId"
                type="text"
                autoComplete="organization"
                placeholder="e.g. bank-a"
                className="input-base pl-9"
                {...register('tenantId')}
              />
            </Field>

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

            <Field id="password" label="Password" icon={<Lock size={15} />} error={errors.password?.message}>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="At least 8 characters"
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

            <Field id="confirmPassword" label="Confirm Password" icon={<Lock size={15} />} error={errors.confirmPassword?.message}>
              <input
                id="confirmPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Repeat password"
                className="input-base pl-9 pr-10"
                {...register('confirmPassword')}
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword((v) => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 transition-colors hover:opacity-80"
                style={{ color: 'var(--text-muted)' }}
                aria-label={showConfirmPassword ? 'Hide confirmation password' : 'Show confirmation password'}
              >
                {showConfirmPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </Field>

            <button
              type="submit"
              disabled={isSubmitting}
              className={cn('btn-primary mt-2 w-full py-2.5 text-sm font-semibold', isSubmitting && 'cursor-not-allowed opacity-70')}
            >
              {isSubmitting ? (
                <><Loader2 size={15} className="animate-spin" /> Creating account…</>
              ) : (
                <><ShieldCheck size={15} /> Create Secure Account</>
              )}
            </button>
          </form>

          <div className="mt-6 flex items-center justify-center gap-1.5 text-xs" style={{ color: 'var(--text-muted)' }}>
            <Zap size={11} />
            <span>AES-256 encryption · Blockchain audit trail</span>
          </div>
        </div>

        <p className="mt-5 text-center text-[11px]" style={{ color: 'var(--text-muted)' }}>
          Already have an account?{' '}
          <Link href="/login" className="font-medium transition-colors hover:opacity-80" style={{ color: 'var(--color-primary)' }}>
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
