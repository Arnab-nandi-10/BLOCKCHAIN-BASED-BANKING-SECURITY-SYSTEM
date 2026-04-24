import {
  useQuery,
  type UseQueryOptions,
} from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import type {
  BlockchainAuditListParams,
  BlockchainLedgerRecord,
  BlockchainServiceHealth,
  BlockchainVerificationResult,
  BlockchainFabricHealth,
  BlockchainTransactionRecord,
  PageResponse,
  PaginationParams,
} from '@/types'

// ─── Query Keys ───────────────────────────────────────────────────────────────

export const blockchainKeys = {
  all:            ['blockchain']                                        as const,
  txDetail:       (txId: string) => [...blockchainKeys.all, 'tx', 'detail', txId] as const,
  txList:         (p: { tenantId: string; page?: number; size?: number }) =>
    [...blockchainKeys.all, 'tx', 'list', p] as const,
  txVerify:       (txId: string) => [...blockchainKeys.all, 'tx', 'verify', txId] as const,
  txHistory:      (txId: string) => [...blockchainKeys.all, 'tx', 'history', txId] as const,
  auditDetail:    (id: string)   => [...blockchainKeys.all, 'audit', 'detail', id] as const,
  auditVerify:    (id: string)   => [...blockchainKeys.all, 'audit', 'verify', id] as const,
  auditHistory:   (id: string)   => [...blockchainKeys.all, 'audit', 'history', id] as const,
  auditList:      (p: BlockchainAuditListParams) => [...blockchainKeys.all, 'audit', 'list', p] as const,
  serviceHealth:  ()             => [...blockchainKeys.all, 'health', 'service']  as const,
  fabricHealth:   ()             => [...blockchainKeys.all, 'health', 'fabric'] as const,
}

// ─── useBlockchainTransaction (single) ───────────────────────────────────────

export function useBlockchainTransaction(
  txId: string,
  options?: Omit<UseQueryOptions<BlockchainTransactionRecord>, 'queryKey' | 'queryFn'>
) {
  return useQuery<BlockchainTransactionRecord>({
    queryKey: blockchainKeys.txDetail(txId),
    queryFn: () => api.blockchain.getTransaction(txId),
    enabled: Boolean(txId),
    ...options,
  })
}

// ─── useBlockchainTransactions (tenant list) ─────────────────────────────────

export function useBlockchainTransactions(
  tenantId: string,
  params: Pick<PaginationParams, 'page' | 'size'> = {},
  options?: Omit<UseQueryOptions<PageResponse<BlockchainLedgerRecord>>, 'queryKey' | 'queryFn'>
) {
  const queryParams = { tenantId, ...params }

  return useQuery<PageResponse<BlockchainLedgerRecord>>({
    queryKey: blockchainKeys.txList(queryParams),
    queryFn: () => api.blockchain.listTransactions(queryParams),
    enabled: Boolean(tenantId),
    placeholderData: (prev) => prev,
    ...options,
  })
}

// ─── useVerifyTransaction ─────────────────────────────────────────────────────

export function useVerifyTransaction(
  txId: string,
  options?: Omit<UseQueryOptions<BlockchainVerificationResult>, 'queryKey' | 'queryFn'>
) {
  return useQuery<BlockchainVerificationResult>({
    queryKey: blockchainKeys.txVerify(txId),
    queryFn:  () => api.blockchain.verifyTransaction(txId),
    enabled:  Boolean(txId),
    ...options,
  })
}

// ─── useTransactionHistory ────────────────────────────────────────────────────

export function useTransactionHistory(
  txId: string,
  options?: Omit<UseQueryOptions<unknown[]>, 'queryKey' | 'queryFn'>
) {
  return useQuery<unknown[]>({
    queryKey: blockchainKeys.txHistory(txId),
    queryFn: () => api.blockchain.getTransactionHistory(txId),
    enabled: Boolean(txId),
    ...options,
  })
}

// ─── useBlockchainAuditRecord (single) ───────────────────────────────────────

export function useBlockchainAuditRecord(
  auditId: string,
  options?: Omit<UseQueryOptions<Record<string, unknown>>, 'queryKey' | 'queryFn'>
) {
  return useQuery<Record<string, unknown>>({
    queryKey: blockchainKeys.auditDetail(auditId),
    queryFn: () => api.blockchain.getAuditRecord(auditId),
    enabled: Boolean(auditId),
    ...options,
  })
}

// ─── useBlockchainAuditRecords (list) ────────────────────────────────────────

export function useBlockchainAuditRecords(
  params: BlockchainAuditListParams,
  options?: Omit<UseQueryOptions<Record<string, unknown>[]>, 'queryKey' | 'queryFn'>
) {
  return useQuery<Record<string, unknown>[]>({
    queryKey: blockchainKeys.auditList(params),
    queryFn: () => api.blockchain.listAuditRecords(params),
    enabled: Boolean(params.tenantId),
    ...options,
  })
}

// ─── useVerifyAudit ───────────────────────────────────────────────────────────

export function useVerifyAudit(
  auditId: string,
  options?: Omit<UseQueryOptions<BlockchainVerificationResult>, 'queryKey' | 'queryFn'>
) {
  return useQuery<BlockchainVerificationResult>({
    queryKey: blockchainKeys.auditVerify(auditId),
    queryFn:  () => api.blockchain.verifyAudit(auditId),
    enabled:  Boolean(auditId),
    ...options,
  })
}

// ─── useAuditHistory ──────────────────────────────────────────────────────────

export function useAuditHistory(
  auditId: string,
  options?: Omit<UseQueryOptions<unknown[]>, 'queryKey' | 'queryFn'>
) {
  return useQuery<unknown[]>({
    queryKey: blockchainKeys.auditHistory(auditId),
    queryFn: () => api.blockchain.getAuditHistory(auditId),
    enabled: Boolean(auditId),
    ...options,
  })
}

// ─── useBlockchainServiceHealth ───────────────────────────────────────────────

export function useBlockchainServiceHealth(
  options?: Omit<UseQueryOptions<BlockchainServiceHealth>, 'queryKey' | 'queryFn'>
) {
  return useQuery<BlockchainServiceHealth>({
    queryKey: blockchainKeys.serviceHealth(),
    queryFn: () => api.blockchain.getHealth(),
    staleTime: 30_000,
    refetchInterval: 60_000,
    ...options,
  })
}

// ─── useFabricHealth ──────────────────────────────────────────────────────────

/**
 * Polls the Fabric health endpoint.  Returns the live connectivity status,
 * mode (REAL_FABRIC vs SIMULATED_FALLBACK), channel name, and peer endpoint.
 * Staletime is 30 s.
 */
export function useFabricHealth(
  options?: Omit<UseQueryOptions<BlockchainFabricHealth>, 'queryKey' | 'queryFn'>
) {
  return useQuery<BlockchainFabricHealth>({
    queryKey: blockchainKeys.fabricHealth(),
    queryFn:  () => api.blockchain.getFabricHealth(),
    staleTime: 30_000,
    refetchInterval: 60_000,
    ...options,
  })
}
