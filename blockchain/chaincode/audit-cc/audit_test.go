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

// MockAuditTransactionContext is a minimal implementation of
// contractapi.TransactionContextInterface backed by a shimtest.MockStub.
// It is used exclusively for unit-testing audit chaincode functions without
// requiring a running Fabric peer.
type MockAuditTransactionContext struct {
	stub *shimtest.MockStub
}

// GetStub satisfies contractapi.TransactionContextInterface.
func (m *MockAuditTransactionContext) GetStub() shim.ChaincodeStubInterface {
	return m.stub
}

// GetClientIdentity satisfies contractapi.TransactionContextInterface.
// Returns nil because no identity checks are performed in these tests.
func (m *MockAuditTransactionContext) GetClientIdentity() cid.ClientIdentity {
	return nil
}

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

// newAuditMockContext creates a fresh MockStub wired to a new AuditChaincode
// instance, begins a mock transaction, and returns a ready context.
func newAuditMockContext(t *testing.T, testName string) (*MockAuditTransactionContext, *shimtest.MockStub) {
	t.Helper()
	cc := new(AuditChaincode)
	stub := shimtest.NewMockStub(testName, cc)
	stub.MockTransactionStart(fmt.Sprintf("txid-%s", testName))
	// Set a deterministic timestamp so GetTxTimestamp() does not return nil.
	stub.TxTimestamp = timestamppb.Now()
	return &MockAuditTransactionContext{stub: stub}, stub
}

// sampleAuditParams returns fixed parameter values reused across multiple tests.
func sampleAuditParams() (auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload string) {
	return "AUDIT-001",
		"TENANT-ALPHA",
		"TRANSACTION",
		"TX-001",
		"STATUS_CHANGE",
		"USER-42",
		"SYSTEM",
		"192.168.1.100",
		`{"previousStatus":"PENDING","newStatus":"COMPLETED"}`
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

// TestCreateAuditEntry verifies that CreateAuditEntry persists the audit record
// with all expected field values and writes a non-nil value to the ledger state.
func TestCreateAuditEntry(t *testing.T) {
	ctx, stub := newAuditMockContext(t, "TestCreateAuditEntry")
	cc := &AuditChaincode{}

	auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload := sampleAuditParams()

	record, err := cc.CreateAuditEntry(ctx, auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload)
	if err != nil {
		t.Fatalf("CreateAuditEntry returned unexpected error: %v", err)
	}

	// Verify returned struct fields.
	if record.AuditID != auditID {
		t.Errorf("AuditID: got %q, want %q", record.AuditID, auditID)
	}
	if record.TenantID != tenantID {
		t.Errorf("TenantID: got %q, want %q", record.TenantID, tenantID)
	}
	if record.EntityType != entityType {
		t.Errorf("EntityType: got %q, want %q", record.EntityType, entityType)
	}
	if record.EntityID != entityID {
		t.Errorf("EntityID: got %q, want %q", record.EntityID, entityID)
	}
	if record.Action != action {
		t.Errorf("Action: got %q, want %q", record.Action, action)
	}
	if record.ActorID != actorID {
		t.Errorf("ActorID: got %q, want %q", record.ActorID, actorID)
	}
	if record.ActorType != actorType {
		t.Errorf("ActorType: got %q, want %q", record.ActorType, actorType)
	}
	if record.IPAddress != ipAddr {
		t.Errorf("IPAddress: got %q, want %q", record.IPAddress, ipAddr)
	}
	if record.Payload != payload {
		t.Errorf("Payload: got %q, want %q", record.Payload, payload)
	}
	if record.DocType != "AUDIT" {
		t.Errorf("DocType: got %q, want %q", record.DocType, "AUDIT")
	}
	if record.Hash == "" {
		t.Error("Hash must not be empty after CreateAuditEntry")
	}
	if record.BlockchainTxID == "" {
		t.Error("BlockchainTxID must not be empty after CreateAuditEntry")
	}
	if record.OccurredAt == "" {
		t.Error("OccurredAt must not be empty after CreateAuditEntry")
	}

	// Verify that the state was persisted to the ledger.
	stateBytes := stub.State[auditID]
	if stateBytes == nil {
		t.Fatal("expected non-nil state after CreateAuditEntry, got nil")
	}

	var storedRecord AuditRecord
	if err := json.Unmarshal(stateBytes, &storedRecord); err != nil {
		t.Fatalf("failed to unmarshal stored audit state: %v", err)
	}
	if storedRecord.AuditID != auditID {
		t.Errorf("stored AuditID: got %q, want %q", storedRecord.AuditID, auditID)
	}
}

// TestCreateAuditEntryIdempotency verifies that creating a second audit entry with
// the same ID returns an error (idempotency enforcement).
func TestCreateAuditEntryIdempotency(t *testing.T) {
	ctx, _ := newAuditMockContext(t, "TestAuditIdempotency")
	cc := &AuditChaincode{}

	auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload := sampleAuditParams()

	if _, err := cc.CreateAuditEntry(ctx, auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload); err != nil {
		t.Fatalf("first CreateAuditEntry failed unexpectedly: %v", err)
	}

	_, err := cc.CreateAuditEntry(ctx, auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload)
	if err == nil {
		t.Fatal("second CreateAuditEntry with same ID should return an error but did not")
	}
}

// TestGetAuditEntry verifies that GetAuditEntry correctly retrieves a record that
// was previously written to the ledger by CreateAuditEntry.
func TestGetAuditEntry(t *testing.T) {
	ctx, _ := newAuditMockContext(t, "TestGetAuditEntry")
	cc := &AuditChaincode{}

	auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload := sampleAuditParams()

	created, err := cc.CreateAuditEntry(ctx, auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload)
	if err != nil {
		t.Fatalf("CreateAuditEntry failed: %v", err)
	}

	retrieved, err := cc.GetAuditEntry(ctx, auditID)
	if err != nil {
		t.Fatalf("GetAuditEntry returned unexpected error: %v", err)
	}

	if retrieved.AuditID != created.AuditID {
		t.Errorf("AuditID mismatch: got %q, want %q", retrieved.AuditID, created.AuditID)
	}
	if retrieved.Hash != created.Hash {
		t.Errorf("Hash mismatch: got %q, want %q", retrieved.Hash, created.Hash)
	}
	if retrieved.Action != action {
		t.Errorf("Action mismatch: got %q, want %q", retrieved.Action, action)
	}
	if retrieved.Payload != payload {
		t.Errorf("Payload mismatch: got %q, want %q", retrieved.Payload, payload)
	}
}

// TestGetAuditEntryNotFound verifies that GetAuditEntry returns a descriptive error
// when the requested audit ID does not exist on the ledger.
func TestGetAuditEntryNotFound(t *testing.T) {
	ctx, _ := newAuditMockContext(t, "TestGetAuditEntryNotFound")
	cc := &AuditChaincode{}

	_, err := cc.GetAuditEntry(ctx, "NON-EXISTENT-AUDIT-99999")
	if err == nil {
		t.Fatal("GetAuditEntry for a missing key should return an error but returned nil")
	}
}

// TestVerifyAuditIntegrity verifies two scenarios:
//  1. A freshly created audit entry passes the integrity check (hash matches).
//  2. A record whose ActorID field has been directly tampered in the state
//     database (while the stored hash remains the original) fails the check.
func TestVerifyAuditIntegrity(t *testing.T) {
	ctx, stub := newAuditMockContext(t, "TestVerifyAuditIntegrity")
	cc := &AuditChaincode{}

	auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload := sampleAuditParams()

	record, err := cc.CreateAuditEntry(ctx, auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload)
	if err != nil {
		t.Fatalf("CreateAuditEntry failed: %v", err)
	}

	// Step 1 — untampered record must pass integrity check.
	valid, err := cc.VerifyAuditIntegrity(ctx, auditID)
	if err != nil {
		t.Fatalf("VerifyAuditIntegrity failed: %v", err)
	}
	if !valid {
		t.Fatal("integrity check should pass for an untampered audit record but returned false")
	}

	// Step 2 — simulate a direct state-database tamper:
	// modify ActorID without updating the stored hash.
	record.ActorID = "ATTACKER-999" // tampered — hash not updated
	// record.Hash is intentionally left as the original value

	tamperedJSON, err := json.Marshal(record)
	if err != nil {
		t.Fatalf("failed to marshal tampered audit record: %v", err)
	}
	stub.State[auditID] = tamperedJSON // bypass chaincode and write directly to state

	// Step 3 — tampered record must fail the integrity check.
	valid, err = cc.VerifyAuditIntegrity(ctx, auditID)
	if err != nil {
		t.Fatalf("VerifyAuditIntegrity failed after tamper: %v", err)
	}
	if valid {
		t.Fatal("integrity check should fail for a tampered audit record but returned true")
	}
}

// TestVerifyAuditIntegrityHashConsistency verifies that the hash computed by
// auditComputeHash is deterministic and reflects the correct field order.
func TestVerifyAuditIntegrityHashConsistency(t *testing.T) {
	ctx, _ := newAuditMockContext(t, "TestHashConsistency")
	cc := &AuditChaincode{}

	auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload := sampleAuditParams()

	record, err := cc.CreateAuditEntry(ctx, auditID, tenantID, entityType, entityID, action, actorID, actorType, ipAddr, payload)
	if err != nil {
		t.Fatalf("CreateAuditEntry failed: %v", err)
	}

	// Independently compute the expected hash using the same helper function.
	expectedHash := auditComputeHash(auditID, tenantID, entityType, entityID, action, actorID)
	if record.Hash != expectedHash {
		t.Errorf("stored hash does not match independently computed hash.\n  stored:   %q\n  computed: %q",
			record.Hash, expectedHash)
	}
}
