package main

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

const (
	auditDocType       = "AUDIT"
	auditSchemaVersion = "2.0.0"
)

// AuditChaincode stores compliance-grade audit entries in Fabric.
type AuditChaincode struct {
	contractapi.Contract
}

// AuditRecordInput is the JSON payload accepted by CreateAudit.
// The payload must already be sanitised and must not contain sensitive raw data.
type AuditRecordInput struct {
	AuditID       string `json:"auditId"`
	TenantID      string `json:"tenantId"`
	EntityType    string `json:"entityType"`
	EntityID      string `json:"entityId"`
	Action        string `json:"action"`
	ActorID       string `json:"actorId"`
	ActorType     string `json:"actorType"`
	IPAddressHash string `json:"ipAddressHash,omitempty"`
	TransactionID string `json:"transactionId,omitempty"`
	FraudScore    string `json:"fraudScore,omitempty"`
	RiskLevel     string `json:"riskLevel,omitempty"`
	Decision      string `json:"decision,omitempty"`
	Explanation   string `json:"explanation,omitempty"`
	Payload       string `json:"payload"`
	PreviousHash  string `json:"previousHash,omitempty"`
	OccurredAt    string `json:"occurredAt,omitempty"`
}

// AuditRecord is the immutable value persisted on the ledger.
type AuditRecord struct {
	AuditID        string `json:"auditId"`
	TenantID       string `json:"tenantId"`
	EntityType     string `json:"entityType"`
	EntityID       string `json:"entityId"`
	Action         string `json:"action"`
	ActorID        string `json:"actorId"`
	ActorType      string `json:"actorType"`
	IPAddressHash  string `json:"ipAddressHash,omitempty"`
	TransactionID  string `json:"transactionId,omitempty"`
	FraudScore     string `json:"fraudScore,omitempty"`
	RiskLevel      string `json:"riskLevel,omitempty"`
	Decision       string `json:"decision,omitempty"`
	Explanation    string `json:"explanation,omitempty"`
	Payload        string `json:"payload"`
	PayloadHash    string `json:"payloadHash"`
	PreviousHash   string `json:"previousHash,omitempty"`
	RecordHash     string `json:"recordHash"`
	OccurredAt     string `json:"occurredAt"`
	BlockchainTxID string `json:"blockchainTxId"`
	DocType        string `json:"docType"`
	SchemaVersion  string `json:"schemaVersion"`
}

// AuditHistoryRecord represents one revision returned by GetAuditHistory.
type AuditHistoryRecord struct {
	TxID      string       `json:"txId"`
	Value     *AuditRecord `json:"value"`
	Timestamp string       `json:"timestamp"`
	IsDelete  bool         `json:"isDelete"`
}

// AuditIntegrityVerification is returned by VerifyIntegrity.
type AuditIntegrityVerification struct {
	RecordID              string `json:"recordId"`
	PayloadHash           string `json:"payloadHash"`
	RecomputedPayloadHash string `json:"recomputedPayloadHash"`
	RecordHash            string `json:"recordHash"`
	RecomputedRecordHash  string `json:"recomputedRecordHash"`
	PreviousHash          string `json:"previousHash,omitempty"`
	Valid                 bool   `json:"valid"`
	VerifiedAt            string `json:"verifiedAt"`
}

func (a *AuditChaincode) InitLedger(ctx contractapi.TransactionContextInterface) error {
	log.Println("AuditChaincode: InitLedger called")
	return nil
}

// CreateAudit stores an immutable audit event supplied as structured JSON.
func (a *AuditChaincode) CreateAudit(
	ctx contractapi.TransactionContextInterface,
	recordJSON string,
) (*AuditRecord, error) {
	input, canonicalPayload, payloadHash, occurredAt, err := parseAuditInput(ctx, recordJSON)
	if err != nil {
		return nil, err
	}

	existing, err := ctx.GetStub().GetState(input.AuditID)
	if err != nil {
		return nil, fmt.Errorf("failed to read audit %q: %w", input.AuditID, err)
	}
	if existing != nil {
		return nil, fmt.Errorf("audit %q already exists on ledger", input.AuditID)
	}

	record := &AuditRecord{
		AuditID:        input.AuditID,
		TenantID:       input.TenantID,
		EntityType:     input.EntityType,
		EntityID:       input.EntityID,
		Action:         input.Action,
		ActorID:        input.ActorID,
		ActorType:      input.ActorType,
		IPAddressHash:  strings.TrimSpace(input.IPAddressHash),
		TransactionID:  strings.TrimSpace(input.TransactionID),
		FraudScore:     strings.TrimSpace(input.FraudScore),
		RiskLevel:      strings.TrimSpace(strings.ToUpper(input.RiskLevel)),
		Decision:       strings.TrimSpace(strings.ToUpper(input.Decision)),
		Explanation:    input.Explanation,
		Payload:        canonicalPayload,
		PayloadHash:    payloadHash,
		PreviousHash:   strings.TrimSpace(input.PreviousHash),
		OccurredAt:     occurredAt,
		BlockchainTxID: ctx.GetStub().GetTxID(),
		DocType:        auditDocType,
		SchemaVersion:  auditSchemaVersion,
	}
	record.RecordHash = computeAuditRecordHash(record)

	stored, err := json.Marshal(record)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal audit %q: %w", input.AuditID, err)
	}
	if err := ctx.GetStub().PutState(record.AuditID, stored); err != nil {
		return nil, fmt.Errorf("failed to persist audit %q: %w", input.AuditID, err)
	}
	if err := ctx.GetStub().SetEvent("AuditRecordCreated", stored); err != nil {
		return nil, fmt.Errorf("failed to emit AuditRecordCreated for %q: %w", input.AuditID, err)
	}

	return record, nil
}

// QueryRecord fetches the audit record by audit ID.
func (a *AuditChaincode) QueryRecord(
	ctx contractapi.TransactionContextInterface,
	auditID string,
) (*AuditRecord, error) {
	recordJSON, err := ctx.GetStub().GetState(auditID)
	if err != nil {
		return nil, fmt.Errorf("failed to read audit %q: %w", auditID, err)
	}
	if recordJSON == nil {
		return nil, fmt.Errorf("audit %q does not exist on ledger", auditID)
	}

	var record AuditRecord
	if err := json.Unmarshal(recordJSON, &record); err != nil {
		return nil, fmt.Errorf("failed to unmarshal audit %q: %w", auditID, err)
	}
	return &record, nil
}

// VerifyIntegrity recomputes both payload_hash and record_hash for the audit record.
func (a *AuditChaincode) VerifyIntegrity(
	ctx contractapi.TransactionContextInterface,
	auditID string,
) (*AuditIntegrityVerification, error) {
	record, err := a.QueryRecord(ctx, auditID)
	if err != nil {
		return nil, err
	}

	canonicalPayload, recomputedPayloadHash, err := canonicaliseAuditPayload(record.Payload)
	if err != nil {
		return nil, fmt.Errorf("failed to canonicalise payload for %q: %w", auditID, err)
	}

	recomputed := *record
	recomputed.Payload = canonicalPayload
	recomputed.PayloadHash = recomputedPayloadHash
	recomputed.RecordHash = computeAuditRecordHash(&recomputed)

	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return nil, fmt.Errorf("failed to get tx timestamp: %w", err)
	}

	return &AuditIntegrityVerification{
		RecordID:              auditID,
		PayloadHash:           record.PayloadHash,
		RecomputedPayloadHash: recomputedPayloadHash,
		RecordHash:            record.RecordHash,
		RecomputedRecordHash:  recomputed.RecordHash,
		PreviousHash:          record.PreviousHash,
		Valid:                 record.PayloadHash == recomputedPayloadHash && record.RecordHash == recomputed.RecordHash,
		VerifiedAt:            txTimestamp.AsTime().Format(time.RFC3339),
	}, nil
}

// CreateAuditEntry is a backwards-compatible wrapper used by older scripts.
func (a *AuditChaincode) CreateAuditEntry(
	ctx contractapi.TransactionContextInterface,
	auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddress, payload string,
) (*AuditRecord, error) {
	recordJSON, err := json.Marshal(AuditRecordInput{
		AuditID:       auditID,
		TenantID:      tenantID,
		EntityType:    entityType,
		EntityID:      entityID,
		Action:        action,
		ActorID:       actorID,
		ActorType:     normaliseActorType(actorType),
		IPAddressHash: hashOptional(ipAddress),
		Payload:       payload,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to marshal legacy audit input: %w", err)
	}
	return a.CreateAudit(ctx, string(recordJSON))
}

// GetAuditEntry is a backwards-compatible alias for QueryRecord.
func (a *AuditChaincode) GetAuditEntry(
	ctx contractapi.TransactionContextInterface,
	auditID string,
) (*AuditRecord, error) {
	return a.QueryRecord(ctx, auditID)
}

// VerifyAuditIntegrity is a backwards-compatible alias returning only the boolean outcome.
func (a *AuditChaincode) VerifyAuditIntegrity(
	ctx contractapi.TransactionContextInterface,
	auditID string,
) (bool, error) {
	result, err := a.VerifyIntegrity(ctx, auditID)
	if err != nil {
		return false, err
	}
	return result.Valid, nil
}

// GetAuditHistory returns all committed versions of the same audit key.
func (a *AuditChaincode) GetAuditHistory(
	ctx contractapi.TransactionContextInterface,
	auditID string,
) ([]AuditHistoryRecord, error) {
	resultsIterator, err := ctx.GetStub().GetHistoryForKey(auditID)
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve history for %q: %w", auditID, err)
	}
	defer resultsIterator.Close()

	var history []AuditHistoryRecord
	for resultsIterator.HasNext() {
		modification, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("failed to iterate history for %q: %w", auditID, err)
		}

		entry := AuditHistoryRecord{
			TxID:      modification.TxId,
			Timestamp: modification.Timestamp.AsTime().Format(time.RFC3339),
			IsDelete:  modification.IsDelete,
		}
		if !modification.IsDelete && modification.Value != nil {
			var record AuditRecord
			if err := json.Unmarshal(modification.Value, &record); err != nil {
				return nil, fmt.Errorf("failed to unmarshal history record for %q at tx %q: %w",
					auditID, modification.TxId, err)
			}
			entry.Value = &record
		}
		history = append(history, entry)
	}

	return history, nil
}

// QueryAuditByTenant performs a CouchDB rich query for all audit records of a tenant.
func (a *AuditChaincode) QueryAuditByTenant(
	ctx contractapi.TransactionContextInterface,
	tenantID string,
) ([]*AuditRecord, error) {
	queryString := fmt.Sprintf(`{"selector":{"docType":"%s","tenantId":"%s"}}`, auditDocType, tenantID)
	return executeAuditRichQuery(ctx, queryString)
}

// QueryAuditByEntity performs a CouchDB rich query for a tenant entity history.
func (a *AuditChaincode) QueryAuditByEntity(
	ctx contractapi.TransactionContextInterface,
	tenantID, entityType, entityID string,
) ([]*AuditRecord, error) {
	queryString := fmt.Sprintf(
		`{"selector":{"docType":"%s","tenantId":"%s","entityType":"%s","entityId":"%s"}}`,
		auditDocType,
		tenantID,
		strings.ToUpper(entityType),
		entityID,
	)
	return executeAuditRichQuery(ctx, queryString)
}

func executeAuditRichQuery(
	ctx contractapi.TransactionContextInterface,
	queryString string,
) ([]*AuditRecord, error) {
	resultsIterator, err := ctx.GetStub().GetQueryResult(queryString)
	if err != nil {
		return nil, fmt.Errorf("failed to execute audit query: %w", err)
	}
	defer resultsIterator.Close()

	var records []*AuditRecord
	for resultsIterator.HasNext() {
		queryResult, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("failed to iterate audit query results: %w", err)
		}

		var record AuditRecord
		if err := json.Unmarshal(queryResult.Value, &record); err != nil {
			return nil, fmt.Errorf("failed to unmarshal audit query result: %w", err)
		}
		records = append(records, &record)
	}

	return records, nil
}

func parseAuditInput(
	ctx contractapi.TransactionContextInterface,
	recordJSON string,
) (*AuditRecordInput, string, string, string, error) {
	var input AuditRecordInput
	if err := json.Unmarshal([]byte(recordJSON), &input); err != nil {
		return nil, "", "", "", fmt.Errorf("invalid audit record JSON: %w", err)
	}

	input.AuditID = strings.TrimSpace(input.AuditID)
	input.TenantID = strings.TrimSpace(input.TenantID)
	input.EntityType = strings.TrimSpace(strings.ToUpper(input.EntityType))
	input.EntityID = strings.TrimSpace(input.EntityID)
	input.Action = strings.TrimSpace(strings.ToUpper(input.Action))
	input.ActorID = strings.TrimSpace(input.ActorID)
	input.ActorType = normaliseActorType(input.ActorType)

	switch {
	case input.AuditID == "":
		return nil, "", "", "", fmt.Errorf("auditId is required")
	case input.TenantID == "":
		return nil, "", "", "", fmt.Errorf("tenantId is required")
	case input.EntityType == "":
		return nil, "", "", "", fmt.Errorf("entityType is required")
	case input.EntityID == "":
		return nil, "", "", "", fmt.Errorf("entityId is required")
	case input.Action == "":
		return nil, "", "", "", fmt.Errorf("action is required")
	case input.ActorID == "":
		return nil, "", "", "", fmt.Errorf("actorId is required")
	case input.ActorType == "":
		return nil, "", "", "", fmt.Errorf("actorType is required")
	}

	canonicalPayload, payloadHash, err := canonicaliseAuditPayload(input.Payload)
	if err != nil {
		return nil, "", "", "", err
	}

	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return nil, "", "", "", fmt.Errorf("failed to get transaction timestamp: %w", err)
	}

	occurredAt := strings.TrimSpace(input.OccurredAt)
	if occurredAt == "" {
		occurredAt = txTimestamp.AsTime().Format(time.RFC3339)
	}

	return &input, canonicalPayload, payloadHash, occurredAt, nil
}

func canonicaliseAuditPayload(payload string) (string, string, error) {
	trimmed := strings.TrimSpace(payload)
	if trimmed == "" {
		trimmed = "{}"
	}

	var payloadValue any
	if err := json.Unmarshal([]byte(trimmed), &payloadValue); err != nil {
		return "", "", fmt.Errorf("payload must be valid JSON: %w", err)
	}

	canonicalBytes, err := json.Marshal(payloadValue)
	if err != nil {
		return "", "", fmt.Errorf("failed to canonicalise payload JSON: %w", err)
	}

	canonical := string(canonicalBytes)
	return canonical, sha256HexAudit(canonical), nil
}

func computeAuditRecordHash(record *AuditRecord) string {
	return sha256HexAudit(strings.Join([]string{
		record.DocType,
		record.SchemaVersion,
		record.AuditID,
		record.TenantID,
		record.EntityType,
		record.EntityID,
		record.Action,
		record.ActorID,
		record.ActorType,
		record.IPAddressHash,
		record.TransactionID,
		record.FraudScore,
		record.RiskLevel,
		record.Decision,
		record.Explanation,
		record.OccurredAt,
		record.PayloadHash,
		record.PreviousHash,
	}, "|"))
}

func sha256HexAudit(input string) string {
	digest := sha256.Sum256([]byte(input))
	return fmt.Sprintf("%x", digest)
}

func normaliseActorType(value string) string {
	value = strings.TrimSpace(strings.ToUpper(value))
	switch value {
	case "USER", "SYSTEM", "API", "SERVICE":
		return value
	default:
		return "SYSTEM"
	}
}

func hashOptional(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	return sha256HexAudit(value)
}
