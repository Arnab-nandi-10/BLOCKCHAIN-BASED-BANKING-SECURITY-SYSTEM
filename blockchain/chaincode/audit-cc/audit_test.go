package main

import (
	"encoding/json"
	"fmt"
	"testing"

	"github.com/hyperledger/fabric-chaincode-go/pkg/cid"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-chaincode-go/shimtest"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"google.golang.org/protobuf/types/known/timestamppb"
)

type MockAuditTransactionContext struct {
	stub *shimtest.MockStub
}

func (m *MockAuditTransactionContext) GetStub() shim.ChaincodeStubInterface {
	return m.stub
}

func (m *MockAuditTransactionContext) GetClientIdentity() cid.ClientIdentity {
	return nil
}

func newAuditMockContext(t *testing.T, testName string) (*MockAuditTransactionContext, *shimtest.MockStub) {
	t.Helper()
	cc, err := contractapi.NewChaincode(&AuditChaincode{})
	if err != nil {
		t.Fatalf("failed to create audit contract chaincode: %v", err)
	}
	stub := shimtest.NewMockStub(testName, cc)
	stub.MockTransactionStart(fmt.Sprintf("txid-%s", testName))
	stub.TxTimestamp = timestamppb.Now()
	return &MockAuditTransactionContext{stub: stub}, stub
}

func sampleAuditInput() AuditRecordInput {
	return AuditRecordInput{
		AuditID:       "AUDIT-001",
		TenantID:      "TENANT-ALPHA",
		EntityType:    "TRANSACTION",
		EntityID:      "TX-001",
		Action:        "FRAUD_DECISION_RECORDED",
		ActorID:       "FRAUD_ENGINE",
		ActorType:     "SYSTEM",
		IPAddressHash: hashOptional("192.168.1.100"),
		TransactionID: "TX-001",
		FraudScore:    "0.92",
		RiskLevel:     "HIGH",
		Decision:      "BLOCK",
		Explanation:   "Risk exceeded policy threshold",
		Payload: `{
			"transactionId":"TX-001",
			"decision":"BLOCK",
			"riskLevel":"HIGH"
		}`,
		PreviousHash: "prev-audit-hash-001",
		OccurredAt:   "2026-04-23T10:35:00Z",
	}
}

func marshalAuditInput(t *testing.T, input AuditRecordInput) string {
	t.Helper()
	value, err := json.Marshal(input)
	if err != nil {
		t.Fatalf("failed to marshal audit input: %v", err)
	}
	return string(value)
}

func TestCreateAudit(t *testing.T) {
	ctx, stub := newAuditMockContext(t, "TestCreateAudit")
	cc := &AuditChaincode{}
	input := sampleAuditInput()

	record, err := cc.CreateAudit(ctx, marshalAuditInput(t, input))
	if err != nil {
		t.Fatalf("CreateAudit returned unexpected error: %v", err)
	}

	if record.AuditID != input.AuditID {
		t.Fatalf("AuditID: got %q want %q", record.AuditID, input.AuditID)
	}
	if record.PayloadHash == "" {
		t.Fatal("PayloadHash must be populated")
	}
	if record.RecordHash == "" {
		t.Fatal("RecordHash must be populated")
	}
	if record.TransactionID != input.TransactionID {
		t.Fatalf("TransactionID: got %q want %q", record.TransactionID, input.TransactionID)
	}

	state := stub.State[input.AuditID]
	if state == nil {
		t.Fatal("expected audit record to be written to mock ledger")
	}
}

func TestCreateAuditIdempotency(t *testing.T) {
	ctx, _ := newAuditMockContext(t, "TestCreateAuditIdempotency")
	cc := &AuditChaincode{}
	input := sampleAuditInput()

	if _, err := cc.CreateAudit(ctx, marshalAuditInput(t, input)); err != nil {
		t.Fatalf("first CreateAudit failed unexpectedly: %v", err)
	}
	if _, err := cc.CreateAudit(ctx, marshalAuditInput(t, input)); err == nil {
		t.Fatal("second CreateAudit with same auditId should fail")
	}
}

func TestQueryRecordNotFound(t *testing.T) {
	ctx, _ := newAuditMockContext(t, "TestAuditQueryRecordNotFound")
	cc := &AuditChaincode{}

	if _, err := cc.QueryRecord(ctx, "missing-audit"); err == nil {
		t.Fatal("QueryRecord should fail for a missing audit record")
	}
}

func TestVerifyAuditIntegrity(t *testing.T) {
	ctx, stub := newAuditMockContext(t, "TestVerifyAuditIntegrity")
	cc := &AuditChaincode{}
	input := sampleAuditInput()

	record, err := cc.CreateAudit(ctx, marshalAuditInput(t, input))
	if err != nil {
		t.Fatalf("CreateAudit failed: %v", err)
	}

	verification, err := cc.VerifyIntegrity(ctx, input.AuditID)
	if err != nil {
		t.Fatalf("VerifyIntegrity failed: %v", err)
	}
	if !verification.Valid {
		t.Fatal("expected untampered audit record to validate successfully")
	}

	record.Decision = "ALLOW"
	tampered, err := json.Marshal(record)
	if err != nil {
		t.Fatalf("failed to marshal tampered audit record: %v", err)
	}
	stub.State[input.AuditID] = tampered

	verification, err = cc.VerifyIntegrity(ctx, input.AuditID)
	if err != nil {
		t.Fatalf("VerifyIntegrity after tamper failed: %v", err)
	}
	if verification.Valid {
		t.Fatal("expected tampered audit record to fail verification")
	}
}

func TestCreateAuditEntryLegacyWrapper(t *testing.T) {
	ctx, _ := newAuditMockContext(t, "TestCreateAuditEntryLegacyWrapper")
	cc := &AuditChaincode{}

	record, err := cc.CreateAuditEntry(
		ctx,
		"AUDIT-LEGACY",
		"TENANT-A",
		"TRANSACTION",
		"TX-123",
		"CREATE",
		"SERVICE-A",
		"SYSTEM",
		"10.0.0.1",
		`{"source":"legacy-wrapper"}`,
	)
	if err != nil {
		t.Fatalf("CreateAuditEntry legacy wrapper failed: %v", err)
	}

	if record.IPAddressHash == "" {
		t.Fatal("legacy wrapper should hash ipAddress before storing it")
	}
	if record.ActorType != "SYSTEM" {
		t.Fatalf("expected actor type SYSTEM, got %q", record.ActorType)
	}
}
