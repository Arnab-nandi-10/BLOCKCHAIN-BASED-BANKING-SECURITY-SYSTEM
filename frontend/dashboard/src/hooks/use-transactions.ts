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
  PageResponse,
} from '@/types'

// ─── Query Keys ───────────────────────────────────────────────────────────────

export const transactionKeys = {
  all:    ['transactions']                           as const,
  lists:  () => [...transactionKeys.all, 'list']    as const,
  list:   (p: TransactionListParams) => [...transactionKeys.lists(), p] as const,
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

// ─── useTransactionStats ─────────────────────────────────────────────────────

export function useTransactionStats(
  options?: Omit<UseQueryOptions<TransactionStats>, 'queryKey' | 'queryFn'>
) {
  return useQuery<TransactionStats>({
    queryKey: transactionKeys.stats(),
    queryFn: () => api.transactions.getStats(),
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
