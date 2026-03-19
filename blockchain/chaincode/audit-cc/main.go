package main

import (
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

func main() {
	chaincode, err := contractapi.NewChaincode(&AuditChaincode{})
	if err != nil {
		fmt.Printf("Error creating audit chaincode: %v\n", err)
		panic(err)
	}

	if err := chaincode.Start(); err != nil {
		fmt.Printf("Error starting audit chaincode: %v\n", err)
		panic(err)
	}
}
