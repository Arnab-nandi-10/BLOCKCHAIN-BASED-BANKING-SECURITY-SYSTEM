import {
  useQuery,
  useMutation,
  type UseMutationOptions,
  type UseQueryOptions,
} from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import type {
  FraudAlert,
  FraudScoreRequest,
  FraudScoreResponse,
  FraudServiceHealth,
  FraudAlertSummary,
  FraudAlertParams,
  PageResponse,
} from '@/types'

// ─── Query Keys ───────────────────────────────────────────────────────────────

export const fraudKeys = {
  all:     ['fraud']                                      as const,
  health:  () => [...fraudKeys.all, 'health']            as const,
  lists:   () => [...fraudKeys.all, 'alerts', 'list']    as const,
  list:    (p: FraudAlertParams) => [...fraudKeys.lists(), p] as const,
  detail:  (id: string) => [...fraudKeys.all, 'alerts', 'detail', id] as const,
  summary: (p?: FraudAlertParams) => [...fraudKeys.all, 'alerts', 'summary', p ?? {}] as const,
}

// ─── useFraudHealth ───────────────────────────────────────────────────────────

export function useFraudHealth(
  options?: Omit<UseQueryOptions<FraudServiceHealth>, 'queryKey' | 'queryFn'>
) {
  return useQuery<FraudServiceHealth>({
    queryKey: fraudKeys.health(),
    queryFn: () => api.fraud.getHealth(),
    staleTime: 30_000,
    refetchInterval: 60_000,
    ...options,
  })
}

// ─── useFraudAlerts ───────────────────────────────────────────────────────────

export function useFraudAlerts(
  params: FraudAlertParams = {},
  options?: Omit<UseQueryOptions<PageResponse<FraudAlert>>, 'queryKey' | 'queryFn'>
) {
  return useQuery<PageResponse<FraudAlert>>({
    queryKey: fraudKeys.list(params),
    queryFn:  () => api.fraud.getAlerts(params),
    placeholderData: (prev) => prev,
    ...options,
  })
}

// ─── useFraudAlert (single) ───────────────────────────────────────────────────

export function useFraudAlert(
  transactionId: string,
  options?: Omit<UseQueryOptions<FraudAlert>, 'queryKey' | 'queryFn'>
) {
  return useQuery<FraudAlert>({
    queryKey: fraudKeys.detail(transactionId),
    queryFn:  () => api.fraud.getAlertById(transactionId),
    enabled:  Boolean(transactionId),
    ...options,
  })
}

// ─── useFraudAlertSummary ─────────────────────────────────────────────────────

export function useFraudAlertSummary(
  params?: FraudAlertParams,
  options?: Omit<UseQueryOptions<FraudAlertSummary>, 'queryKey' | 'queryFn'>
) {
  return useQuery<FraudAlertSummary>({
    queryKey: fraudKeys.summary(params),
    queryFn:  () => api.fraud.getAlertSummary(params),
    staleTime: 60_000,
    ...options,
  })
}

// ─── useFraudScore ────────────────────────────────────────────────────────────

export function useFraudScore(
  options?: Omit<UseMutationOptions<FraudScoreResponse, unknown, FraudScoreRequest>, 'mutationFn'>
) {
  return useMutation<FraudScoreResponse, unknown, FraudScoreRequest>({
    mutationFn: (request) => api.fraud.getScore(request),
    ...options,
  })
}

// ─── useFraudBatchScore ───────────────────────────────────────────────────────

export function useFraudBatchScore(
  options?: Omit<UseMutationOptions<FraudScoreResponse[], unknown, FraudScoreRequest[]>, 'mutationFn'>
) {
  return useMutation<FraudScoreResponse[], unknown, FraudScoreRequest[]>({
    mutationFn: (requests) => api.fraud.batchScore(requests),
    ...options,
  })
}
