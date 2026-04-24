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
export type LedgerStatus = 'NOT_REQUESTED' | 'PENDING_LEDGER' | 'COMMITTED' | 'FAILED_LEDGER'
export type VerificationStatus = 'NOT_VERIFIED' | 'VERIFIED' | 'HASH_MISMATCH' | 'UNAVAILABLE'
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

export interface AuthUserInfo {
  id: string
  email: string
  firstName: string
  lastName: string
  roles: string[]
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
  ledgerStatus?: LedgerStatus
  verificationStatus?: VerificationStatus
  blockchainTxId?: string
  blockNumber?: string
  fraudScore: number
  fraudRiskLevel?: string
  fraudDecision?: string
  fraudRecommendation?: string
  reviewRequired?: boolean
  triggeredRules?: string[]
  explanations?: string[]
  payloadHash?: string
  recordHash?: string
  previousHash?: string
  correlationId?: string
  createdAt: string
  completedAt?: string
}

export interface DailyVolumePoint {
  date: string
  label: string
  transactionCount: number
  totalAmount: number
}

export interface TransactionStats {
  tenantId?: string
  totalTransactions: number
  totalAmount: number
  totalSubmitted: number
  totalVerified: number
  totalBlocked: number
  totalFraudHold: number
  totalCompleted: number
  totalFailed: number
  statusCounts?: Record<string, number>
  ledgerStatusCounts?: Record<string, number>
  verificationStatusCounts?: Record<string, number>
  fraudRiskDistribution?: Record<string, number>
  dailyVolume?: DailyVolumePoint[]
  windowTransactions?: number
  windowAmount?: number
  from?: string
  to?: string
}

export interface SubmitTransactionRequest {
  fromAccount: string
  toAccount: string
  amount: number
  currency: string
  type: TransactionType
  metadata?: Record<string, string>
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
  verificationStatus?: VerificationStatus
  status: AuditStatus
  occurredAt: string
}

export interface AuditSummary {
  totalEntries: number
  last24hEntries?: number
  committedToBlockchain: number
  pending: number
  pendingEntries?: number
  failed: number
  failedEntries?: number
  verificationCounts?: Record<string, number>
  verifiedRecords?: number
  hashMismatchRecords?: number
  verificationUnavailableRecords?: number
  actionBreakdown?: Record<string, number>
  generatedAt?: string
}

export interface BlockchainVerificationResult {
  recordId: string
  verificationStatus: VerificationStatus
  valid: boolean
  payloadHash?: string
  recomputedPayloadHash?: string
  recordHash?: string
  recomputedRecordHash?: string
  previousHash?: string
  verifiedAt?: string
}

export interface BlockchainSubmitRequest {
  transactionId: string
  tenantId: string
  fromAccount: string
  toAccount: string
  amount: number
  currency: string
  type: TransactionType
  status: TransactionStatus | string
  fraudScore?: number
  riskLevel?: RiskLevel | string
  decision?: string
  decisionReason?: string
  timestamp?: string
  ipAddress?: string
  metadata?: Record<string, string>
}

export interface BlockchainSubmitResult {
  transactionId: string
  blockchainTxId?: string
  blockNumber?: string
  ledgerStatus: string
  verificationStatus: VerificationStatus | string
  payloadHash?: string
  recordHash?: string
  previousHash?: string
  success: boolean
  message: string
}

export interface BlockchainTransactionRecord {
  transactionId: string
  blockchainTxId?: string
  blockNumber?: string
  ledgerStatus: string
  verificationStatus: VerificationStatus | string
  payloadHash?: string
  recordHash?: string
  previousHash?: string
  status?: string
  timestamp?: string
}

export interface BlockchainLedgerRecord {
  id?: string
  transactionId: string
  tenantId: string
  blockchainTxId?: string
  blockNumber?: string
  chaincodeId?: string
  payload?: string
  status?: string
  ledgerStatus: string
  verificationStatus: VerificationStatus | string
  payloadHash?: string
  recordHash?: string
  previousHash?: string
  lastError?: string
  retryCount?: number
  nextRetryAt?: string
  createdAt: string
  updatedAt?: string
}

export interface BlockchainAuditRequest {
  auditId: string
  tenantId: string
  entityType: string
  entityId: string
  action: string
  actorId: string
  payload: string
  occurredAt?: string
}

export interface BlockchainAuditResult {
  auditId: string
  blockchainTxId?: string
  blockNumber?: string
  verificationStatus: VerificationStatus | string
  success: boolean
}

export interface BlockchainServiceHealth {
  status: string
  mode: 'REAL_FABRIC' | 'SIMULATED_FALLBACK' | string
  fabricEnabled?: boolean
  message: string
}

export interface BlockchainFabricHealth {
  status: 'UP' | 'DOWN' | 'SIMULATED'
  mode: 'REAL_FABRIC' | 'SIMULATED_FALLBACK'
  channel: string
  peerEndpoint: string
  effectiveHealth: 'UP' | 'DOWN' | 'DEGRADED'
  message: string
}

// ─── Fraud ────────────────────────────────────────────────────────────────────

export interface FraudAlert {
  transactionId: string
  tenantId: string
  score: number
  riskLevel: RiskLevel
  decision: string
  reviewRequired: boolean
  shouldBlock: boolean
  fallbackUsed: boolean
  triggeredRules: string[]
  explanations: string[]
  signalBreakdown?: Array<{
    key: string
    source: string
    weight: number
    severity: string
    explanation: string
    evidence?: Record<string, unknown>
  }>
  recommendation: string
  detectedAt: string
}

export interface FraudScoreRequest {
  transactionId: string
  tenantId: string
  fromAccount: string
  toAccount: string
  amount: number
  currency: string
  transactionType: TransactionType
  metadata?: Record<string, string>
}

export interface FraudScoreResponse {
  transactionId: string
  score: number
  riskLevel: RiskLevel
  triggeredRules: string[]
  explanations?: string[]
  recommendation: string
  mlScore?: number
  ruleScore?: number
  behavioralScore?: number
  decision?: string
  reviewRequired?: boolean
  shouldBlock?: boolean
  fallbackUsed?: boolean
  modelVersion?: string
  processingTimeMs?: number
}

export interface FraudAlertSummary {
  tenantId: string
  totalAlerts: number
  reviewRequiredCount: number
  decisionCounts: Record<string, number>
  riskLevelCounts: Record<string, number>
  fromDate?: string
  toDate?: string
}

export interface FraudServiceHealth {
  status: string
  service: string
  version: string
  modelLoaded?: boolean
  mode?: string
  fallbackEnabled?: boolean
}

// ─── Tenant ───────────────────────────────────────────────────────────────────

export interface Tenant {
  tenantId: string
  name: string
  adminEmail: string
  status: TenantStatus
  plan: TenantPlan
  apiKey: string
  webhookUrl?: string
  createdAt: string
  updatedAt?: string
  config?: Record<string, string>
  monthlyTransactionLimit: number
  maxUsers: number
}

export interface CreateTenantRequest {
  name: string
  adminEmail: string
  plan: TenantPlan
  webhookUrl?: string
}

// ─── Pagination & API Wrapper ─────────────────────────────────────────────────

export interface PageResponse<T> {
  content: T[]
  page?: number
  number?: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
  first?: boolean
  numberOfElements?: number
  empty?: boolean
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
  ledgerStatus?: string
  verificationStatus?: string
}

export interface AuditListParams extends PaginationParams {
  entityType?: string
  action?: string
  status?: string
  verificationStatus?: string
  fromDate?: string
  toDate?: string
  search?: string
}

export interface FraudAlertParams extends PaginationParams {
  riskLevel?: string
  decision?: string
  reviewRequired?: boolean
  fromDate?: string
  toDate?: string
  search?: string
}

export interface TenantListParams extends PaginationParams {
  status?: string
  search?: string
}

export interface BlockchainTransactionListParams extends PaginationParams {
  tenantId: string
}

export interface BlockchainAuditListParams {
  tenantId: string
  entityType?: string
  entityId?: string
  action?: string
  fromDate?: string
  toDate?: string
}
