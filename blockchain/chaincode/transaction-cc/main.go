package main

import (
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

func main() {
	chaincode, err := contractapi.NewChaincode(&TransactionChaincode{})
	if err != nil {
		fmt.Printf("Error creating transaction chaincode: %v\n", err)
		panic(err)
	}

	if err := chaincode.Start(); err != nil {
		fmt.Printf("Error starting transaction chaincode: %v\n", err)
		panic(err)
	}
}
