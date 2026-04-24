import axios, {
  AxiosError,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios'
import Cookies from 'js-cookie'
import { useAuthStore } from '@/store/auth-store'
import type {
  AuthResponse,
  AuthUserInfo,
  LoginRequest,
  RegisterRequest,
  Transaction,
  TransactionStats,
  SubmitTransactionRequest,
  AuditEntry,
  AuditSummary,
  Tenant,
  CreateTenantRequest,
  FraudAlert,
  FraudScoreRequest,
  FraudScoreResponse,
  FraudServiceHealth,
  BlockchainSubmitRequest,
  BlockchainSubmitResult,
  BlockchainTransactionRecord,
  BlockchainLedgerRecord,
  BlockchainAuditRequest,
  BlockchainAuditResult,
  BlockchainVerificationResult,
  BlockchainServiceHealth,
  BlockchainFabricHealth,
  PageResponse,
  TransactionListParams,
  AuditListParams,
  FraudAlertParams,
  TenantListParams,
  FraudAlertSummary,
  PaginationParams,
  BlockchainTransactionListParams,
  BlockchainAuditListParams,
} from '@/types'

// ─── Axios Instance ───────────────────────────────────────────────────────────

const BASE_URL =
  typeof window !== 'undefined'
    ? ''
    : process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'

const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
})

let refreshTokenPromise: Promise<AuthResponse> | null = null

function getRefreshToken() {
  return Cookies.get('refreshToken') ?? null
}

function clearSession() {
  useAuthStore.getState().clearAuth()
  if (typeof window !== 'undefined') {
    localStorage.removeItem('bbss-auth-storage')
  }
}

function redirectToLogin() {
  if (typeof window !== 'undefined') {
    window.location.href = '/login'
  }
}

function createRequestId() {
  return globalThis.crypto?.randomUUID?.() ?? `req-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

// ─── Request Interceptor ──────────────────────────────────────────────────────

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token =
      Cookies.get('accessToken') ??
      (typeof window !== 'undefined'
        ? localStorage.getItem('accessToken')
        : null)

    const tenantId =
      Cookies.get('tenantId') ??
      (typeof window !== 'undefined'
        ? localStorage.getItem('tenantId')
        : null)

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    if (tenantId) {
      config.headers['X-Tenant-ID'] = tenantId
    }
    if (!config.headers['X-Request-ID']) {
      config.headers['X-Request-ID'] = createRequestId()
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error)
)

// ─── Response Interceptor ─────────────────────────────────────────────────────

apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    // Unwrap ApiResponse<T> → T when the shape matches
    const d = response.data
    if (
      d !== null &&
      typeof d === 'object' &&
      'success' in d &&
      'data' in d
    ) {
      return { ...response, data: d.data }
    }
    return response
  },
  async (error: AxiosError) => {
    const requestUrl = error.config?.url ?? ''
    const isAuthEntryRequest =
      requestUrl.includes('/api/v1/auth/login') ||
      requestUrl.includes('/api/v1/auth/register') ||
      requestUrl.includes('/api/v1/auth/refresh') ||
      requestUrl.includes('/api/v1/auth/logout')

    const originalRequest = error.config as
      | (InternalAxiosRequestConfig & { _retry?: boolean })
      | undefined

    if (error.response?.status === 401 && !isAuthEntryRequest) {
      const refreshToken = getRefreshToken()

      if (originalRequest && refreshToken && !originalRequest._retry) {
        originalRequest._retry = true

        try {
          if (!refreshTokenPromise) {
            refreshTokenPromise = post<AuthResponse>('/api/v1/auth/refresh', { refreshToken })
              .then((response) => {
                useAuthStore.getState().setAuth(response)
                return response
              })
              .finally(() => {
                refreshTokenPromise = null
              })
          }

          await refreshTokenPromise
          return apiClient(originalRequest)
        } catch (refreshError) {
          clearSession()
          redirectToLogin()
          return Promise.reject(refreshError)
        }
      }

      clearSession()
      redirectToLogin()
    }
    return Promise.reject(error)
  }
)

// ─── Typed helper wrappers ────────────────────────────────────────────────────

async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await apiClient.get<T>(url, { params })
  return res.data
}

async function post<T>(url: string, data?: unknown): Promise<T> {
  const res = await apiClient.post<T>(url, data)
  return res.data
}

async function put<T>(url: string, data?: unknown): Promise<T> {
  const res = await apiClient.put<T>(url, data)
  return res.data
}

async function del<T>(url: string): Promise<T> {
  const res = await apiClient.delete<T>(url)
  return res.data
}

// ─── Auth API ─────────────────────────────────────────────────────────────────

const auth = {
  login: (req: LoginRequest) =>
    post<AuthResponse>('/api/v1/auth/login', req),

  register: (req: RegisterRequest) =>
    post<AuthResponse>('/api/v1/auth/register', req),

  refreshToken: (token: string) =>
    post<AuthResponse>('/api/v1/auth/refresh', { refreshToken: token }),

  logout: () => {
    const refreshToken = getRefreshToken()
    return refreshToken
      ? post<void>('/api/v1/auth/logout', { refreshToken })
      : Promise.resolve(undefined)
  },

  getMe: () =>
    get<AuthUserInfo>('/api/v1/auth/me'),
}

// ─── Transactions API ─────────────────────────────────────────────────────────

const transactions = {
  list: (params?: TransactionListParams) =>
    get<PageResponse<Transaction>>('/api/v1/transactions', params as Record<string, unknown>),

  listByStatus: (status: string, params?: Pick<TransactionListParams, 'page' | 'size'>) =>
    get<PageResponse<Transaction>>(
      `/api/v1/transactions/status/${status}`,
      params as Record<string, unknown>
    ),

  getById: (id: string) =>
    get<Transaction>(`/api/v1/transactions/${id}`),

  submit: (req: SubmitTransactionRequest) =>
    post<Transaction>('/api/v1/transactions', req),

  getStats: (params?: Pick<TransactionListParams, 'fromDate' | 'toDate'>) =>
    get<TransactionStats>('/api/v1/transactions/stats', params as Record<string, unknown>),
}

// ─── Audit API ────────────────────────────────────────────────────────────────

const audit = {
  list: (params?: AuditListParams) =>
    get<PageResponse<AuditEntry>>('/api/v1/audit', params as Record<string, unknown>),

  getById: (id: string) =>
    get<AuditEntry>(`/api/v1/audit/${id}`),

  listByEntity: (
    entityType: string,
    entityId: string,
    params?: AuditListParams
  ) =>
    get<PageResponse<AuditEntry>>(
      `/api/v1/audit/entity/${entityType}/${entityId}`,
      params as Record<string, unknown>
    ),

  listByAction: (action: string, params?: Pick<AuditListParams, 'page' | 'size'>) =>
    get<PageResponse<AuditEntry>>(
      `/api/v1/audit/action/${action}`,
      params as Record<string, unknown>
    ),

  listByDateRange: (
    from: string,
    to: string,
    params?: PaginationParams
  ) =>
    get<PageResponse<AuditEntry>>('/api/v1/audit/date-range', {
      from,
      to,
      ...(params as Record<string, unknown>),
    }),

  getSummary: () =>
    get<AuditSummary>('/api/v1/audit/summary'),
}

// ─── Tenants API ──────────────────────────────────────────────────────────────

const tenants = {
  list: (params?: TenantListParams) =>
    get<PageResponse<Tenant>>('/api/v1/tenants', params as Record<string, unknown>),

  getById: (id: string) =>
    get<Tenant>(`/api/v1/tenants/${id}`),

  create: (req: CreateTenantRequest) =>
    post<Tenant>('/api/v1/tenants', req),

  activate: (id: string) =>
    put<Tenant>(`/api/v1/tenants/${id}/activate`),

  suspend: (id: string) =>
    put<Tenant>(`/api/v1/tenants/${id}/suspend`),

  delete: (id: string) =>
    del<void>(`/api/v1/tenants/${id}`),

  regenerateApiKey: (id: string) =>
    post<Tenant>(`/api/v1/tenants/${id}/api-key/regenerate`),

  updateConfig: (id: string, config: Record<string, string>) =>
    put<Tenant>(`/api/v1/tenants/${id}/config`, config),
}

// ─── Fraud API ────────────────────────────────────────────────────────────────

const fraud = {
  getScore: (req: FraudScoreRequest) =>
    post<FraudScoreResponse>('/api/v1/fraud/score', req),

  batchScore: (requests: FraudScoreRequest[]) =>
    post<FraudScoreResponse[]>('/api/v1/fraud/batch-score', requests),

  getHealth: () =>
    get<FraudServiceHealth>('/api/v1/fraud/health'),

  getAlerts: (params?: FraudAlertParams) =>
    get<PageResponse<FraudAlert>>('/api/v1/fraud/alerts', params as Record<string, unknown>),

  getAlertSummary: (params?: FraudAlertParams) =>
    get<FraudAlertSummary>('/api/v1/fraud/alerts/summary', params as Record<string, unknown>),

  getAlertById: (transactionId: string) =>
    get<FraudAlert>(`/api/v1/fraud/alerts/${transactionId}`),
}

// ─── Blockchain API ───────────────────────────────────────────────────────────

const blockchain = {
  submitTransaction: (req: BlockchainSubmitRequest) =>
    post<BlockchainSubmitResult>('/api/v1/blockchain/transactions', req),

  getTransaction: (txId: string) =>
    get<BlockchainTransactionRecord>(`/api/v1/blockchain/transactions/${txId}`),

  verifyTransaction: (txId: string) =>
    get<BlockchainVerificationResult>(`/api/v1/blockchain/transactions/${txId}/verify`),

  getTransactionHistory: (txId: string) =>
    get<unknown[]>(`/api/v1/blockchain/transactions/${txId}/history`),

  listTransactions: (params: BlockchainTransactionListParams) =>
    get<PageResponse<BlockchainLedgerRecord>>('/api/v1/blockchain/transactions', params as unknown as Record<string, unknown>),

  submitAuditEntry: (req: BlockchainAuditRequest) =>
    post<BlockchainAuditResult>('/api/v1/blockchain/audit', req),

  getAuditRecord: (auditId: string) =>
    get<Record<string, unknown>>(`/api/v1/blockchain/audit/${auditId}`),

  verifyAudit: (auditId: string) =>
    get<BlockchainVerificationResult>(`/api/v1/blockchain/audit/${auditId}/verify`),

  getAuditHistory: (auditId: string) =>
    get<unknown[]>(`/api/v1/blockchain/audit/${auditId}/history`),

  /** GET /api/v1/blockchain/audit — list with optional entity/action/date filters */
  listAuditRecords: (params: BlockchainAuditListParams) =>
    get<Record<string, unknown>[]>('/api/v1/blockchain/audit', params as unknown as Record<string, unknown>),

  /** GET /api/v1/blockchain/health/service — overall blockchain-service health */
  getHealth: () =>
    get<BlockchainServiceHealth>('/api/v1/blockchain/health/service'),

  /** GET /api/v1/blockchain/health/fabric — Fabric peer connectivity + mode */
  getFabricHealth: () =>
    get<BlockchainFabricHealth>('/api/v1/blockchain/health/fabric'),
}

// ─── Export ───────────────────────────────────────────────────────────────────

export const api = { auth, transactions, audit, tenants, fraud, blockchain }
export default apiClient
