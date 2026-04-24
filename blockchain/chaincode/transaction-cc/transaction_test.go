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

type MockTransactionContext struct {
	stub *shimtest.MockStub
}

func (m *MockTransactionContext) GetStub() shim.ChaincodeStubInterface {
	return m.stub
}

func (m *MockTransactionContext) GetClientIdentity() cid.ClientIdentity {
	return nil
}

func newMockContext(t *testing.T, testName string) (*MockTransactionContext, *shimtest.MockStub) {
	t.Helper()
	cc, err := contractapi.NewChaincode(&TransactionChaincode{})
	if err != nil {
		t.Fatalf("failed to create contract chaincode: %v", err)
	}
	stub := shimtest.NewMockStub(testName, cc)
	stub.MockTransactionStart(fmt.Sprintf("txid-%s", testName))
	stub.TxTimestamp = timestamppb.Now()
	return &MockTransactionContext{stub: stub}, stub
}

func sampleTransactionInput() TransactionRecordInput {
	return TransactionRecordInput{
		TransactionID:     "TX-001",
		TenantID:          "TENANT-ALPHA",
		FromAccountMasked: "******1001",
		ToAccountMasked:   "******2002",
		Amount:            "5000.00",
		Currency:          "USD",
		TransactionType:   "TRANSFER",
		Status:            "BLOCKED",
		FraudScore:        "0.92",
		RiskLevel:         "HIGH",
		Decision:          "BLOCK",
		DecisionReason:    "Velocity breach",
		Explanation:       "Blocked after fraud review",
		Payload: `{
			"transactionId":"TX-001",
			"fraudScore":"0.92",
			"riskLevel":"HIGH",
			"decision":"BLOCK"
		}`,
		PreviousHash:      "prev-hash-001",
		DecisionTimestamp: "2026-04-23T10:30:00Z",
	}
}

func marshalTransactionInput(t *testing.T, input TransactionRecordInput) string {
	t.Helper()
	value, err := json.Marshal(input)
	if err != nil {
		t.Fatalf("failed to marshal transaction input: %v", err)
	}
	return string(value)
}

func TestCreateRecord(t *testing.T) {
	ctx, stub := newMockContext(t, "TestCreateRecord")
	cc := &TransactionChaincode{}
	input := sampleTransactionInput()

	record, err := cc.CreateRecord(ctx, marshalTransactionInput(t, input))
	if err != nil {
		t.Fatalf("CreateRecord returned unexpected error: %v", err)
	}

	if record.TransactionID != input.TransactionID {
		t.Fatalf("TransactionID: got %q want %q", record.TransactionID, input.TransactionID)
	}
	if record.Decision != input.Decision {
		t.Fatalf("Decision: got %q want %q", record.Decision, input.Decision)
	}
	if record.PayloadHash == "" {
		t.Fatal("PayloadHash must be populated")
	}
	if record.RecordHash == "" {
		t.Fatal("RecordHash must be populated")
	}
	if record.PreviousHash != input.PreviousHash {
		t.Fatalf("PreviousHash: got %q want %q", record.PreviousHash, input.PreviousHash)
	}

	state := stub.State[input.TransactionID]
	if state == nil {
		t.Fatal("expected state to be written to mock ledger")
	}

	var stored TransactionRecord
	if err := json.Unmarshal(state, &stored); err != nil {
		t.Fatalf("failed to unmarshal stored state: %v", err)
	}
	if stored.Payload != `{"decision":"BLOCK","fraudScore":"0.92","riskLevel":"HIGH","transactionId":"TX-001"}` {
		t.Fatalf("payload was not canonicalised: %s", stored.Payload)
	}
}

func TestCreateRecordIdempotency(t *testing.T) {
	ctx, _ := newMockContext(t, "TestCreateRecordIdempotency")
	cc := &TransactionChaincode{}
	input := sampleTransactionInput()

	if _, err := cc.CreateRecord(ctx, marshalTransactionInput(t, input)); err != nil {
		t.Fatalf("first CreateRecord failed unexpectedly: %v", err)
	}
	if _, err := cc.CreateRecord(ctx, marshalTransactionInput(t, input)); err == nil {
		t.Fatal("second CreateRecord with same transactionId should fail")
	}
}

func TestQueryRecordNotFound(t *testing.T) {
	ctx, _ := newMockContext(t, "TestQueryRecordNotFound")
	cc := &TransactionChaincode{}

	if _, err := cc.QueryRecord(ctx, "missing-tx"); err == nil {
		t.Fatal("QueryRecord should fail for a missing transaction")
	}
}

func TestVerifyIntegrity(t *testing.T) {
	ctx, stub := newMockContext(t, "TestVerifyIntegrity")
	cc := &TransactionChaincode{}
	input := sampleTransactionInput()

	record, err := cc.CreateRecord(ctx, marshalTransactionInput(t, input))
	if err != nil {
		t.Fatalf("CreateRecord failed: %v", err)
	}

	verification, err := cc.VerifyIntegrity(ctx, input.TransactionID)
	if err != nil {
		t.Fatalf("VerifyIntegrity failed: %v", err)
	}
	if !verification.Valid {
		t.Fatal("expected untampered transaction to validate successfully")
	}

	record.Amount = "999999.00"
	tampered, err := json.Marshal(record)
	if err != nil {
		t.Fatalf("failed to marshal tampered record: %v", err)
	}
	stub.State[input.TransactionID] = tampered

	verification, err = cc.VerifyIntegrity(ctx, input.TransactionID)
	if err != nil {
		t.Fatalf("VerifyIntegrity after tamper failed: %v", err)
	}
	if verification.Valid {
		t.Fatal("expected tampered transaction to fail verification")
	}
}

func TestCreateTransactionLegacyWrapper(t *testing.T) {
	ctx, _ := newMockContext(t, "TestCreateTransactionLegacyWrapper")
	cc := &TransactionChaincode{}

	record, err := cc.CreateTransaction(ctx, "TX-LEGACY", "TENANT-A", "1234567890", "9876543210", "100.00", "USD", "TRANSFER", "SUBMITTED")
	if err != nil {
		t.Fatalf("CreateTransaction legacy wrapper failed: %v", err)
	}

	if record.FromAccountMasked != "******7890" {
		t.Fatalf("legacy wrapper did not mask source account: %q", record.FromAccountMasked)
	}
	if record.Decision != "UNKNOWN" {
		t.Fatalf("legacy wrapper should default decision to UNKNOWN, got %q", record.Decision)
	}
}
