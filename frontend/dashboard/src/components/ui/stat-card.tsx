import React from 'react'
import { TrendingUp, TrendingDown } from 'lucide-react'
import { cn } from '@/lib/utils'

type ColorVariant = 'blue' | 'green' | 'red' | 'amber' | 'purple' | 'slate'

interface TrendInfo {
  value: number
  isPositive: boolean
  label?: string
}

interface StatCardProps {
  title:     string
  value:     number | string
  subtitle?: string
  icon:      React.ReactNode
  trend?:    TrendInfo
  color?:    ColorVariant
  isLoading?: boolean
  className?: string
}

const gradientMap: Record<ColorVariant, string> = {
  blue:   'linear-gradient(135deg, #2563EB 0%, #0EA5E9 100%)',
  green:  'linear-gradient(135deg, #16A34A 0%, #22C55E 100%)',
  red:    'linear-gradient(135deg, #DC2626 0%, #F43F5E 100%)',
  amber:  'linear-gradient(135deg, #D97706 0%, #F59E0B 100%)',
  purple: 'linear-gradient(135deg, #7C3AED 0%, #8B5CF6 100%)',
  slate:  'linear-gradient(135deg, #475569 0%, #94A3B8 100%)',
}

const shadowMap: Record<ColorVariant, string> = {
  blue:   'rgba(37,99,235,0.3)',
  green:  'rgba(22,163,74,0.3)',
  red:    'rgba(220,38,38,0.3)',
  amber:  'rgba(217,119,6,0.3)',
  purple: 'rgba(124,58,237,0.3)',
  slate:  'rgba(71,85,105,0.2)',
}

export function StatCard({
  title,
  value,
  subtitle,
  icon,
  trend,
  color = 'blue',
  isLoading = false,
  className,
}: StatCardProps) {
  if (isLoading) {
    return (
      <div
        className={cn('relative overflow-hidden rounded-2xl p-5', className)}
        style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
      >
        <div className="absolute left-0 right-0 top-0 h-[3px] rounded-t-2xl skeleton" />
        <div className="skeleton mt-2 mb-4 h-11 w-11 rounded-xl" />
        <div className="skeleton mb-2 h-7 w-20 rounded" />
        <div className="skeleton h-3.5 w-28 rounded" />
      </div>
    )
  }

  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-2xl p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg',
        className
      )}
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
      }}
    >
      {/* Top accent gradient bar */}
      <div className="absolute left-0 right-0 top-0 h-[3px] rounded-t-2xl"
        style={{ background: gradientMap[color] }} />

      {/* Icon */}
      <div
        className="mt-1 mb-4 inline-flex h-11 w-11 items-center justify-center rounded-xl text-white"
        style={{ background: gradientMap[color], boxShadow: `0 4px 14px ${shadowMap[color]}` }}
      >
        {icon}
      </div>

      {/* Value */}
      <p className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>{value}</p>

      {/* Title + trend row */}
      <div className="mt-1.5 flex items-end justify-between gap-2">
        <div>
          <p className="text-sm font-medium" style={{ color: 'var(--text-secondary)' }}>{title}</p>
          {subtitle && <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>{subtitle}</p>}
        </div>
        {trend && (
          <div
            className="flex flex-shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold"
            style={{
              background: trend.isPositive ? 'var(--color-success-bg)' : 'var(--color-error-bg)',
              color: trend.isPositive ? 'var(--color-success)' : 'var(--color-error)',
              border: `1px solid ${trend.isPositive ? 'var(--color-success-border)' : 'var(--color-error-border)'}`,
            }}
          >
            {trend.isPositive ? <TrendingUp size={10} /> : <TrendingDown size={10} />}
            {Math.abs(trend.value)}%
          </div>
        )}
      </div>
    </div>
  )
}

export default StatCard
