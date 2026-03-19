'use client'

import React, { useEffect, useRef } from 'react'
import Link from 'next/link'
import {
  ShieldCheck, Blocks, AlertTriangle, BarChart3,
  ArrowRight, Lock, Zap, Eye, ChevronRight,
} from 'lucide-react'
import { ThemeToggle } from '@/components/ui/theme-toggle'

// ─── Animate-on-scroll wrapper ────────────────────────────────────────────────

function FadeIn({
  children,
  delay = 0,
  className = '',
}: {
  children: React.ReactNode
  delay?: number
  className?: string
}) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const obs = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          el.style.opacity = '1'
          el.style.transform = 'translateY(0)'
          obs.disconnect()
        }
      },
      { threshold: 0.12 }
    )
    obs.observe(el)
    return () => obs.disconnect()
  }, [])
  return (
    <div
      ref={ref}
      className={className}
      style={{
        opacity: 0,
        transform: 'translateY(22px)',
        transition: `opacity 0.7s ease ${delay}ms, transform 0.7s cubic-bezier(0.16,1,0.3,1) ${delay}ms`,
      }}
    >
      {children}
    </div>
  )
}

// ─── Feature cards ────────────────────────────────────────────────────────────

const features = [
  {
    icon: Blocks,
    title: 'Immutable Blockchain Ledger',
    desc: 'Every transaction is anchored to Hyperledger Fabric, creating a tamper-proof audit trail that stands up to any scrutiny.',
  },
  {
    icon: AlertTriangle,
    title: 'Real-time Fraud Detection',
    desc: 'ML-powered risk scoring on every transaction with sub-50ms latency and configurable alert thresholds.',
  },
  {
    icon: BarChart3,
    title: 'Live Analytics Dashboard',
    desc: 'Monitor transaction volume, fraud trends, and tenant health in real time with interactive charts.',
  },
  {
    icon: Lock,
    title: 'Multi-tenant Architecture',
    desc: 'Fully isolated data planes per tenant with zero-trust networking and granular role-based access control.',
  },
]

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function LandingPage() {
  return (
    <div className="min-h-screen" style={{ background: 'var(--bg-body)', color: 'var(--text-primary)' }}>

      {/* ── Navigation ─────────────────────────────────────────────────────── */}
      <nav
        className="sticky top-0 z-50 backdrop-blur-xl"
        style={{ borderBottom: '1px solid var(--border)', background: 'var(--header-bg)' }}
      >
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-6">
          <div className="flex items-center gap-2.5">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-blue-600">
              <ShieldCheck size={14} className="text-white" />
            </div>
            <span className="font-bold text-sm tracking-tight" style={{ color: 'var(--text-primary)' }}>
              BBSS
            </span>
            <span className="hidden text-xs sm:inline" style={{ color: 'var(--text-muted)' }}>
              Blockchain Banking Security
            </span>
          </div>
          <div className="flex items-center gap-2.5">
            <ThemeToggle />
            <Link
              href="/login"
              className="btn-primary inline-flex items-center gap-1.5 px-4 py-1.5 text-sm"
            >
              Sign In <ChevronRight size={13} />
            </Link>
          </div>
        </div>
      </nav>

      {/* ── Hero ───────────────────────────────────────────────────────────── */}
      <section className="mx-auto max-w-5xl px-6 pb-20 pt-28 text-center">
        <FadeIn>
          <div
            className="mb-6 inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium"
            style={{
              background: 'var(--color-primary-light)',
              color: 'var(--color-primary)',
              border: '1px solid var(--color-primary-border)',
            }}
          >
            <Zap size={10} />
            Enterprise-grade · Real-time · Immutable
          </div>
        </FadeIn>

        <FadeIn delay={80}>
          <h1
            className="mx-auto max-w-3xl text-5xl font-bold leading-[1.13] tracking-tight"
            style={{ color: 'var(--text-primary)' }}
          >
            Secure Banking Infrastructure
            <br />
            <span style={{ color: 'var(--color-primary)' }}>Backed by Blockchain</span>
          </h1>
        </FadeIn>

        <FadeIn delay={160}>
          <p
            className="mx-auto mt-6 max-w-lg text-lg leading-relaxed"
            style={{ color: 'var(--text-secondary)' }}
          >
            A unified platform combining Hyperledger Fabric, real-time fraud detection,
            and a multi-tenant architecture built for modern financial institutions.
          </p>
        </FadeIn>

        <FadeIn delay={240}>
          <div className="mt-10 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
            <Link
              href="/login"
              className="btn-primary inline-flex items-center gap-2 px-6 py-2.5 text-sm font-semibold shadow-sm hover:shadow-md transition-shadow"
            >
              Get Started <ArrowRight size={15} />
            </Link>
            <a
              href="#features"
              className="btn-secondary inline-flex items-center gap-2 px-6 py-2.5 text-sm"
            >
              Explore Features
            </a>
          </div>
        </FadeIn>

        {/* Stats row */}
        <FadeIn delay={320}>
          <div
            className="mx-auto mt-20 grid max-w-xl grid-cols-3 gap-6 rounded-2xl p-6"
            style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
          >
            {[
              { n: '99.9%', l: 'Uptime SLA' },
              { n: '<50ms', l: 'Fraud Scoring' },
              { n: '100%', l: 'Immutable Audit' },
            ].map(({ n, l }) => (
              <div key={l} className="text-center">
                <p className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{n}</p>
                <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>{l}</p>
              </div>
            ))}
          </div>
        </FadeIn>
      </section>

      {/* ── Features ───────────────────────────────────────────────────────── */}
      <section
        id="features"
        className="border-t py-24"
        style={{ borderColor: 'var(--border)' }}
      >
        <div className="mx-auto max-w-5xl px-6">
          <FadeIn>
            <div className="mb-14 text-center">
              <p
                className="mb-2 text-[11px] font-semibold uppercase tracking-widest"
                style={{ color: 'var(--color-primary)' }}
              >
                Platform Features
              </p>
              <h2
                className="text-3xl font-bold"
                style={{ color: 'var(--text-primary)' }}
              >
                Built for Security at Scale
              </h2>
            </div>
          </FadeIn>

          <div className="grid gap-5 sm:grid-cols-2">
            {features.map((f, i) => {
              const Icon = f.icon
              return (
                <FadeIn key={f.title} delay={i * 70}>
                  <div
                    className="group rounded-2xl p-6 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lg"
                    style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
                  >
                    <div
                      className="mb-4 flex h-9 w-9 items-center justify-center rounded-xl"
                      style={{
                        background: 'var(--color-primary-light)',
                        border: '1px solid var(--color-primary-border)',
                      }}
                    >
                      <Icon size={16} style={{ color: 'var(--color-primary)' }} />
                    </div>
                    <h3
                      className="mb-1.5 font-semibold text-sm"
                      style={{ color: 'var(--text-primary)' }}
                    >
                      {f.title}
                    </h3>
                    <p
                      className="text-sm leading-relaxed"
                      style={{ color: 'var(--text-secondary)' }}
                    >
                      {f.desc}
                    </p>
                  </div>
                </FadeIn>
              )
            })}
          </div>
        </div>
      </section>

      {/* ── CTA ────────────────────────────────────────────────────────────── */}
      <section
        className="border-t py-24"
        style={{ borderColor: 'var(--border)' }}
      >
        <div className="mx-auto max-w-xl px-6 text-center">
          <FadeIn>
            <div
              className="mx-auto mb-5 flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-600"
            >
              <Eye size={20} className="text-white" />
            </div>
            <h2
              className="text-3xl font-bold"
              style={{ color: 'var(--text-primary)' }}
            >
              Ready to Explore?
            </h2>
            <p className="mt-3 text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
              Sign in to access real-time monitoring, transaction analytics,
              fraud alerts, and immutable audit trails.
            </p>
            <Link
              href="/login"
              className="btn-primary mt-8 inline-flex items-center gap-2 px-6 py-2.5 text-sm font-semibold"
            >
              Sign In to Dashboard <ArrowRight size={15} />
            </Link>
          </FadeIn>
        </div>
      </section>

      {/* ── Footer ─────────────────────────────────────────────────────────── */}
      <footer
        className="border-t py-6"
        style={{ borderColor: 'var(--border)' }}
      >
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6">
          <div className="flex items-center gap-2">
            <div className="dot-live" />
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
              All services operational
            </p>
          </div>
          <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
            BBSS v1.0.0 · Blockchain Banking Security System
          </p>
        </div>
      </footer>
    </div>
  )
}
