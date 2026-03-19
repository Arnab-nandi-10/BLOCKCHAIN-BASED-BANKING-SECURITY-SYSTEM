package main

import (
	"encoding/json"
	"fmt"
	"testing"

	"github.com/hyperledger/fabric-chaincode-go/pkg/cid"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-chaincode-go/shimtest"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// ---------------------------------------------------------------------------
// Mock transaction context
// ---------------------------------------------------------------------------

// MockTransactionContext is a minimal implementation of
// contractapi.TransactionContextInterface backed by a shimtest.MockStub.
// It is used exclusively for unit-testing chaincode functions without
// requiring a running Fabric peer.
type MockTransactionContext struct {
	stub *shimtest.MockStub
}

// GetStub satisfies contractapi.TransactionContextInterface.
func (m *MockTransactionContext) GetStub() shim.ChaincodeStubInterface {
	return m.stub
}

// GetClientIdentity satisfies contractapi.TransactionContextInterface.
// Returns nil because no identity checks are performed in these tests.
func (m *MockTransactionContext) GetClientIdentity() cid.ClientIdentity {
	return nil
}

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

// newMockContext creates a fresh MockStub wired to a new TransactionChaincode
// instance, begins a mock transaction, and returns a MockTransactionContext
// ready for direct chaincode-function invocation.
func newMockContext(t *testing.T, testName string) (*MockTransactionContext, *shimtest.MockStub) {
	t.Helper()
	cc := new(TransactionChaincode)
	stub := shimtest.NewMockStub(testName, cc)
	stub.MockTransactionStart(fmt.Sprintf("txid-%s", testName))
	// Set a deterministic timestamp so GetTxTimestamp() does not return nil.
	stub.TxTimestamp = timestamppb.Now()
	return &MockTransactionContext{stub: stub}, stub
}

// sampleTransaction returns the fixed parameters used across multiple tests.
func sampleTransaction() (txID, tenantID, fromAcc, toAcc, amount, currency, txType, status string) {
	return "TX-001", "TENANT-ALPHA", "ACC-1001", "ACC-2002", "5000.00", "USD", "TRANSFER", "PENDING"
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

// TestCreateTransaction verifies that CreateTransaction persists the record with
// all expected field values and that the state is non-nil after the call.
func TestCreateTransaction(t *testing.T) {
	ctx, stub := newMockContext(t, "TestCreateTransaction")
	cc := &TransactionChaincode{}

	txID, tenantID, fromAcc, toAcc, amount, currency, txType, status := sampleTransaction()

	record, err := cc.CreateTransaction(ctx, txID, tenantID, fromAcc, toAcc, amount, currency, txType, status)
	if err != nil {
		t.Fatalf("CreateTransaction returned unexpected error: %v", err)
	}

	// Verify returned struct fields.
	if record.TransactionID != txID {
		t.Errorf("TransactionID: got %q, want %q", record.TransactionID, txID)
	}
	if record.TenantID != tenantID {
		t.Errorf("TenantID: got %q, want %q", record.TenantID, tenantID)
	}
	if record.FromAccount != fromAcc {
		t.Errorf("FromAccount: got %q, want %q", record.FromAccount, fromAcc)
	}
	if record.ToAccount != toAcc {
		t.Errorf("ToAccount: got %q, want %q", record.ToAccount, toAcc)
	}
	if record.Amount != amount {
		t.Errorf("Amount: got %q, want %q", record.Amount, amount)
	}
	if record.Currency != currency {
		t.Errorf("Currency: got %q, want %q", record.Currency, currency)
	}
	if record.Type != txType {
		t.Errorf("Type: got %q, want %q", record.Type, txType)
	}
	if record.Status != status {
		t.Errorf("Status: got %q, want %q", record.Status, status)
	}
	if record.DocType != "TRANSACTION" {
		t.Errorf("DocType: got %q, want %q", record.DocType, "TRANSACTION")
	}
	if record.Hash == "" {
		t.Error("Hash must not be empty after CreateTransaction")
	}
	if record.BlockchainTxID == "" {
		t.Error("BlockchainTxID must not be empty after CreateTransaction")
	}
	if record.CreatedAt == "" {
		t.Error("CreatedAt must not be empty after CreateTransaction")
	}
	if record.FraudRiskLevel != "UNKNOWN" {
		t.Errorf("FraudRiskLevel: got %q, want %q", record.FraudRiskLevel, "UNKNOWN")
	}

	// Verify that the state was written to the ledger.
	stateBytes := stub.State[txID]
	if stateBytes == nil {
		t.Fatal("expected state to be non-nil after CreateTransaction, got nil")
	}

	var storedRecord TransactionRecord
	if err := json.Unmarshal(stateBytes, &storedRecord); err != nil {
		t.Fatalf("failed to unmarshal stored state: %v", err)
	}
	if storedRecord.TransactionID != txID {
		t.Errorf("stored TransactionID: got %q, want %q", storedRecord.TransactionID, txID)
	}
}

// TestCreateTransactionIdempotency verifies that attempting to create a second
// transaction with the same ID returns an error (idempotency enforcement).
func TestCreateTransactionIdempotency(t *testing.T) {
	ctx, _ := newMockContext(t, "TestIdempotency")
	cc := &TransactionChaincode{}

	txID, tenantID, fromAcc, toAcc, amount, currency, txType, status := sampleTransaction()

	if _, err := cc.CreateTransaction(ctx, txID, tenantID, fromAcc, toAcc, amount, currency, txType, status); err != nil {
		t.Fatalf("first CreateTransaction failed unexpectedly: %v", err)
	}

	_, err := cc.CreateTransaction(ctx, txID, tenantID, fromAcc, toAcc, amount, currency, txType, status)
	if err == nil {
		t.Fatal("second CreateTransaction with same ID should have returned an error but did not")
	}
}

// TestGetTransaction verifies that GetTransaction retrieves the record that was
// previously committed to the ledger by CreateTransaction.
func TestGetTransaction(t *testing.T) {
	ctx, _ := newMockContext(t, "TestGetTransaction")
	cc := &TransactionChaincode{}

	txID, tenantID, fromAcc, toAcc, amount, currency, txType, status := sampleTransaction()

	created, err := cc.CreateTransaction(ctx, txID, tenantID, fromAcc, toAcc, amount, currency, txType, status)
	if err != nil {
		t.Fatalf("CreateTransaction failed: %v", err)
	}

	retrieved, err := cc.GetTransaction(ctx, txID)
	if err != nil {
		t.Fatalf("GetTransaction returned unexpected error: %v", err)
	}

	if retrieved.TransactionID != created.TransactionID {
		t.Errorf("TransactionID mismatch: got %q, want %q", retrieved.TransactionID, created.TransactionID)
	}
	if retrieved.Hash != created.Hash {
		t.Errorf("Hash mismatch: got %q, want %q", retrieved.Hash, created.Hash)
	}
	if retrieved.Amount != amount {
		t.Errorf("Amount mismatch: got %q, want %q", retrieved.Amount, amount)
	}
}

// TestGetTransactionNotFound verifies that GetTransaction returns a descriptive
// error when the requested transaction ID does not exist on the ledger.
func TestGetTransactionNotFound(t *testing.T) {
	ctx, _ := newMockContext(t, "TestGetTransactionNotFound")
	cc := &TransactionChaincode{}

	_, err := cc.GetTransaction(ctx, "NON-EXISTENT-TX-99999")
	if err == nil {
		t.Fatal("GetTransaction for a missing key should return an error but returned nil")
	}
}

// TestUpdateTransactionStatus verifies that UpdateTransactionStatus correctly
// mutates the status and fraud fields while preserving immutable core fields.
func TestUpdateTransactionStatus(t *testing.T) {
	ctx, stub := newMockContext(t, "TestUpdateTransactionStatus")
	cc := &TransactionChaincode{}

	txID, tenantID, fromAcc, toAcc, amount, currency, txType, _ := sampleTransaction()

	if _, err := cc.CreateTransaction(ctx, txID, tenantID, fromAcc, toAcc, amount, currency, txType, "PENDING"); err != nil {
		t.Fatalf("CreateTransaction failed: %v", err)
	}

	// Start a new mock transaction to simulate a separate block for the update.
	stub.MockTransactionEnd(fmt.Sprintf("txid-%s", "TestUpdateTransactionStatus"))
	stub.MockTransactionStart("txid-TestUpdateTransactionStatus-update")
	stub.TxTimestamp = timestamppb.Now()

	updated, err := cc.UpdateTransactionStatus(ctx, txID, "COMPLETED", "0.15", "LOW")
	if err != nil {
		t.Fatalf("UpdateTransactionStatus returned unexpected error: %v", err)
	}

	if updated.Status != "COMPLETED" {
		t.Errorf("Status: got %q, want %q", updated.Status, "COMPLETED")
	}
	if updated.FraudScore != 0.15 {
		t.Errorf("FraudScore: got %f, want 0.15", updated.FraudScore)
	}
	if updated.FraudRiskLevel != "LOW" {
		t.Errorf("FraudRiskLevel: got %q, want %q", updated.FraudRiskLevel, "LOW")
	}
	// Core immutable fields must be preserved.
	if updated.Amount != amount {
		t.Errorf("Amount must not change: got %q, want %q", updated.Amount, amount)
	}
	if updated.FromAccount != fromAcc {
		t.Errorf("FromAccount must not change: got %q, want %q", updated.FromAccount, fromAcc)
	}
	// Hash must still be consistent with the immutable fields.
	expectedHash := computeHash(txID, tenantID, fromAcc, toAcc, amount, currency)
	if updated.Hash != expectedHash {
		t.Errorf("Hash after update does not match expected value.\n  got:  %q\n  want: %q", updated.Hash, expectedHash)
	}
}

// TestUpdateTransactionStatusInvalidFraudScore verifies that a non-numeric fraud
// score string causes UpdateTransactionStatus to return an error.
func TestUpdateTransactionStatusInvalidFraudScore(t *testing.T) {
	ctx, _ := newMockContext(t, "TestInvalidFraudScore")
	cc := &TransactionChaincode{}

	txID, tenantID, fromAcc, toAcc, amount, currency, txType, status := sampleTransaction()
	if _, err := cc.CreateTransaction(ctx, txID, tenantID, fromAcc, toAcc, amount, currency, txType, status); err != nil {
		t.Fatalf("CreateTransaction failed: %v", err)
	}

	_, err := cc.UpdateTransactionStatus(ctx, txID, "COMPLETED", "not-a-number", "HIGH")
	if err == nil {
		t.Fatal("UpdateTransactionStatus with invalid fraud score should return an error")
	}
}

// TestVerifyTransactionIntegrity verifies that:
//  1. A freshly created transaction passes the integrity check (hash matches).
//  2. A transaction whose Amount field has been directly tampered in the state
//     database (while the hash remains unchanged) fails the integrity check.
func TestVerifyTransactionIntegrity(t *testing.T) {
	ctx, stub := newMockContext(t, "TestVerifyTransactionIntegrity")
	cc := &TransactionChaincode{}

	txID, tenantID, fromAcc, toAcc, amount, currency, txType, status := sampleTransaction()

	record, err := cc.CreateTransaction(ctx, txID, tenantID, fromAcc, toAcc, amount, currency, txType, status)
	if err != nil {
		t.Fatalf("CreateTransaction failed: %v", err)
	}

	// Step 1 — untampered record must pass integrity check.
	valid, err := cc.VerifyTransactionIntegrity(ctx, txID)
	if err != nil {
		t.Fatalf("VerifyTransactionIntegrity failed: %v", err)
	}
	if !valid {
		t.Fatal("integrity check should pass for an untampered record but returned false")
	}

	// Step 2 — simulate a direct state-database tamper:
	// change the Amount without updating the stored hash.
	record.Amount = "99999999.00" // tampered amount
	// record.Hash intentionally left as the original value

	tamperedJSON, err := json.Marshal(record)
	if err != nil {
		t.Fatalf("failed to marshal tampered record: %v", err)
	}
	stub.State[txID] = tamperedJSON // bypass chaincode and write directly to state

	// Step 3 — tampered record must fail integrity check.
	valid, err = cc.VerifyTransactionIntegrity(ctx, txID)
	if err != nil {
		t.Fatalf("VerifyTransactionIntegrity failed after tamper: %v", err)
	}
	if valid {
		t.Fatal("integrity check should fail for a tampered record but returned true")
	}
}
