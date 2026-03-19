package main

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"log"
	"strconv"
	"strings"
	"time"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// TransactionChaincode implements the Hyperledger Fabric smart contract functions
// for managing financial transactions with tamper-evident audit trails.
type TransactionChaincode struct {
	contractapi.Contract
}

// TransactionRecord represents a financial transaction stored on the blockchain ledger.
// The Amount field uses string type to avoid floating-point precision issues.
type TransactionRecord struct {
	TransactionID  string  `json:"transactionId"`
	TenantID       string  `json:"tenantId"`
	FromAccount    string  `json:"fromAccount"`
	ToAccount      string  `json:"toAccount"`
	Amount         string  `json:"amount"`
	Currency       string  `json:"currency"`
	Type           string  `json:"type"`
	Status         string  `json:"status"`
	FraudScore     float64 `json:"fraudScore"`
	FraudRiskLevel string  `json:"fraudRiskLevel"`
	CreatedAt      string  `json:"createdAt"`
	UpdatedAt      string  `json:"updatedAt"`
	BlockchainTxID string  `json:"blockchainTxId"`
	Hash           string  `json:"hash"`
	DocType        string  `json:"docType"`
}

// HistoryRecord represents a single entry in the history of a transaction key.
type HistoryRecord struct {
	TxID      string             `json:"txId"`
	Value     *TransactionRecord `json:"value"`
	Timestamp string             `json:"timestamp"`
	IsDelete  bool               `json:"isDelete"`
}

// InitLedger initializes the chaincode ledger. This is a no-op for the transaction
// chaincode as no seed data is required; it simply logs that initialization completed.
func (t *TransactionChaincode) InitLedger(ctx contractapi.TransactionContextInterface) error {
	log.Println("TransactionChaincode: InitLedger called — initialization complete")
	return nil
}

// CreateTransaction creates a new immutable transaction record on the blockchain ledger.
// It enforces idempotency by rejecting duplicate transaction IDs. A SHA-256 hash of
// the core transaction fields is computed and stored for tamper detection.
func (t *TransactionChaincode) CreateTransaction(
	ctx contractapi.TransactionContextInterface,
	transactionID, tenantID, fromAccount, toAccount, amount, currency, txType, status string,
) (*TransactionRecord, error) {

	// Idempotency check: reject if this transactionID already exists on the ledger.
	existing, err := ctx.GetStub().GetState(transactionID)
	if err != nil {
		return nil, fmt.Errorf("failed to read world state for transaction %q: %w", transactionID, err)
	}
	if existing != nil {
		return nil, fmt.Errorf("transaction with ID %q already exists on the ledger", transactionID)
	}

	// Retrieve the deterministic transaction timestamp from the Fabric stub.
	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return nil, fmt.Errorf("failed to get transaction timestamp: %w", err)
	}
	timestamp := txTimestamp.AsTime().Format(time.RFC3339)

	// Compute SHA-256 hash of the core immutable fields for tamper evidence.
	hash := computeHash(transactionID, tenantID, fromAccount, toAccount, amount, currency)

	record := &TransactionRecord{
		TransactionID:  transactionID,
		TenantID:       tenantID,
		FromAccount:    fromAccount,
		ToAccount:      toAccount,
		Amount:         amount,
		Currency:       currency,
		Type:           txType,
		Status:         status,
		FraudScore:     0.0,
		FraudRiskLevel: "UNKNOWN",
		CreatedAt:      timestamp,
		UpdatedAt:      timestamp,
		BlockchainTxID: ctx.GetStub().GetTxID(),
		Hash:           hash,
		DocType:        "TRANSACTION",
	}

	recordJSON, err := json.Marshal(record)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal transaction record for %q: %w", transactionID, err)
	}

	if err := ctx.GetStub().PutState(transactionID, recordJSON); err != nil {
		return nil, fmt.Errorf("failed to write transaction %q to world state: %w", transactionID, err)
	}

	// Emit a chaincode event so off-chain listeners can react to new transactions.
	if err := ctx.GetStub().SetEvent("TransactionCreated", recordJSON); err != nil {
		return nil, fmt.Errorf("failed to emit TransactionCreated event for %q: %w", transactionID, err)
	}

	return record, nil
}

// UpdateTransactionStatus updates the processing status and fraud-analysis fields of an
// existing transaction. The core identity fields (from/to accounts, amount, currency) are
// not modified; the stored hash is recomputed from those same immutable fields so that
// VerifyTransactionIntegrity remains consistent.
func (t *TransactionChaincode) UpdateTransactionStatus(
	ctx contractapi.TransactionContextInterface,
	transactionID, newStatus, fraudScore, fraudRiskLevel string,
) (*TransactionRecord, error) {

	record, err := t.GetTransaction(ctx, transactionID)
	if err != nil {
		return nil, err
	}

	parsedFraudScore, err := strconv.ParseFloat(fraudScore, 64)
	if err != nil {
		return nil, fmt.Errorf("invalid fraud score value %q — must be a parseable float64: %w", fraudScore, err)
	}

	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return nil, fmt.Errorf("failed to get transaction timestamp: %w", err)
	}
	updatedAt := txTimestamp.AsTime().Format(time.RFC3339)

	record.Status         = newStatus
	record.FraudScore     = parsedFraudScore
	record.FraudRiskLevel = fraudRiskLevel
	record.UpdatedAt      = updatedAt
	record.BlockchainTxID = ctx.GetStub().GetTxID()

	// Recompute hash from the same immutable fields used at creation time.
	record.Hash = computeHash(
		record.TransactionID,
		record.TenantID,
		record.FromAccount,
		record.ToAccount,
		record.Amount,
		record.Currency,
	)

	recordJSON, err := json.Marshal(record)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal updated transaction %q: %w", transactionID, err)
	}

	if err := ctx.GetStub().PutState(transactionID, recordJSON); err != nil {
		return nil, fmt.Errorf("failed to write updated transaction %q to world state: %w", transactionID, err)
	}

	if err := ctx.GetStub().SetEvent("TransactionStatusUpdated", recordJSON); err != nil {
		return nil, fmt.Errorf("failed to emit TransactionStatusUpdated event for %q: %w", transactionID, err)
	}

	return record, nil
}

// GetTransaction retrieves a single transaction record by its unique transaction ID.
func (t *TransactionChaincode) GetTransaction(
	ctx contractapi.TransactionContextInterface,
	transactionID string,
) (*TransactionRecord, error) {

	recordJSON, err := ctx.GetStub().GetState(transactionID)
	if err != nil {
		return nil, fmt.Errorf("failed to read transaction %q from world state: %w", transactionID, err)
	}
	if recordJSON == nil {
		return nil, fmt.Errorf("transaction with ID %q does not exist on the ledger", transactionID)
	}

	var record TransactionRecord
	if err := json.Unmarshal(recordJSON, &record); err != nil {
		return nil, fmt.Errorf("failed to unmarshal transaction record for %q: %w", transactionID, err)
	}

	return &record, nil
}

// GetTransactionHistory returns the full ledger history for a transaction key, including
// all versions that were committed in prior blocks, with delete markers if applicable.
func (t *TransactionChaincode) GetTransactionHistory(
	ctx contractapi.TransactionContextInterface,
	transactionID string,
) ([]HistoryRecord, error) {

	resultsIterator, err := ctx.GetStub().GetHistoryForKey(transactionID)
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve history for transaction %q: %w", transactionID, err)
	}
	defer resultsIterator.Close()

	var history []HistoryRecord

	for resultsIterator.HasNext() {
		modification, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("error iterating history for transaction %q: %w", transactionID, err)
		}

		histEntry := HistoryRecord{
			TxID:      modification.TxId,
			Timestamp: modification.Timestamp.AsTime().Format(time.RFC3339),
			IsDelete:  modification.IsDelete,
		}

		if !modification.IsDelete && modification.Value != nil {
			var txRecord TransactionRecord
			if err := json.Unmarshal(modification.Value, &txRecord); err != nil {
				return nil, fmt.Errorf("failed to unmarshal history value for tx %q at blockchain tx %q: %w",
					transactionID, modification.TxId, err)
			}
			histEntry.Value = &txRecord
		}

		history = append(history, histEntry)
	}

	return history, nil
}

// QueryTransactionsByTenant performs a CouchDB rich query to retrieve all TRANSACTION
// records belonging to the specified tenant. This function requires CouchDB as the
// peer state database; it will not work with the default LevelDB state database.
func (t *TransactionChaincode) QueryTransactionsByTenant(
	ctx contractapi.TransactionContextInterface,
	tenantID string,
) ([]*TransactionRecord, error) {

	queryString := fmt.Sprintf(
		`{"selector":{"docType":"TRANSACTION","tenantId":"%s"},"use_index":["_design/indexTenantDoc","indexTenant"]}`,
		tenantID,
	)
	return executeTransactionRichQuery(ctx, queryString)
}

// QueryTransactionsByStatus performs a CouchDB rich query to retrieve all TRANSACTION
// records for a given tenant filtered by processing status. Requires CouchDB.
func (t *TransactionChaincode) QueryTransactionsByStatus(
	ctx contractapi.TransactionContextInterface,
	tenantID, status string,
) ([]*TransactionRecord, error) {

	queryString := fmt.Sprintf(
		`{"selector":{"docType":"TRANSACTION","tenantId":"%s","status":"%s"}}`,
		tenantID,
		status,
	)
	return executeTransactionRichQuery(ctx, queryString)
}

// VerifyTransactionIntegrity recomputes the SHA-256 hash from the stored transaction
// fields and compares it against the hash recorded at creation time. A mismatch indicates
// that the state-database record was tampered with outside of chaincode logic.
func (t *TransactionChaincode) VerifyTransactionIntegrity(
	ctx contractapi.TransactionContextInterface,
	transactionID string,
) (bool, error) {

	record, err := t.GetTransaction(ctx, transactionID)
	if err != nil {
		return false, err
	}

	// Recompute using the exact same fields and order as CreateTransaction.
	expectedHash := computeHash(
		record.TransactionID,
		record.TenantID,
		record.FromAccount,
		record.ToAccount,
		record.Amount,
		record.Currency,
	)

	return record.Hash == expectedHash, nil
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

// executeTransactionRichQuery executes the provided CouchDB selector query string and
// collects the matching TransactionRecord values into a slice.
func executeTransactionRichQuery(
	ctx contractapi.TransactionContextInterface,
	queryString string,
) ([]*TransactionRecord, error) {

	resultsIterator, err := ctx.GetStub().GetQueryResult(queryString)
	if err != nil {
		return nil, fmt.Errorf("failed to execute rich query: %w", err)
	}
	defer resultsIterator.Close()

	var records []*TransactionRecord

	for resultsIterator.HasNext() {
		queryResult, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("error iterating rich query results: %w", err)
		}

		var record TransactionRecord
		if err := json.Unmarshal(queryResult.Value, &record); err != nil {
			return nil, fmt.Errorf("failed to unmarshal rich query result: %w", err)
		}
		records = append(records, &record)
	}

	return records, nil
}

// computeHash computes a hex-encoded SHA-256 digest of the pipe-delimited concatenation
// of all provided string fields. The pipe separator prevents hash collisions arising from
// adjacent-field boundary ambiguity.
func computeHash(fields ...string) string {
	combined := strings.Join(fields, "|")
	digest := sha256.Sum256([]byte(combined))
	return fmt.Sprintf("%x", digest)
}
