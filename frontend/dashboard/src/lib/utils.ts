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
  if (!data.length) return
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
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
