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
	transactionDocType       = "TRANSACTION"
	transactionSchemaVersion = "2.0.0"
)

// TransactionChaincode stores post-fraud transaction decisions in a tamper-evident form.
// Fraud scoring still happens off-chain; the ledger only anchors the decision outcome.
type TransactionChaincode struct {
	contractapi.Contract
}

// TransactionRecordInput is the JSON payload accepted by CreateRecord.
// The payload must already be sanitised by the caller; no plain sensitive data
// should be written to the ledger.
type TransactionRecordInput struct {
	TransactionID     string `json:"transactionId"`
	TenantID          string `json:"tenantId"`
	FromAccountMasked string `json:"fromAccountMasked"`
	ToAccountMasked   string `json:"toAccountMasked"`
	Amount            string `json:"amount"`
	Currency          string `json:"currency"`
	TransactionType   string `json:"transactionType"`
	Status            string `json:"status"`
	FraudScore        string `json:"fraudScore"`
	RiskLevel         string `json:"riskLevel"`
	Decision          string `json:"decision"`
	DecisionReason    string `json:"decisionReason"`
	Explanation       string `json:"explanation"`
	Payload           string `json:"payload"`
	PreviousHash      string `json:"previousHash,omitempty"`
	DecisionTimestamp string `json:"decisionTimestamp,omitempty"`
}

// TransactionRecord represents the immutable record persisted in Fabric.
type TransactionRecord struct {
	TransactionID     string `json:"transactionId"`
	TenantID          string `json:"tenantId"`
	FromAccountMasked string `json:"fromAccountMasked"`
	ToAccountMasked   string `json:"toAccountMasked"`
	Amount            string `json:"amount"`
	Currency          string `json:"currency"`
	TransactionType   string `json:"transactionType"`
	Status            string `json:"status"`
	FraudScore        string `json:"fraudScore"`
	RiskLevel         string `json:"riskLevel"`
	Decision          string `json:"decision"`
	DecisionReason    string `json:"decisionReason"`
	Explanation       string `json:"explanation"`
	Payload           string `json:"payload"`
	PayloadHash       string `json:"payloadHash"`
	PreviousHash      string `json:"previousHash,omitempty"`
	RecordHash        string `json:"recordHash"`
	DecisionTimestamp string `json:"decisionTimestamp"`
	BlockchainTxID    string `json:"blockchainTxId"`
	DocType           string `json:"docType"`
	SchemaVersion     string `json:"schemaVersion"`
}

// HistoryRecord represents one revision of the same world-state key.
type HistoryRecord struct {
	TxID      string             `json:"txId"`
	Value     *TransactionRecord `json:"value"`
	Timestamp string             `json:"timestamp"`
	IsDelete  bool               `json:"isDelete"`
}

// IntegrityVerification is returned by VerifyIntegrity to show exactly what was checked.
type IntegrityVerification struct {
	RecordID              string `json:"recordId"`
	PayloadHash           string `json:"payloadHash"`
	RecomputedPayloadHash string `json:"recomputedPayloadHash"`
	RecordHash            string `json:"recordHash"`
	RecomputedRecordHash  string `json:"recomputedRecordHash"`
	PreviousHash          string `json:"previousHash,omitempty"`
	Valid                 bool   `json:"valid"`
	VerifiedAt            string `json:"verifiedAt"`
}

func (t *TransactionChaincode) InitLedger(ctx contractapi.TransactionContextInterface) error {
	log.Println("TransactionChaincode: InitLedger called")
	return nil
}

// CreateRecord stores a post-fraud transaction decision using a structured JSON input payload.
func (t *TransactionChaincode) CreateRecord(
	ctx contractapi.TransactionContextInterface,
	recordJSON string,
) (*TransactionRecord, error) {
	input, canonicalPayload, payloadHash, decisionTimestamp, err := parseTransactionInput(ctx, recordJSON)
	if err != nil {
		return nil, err
	}

	existing, err := ctx.GetStub().GetState(input.TransactionID)
	if err != nil {
		return nil, fmt.Errorf("failed to read transaction %q: %w", input.TransactionID, err)
	}
	if existing != nil {
		return nil, fmt.Errorf("transaction %q already exists on ledger", input.TransactionID)
	}

	record := &TransactionRecord{
		TransactionID:     input.TransactionID,
		TenantID:          input.TenantID,
		FromAccountMasked: input.FromAccountMasked,
		ToAccountMasked:   input.ToAccountMasked,
		Amount:            input.Amount,
		Currency:          input.Currency,
		TransactionType:   input.TransactionType,
		Status:            input.Status,
		FraudScore:        defaultString(input.FraudScore, "UNKNOWN"),
		RiskLevel:         defaultString(input.RiskLevel, "UNKNOWN"),
		Decision:          defaultString(input.Decision, "UNKNOWN"),
		DecisionReason:    input.DecisionReason,
		Explanation:       input.Explanation,
		Payload:           canonicalPayload,
		PayloadHash:       payloadHash,
		PreviousHash:      strings.TrimSpace(input.PreviousHash),
		DecisionTimestamp: decisionTimestamp,
		BlockchainTxID:    ctx.GetStub().GetTxID(),
		DocType:           transactionDocType,
		SchemaVersion:     transactionSchemaVersion,
	}
	record.RecordHash = computeTransactionRecordHash(record)

	stored, err := json.Marshal(record)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal transaction %q: %w", input.TransactionID, err)
	}
	if err := ctx.GetStub().PutState(record.TransactionID, stored); err != nil {
		return nil, fmt.Errorf("failed to persist transaction %q: %w", input.TransactionID, err)
	}
	if err := ctx.GetStub().SetEvent("TransactionRecordCreated", stored); err != nil {
		return nil, fmt.Errorf("failed to emit TransactionRecordCreated for %q: %w", input.TransactionID, err)
	}

	return record, nil
}

// QueryRecord fetches the current version of the transaction record by transaction ID.
func (t *TransactionChaincode) QueryRecord(
	ctx contractapi.TransactionContextInterface,
	transactionID string,
) (*TransactionRecord, error) {
	recordJSON, err := ctx.GetStub().GetState(transactionID)
	if err != nil {
		return nil, fmt.Errorf("failed to read transaction %q: %w", transactionID, err)
	}
	if recordJSON == nil {
		return nil, fmt.Errorf("transaction %q does not exist on ledger", transactionID)
	}

	var record TransactionRecord
	if err := json.Unmarshal(recordJSON, &record); err != nil {
		return nil, fmt.Errorf("failed to unmarshal transaction %q: %w", transactionID, err)
	}

	return &record, nil
}

// VerifyIntegrity recomputes both payload_hash and record_hash to detect tampering.
func (t *TransactionChaincode) VerifyIntegrity(
	ctx contractapi.TransactionContextInterface,
	transactionID string,
) (*IntegrityVerification, error) {
	record, err := t.QueryRecord(ctx, transactionID)
	if err != nil {
		return nil, err
	}

	canonicalPayload, recomputedPayloadHash, err := canonicalisePayload(record.Payload)
	if err != nil {
		return nil, fmt.Errorf("failed to canonicalise payload for %q: %w", transactionID, err)
	}

	recomputed := *record
	recomputed.Payload = canonicalPayload
	recomputed.PayloadHash = recomputedPayloadHash
	recomputed.RecordHash = computeTransactionRecordHash(&recomputed)

	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return nil, fmt.Errorf("failed to get tx timestamp: %w", err)
	}

	return &IntegrityVerification{
		RecordID:              transactionID,
		PayloadHash:           record.PayloadHash,
		RecomputedPayloadHash: recomputedPayloadHash,
		RecordHash:            record.RecordHash,
		RecomputedRecordHash:  recomputed.RecordHash,
		PreviousHash:          record.PreviousHash,
		Valid:                 record.PayloadHash == recomputedPayloadHash && record.RecordHash == recomputed.RecordHash,
		VerifiedAt:            txTimestamp.AsTime().Format(time.RFC3339),
	}, nil
}

// CreateTransaction is a backwards-compatible wrapper used by older scripts.
func (t *TransactionChaincode) CreateTransaction(
	ctx contractapi.TransactionContextInterface,
	transactionID, tenantID, fromAccount, toAccount, amount, currency, txType, status string,
) (*TransactionRecord, error) {
	payload, err := json.Marshal(map[string]string{
		"transactionId": transactionID,
		"fromAccount":   maskAccount(fromAccount),
		"toAccount":     maskAccount(toAccount),
		"amount":        amount,
		"currency":      currency,
		"status":        status,
		"type":          txType,
		"source":        "legacy-wrapper",
	})
	if err != nil {
		return nil, fmt.Errorf("failed to build legacy payload: %w", err)
	}

	recordJSON, err := json.Marshal(TransactionRecordInput{
		TransactionID:     transactionID,
		TenantID:          tenantID,
		FromAccountMasked: maskAccount(fromAccount),
		ToAccountMasked:   maskAccount(toAccount),
		Amount:            amount,
		Currency:          currency,
		TransactionType:   txType,
		Status:            status,
		Decision:          "UNKNOWN",
		Payload:           string(payload),
	})
	if err != nil {
		return nil, fmt.Errorf("failed to marshal legacy transaction input: %w", err)
	}

	return t.CreateRecord(ctx, string(recordJSON))
}

// GetTransaction is a backwards-compatible alias for QueryRecord.
func (t *TransactionChaincode) GetTransaction(
	ctx contractapi.TransactionContextInterface,
	transactionID string,
) (*TransactionRecord, error) {
	return t.QueryRecord(ctx, transactionID)
}

// VerifyTransactionIntegrity is a backwards-compatible alias that returns only the boolean result.
func (t *TransactionChaincode) VerifyTransactionIntegrity(
	ctx contractapi.TransactionContextInterface,
	transactionID string,
) (bool, error) {
	result, err := t.VerifyIntegrity(ctx, transactionID)
	if err != nil {
		return false, err
	}
	return result.Valid, nil
}

// GetTransactionHistory returns all committed versions of the same transaction key.
func (t *TransactionChaincode) GetTransactionHistory(
	ctx contractapi.TransactionContextInterface,
	transactionID string,
) ([]HistoryRecord, error) {
	resultsIterator, err := ctx.GetStub().GetHistoryForKey(transactionID)
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve history for %q: %w", transactionID, err)
	}
	defer resultsIterator.Close()

	var history []HistoryRecord
	for resultsIterator.HasNext() {
		modification, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("failed to iterate history for %q: %w", transactionID, err)
		}

		entry := HistoryRecord{
			TxID:      modification.TxId,
			Timestamp: modification.Timestamp.AsTime().Format(time.RFC3339),
			IsDelete:  modification.IsDelete,
		}

		if !modification.IsDelete && modification.Value != nil {
			var record TransactionRecord
			if err := json.Unmarshal(modification.Value, &record); err != nil {
				return nil, fmt.Errorf("failed to unmarshal history record for %q at tx %q: %w",
					transactionID, modification.TxId, err)
			}
			entry.Value = &record
		}

		history = append(history, entry)
	}

	return history, nil
}

// QueryTransactionsByTenant performs a CouchDB rich query and returns all transaction records for a tenant.
func (t *TransactionChaincode) QueryTransactionsByTenant(
	ctx contractapi.TransactionContextInterface,
	tenantID string,
) ([]*TransactionRecord, error) {
	queryString := fmt.Sprintf(`{"selector":{"docType":"%s","tenantId":"%s"}}`, transactionDocType, tenantID)
	return executeTransactionRichQuery(ctx, queryString)
}

// QueryTransactionsByDecision performs a CouchDB rich query filtered by decision.
func (t *TransactionChaincode) QueryTransactionsByDecision(
	ctx contractapi.TransactionContextInterface,
	tenantID, decision string,
) ([]*TransactionRecord, error) {
	queryString := fmt.Sprintf(
		`{"selector":{"docType":"%s","tenantId":"%s","decision":"%s"}}`,
		transactionDocType,
		tenantID,
		decision,
	)
	return executeTransactionRichQuery(ctx, queryString)
}

func executeTransactionRichQuery(
	ctx contractapi.TransactionContextInterface,
	queryString string,
) ([]*TransactionRecord, error) {
	resultsIterator, err := ctx.GetStub().GetQueryResult(queryString)
	if err != nil {
		return nil, fmt.Errorf("failed to execute transaction query: %w", err)
	}
	defer resultsIterator.Close()

	var records []*TransactionRecord
	for resultsIterator.HasNext() {
		queryResult, err := resultsIterator.Next()
		if err != nil {
			return nil, fmt.Errorf("failed to iterate transaction query results: %w", err)
		}

		var record TransactionRecord
		if err := json.Unmarshal(queryResult.Value, &record); err != nil {
			return nil, fmt.Errorf("failed to unmarshal transaction query result: %w", err)
		}
		records = append(records, &record)
	}

	return records, nil
}

func parseTransactionInput(
	ctx contractapi.TransactionContextInterface,
	recordJSON string,
) (*TransactionRecordInput, string, string, string, error) {
	var input TransactionRecordInput
	if err := json.Unmarshal([]byte(recordJSON), &input); err != nil {
		return nil, "", "", "", fmt.Errorf("invalid transaction record JSON: %w", err)
	}

	input.TransactionID = strings.TrimSpace(input.TransactionID)
	input.TenantID = strings.TrimSpace(input.TenantID)
	input.Amount = strings.TrimSpace(input.Amount)
	input.Currency = strings.TrimSpace(strings.ToUpper(input.Currency))
	input.TransactionType = strings.TrimSpace(strings.ToUpper(input.TransactionType))
	input.Status = strings.TrimSpace(strings.ToUpper(input.Status))
	input.Decision = strings.TrimSpace(strings.ToUpper(input.Decision))
	input.FromAccountMasked = strings.TrimSpace(input.FromAccountMasked)
	input.ToAccountMasked = strings.TrimSpace(input.ToAccountMasked)

	switch {
	case input.TransactionID == "":
		return nil, "", "", "", fmt.Errorf("transactionId is required")
	case input.TenantID == "":
		return nil, "", "", "", fmt.Errorf("tenantId is required")
	case input.Amount == "":
		return nil, "", "", "", fmt.Errorf("amount is required")
	case input.Currency == "":
		return nil, "", "", "", fmt.Errorf("currency is required")
	case input.TransactionType == "":
		return nil, "", "", "", fmt.Errorf("transactionType is required")
	case input.Status == "":
		return nil, "", "", "", fmt.Errorf("status is required")
	case input.FromAccountMasked == "":
		return nil, "", "", "", fmt.Errorf("fromAccountMasked is required")
	case input.ToAccountMasked == "":
		return nil, "", "", "", fmt.Errorf("toAccountMasked is required")
	}

	canonicalPayload, payloadHash, err := canonicalisePayload(input.Payload)
	if err != nil {
		return nil, "", "", "", err
	}

	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return nil, "", "", "", fmt.Errorf("failed to get transaction timestamp: %w", err)
	}

	decisionTimestamp := strings.TrimSpace(input.DecisionTimestamp)
	if decisionTimestamp == "" {
		decisionTimestamp = txTimestamp.AsTime().Format(time.RFC3339)
	}

	return &input, canonicalPayload, payloadHash, decisionTimestamp, nil
}

func canonicalisePayload(payload string) (string, string, error) {
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
	return canonical, sha256Hex(canonical), nil
}

func computeTransactionRecordHash(record *TransactionRecord) string {
	return sha256Hex(strings.Join([]string{
		record.DocType,
		record.SchemaVersion,
		record.TransactionID,
		record.TenantID,
		record.FromAccountMasked,
		record.ToAccountMasked,
		record.Amount,
		record.Currency,
		record.TransactionType,
		record.Status,
		record.FraudScore,
		record.RiskLevel,
		record.Decision,
		record.DecisionReason,
		record.Explanation,
		record.DecisionTimestamp,
		record.PayloadHash,
		record.PreviousHash,
	}, "|"))
}

func sha256Hex(input string) string {
	digest := sha256.Sum256([]byte(input))
	return fmt.Sprintf("%x", digest)
}

func defaultString(value, fallback string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return fallback
	}
	return value
}

func maskAccount(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	if len(value) <= 4 {
		return strings.Repeat("*", len(value))
	}
	return strings.Repeat("*", len(value)-4) + value[len(value)-4:]
}
