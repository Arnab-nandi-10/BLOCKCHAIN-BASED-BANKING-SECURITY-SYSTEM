module github.com/bbss/audit-cc

go 1.21

require (
	github.com/hyperledger/fabric-contract-api-go v1.2.1
	github.com/hyperledger/fabric-chaincode-go v0.6.0
	// CVE-2024-24786: infinite loop in protojson.Unmarshal fixed in v1.33.0
	google.golang.org/protobuf v1.33.0
)

require (
	github.com/go-openapi/jsonpointer v0.19.5 // indirect
	github.com/go-openapi/jsonreference v0.19.6 // indirect
	github.com/go-openapi/spec v0.20.4 // indirect
	github.com/go-openapi/swag v0.19.15 // indirect
	github.com/gobuffalo/envy v1.7.0 // indirect
	github.com/gobuffalo/packd v0.3.0 // indirect
	github.com/gobuffalo/packr v1.30.1 // indirect
	github.com/golang/protobuf v1.5.4 // indirect
	github.com/hyperledger/fabric-protos-go v0.3.0 // indirect
	github.com/joho/godotenv v1.3.0 // indirect
	github.com/mailru/easyjson v0.7.6 // indirect
	github.com/rogpeppe/go-internal v1.3.0 // indirect
	github.com/xeipuuv/gojsonpointer v0.0.0-20180127040702-4e3ac2762d5f // indirect
	github.com/xeipuuv/gojsonreference v0.0.0-20180127040603-bd5ef7bd5415 // indirect
	github.com/xeipuuv/gojsonschema v1.2.0 // indirect
	gopkg.in/yaml.v2 v2.4.0 // indirect
)
