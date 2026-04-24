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
  action:  (action: string, p: Pick<AuditListParams, 'page' | 'size'>) =>
    [...auditKeys.all, 'action', action, p] as const,
  dateRange: (
    from: string,
    to: string,
    p: Pick<AuditListParams, 'page' | 'size' | 'sort'>
  ) => [...auditKeys.all, 'date-range', from, to, p] as const,
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

// ─── useAuditByAction ────────────────────────────────────────────────────────

export function useAuditByAction(
  action: string,
  params: Pick<AuditListParams, 'page' | 'size'> = {},
  options?: Omit<UseQueryOptions<PageResponse<AuditEntry>>, 'queryKey' | 'queryFn'>
) {
  return useQuery<PageResponse<AuditEntry>>({
    queryKey: auditKeys.action(action, params),
    queryFn: () => api.audit.listByAction(action, params),
    enabled: Boolean(action),
    placeholderData: (prev) => prev,
    ...options,
  })
}

// ─── useAuditByDateRange ─────────────────────────────────────────────────────

export function useAuditByDateRange(
  from: string,
  to: string,
  params: Pick<AuditListParams, 'page' | 'size' | 'sort'> = {},
  options?: Omit<UseQueryOptions<PageResponse<AuditEntry>>, 'queryKey' | 'queryFn'>
) {
  return useQuery<PageResponse<AuditEntry>>({
    queryKey: auditKeys.dateRange(from, to, params),
    queryFn: () => api.audit.listByDateRange(from, to, params),
    enabled: Boolean(from) && Boolean(to),
    placeholderData: (prev) => prev,
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
