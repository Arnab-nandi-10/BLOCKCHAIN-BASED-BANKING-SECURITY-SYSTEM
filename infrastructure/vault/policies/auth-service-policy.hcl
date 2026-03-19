# Auth Service Policy - Read-only access to auth secrets
path "secret/data/auth-service" {
  capabilities = ["read", "list"]
}

path "secret/data/database" {
  capabilities = ["read", "list"]
}

path "secret/data/kafka" {
  capabilities = ["read", "list"]
}

path "secret/data/redis" {
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
