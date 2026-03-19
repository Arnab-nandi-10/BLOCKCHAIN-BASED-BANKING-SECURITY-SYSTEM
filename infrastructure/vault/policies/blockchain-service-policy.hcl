# Blockchain Service Policy
path "secret/data/database" {
  capabilities = ["read", "list"]
}

path "secret/data/kafka" {
  capabilities = ["read", "list"]
}

path "secret/data/fabric" {
  capabilities = ["read", "list"]
}

# Renewal and revocation
path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "auth/token/lookup-self" {
  capabilities = ["read"]
}

path "auth/token/revoke-self" {
  capabilities = ["update"]
}
