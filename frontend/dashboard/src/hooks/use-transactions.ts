import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryOptions,
} from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import type {
  Transaction,
  TransactionStats,
  SubmitTransactionRequest,
  TransactionListParams,
  TransactionStatus,
  PageResponse,
} from '@/types'

// ─── Query Keys ───────────────────────────────────────────────────────────────

export const transactionKeys = {
  all:    ['transactions']                           as const,
  lists:  () => [...transactionKeys.all, 'list']    as const,
  list:   (p: TransactionListParams) => [...transactionKeys.lists(), p] as const,
  byStatus: (status: string, p: Pick<TransactionListParams, 'page' | 'size'>) =>
    [...transactionKeys.all, 'status', status, p] as const,
  detail: (id: string) => [...transactionKeys.all, 'detail', id] as const,
  stats:  () => [...transactionKeys.all, 'stats']   as const,
}

// ─── useTransactions ──────────────────────────────────────────────────────────

export function useTransactions(
  params: TransactionListParams = {},
  options?: Omit<
    UseQueryOptions<PageResponse<Transaction>>,
    'queryKey' | 'queryFn'
  >
) {
  return useQuery<PageResponse<Transaction>>({
    queryKey: transactionKeys.list(params),
    queryFn: () => api.transactions.list(params),
    placeholderData: (prev) => prev,
    ...options,
  })
}

// ─── useTransaction (single) ──────────────────────────────────────────────────

export function useTransaction(
  id: string,
  options?: Omit<UseQueryOptions<Transaction>, 'queryKey' | 'queryFn'>
) {
  return useQuery<Transaction>({
    queryKey: transactionKeys.detail(id),
    queryFn: () => api.transactions.getById(id),
    enabled: Boolean(id),
    ...options,
  })
}

// ─── useTransactionsByStatus ─────────────────────────────────────────────────

export function useTransactionsByStatus(
  status: TransactionStatus,
  params: Pick<TransactionListParams, 'page' | 'size'> = {},
  options?: Omit<UseQueryOptions<PageResponse<Transaction>>, 'queryKey' | 'queryFn'>
) {
  return useQuery<PageResponse<Transaction>>({
    queryKey: transactionKeys.byStatus(status, params),
    queryFn: () => api.transactions.listByStatus(status, params),
    enabled: Boolean(status),
    placeholderData: (prev) => prev,
    ...options,
  })
}

// ─── useTransactionStats ─────────────────────────────────────────────────────

export function useTransactionStats(
  params?: Pick<TransactionListParams, 'fromDate' | 'toDate'>,
  options?: Omit<UseQueryOptions<TransactionStats>, 'queryKey' | 'queryFn'>
) {
  return useQuery<TransactionStats>({
    queryKey: [...transactionKeys.stats(), params ?? {}],
    queryFn: () => api.transactions.getStats(params),
    staleTime: 60_000,  // stats update every minute is fine
    ...options,
  })
}

// ─── useSubmitTransaction ─────────────────────────────────────────────────────

export function useSubmitTransaction() {
  const qc = useQueryClient()

  return useMutation<Transaction, Error, SubmitTransactionRequest>({
    mutationFn: (req) => api.transactions.submit(req),
    onSuccess: () => {
      // Invalidate list & stats after a successful submission
      qc.invalidateQueries({ queryKey: transactionKeys.lists() })
      qc.invalidateQueries({ queryKey: transactionKeys.stats() })
    },
  })
}
