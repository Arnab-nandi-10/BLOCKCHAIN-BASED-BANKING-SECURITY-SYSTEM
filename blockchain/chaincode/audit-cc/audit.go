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

// AuditChaincode implements the Hyperledger Fabric smart contract functions for
// creating and querying immutable audit-trail entries. Every audit entry is
// content-addressed with a SHA-256 hash to enable tamper detection.
type AuditChaincode struct {
	contractapi.Contract
}

// AuditRecord represents a single audit log entry stored on the blockchain ledger.
// The Payload field carries an opaque JSON string supplied by the caller, allowing
// audit consumers to embed any domain-specific context without constraining the
// chaincode schema.
type AuditRecord struct {
	AuditID        string `json:"auditId"`
	TenantID       string `json:"tenantId"`
	EntityType     string `json:"entityType"`
	EntityID       string `json:"entityId"`
	Action         string `json:"action"`
	ActorID        string `json:"actorId"`
	ActorType      string `json:"actorType"`
	IPAddress      string `json:"ipAddress"`
	Payload        string `json:"payload"`
	OccurredAt     string `json:"occurredAt"`
	BlockchainTxID string `json:"blockchainTxId"`
	Hash           string `json:"hash"`
	DocType        string `json:"docType"`
}

// AuditHistoryRecord represents one version of an AuditRecord retrieved from the
// blockchain key history iterator.
type AuditHistoryRecord struct {
	TxID      string       `json:"txId"`
	Value     *AuditRecord `json:"value"`
	Timestamp string       `json:"timestamp"`
	IsDelete  bool         `json:"isDelete"`
}

// InitLedger initializes the audit chaincode. This is intentionally a no-op
// because audit entries are created on demand; no seed data is required.
func (a *AuditChaincode) InitLedger(ctx contractapi.TransactionContextInterface) error {
	log.Println("AuditChaincode: InitLedger called — initialization complete")
	return nil
}

// CreateAuditEntry writes a new immutable audit record to the blockchain ledger.
// Idempotency is enforced by rejecting any attempt to create a second record with
// the same auditID. A SHA-256 hash of the core identifying fields is stored with
// the record to allow VerifyAuditIntegrity to detect out-of-band state tampering.
func (a *AuditChaincode) CreateAuditEntry(
	ctx contractapi.TransactionContextInterface,
	auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddress, payload string,
) (*AuditRecord, error) {

	// Idempotency check: reject if this auditID already exists on the ledger.
	existing, err := ctx.GetStub().GetState(auditID)
	if err != nil {
		return nil, fmt.Errorf("failed to read world state for audit entry %q: %w", auditID, err)
	}
	if existing != nil {
		return nil, fmt.Errorf("audit entry with ID %q already exists on the ledger", auditID)
	}

	// Retrieve the deterministic transaction timestamp provided by the Fabric stub.
	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return nil, fmt.Errorf("failed to get transaction timestamp: %w", err)
	}
	occurredAt := txTimestamp.AsTime().Format(time.RFC3339)

	// Compute SHA-256 hash of the core identifying fields for tamper evidence.
	hash := auditComputeHash(auditID, tenantID, entityType, entityID, action, actorID)

	record := &AuditRecord{
		AuditID:        auditID,
		TenantID:       tenantID,
		EntityType:     entityType,
		EntityID:       entityID,
		Action:         action,
		ActorID:        actorID,
		ActorType:      actorType,
		IPAddress:      ipAddress,
		Payload:        payload,
		OccurredAt:     occurredAt,
		BlockchainTxID: ctx.GetStub().GetTxID(),
		Hash:           hash,
		DocType:        "AUDIT",
	}

	recordJSON, err := json.Marshal(record)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal audit record for %q: %w", auditID, err)
	}

	if err := ctx.GetStub().PutState(auditID, recordJSON); err != nil {
		return nil, fmt.Errorf("failed to write audit entry %q to world state: %w", auditID, err)
	}

	// Emit a chaincode event so off-chain listeners can react to new audit entries.
	if err := ctx.GetStub().SetEvent("AuditEntryCreated", recordJSON); err != nil {
		return nil, fmt.Errorf("failed to emit AuditEntryCreated event for %q: %w", auditID, err)
	}

	return record, nil
}

// GetAuditEntry retrieves a single audit record by its unique audit ID.
func (a *AuditChaincode) GetAuditEntry(
	ctx contractapi.TransactionContextInterface,
	auditID string,
) (*AuditRecord, error) {

	recordJSON, err := ctx.GetStub().GetState(auditID)
	if err != nil {
		return nil, fmt.Errorf("failed to read audit entry %q from world state: %w", auditID, err)
	}
	if recordJSON == nil {
		return nil, fmt.Errorf("audit entry with ID %q does not exist on the ledger", auditID)
	}

	var record AuditRecord
	if err := json.Unmarshal(recordJSON, &record); err != nil {
		return nil, fmt.Errorf("failed to unmarshal audit record for %q: %w", auditID, err)
	}

	return &record, nil
}

// GetAuditHistory returns every committed version of an audit entry key, including
// the blockchain transaction ID, timestamp, and delete marker for each revision.
func (a *AuditChaincode) GetAuditHistory(
	ctx contractapi.TransactionContextInterface,
	auditID string,
) ([]AuditHistoryRecord, error) {

	resultsIterator, err := ctx.GetStub().GetHistoryForKey(auditID)
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve ledger history for audit entry %q: %w", auditID, err)
	}
	defer resultsIterator.Close()

	var history []AuditHistoryRecord

	for resultsIterator.HasNext() {
		modification, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("error iterating history for audit entry %q: %w", auditID, err)
		}

		histEntry := AuditHistoryRecord{
			TxID:      modification.TxId,
			Timestamp: modification.Timestamp.AsTime().Format(time.RFC3339),
			IsDelete:  modification.IsDelete,
		}

		if !modification.IsDelete && modification.Value != nil {
			var auditRecord AuditRecord
			if err := json.Unmarshal(modification.Value, &auditRecord); err != nil {
				return nil, fmt.Errorf(
					"failed to unmarshal history value for audit %q at blockchain tx %q: %w",
					auditID, modification.TxId, err,
				)
			}
			histEntry.Value = &auditRecord
		}

		history = append(history, histEntry)
	}

	return history, nil
}

// QueryAuditByTenant performs a CouchDB rich query to retrieve all AUDIT records
// belonging to the specified tenant. Requires CouchDB as the peer state database.
func (a *AuditChaincode) QueryAuditByTenant(
	ctx contractapi.TransactionContextInterface,
	tenantID string,
) ([]*AuditRecord, error) {

	queryString := fmt.Sprintf(
		`{"selector":{"docType":"AUDIT","tenantId":"%s"},"use_index":["_design/indexAuditTenantDoc","indexAuditTenant"]}`,
		tenantID,
	)
	return executeAuditRichQuery(ctx, queryString)
}

// QueryAuditByEntity performs a CouchDB rich query to retrieve all AUDIT records
// for a specific entity (identified by entityType + entityID) within a tenant.
// Requires CouchDB as the peer state database.
func (a *AuditChaincode) QueryAuditByEntity(
	ctx contractapi.TransactionContextInterface,
	tenantID, entityType, entityID string,
) ([]*AuditRecord, error) {

	queryString := fmt.Sprintf(
		`{"selector":{"docType":"AUDIT","tenantId":"%s","entityType":"%s","entityId":"%s"}}`,
		tenantID, entityType, entityID,
	)
	return executeAuditRichQuery(ctx, queryString)
}

// QueryAuditByAction performs a CouchDB rich query to retrieve all AUDIT records
// for a specific action within a tenant. Requires CouchDB as the peer state database.
func (a *AuditChaincode) QueryAuditByAction(
	ctx contractapi.TransactionContextInterface,
	tenantID, action string,
) ([]*AuditRecord, error) {

	queryString := fmt.Sprintf(
		`{"selector":{"docType":"AUDIT","tenantId":"%s","action":"%s"}}`,
		tenantID, action,
	)
	return executeAuditRichQuery(ctx, queryString)
}

// VerifyAuditIntegrity recomputes the SHA-256 hash from the stored audit-record
// fields and compares it against the hash recorded at entry-creation time.
// A mismatch indicates that the CouchDB state was tampered with outside of
// normal chaincode invocation.
func (a *AuditChaincode) VerifyAuditIntegrity(
	ctx contractapi.TransactionContextInterface,
	auditID string,
) (bool, error) {

	record, err := a.GetAuditEntry(ctx, auditID)
	if err != nil {
		return false, err
	}

	// Recompute using the exact same fields and order as CreateAuditEntry.
	expectedHash := auditComputeHash(
		record.AuditID,
		record.TenantID,
		record.EntityType,
		record.EntityID,
		record.Action,
		record.ActorID,
	)

	return record.Hash == expectedHash, nil
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

// executeAuditRichQuery executes the provided CouchDB selector query string and
// collects all matching AuditRecord values into a slice.
func executeAuditRichQuery(
	ctx contractapi.TransactionContextInterface,
	queryString string,
) ([]*AuditRecord, error) {

	resultsIterator, err := ctx.GetStub().GetQueryResult(queryString)
	if err != nil {
		return nil, fmt.Errorf("failed to execute audit rich query: %w", err)
	}
	defer resultsIterator.Close()

	var records []*AuditRecord

	for resultsIterator.HasNext() {
		queryResult, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("error iterating audit rich query results: %w", err)
		}

		var record AuditRecord
		if err := json.Unmarshal(queryResult.Value, &record); err != nil {
			return nil, fmt.Errorf("failed to unmarshal audit rich query result: %w", err)
		}
		records = append(records, &record)
	}

	return records, nil
}

// auditComputeHash computes a hex-encoded SHA-256 digest of the pipe-delimited
// concatenation of all provided string fields. The pipe separator prevents hash
// collisions from adjacent-field boundary ambiguity.
func auditComputeHash(fields ...string) string {
	combined := strings.Join(fields, "|")
	digest := sha256.Sum256([]byte(combined))
	return fmt.Sprintf("%x", digest)
}
