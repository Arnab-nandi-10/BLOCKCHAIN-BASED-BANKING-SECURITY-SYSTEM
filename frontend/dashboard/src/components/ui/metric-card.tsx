import React from 'react'
import { TrendingUp, TrendingDown } from 'lucide-react'
import { cn } from '@/lib/utils'

// ══════════════════════════════════════════════════════════════════════════════
// Modern Metric Card Component
// Clean, data-focused design inspired by Stripe and Vercel dashboards
// No gradients, no heavy shadows — pure content focus
// ══════════════════════════════════════════════════════════════════════════════

export interface MetricCardProps {
  title: string
  value: string | number
  subtitle?: string
  icon: React.ReactNode
  trend?: {
    value: number
    label?: string
    isPositive?: boolean
  }
  color?: 'blue' | 'emerald' | 'amber' | 'rose' | 'slate'
  isLoading?: boolean
  className?: string
}

const colorStyles = {
  blue: {
    icon: 'bg-blue-50 text-blue-600 dark:bg-blue-950/30 dark:text-blue-400',
    trend: 'text-blue-600 dark:text-blue-400',
  },
  emerald: {
    icon: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/30 dark:text-emerald-400',
    trend: 'text-emerald-600 dark:text-emerald-400',
  },
  amber: {
    icon: 'bg-amber-50 text-amber-600 dark:bg-amber-950/30 dark:text-amber-400',
    trend: 'text-amber-600 dark:text-amber-400',
  },
  rose: {
    icon: 'bg-rose-50 text-rose-600 dark:bg-rose-950/30 dark:text-rose-400',
    trend: 'text-rose-600 dark:text-rose-400',
  },
  slate: {
    icon: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400',
    trend: 'text-slate-600 dark:text-slate-400',
  },
}

export function MetricCard({
  title,
  value,
  subtitle,
  icon,
  trend,
  color = 'blue',
  isLoading = false,
  className,
}: MetricCardProps) {
  if (isLoading) {
    return (
      <div className={cn('rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900', className)}>
        <div className="flex items-start justify-between">
          <div className="flex-1 space-y-3">
            <div className="h-4 w-24 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
            <div className="h-8 w-32 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
            <div className="h-3 w-20 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
          </div>
          <div className="h-10 w-10 animate-pulse rounded-lg bg-slate-200 dark:bg-slate-800" />
        </div>
      </div>
    )
  }

  const styles = colorStyles[color]

  return (
    <div className={cn(
      'group relative rounded-xl border border-slate-200 bg-white p-6 transition-all duration-200',
      'hover:border-slate-300 hover:shadow-sm',
      'dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700',
      className
    )}>
      <div className="flex items-start justify-between">
        {/* Left Content */}
        <div className="flex-1 space-y-3">
          {/* Title */}
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
            {title}
          </p>

          {/* Value */}
          <p className="text-3xl font-semibold text-slate-900 dark:text-slate-100">
            {value}
          </p>

          {/* Subtitle or Trend */}
          <div className="flex items-center gap-3">
            {trend && (
              <div className={cn(
                'flex items-center gap-1 text-xs font-medium',
                trend.isPositive ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'
              )}>
                {trend.isPositive ? (
                  <TrendingUp className="h-3.5 w-3.5" />
                ) : (
                  <TrendingDown className="h-3.5 w-3.5" />
                )}
                <span>{Math.abs(trend.value)}%</span>
                {trend.label && (
                  <span className="text-slate-500 dark:text-slate-400">{trend.label}</span>
                )}
              </div>
            )}
            
            {subtitle && !trend && (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {subtitle}
              </p>
            )}
          </div>
        </div>

        {/* Icon */}
        <div className={cn(
          'flex h-10 w-10 items-center justify-center rounded-lg transition-transform duration-200',
          'group-hover:scale-105',
          styles.icon
        )}>
          {icon}
        </div>
      </div>
    </div>
  )
}

// Skeleton loader for metric cards
export function MetricCardSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn('rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900', className)}>
      <div className="flex items-start justify-between">
        <div className="flex-1 space-y-3">
          <div className="h-4 w-24 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
          <div className="h-8 w-32 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
          <div className="h-3 w-20 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
        </div>
        <div className="h-10 w-10 animate-pulse rounded-lg bg-slate-200 dark:bg-slate-800" />
      </div>
    </div>
  )
}
