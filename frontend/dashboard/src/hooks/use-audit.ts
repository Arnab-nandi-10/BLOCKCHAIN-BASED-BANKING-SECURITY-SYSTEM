import {
  useQuery,
  type UseQueryOptions,
} from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import type {
  AuditEntry,
  AuditSummary,
  AuditListParams,
  PageResponse,
} from '@/types'

// ─── Query Keys ───────────────────────────────────────────────────────────────

export const auditKeys = {
  all:     ['audit']                             as const,
  lists:   () => [...auditKeys.all, 'list']      as const,
  list:    (p: AuditListParams) => [...auditKeys.lists(), p] as const,
  detail:  (id: string) => [...auditKeys.all, 'detail', id] as const,
  summary: () => [...auditKeys.all, 'summary']   as const,
  entity:  (type: string, id: string) =>
    [...auditKeys.all, 'entity', type, id]        as const,
}

// ─── useAuditEntries ──────────────────────────────────────────────────────────

export function useAuditEntries(
  params: AuditListParams = {},
  options?: Omit<
    UseQueryOptions<PageResponse<AuditEntry>>,
    'queryKey' | 'queryFn'
  >
) {
  return useQuery<PageResponse<AuditEntry>>({
    queryKey: auditKeys.list(params),
    queryFn: () => api.audit.list(params),
    placeholderData: (prev) => prev,
    ...options,
  })
}

// ─── useAuditEntry (single) ───────────────────────────────────────────────────

export function useAuditEntry(
  id: string,
  options?: Omit<UseQueryOptions<AuditEntry>, 'queryKey' | 'queryFn'>
) {
  return useQuery<AuditEntry>({
    queryKey: auditKeys.detail(id),
    queryFn: () => api.audit.getById(id),
    enabled: Boolean(id),
    ...options,
  })
}

// ─── useAuditByEntity ─────────────────────────────────────────────────────────

export function useAuditByEntity(
  entityType: string,
  entityId: string,
  params: AuditListParams = {},
  options?: Omit<
    UseQueryOptions<PageResponse<AuditEntry>>,
    'queryKey' | 'queryFn'
  >
) {
  return useQuery<PageResponse<AuditEntry>>({
    queryKey: [...auditKeys.entity(entityType, entityId), params],
    queryFn: () => api.audit.listByEntity(entityType, entityId, params),
    enabled: Boolean(entityType) && Boolean(entityId),
    ...options,
  })
}

// ─── useAuditSummary ──────────────────────────────────────────────────────────

export function useAuditSummary(
  options?: Omit<UseQueryOptions<AuditSummary>, 'queryKey' | 'queryFn'>
) {
  return useQuery<AuditSummary>({
    queryKey: auditKeys.summary(),
    queryFn: () => api.audit.getSummary(),
    staleTime: 60_000,
    ...options,
  })
}
