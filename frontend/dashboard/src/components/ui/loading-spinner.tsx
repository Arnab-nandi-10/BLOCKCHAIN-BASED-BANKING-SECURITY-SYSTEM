import React from 'react'
import { cn } from '@/lib/utils'

type SpinnerSize  = 'sm' | 'md' | 'lg' | 'xl'
type SpinnerColor = 'primary' | 'white' | 'success' | 'danger' | 'warning'

interface LoadingSpinnerProps {
  size?: SpinnerSize
  color?: SpinnerColor
  className?: string
  label?: string
}

const sizeMap: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-6 w-6 border-2',
  lg: 'h-8 w-8 border-[2.5px]',
  xl: 'h-12 w-12 border-[3px]',
}

// Kept as Tailwind classes for compatibility with existing usage
const colorMap: Record<SpinnerColor, string> = {
  primary: 'border-primary-500/20 border-t-primary-500',
  white:   'border-white/20       border-t-white',
  success: 'border-success-500/20 border-t-success-500',
  danger:  'border-danger-500/20  border-t-danger-500',
  warning: 'border-warning-500/20 border-t-warning-500',
}

export function LoadingSpinner({ size = 'md', color = 'primary', className, label }: LoadingSpinnerProps) {
  return (
    <span role="status" aria-label={label ?? 'Loading…'} className={cn('inline-flex items-center gap-2', className)}>
      <span className={cn('animate-spin rounded-full', sizeMap[size], colorMap[color])} />
      {label && <span className="text-sm" style={{ color: '#A1A1A1' }}>{label}</span>}
    </span>
  )
}

export function PageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex h-64 w-full flex-col items-center justify-center gap-3">
      <LoadingSpinner size="lg" />
      <p className="text-sm" style={{ color: '#A1A1A1' }}>{label}</p>
    </div>
  )
}

export default LoadingSpinner
