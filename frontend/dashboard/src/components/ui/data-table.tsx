import React from 'react'
import { cn } from '@/lib/utils'
import { FileX } from 'lucide-react'
import { LoadingSpinner } from './loading-spinner'

export interface Column<T> {
  key: string
  header: string
  render?: (row: T) => React.ReactNode
  className?: string
  headerClassName?: string
}

interface DataTableProps<T> {
  columns: Column<T>[]
  data: T[]
  isLoading?: boolean
  emptyMessage?: string
  keyExtractor?: (row: T, index: number) => string | number
  onRowClick?: (row: T) => void
  className?: string
}

export function DataTable<T extends Record<string, unknown>>({
  columns,
  data,
  isLoading = false,
  emptyMessage = 'No records found.',
  keyExtractor,
  onRowClick,
  className,
}: DataTableProps<T>) {
  return (
    <div
      className={cn('w-full overflow-x-auto rounded-xl bg-white', className)}
      style={{ border: '1px solid #E2E8F0' }}
    >
      <table className="min-w-full text-sm">
        <thead>
          <tr className="bg-slate-50" style={{ borderBottom: '1px solid #E2E8F0' }}>
            {columns.map((col) => (
              <th
                key={col.key}
                className={cn(
                  'whitespace-nowrap px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-wider text-slate-500',
                  col.headerClassName
                )}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {isLoading ? (
            Array.from({ length: 8 }).map((_, i) => (
              <tr key={i} style={{ borderBottom: '1px solid #F1F5F9' }}>
                {Array.from({ length: columns.length }).map((__, j) => (
                  <td key={j} className="px-4 py-3">
                    <div className="skeleton h-4 rounded" />
                  </td>
                ))}
              </tr>
            ))
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="py-16 text-center">
                <div className="flex flex-col items-center gap-3">
                  <FileX size={28} className="text-slate-300" />
                  <p className="text-sm text-slate-400">{emptyMessage}</p>
                </div>
              </td>
            </tr>
          ) : (
            data.map((row, idx) => (
              <tr
                key={keyExtractor ? keyExtractor(row, idx) : idx}
                onClick={() => onRowClick?.(row)}
                className={cn('transition-colors hover:bg-slate-50', onRowClick && 'cursor-pointer')}
                style={{ borderBottom: '1px solid #F1F5F9' }}
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={cn('whitespace-nowrap px-4 py-3 text-slate-600', col.className)}
                  >
                    {col.render ? col.render(row) : String(row[col.key] ?? '—')}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>

      {isLoading && (
        <div className="flex justify-center py-4">
          <LoadingSpinner size="sm" />
        </div>
      )}
    </div>
  )
}

export default DataTable
