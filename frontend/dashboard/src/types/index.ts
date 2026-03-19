// ─── Enums / Literal Unions ──────────────────────────────────────────────────

export type TransactionType = 'TRANSFER' | 'PAYMENT' | 'WITHDRAWAL' | 'DEPOSIT'

export type TransactionStatus =
  | 'SUBMITTED'
  | 'PENDING_FRAUD_CHECK'
  | 'VERIFIED'
  | 'FRAUD_HOLD'
  | 'BLOCKED'
  | 'COMPLETED'
  | 'FAILED'

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AuditStatus = 'PENDING' | 'COMMITTED' | 'FAILED'
export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'PENDING' | 'DELETED'
export type TenantPlan = 'STARTER' | 'PROFESSIONAL' | 'ENTERPRISE'

// ─── Auth ─────────────────────────────────────────────────────────────────────

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  roles: string[]
  tenantId: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  tenantId: string
  user: User
}

export interface LoginRequest {
  email: string
  password: string
  tenantId: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  tenantId: string
}

// ─── Transaction ──────────────────────────────────────────────────────────────

export interface Transaction {
  transactionId: string
  tenantId: string
  fromAccount: string
  toAccount: string
  amount: number
  currency: string
  type: TransactionType
  status: TransactionStatus
  blockchainTxId?: string
  blockNumber?: string
  fraudScore: number
  fraudRiskLevel?: string
  createdAt: string
  completedAt?: string
}

export interface TransactionStats {
  totalSubmitted: number
  totalVerified: number
  totalBlocked: number
  totalFraudHold: number
  totalCompleted: number
  totalFailed: number
}

export interface SubmitTransactionRequest {
  fromAccount: string
  toAccount: string
  amount: number
  currency: string
  type: TransactionType
}

// ─── Audit ────────────────────────────────────────────────────────────────────

export interface AuditEntry {
  auditId: string
  tenantId: string
  entityType: string
  entityId: string
  action: string
  actorId: string
  actorType: string
  ipAddress?: string
  payload?: string
  blockchainTxId?: string
  blockNumber?: string
  status: AuditStatus
  occurredAt: string
}

export interface AuditSummary {
  totalEntries: number
  committedToBlockchain: number
  pending: number
  failed: number
}

// ─── Fraud ────────────────────────────────────────────────────────────────────

export interface FraudAlert {
  transactionId: string
  score: number
  riskLevel: RiskLevel
  triggeredRules: string[]
  recommendation: string
  detectedAt: string
}

export interface FraudScoreRequest {
  transactionId: string
  fromAccount: string
  toAccount: string
  amount: number
  currency: string
  type: TransactionType
}

export interface FraudScoreResponse {
  transactionId: string
  score: number
  riskLevel: RiskLevel
  triggeredRules: string[]
  recommendation: string
}

// ─── Tenant ───────────────────────────────────────────────────────────────────

export interface Tenant {
  tenantId: string
  name: string
  adminEmail: string
  status: TenantStatus
  plan: TenantPlan
  apiKey: string
  createdAt: string
}

export interface CreateTenantRequest {
  name: string
  adminEmail: string
  plan: TenantPlan
}

// ─── Pagination & API Wrapper ─────────────────────────────────────────────────

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  timestamp: string
}

// ─── Query Params ─────────────────────────────────────────────────────────────

export interface PaginationParams {
  page?: number
  size?: number
  sort?: string
}

export interface TransactionListParams extends PaginationParams {
  status?: string
  fromDate?: string
  toDate?: string
  search?: string
  type?: string
}

export interface AuditListParams extends PaginationParams {
  entityType?: string
  action?: string
  fromDate?: string
  toDate?: string
  search?: string
}

export interface FraudAlertParams extends PaginationParams {
  riskLevel?: string
  fromDate?: string
  toDate?: string
}

export interface TenantListParams extends PaginationParams {
  status?: string
  search?: string
}
