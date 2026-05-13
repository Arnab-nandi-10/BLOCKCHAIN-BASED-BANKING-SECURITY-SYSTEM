import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import { format, formatDistanceToNow } from 'date-fns'

/** Merge Tailwind classes safely */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}

/** Format a currency amount */
export function formatCurrency(amount: number, currency = 'USD'): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

/** Format a date string to a readable format */
export function formatDate(dateStr: string, fmt = 'MMM dd, yyyy HH:mm'): string {
  try {
    return format(new Date(dateStr), fmt)
  } catch {
    return dateStr
  }
}

/** Return relative time (e.g. "3 minutes ago") */
export function timeAgo(dateStr: string): string {
  try {
    return formatDistanceToNow(new Date(dateStr), { addSuffix: true })
  } catch {
    return dateStr
  }
}

/** Truncate a long string (e.g. blockchain tx ID) */
export function truncate(str: string, start = 6, end = 4): string {
  if (!str) return ''
  if (str.length <= start + end + 3) return str
  return `${str.slice(0, start)}...${str.slice(-end)}`
}

/**
 * Mask an account identifier for display without assuming a single banking format.
 * Numeric accounts keep their first/last digits visible, while mixed identifiers
 * retain their prefix/suffix so analysts can still recognise the source.
 */
export function formatAccountNumber(account: string): string {
  if (!account) return '—'
  const trimmed = account.trim()
  const clean = trimmed.replace(/\D/g, '')

  if (/^\d{10,34}$/.test(trimmed)) {
    return `${clean.slice(0, 4)} **** ${clean.slice(-4)}`
  }

  if (/^[A-Za-z0-9-]{8,34}$/.test(trimmed)) {
    return `${trimmed.slice(0, 4)} **** ${trimmed.slice(-4)}`
  }

  if (trimmed.length >= 5) {
    return `${trimmed.slice(0, 2)}***${trimmed.slice(-2)}`
  }

  return trimmed
}

/**
 * Normalise a fraud score to the 0–100 range regardless of whether the
 * backend returned it as 0.0–1.0 or 0–100.
 */
export function normalizeFraudScore(score: number): number {
  const s = Number(score)
  if (!isFinite(s)) return 0
  const normalized = s <= 1 ? s * 100 : s
  return Math.max(0, Math.min(100, normalized))
}

/**
 * Return a hex colour for a fraud score (already in 0–100 range after normalization).
 */
export function fraudScoreColor(score: number): string {
  const s = normalizeFraudScore(score)
  if (s >= 80) return '#ef4444'
  if (s >= 60) return '#f97316'
  if (s >= 40) return '#f59e0b'
  return '#10b981'
}

/** Format a large number with commas */
export function formatNumber(n: number): string {
  return new Intl.NumberFormat('en-US').format(n)
}

/** Safe JSON pretty-print */
export function prettyJson(jsonStr: string | undefined): string {
  if (!jsonStr) return ''
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    return jsonStr
  }
}

/** Download an array of objects as a CSV file */
export function downloadCsv<T extends Record<string, unknown>>(
  data: T[],
  filename: string
): void {
  if (typeof document === 'undefined' || !data.length) return

  const headers = Object.keys(data[0])
  const escape = (val: string) =>
    val.includes(',') || val.includes('"') || val.includes('\n')
      ? `"${val.replace(/"/g, '""')}"`
      : val
  const rows = data.map((row) =>
    headers.map((h) => escape(String(row[h] ?? ''))).join(',')
  )
  const csv = [headers.join(','), ...rows].join('\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener'
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    a.remove()
    URL.revokeObjectURL(url)
  }, 0)
}
