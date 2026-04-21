'use client'

import React, { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  BarChart3,
  Blocks,
  Building2,
  ChevronRight,
  Eye,
  Fingerprint,
  Layers3,
  Lock,
  ScrollText,
  ShieldCheck,
  Sparkles,
  Zap,
} from 'lucide-react'
import { ThemeToggle } from '@/components/ui/theme-toggle'

function clamp(value: number, min = 0, max = 1) {
  return Math.min(max, Math.max(min, value))
}

function reveal(progress: number, start: number, duration = 0.16) {
  return clamp((progress - start) / duration)
}

function mix(from: number, to: number, progress: number) {
  return from + (to - from) * progress
}

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
    const element = ref.current
    if (!element) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          element.style.opacity = '1'
          element.style.transform = 'translateY(0)'
          observer.disconnect()
        }
      },
      { threshold: 0.14 }
    )

    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return (
    <div
      ref={ref}
      className={className}
      style={{
        opacity: 0,
        transform: 'translateY(24px)',
        transition: `opacity 0.8s ease ${delay}ms, transform 0.8s cubic-bezier(0.16, 1, 0.3, 1) ${delay}ms`,
      }}
    >
      {children}
    </div>
  )
}

const features = [
  {
    icon: Blocks,
    title: 'Immutable Blockchain Ledger',
    desc: 'Every money movement is anchored to Hyperledger Fabric with a trace you can inspect long after the event is over.',
  },
  {
    icon: AlertTriangle,
    title: 'Real-time Fraud Detection',
    desc: 'Behavioral signals, transaction anomalies, and fraud scoring feed into a live decision stream before risk spreads.',
  },
  {
    icon: Building2,
    title: 'Tenant Isolation',
    desc: 'Separate tenant context, scoped roles, and cross-service propagation keep institutions segmented by design.',
  },
  {
    icon: ScrollText,
    title: 'Audit-first Operations',
    desc: 'Investigators, compliance teams, and platform owners all get the same tamper-evident narrative of what happened.',
  },
]

const layeredMoments = [
  {
    icon: AlertTriangle,
    eyebrow: 'Layer 01',
    title: 'Fraud signal hits first',
    copy: 'The main visual stays pinned while the first intelligence sheet slides over it with anomaly scores, risk pulses, and live alerts.',
  },
  {
    icon: Building2,
    eyebrow: 'Layer 02',
    title: 'Tenant boundaries lock in',
    copy: 'A second layer arrives with tenant-scoped shielding, context propagation, and isolation markers so the story gets deeper, not busier.',
  },
  {
    icon: Blocks,
    eyebrow: 'Layer 03',
    title: 'Ledger confirmation lands',
    copy: 'Then the blockchain layer stacks on top and makes the scene feel heavier, more consequential, and visibly irreversible.',
  },
  {
    icon: ScrollText,
    eyebrow: 'Layer 04',
    title: 'Audit trail closes the loop',
    copy: 'The final sheet settles into place like a forensic summary. That is the “static visual, scrolling layers” animation you described.',
  },
]

function StickyLayerShowcase() {
  const sectionRef = useRef<HTMLElement>(null)
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    let frame = 0

    const updateProgress = () => {
      frame = 0

      const section = sectionRef.current
      if (!section) return

      const rect = section.getBoundingClientRect()
      const totalScrollableDistance = Math.max(section.offsetHeight - window.innerHeight, 1)
      const next = clamp(-rect.top / totalScrollableDistance)

      setProgress((current) => (Math.abs(current - next) < 0.002 ? current : next))
    }

    const requestUpdate = () => {
      if (frame) return
      frame = window.requestAnimationFrame(updateProgress)
    }

    updateProgress()
    window.addEventListener('scroll', requestUpdate, { passive: true })
    window.addEventListener('resize', requestUpdate)

    return () => {
      if (frame) {
        window.cancelAnimationFrame(frame)
      }
      window.removeEventListener('scroll', requestUpdate)
      window.removeEventListener('resize', requestUpdate)
    }
  }, [])

  const stageA = reveal(progress, 0.06)
  const stageB = reveal(progress, 0.28)
  const stageC = reveal(progress, 0.5)
  const stageD = reveal(progress, 0.72)
  const activeIndex = progress >= 0.72 ? 3 : progress >= 0.5 ? 2 : progress >= 0.28 ? 1 : 0

  const layerStyle = (
    amount: number,
    offsetX: number,
    offsetY: number,
    rotate: number,
    scale = 0.92
  ): React.CSSProperties => ({
    opacity: amount,
    transform: `translate3d(${mix(offsetX, 0, amount)}px, ${mix(offsetY, 0, amount)}px, 0) rotate(${mix(rotate, 0, amount)}deg) scale(${mix(scale, 1, amount)})`,
    filter: `blur(${mix(14, 0, amount)}px)`,
  })

  return (
    <section
      ref={sectionRef}
      id="experience"
      className="relative h-[260vh] overflow-clip border-y"
      style={{ borderColor: 'var(--border)' }}
    >
      <div className="bg-grid-fade pointer-events-none absolute inset-0 opacity-70" />
      <div className="pointer-events-none absolute left-[8%] top-24 h-72 w-72 rounded-full bg-[rgba(37,99,235,0.14)] blur-3xl" />
      <div className="pointer-events-none absolute bottom-24 right-[10%] h-72 w-72 rounded-full bg-[rgba(20,184,166,0.12)] blur-3xl" />

      <div className="sticky top-16 flex min-h-[calc(100vh-4rem)] items-center">
        <div className="mx-auto grid w-full max-w-6xl gap-12 px-6 py-14 lg:grid-cols-[0.9fr_1.1fr] lg:items-center">
          <div className="relative z-10">
            <FadeIn>
              <p
                className="mb-3 text-[11px] font-semibold uppercase tracking-[0.34em]"
                style={{ color: 'var(--color-primary)' }}
              >
                Pinned Scroll Story
              </p>
            </FadeIn>

            <FadeIn delay={80}>
              <h2
                className="max-w-xl text-4xl font-bold leading-[1.05] tracking-tight sm:text-5xl"
                style={{ color: 'var(--text-primary)' }}
              >
                One fixed scene.
                <br />
                <span style={{ color: 'var(--color-primary)' }}>Four layers arriving one by one.</span>
              </h2>
            </FadeIn>

            <FadeIn delay={160}>
              <p
                className="mt-5 max-w-xl text-base leading-7 sm:text-lg"
                style={{ color: 'var(--text-secondary)' }}
              >
                This is the effect you were describing: the main visual stays planted while the scroll adds new surfaces over it,
                like a stack of live intelligence panes sliding into the same locked camera shot.
              </p>
            </FadeIn>

            <div className="mt-10 space-y-4">
              {layeredMoments.map((moment, index) => {
                const Icon = moment.icon
                const isActive = activeIndex === index

                return (
                  <FadeIn key={moment.title} delay={220 + index * 70}>
                    <div
                      className="rounded-3xl border px-5 py-4 transition-all duration-500"
                      style={{
                        background: isActive ? 'linear-gradient(135deg, rgba(37,99,235,0.12), rgba(255,255,255,0.86))' : 'var(--bg-surface)',
                        borderColor: isActive ? 'var(--color-primary-border)' : 'var(--border)',
                        boxShadow: isActive ? '0 24px 70px rgba(37, 99, 235, 0.16)' : '0 12px 40px rgba(15, 23, 42, 0.05)',
                        transform: isActive ? 'translateX(8px)' : 'translateX(0)',
                      }}
                    >
                      <div className="flex items-start gap-4">
                        <div
                          className="mt-0.5 flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-2xl"
                          style={{
                            background: isActive ? 'rgba(37,99,235,0.12)' : 'var(--bg-subtle)',
                            color: isActive ? 'var(--color-primary)' : 'var(--text-muted)',
                          }}
                        >
                          <Icon size={18} />
                        </div>
                        <div>
                          <p className="text-[11px] font-semibold uppercase tracking-[0.28em]" style={{ color: 'var(--text-muted)' }}>
                            {moment.eyebrow}
                          </p>
                          <h3 className="mt-1 text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                            {moment.title}
                          </h3>
                          <p className="mt-2 text-sm leading-6" style={{ color: 'var(--text-secondary)' }}>
                            {moment.copy}
                          </p>
                        </div>
                      </div>
                    </div>
                  </FadeIn>
                )
              })}
            </div>
          </div>

          <div className="relative z-10 mx-auto w-full max-w-[600px]">
            <div className="relative overflow-hidden rounded-[36px] border p-4 sm:p-6 glass-panel">
              <div className="story-beam absolute -left-1/4 top-0 h-full w-1/3 opacity-40" />
              <div className="absolute inset-0 rounded-[30px] border border-white/20" />

              <div className="mb-4 flex items-center justify-between rounded-2xl border px-4 py-3" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.45)' }}>
                <div>
                  <p className="text-[11px] uppercase tracking-[0.28em]" style={{ color: 'var(--text-muted)' }}>
                    Static Core
                  </p>
                  <p className="mt-1 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    BBSS Security Command Layer
                  </p>
                </div>
                <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--text-secondary)' }}>
                  <span className="dot-live" />
                  Locked View
                </div>
              </div>

              <div className="relative h-[440px] rounded-[30px] border p-5 sm:h-[520px]" style={{ borderColor: 'rgba(148, 163, 184, 0.18)', background: 'linear-gradient(180deg, rgba(255,255,255,0.5), rgba(241,245,249,0.72))' }}>
                <div className="showcase-grid absolute inset-0 rounded-[26px] opacity-70" />

                <div className="relative z-10">
                  <div className="grid gap-4 sm:grid-cols-[1.2fr_0.8fr]">
                    <div className="rounded-3xl border px-4 py-4" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.84)' }}>
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-[11px] uppercase tracking-[0.28em]" style={{ color: 'var(--text-muted)' }}>
                            Risk feed
                          </p>
                          <p className="mt-2 text-3xl font-bold" style={{ color: 'var(--text-primary)' }}>
                            24
                          </p>
                        </div>
                        <div className="rounded-2xl px-3 py-2 text-right" style={{ background: 'rgba(37,99,235,0.08)' }}>
                          <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                            Critical
                          </p>
                          <p className="text-base font-semibold" style={{ color: 'var(--color-primary)' }}>
                            03 live
                          </p>
                        </div>
                      </div>
                      <div className="mt-4 grid grid-cols-4 gap-2">
                        {[72, 38, 90, 56].map((height, index) => (
                          <div key={index} className="flex h-20 items-end rounded-2xl p-2" style={{ background: 'var(--bg-subtle)' }}>
                            <div
                              className="w-full rounded-full"
                              style={{
                                height: `${height}%`,
                                background: index === 2 ? 'linear-gradient(180deg, #F97316, #EF4444)' : 'linear-gradient(180deg, #60A5FA, #2563EB)',
                              }}
                            />
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="space-y-4">
                      <div className="rounded-3xl border px-4 py-4" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.8)' }}>
                        <div className="flex items-center gap-2">
                          <Fingerprint size={15} style={{ color: 'var(--text-muted)' }} />
                          <p className="text-xs font-semibold uppercase tracking-[0.24em]" style={{ color: 'var(--text-muted)' }}>
                            Identity
                          </p>
                        </div>
                        <p className="mt-3 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                          Multi-factor trust
                        </p>
                        <p className="mt-1 text-xs leading-5" style={{ color: 'var(--text-secondary)' }}>
                          JWT, tenant context, and event correlation all chained together.
                        </p>
                      </div>

                      <div className="rounded-3xl border px-4 py-4" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.8)' }}>
                        <div className="flex items-center justify-between">
                          <p className="text-xs font-semibold uppercase tracking-[0.24em]" style={{ color: 'var(--text-muted)' }}>
                            Throughput
                          </p>
                          <Activity size={15} style={{ color: 'var(--color-primary)' }} />
                        </div>
                        <p className="mt-3 text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                          6.8k
                        </p>
                        <p className="mt-1 text-xs leading-5" style={{ color: 'var(--text-secondary)' }}>
                          requests monitored in the current observation window.
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="mt-4 rounded-3xl border px-4 py-4" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.82)' }}>
                    <div className="mb-3 flex items-center justify-between">
                      <p className="text-xs font-semibold uppercase tracking-[0.24em]" style={{ color: 'var(--text-muted)' }}>
                        Transaction stream
                      </p>
                      <span className="rounded-full px-2.5 py-1 text-[11px]" style={{ background: 'rgba(34,197,94,0.12)', color: 'var(--color-success)' }}>
                        Clean baseline
                      </span>
                    </div>
                    <div className="grid gap-2">
                      {[
                        ['Wire transfer', 'Passed in 42ms'],
                        ['Card settlement', 'Flagged for review'],
                        ['Tenant switch', 'Context locked'],
                      ].map(([label, note]) => (
                        <div key={label} className="flex items-center justify-between rounded-2xl px-3 py-2" style={{ background: 'var(--bg-subtle)' }}>
                          <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                            {label}
                          </span>
                          <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                            {note}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

                <div
                  className="absolute inset-x-10 top-12 z-20 rounded-[28px] border p-4 shadow-2xl"
                  style={{
                    ...layerStyle(stageA, 0, 48, -7),
                    background: 'linear-gradient(145deg, rgba(255,255,255,0.94), rgba(254,242,242,0.94))',
                    borderColor: 'rgba(248,113,113,0.24)',
                    boxShadow: '0 28px 90px rgba(239, 68, 68, 0.18)',
                  }}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <AlertTriangle size={16} style={{ color: '#DC2626' }} />
                      <p className="text-xs font-semibold uppercase tracking-[0.24em]" style={{ color: '#B91C1C' }}>
                        Fraud Lens
                      </p>
                    </div>
                    <span className="rounded-full px-2.5 py-1 text-[11px]" style={{ background: 'rgba(239,68,68,0.12)', color: '#B91C1C' }}>
                      score 0.91
                    </span>
                  </div>
                  <div className="mt-3 grid gap-3 sm:grid-cols-[1.1fr_0.9fr]">
                    <div className="rounded-2xl p-3" style={{ background: 'rgba(255,255,255,0.78)' }}>
                      <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                        Anomaly spike on wire corridor
                      </p>
                      <p className="mt-1 text-xs leading-5" style={{ color: 'var(--text-secondary)' }}>
                        The first layer hits hard and makes the static frame feel alive without moving the main shot.
                      </p>
                    </div>
                    <div className="rounded-2xl p-3" style={{ background: 'rgba(255,255,255,0.78)' }}>
                      <div className="mb-2 flex items-center justify-between text-[11px]" style={{ color: 'var(--text-muted)' }}>
                        <span>velocity</span>
                        <span>burst</span>
                      </div>
                      <div className="h-2 rounded-full" style={{ background: 'rgba(239,68,68,0.14)' }}>
                        <div className="h-full rounded-full" style={{ width: '84%', background: 'linear-gradient(90deg, #FB923C, #EF4444)' }} />
                      </div>
                    </div>
                  </div>
                </div>

                <div
                  className="absolute inset-x-8 top-[122px] z-30 rounded-[28px] border p-4 shadow-2xl"
                  style={{
                    ...layerStyle(stageB, 42, 32, 6, 0.94),
                    background: 'linear-gradient(145deg, rgba(255,255,255,0.94), rgba(236,253,245,0.94))',
                    borderColor: 'rgba(16,185,129,0.22)',
                    boxShadow: '0 28px 90px rgba(16, 185, 129, 0.16)',
                  }}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <ShieldCheck size={16} style={{ color: '#047857' }} />
                      <p className="text-xs font-semibold uppercase tracking-[0.24em]" style={{ color: '#047857' }}>
                        Tenant Shield
                      </p>
                    </div>
                    <span className="rounded-full px-2.5 py-1 text-[11px]" style={{ background: 'rgba(16,185,129,0.12)', color: '#047857' }}>
                      isolated
                    </span>
                  </div>
                  <div className="mt-3 grid gap-3 sm:grid-cols-3">
                    {[
                      ['tenant', 'test-tenant'],
                      ['scope', 'role-admin'],
                      ['context', 'propagated'],
                    ].map(([label, value]) => (
                      <div key={label} className="rounded-2xl p-3" style={{ background: 'rgba(255,255,255,0.8)' }}>
                        <p className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-muted)' }}>
                          {label}
                        </p>
                        <p className="mt-2 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                          {value}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>

                <div
                  className="absolute inset-x-12 top-[228px] z-40 rounded-[28px] border p-4 shadow-2xl"
                  style={{
                    ...layerStyle(stageC, -44, 40, -5, 0.95),
                    background: 'linear-gradient(145deg, rgba(255,255,255,0.96), rgba(239,246,255,0.94))',
                    borderColor: 'rgba(37,99,235,0.24)',
                    boxShadow: '0 28px 90px rgba(37, 99, 235, 0.18)',
                  }}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Layers3 size={16} style={{ color: '#2563EB' }} />
                      <p className="text-xs font-semibold uppercase tracking-[0.24em]" style={{ color: '#1D4ED8' }}>
                        Ledger Seal
                      </p>
                    </div>
                    <span className="rounded-full px-2.5 py-1 text-[11px]" style={{ background: 'rgba(37,99,235,0.12)', color: '#1D4ED8' }}>
                      block committed
                    </span>
                  </div>
                  <div className="mt-3 rounded-2xl p-3" style={{ background: 'rgba(255,255,255,0.78)' }}>
                    <div className="flex items-center justify-between text-xs" style={{ color: 'var(--text-secondary)' }}>
                      <span>tx hash</span>
                      <span className="font-mono">0x8b7f...d42c</span>
                    </div>
                    <div className="mt-3 h-2 rounded-full" style={{ background: 'rgba(37,99,235,0.14)' }}>
                      <div className="h-full rounded-full" style={{ width: '92%', background: 'linear-gradient(90deg, #60A5FA, #2563EB)' }} />
                    </div>
                  </div>
                </div>

                <div
                  className="absolute inset-x-6 bottom-6 z-50 rounded-[28px] border p-4 shadow-2xl"
                  style={{
                    ...layerStyle(stageD, 0, 54, 0, 0.96),
                    background: 'linear-gradient(145deg, rgba(255,255,255,0.98), rgba(248,250,252,0.96))',
                    borderColor: 'rgba(148,163,184,0.22)',
                    boxShadow: '0 28px 90px rgba(15, 23, 42, 0.16)',
                  }}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <ScrollText size={16} style={{ color: 'var(--text-primary)' }} />
                      <p className="text-xs font-semibold uppercase tracking-[0.24em]" style={{ color: 'var(--text-muted)' }}>
                        Audit Wrap-up
                      </p>
                    </div>
                    <span className="rounded-full px-2.5 py-1 text-[11px]" style={{ background: 'var(--bg-subtle)', color: 'var(--text-secondary)' }}>
                      ready for review
                    </span>
                  </div>
                  <div className="mt-3 grid gap-2">
                    {[
                      'Signal reviewed and preserved with trace context',
                      'Tenant ownership attached to downstream events',
                      'Ledger commit and audit snapshot aligned in one chain',
                    ].map((item) => (
                      <div key={item} className="flex items-center gap-3 rounded-2xl px-3 py-2.5" style={{ background: 'var(--bg-subtle)' }}>
                        <Sparkles size={14} style={{ color: 'var(--color-primary)' }} />
                        <span className="text-sm" style={{ color: 'var(--text-primary)' }}>
                          {item}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <div className="mt-5 flex items-center justify-between gap-4 text-xs" style={{ color: 'var(--text-muted)' }}>
                <span>Scroll depth</span>
                <div className="h-2 flex-1 overflow-hidden rounded-full" style={{ background: 'var(--bg-subtle)' }}>
                  <div
                    className="h-full rounded-full"
                    style={{
                      width: `${Math.max(progress * 100, 6)}%`,
                      background: 'linear-gradient(90deg, #38BDF8, #2563EB 55%, #0F172A)',
                      transition: 'width 120ms linear',
                    }}
                  />
                </div>
                <span>{Math.round(progress * 100)}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

export default function LandingPage() {
  return (
    <div className="min-h-screen overflow-x-hidden" style={{ background: 'var(--bg-body)', color: 'var(--text-primary)' }}>
      <nav
        className="sticky top-0 z-50 backdrop-blur-xl"
        style={{ borderBottom: '1px solid var(--border)', background: 'var(--header-bg)' }}
      >
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-6">
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-blue-600 shadow-lg shadow-blue-600/20">
              <ShieldCheck size={15} className="text-white" />
            </div>
            <div>
              <span className="block text-sm font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
                BBSS
              </span>
              <span className="hidden text-[11px] sm:block" style={{ color: 'var(--text-muted)' }}>
                Blockchain Banking Security System
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            <a href="#experience" className="hidden rounded-full px-3 py-1.5 text-xs font-medium sm:inline-flex" style={{ color: 'var(--text-secondary)' }}>
              Experience
            </a>
            <ThemeToggle />
            <Link href="/login" className="btn-primary inline-flex items-center gap-1.5 px-4 py-1.5 text-sm">
              Sign In <ChevronRight size={13} />
            </Link>
          </div>
        </div>
      </nav>

      <section className="relative overflow-hidden px-6 pb-20 pt-20 sm:pt-24">
        <div className="hero-orb hero-orb-primary" />
        <div className="hero-orb hero-orb-secondary" />
        <div className="hero-orb hero-orb-tertiary" />

        <div className="mx-auto grid max-w-6xl gap-12 lg:grid-cols-[0.95fr_1.05fr] lg:items-center">
          <div className="relative z-10">
            <FadeIn>
              <div
                className="inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-medium"
                style={{
                  background: 'rgba(37,99,235,0.08)',
                  color: 'var(--color-primary)',
                  border: '1px solid var(--color-primary-border)',
                }}
              >
                <Zap size={12} />
                Pinned visuals · layered motion · a stronger first impression
              </div>
            </FadeIn>

            <FadeIn delay={80}>
              <h1 className="mt-7 max-w-3xl text-5xl font-bold leading-[0.98] tracking-tight sm:text-6xl lg:text-7xl">
                Security that feels
                <br />
                <span style={{ color: 'var(--color-primary)' }}>alive while you scroll.</span>
              </h1>
            </FadeIn>

            <FadeIn delay={160}>
              <p
                className="mt-7 max-w-xl text-base leading-7 sm:text-lg"
                style={{ color: 'var(--text-secondary)' }}
              >
                BBSS now tells its story with a locked visual core, stacked intelligence layers, and motion that feels deliberate instead of decorative.
              </p>
            </FadeIn>

            <FadeIn delay={240}>
              <div className="mt-9 flex flex-col gap-3 sm:flex-row">
                <a href="#experience" className="btn-primary inline-flex items-center gap-2 px-6 py-3 text-sm font-semibold shadow-[0_18px_50px_rgba(37,99,235,0.24)]">
                  Watch the sequence <ArrowRight size={15} />
                </a>
                <Link href="/login" className="btn-secondary inline-flex items-center gap-2 px-6 py-3 text-sm">
                  Open dashboard
                </Link>
              </div>
            </FadeIn>

            <FadeIn delay={320}>
              <div className="mt-12 grid max-w-2xl gap-4 sm:grid-cols-3">
                {[
                  { label: 'Fraud scoring', value: '<50ms' },
                  { label: 'Tenant isolation', value: '100%' },
                  { label: 'Audit certainty', value: 'Immutable' },
                ].map((item) => (
                  <div
                    key={item.label}
                    className="rounded-3xl border px-4 py-4"
                    style={{
                      background: 'rgba(255,255,255,0.66)',
                      borderColor: 'rgba(148,163,184,0.18)',
                      backdropFilter: 'blur(12px)',
                    }}
                  >
                    <p className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                      {item.value}
                    </p>
                    <p className="mt-1 text-xs uppercase tracking-[0.22em]" style={{ color: 'var(--text-muted)' }}>
                      {item.label}
                    </p>
                  </div>
                ))}
              </div>
            </FadeIn>
          </div>

          <FadeIn delay={160} className="relative z-10">
            <div className="relative overflow-hidden rounded-[36px] border p-5 sm:p-6 glass-panel">
              <div className="story-beam absolute -left-1/4 top-0 h-full w-1/3 opacity-30" />
              <div className="bg-grid-fade absolute inset-0 opacity-70" />

              <div className="relative z-10 flex items-center justify-between rounded-2xl border px-4 py-3" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.52)' }}>
                <div>
                  <p className="text-[11px] uppercase tracking-[0.28em]" style={{ color: 'var(--text-muted)' }}>
                    Experience Preview
                  </p>
                  <p className="mt-1 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    Static frame, layered intelligence
                  </p>
                </div>
                <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--text-secondary)' }}>
                  <span className="dot-live" />
                  Epic mode
                </div>
              </div>

              <div className="relative mt-4 rounded-[30px] border p-5" style={{ borderColor: 'rgba(148,163,184,0.18)', background: 'linear-gradient(180deg, rgba(255,255,255,0.58), rgba(241,245,249,0.76))' }}>
                <div className="grid gap-4 sm:grid-cols-[1.1fr_0.9fr]">
                  <div className="rounded-3xl border p-4 motion-float" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.84)' }}>
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-[11px] uppercase tracking-[0.22em]" style={{ color: 'var(--text-muted)' }}>
                          Threat canvas
                        </p>
                        <p className="mt-2 text-3xl font-bold" style={{ color: 'var(--text-primary)' }}>
                          91%
                        </p>
                      </div>
                      <div className="rounded-2xl p-3" style={{ background: 'rgba(239,68,68,0.1)' }}>
                        <AlertTriangle size={18} style={{ color: '#DC2626' }} />
                      </div>
                    </div>
                    <div className="mt-5 flex gap-2">
                      {[68, 84, 42, 92, 58].map((value, index) => (
                        <div key={index} className="flex h-24 flex-1 items-end rounded-2xl p-2" style={{ background: 'var(--bg-subtle)' }}>
                          <div
                            className="w-full rounded-full"
                            style={{
                              height: `${value}%`,
                              background: index === 3 ? 'linear-gradient(180deg, #FB923C, #EF4444)' : 'linear-gradient(180deg, #7DD3FC, #2563EB)',
                            }}
                          />
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="space-y-4">
                    <div className="rounded-3xl border p-4 motion-float-delayed" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.82)' }}>
                      <div className="flex items-center gap-2">
                        <Fingerprint size={15} style={{ color: 'var(--color-primary)' }} />
                        <p className="text-xs font-semibold uppercase tracking-[0.22em]" style={{ color: 'var(--text-muted)' }}>
                          Access graph
                        </p>
                      </div>
                      <p className="mt-3 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                        Identity fused with tenant scope
                      </p>
                    </div>

                    <div className="rounded-3xl border p-4 motion-float" style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.82)' }}>
                      <div className="flex items-center gap-2">
                        <Layers3 size={15} style={{ color: 'var(--color-primary)' }} />
                        <p className="text-xs font-semibold uppercase tracking-[0.22em]" style={{ color: 'var(--text-muted)' }}>
                          Layer stack
                        </p>
                      </div>
                      <div className="mt-3 space-y-2">
                        {[
                          ['Fraud lens', 'active'],
                          ['Tenant shield', 'ready'],
                          ['Ledger seal', 'standby'],
                        ].map(([label, state]) => (
                          <div key={label} className="flex items-center justify-between rounded-2xl px-3 py-2" style={{ background: 'var(--bg-subtle)' }}>
                            <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                              {label}
                            </span>
                            <span className="text-[11px] uppercase tracking-[0.2em]" style={{ color: 'var(--text-muted)' }}>
                              {state}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-4 grid gap-4 sm:grid-cols-3">
                  {[
                    { icon: Activity, title: 'Live scoring' },
                    { icon: ShieldCheck, title: 'Scoped trust' },
                    { icon: ScrollText, title: 'Audit-ready' },
                  ].map((item, index) => {
                    const Icon = item.icon

                    return (
                      <div key={item.title} className={`rounded-3xl border p-4 ${index % 2 === 0 ? 'motion-float' : 'motion-float-delayed'}`} style={{ borderColor: 'var(--border)', background: 'rgba(255,255,255,0.8)' }}>
                        <Icon size={16} style={{ color: 'var(--color-primary)' }} />
                        <p className="mt-3 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                          {item.title}
                        </p>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          </FadeIn>
        </div>
      </section>

      <StickyLayerShowcase />

      <section id="features" className="border-t py-24" style={{ borderColor: 'var(--border)' }}>
        <div className="mx-auto max-w-6xl px-6">
          <FadeIn>
            <div className="mb-14 max-w-2xl">
              <p
                className="mb-3 text-[11px] font-semibold uppercase tracking-[0.34em]"
                style={{ color: 'var(--color-primary)' }}
              >
                Platform Features
              </p>
              <h2 className="text-4xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
                Stronger motion, still anchored in the product.
              </h2>
              <p className="mt-4 text-base leading-7" style={{ color: 'var(--text-secondary)' }}>
                The page now feels more cinematic, but the story is still tied to real platform pillars instead of generic visual noise.
              </p>
            </div>
          </FadeIn>

          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
            {features.map((feature, index) => {
              const Icon = feature.icon

              return (
                <FadeIn key={feature.title} delay={index * 70}>
                  <div
                    className="group relative overflow-hidden rounded-[28px] border p-6 transition-all duration-300 hover:-translate-y-1"
                    style={{
                      background: 'linear-gradient(180deg, rgba(255,255,255,0.88), rgba(248,250,252,0.94))',
                      borderColor: 'var(--border)',
                      boxShadow: '0 18px 50px rgba(15, 23, 42, 0.06)',
                    }}
                  >
                    <div className="pointer-events-none absolute -right-10 -top-10 h-24 w-24 rounded-full bg-[rgba(37,99,235,0.08)] blur-2xl transition-transform duration-500 group-hover:scale-125" />
                    <div
                      className="relative mb-5 flex h-11 w-11 items-center justify-center rounded-2xl"
                      style={{
                        background: 'rgba(37,99,235,0.1)',
                        color: 'var(--color-primary)',
                      }}
                    >
                      <Icon size={18} />
                    </div>
                    <h3 className="relative text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                      {feature.title}
                    </h3>
                    <p className="relative mt-3 text-sm leading-6" style={{ color: 'var(--text-secondary)' }}>
                      {feature.desc}
                    </p>
                  </div>
                </FadeIn>
              )
            })}
          </div>
        </div>
      </section>

      <section className="border-t py-24" style={{ borderColor: 'var(--border)' }}>
        <div className="mx-auto max-w-4xl px-6 text-center">
          <FadeIn>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-[22px] bg-blue-600 shadow-[0_18px_50px_rgba(37,99,235,0.22)]">
              <Eye size={24} className="text-white" />
            </div>
            <h2 className="mt-8 text-4xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
              Ready for a sharper first impression?
            </h2>
            <p className="mx-auto mt-4 max-w-2xl text-base leading-7" style={{ color: 'var(--text-secondary)' }}>
              Sign in to the dashboard, review the live system, and keep iterating. If you want, I can push this even further with deeper parallax, scroll-snapped chapters, or an animated dashboard mockup next.
            </p>
            <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Link href="/login" className="btn-primary inline-flex items-center gap-2 px-6 py-3 text-sm font-semibold">
                Sign In to Dashboard <ArrowRight size={15} />
              </Link>
              <a href="#experience" className="btn-secondary inline-flex items-center gap-2 px-6 py-3 text-sm">
                Replay the scroll scene
              </a>
            </div>
          </FadeIn>
        </div>
      </section>

      <footer className="border-t py-6" style={{ borderColor: 'var(--border)' }}>
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-6 text-xs sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <div className="dot-live" />
            <p style={{ color: 'var(--text-muted)' }}>All services operational</p>
          </div>
          <p style={{ color: 'var(--text-muted)' }}>
            BBSS v1.0.0 · static frame storytelling with layered motion
          </p>
        </div>
      </footer>
    </div>
  )
}
