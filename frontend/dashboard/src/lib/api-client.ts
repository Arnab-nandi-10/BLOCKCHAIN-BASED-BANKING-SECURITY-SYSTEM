import axios, {
  AxiosError,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios'
import Cookies from 'js-cookie'
import type {
  AuthResponse,
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
  PageResponse,
  User,
  TransactionListParams,
  AuditListParams,
  FraudAlertParams,
  TenantListParams,
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
      'data' in d &&
      'timestamp' in d
    ) {
      return { ...response, data: d.data }
    }
    return response
  },
  (error: AxiosError) => {
    const requestUrl = error.config?.url ?? ''
    const isAuthEntryRequest =
      requestUrl.includes('/api/v1/auth/login') ||
      requestUrl.includes('/api/v1/auth/register')

    if (error.response?.status === 401 && !isAuthEntryRequest) {
      // Clear persisted auth
      Cookies.remove('accessToken')
      Cookies.remove('refreshToken')
      Cookies.remove('tenantId')
      if (typeof window !== 'undefined') {
        localStorage.removeItem('bbss-auth-storage')
        window.location.href = '/login'
      }
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

  logout: () =>
    post<void>('/api/v1/auth/logout'),

  getMe: () =>
    get<User>('/api/v1/auth/me'),
}

// ─── Transactions API ─────────────────────────────────────────────────────────

const transactions = {
  list: (params?: TransactionListParams) =>
    get<PageResponse<Transaction>>('/api/v1/transactions', params as Record<string, unknown>),

  getById: (id: string) =>
    get<Transaction>(`/api/v1/transactions/${id}`),

  submit: (req: SubmitTransactionRequest) =>
    post<Transaction>('/api/v1/transactions', req),

  getStats: () =>
    get<TransactionStats>('/api/v1/transactions/stats'),
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
}

// ─── Fraud API ────────────────────────────────────────────────────────────────

const fraud = {
  getAlerts: (params?: FraudAlertParams) =>
    get<PageResponse<FraudAlert>>('/api/v1/fraud/alerts', params as Record<string, unknown>),

  getScore: (req: FraudScoreRequest) =>
    post<FraudScoreResponse>('/api/v1/fraud/score', req),
}

// ─── Export ───────────────────────────────────────────────────────────────────

export const api = { auth, transactions, audit, tenants, fraud }
export default apiClient
