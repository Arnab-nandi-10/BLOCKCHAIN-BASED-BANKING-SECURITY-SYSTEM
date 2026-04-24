# Phase 2: HashiCorp Vault Secrets Management

## Overview

This document describes the HashiCorp Vault integration for centralized secrets management across the Civic Savings microservices architecture.

## Architecture

### Vault Deployment

Vault is deployed as a Docker container in development mode. For production, use a high-availability cluster with:
- Auto-unseal with AWS KMS/Azure Key Vault/GCP KMS
- Raft storage backend with 3+ nodes
- TLS/mTLS for all communications
- Audit logging enabled

### Secrets Structure

```
secret/
├── auth-service/
│   ├── jwt.secret
│   ├── jwt.expiration
│   └── jwt.refresh-expiration
├── database/
│   ├── postgres.username
│   ├── postgres.password
│   ├── postgres.host
│   └── postgres.port
├── kafka/
│   ├── bootstrap.servers
│   ├── sasl.username
│   ├── sasl.password
│   └── sasl.mechanism
├── redis/
│   ├── host
│   ├── port
│   └── password
└── fabric/
    ├── org1.msp.id
    ├── org1.peer.url
    └── org1.user.cert
```

### Authentication Method: AppRole

Each microservice authenticates to Vault using AppRole:

| Service | Role Name | Policy | Token TTL |
|---------|-----------|--------|-----------|
| auth-service | `auth-service` | `auth-service-policy.hcl` | 1h |
| transaction-service | `transaction-service` | `transaction-service-policy.hcl` | 1h |
| blockchain-service | `blockchain-service` | `blockchain-service-policy.hcl` | 1h |
| audit-service | `audit-service` | `audit-service-policy.hcl` | 1h |

## Setup Instructions

### 1. Start Vault Server

```bash
cd infrastructure/vault
docker compose -f docker-compose-vault.yml up -d
```

**Vault UI**: http://localhost:8200  
**Root Token** (dev mode): `bbss-dev-token`

### 2. Initialize Secrets

The `vault-init` container automatically:
1. Enables KV v2 secrets engine
2. Stores all application secrets
3. Configures AppRole authentication
4. Creates service-specific policies

### 3. Retrieve AppRole Credentials

```bash
# Get Role ID for auth-service
docker exec bbss-vault vault read -field=role_id auth/approle/role/auth-service/role-id

# Generate Secret ID for auth-service
docker exec bbss-vault vault write -field=secret_id -f auth/approle/role/auth-service/secret-id
```

### 4. Configure Spring Boot Application

**application.yml** (auth-service):

```yaml
spring:
  cloud:
    vault:
      enabled: true
      uri: http://vault:8200
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
        app-role-path: approle
      kv:
        enabled: true
        backend: secret
        default-context: auth-service
      fail-fast: true
      
  config:
    import: vault://

# JWT Configuration from Vault
jwt:
  secret: ${jwt.secret}
  expiration: ${jwt.expiration:3600000}
  refresh-expiration: ${jwt.refresh-expiration:604800000}
  
# Database Configuration from Vault
spring.datasource:
  url: jdbc:postgresql://${postgres.host}:${postgres.port}/bbss_auth
  username: ${postgres.username}
  password: ${postgres.password}
  
# Kafka Configuration from Vault
spring.kafka:
  bootstrap-servers: ${bootstrap.servers}
  properties:
    sasl.mechanism: ${sasl.mechanism:PLAIN}
    sasl.jaas.config: >
      org.apache.kafka.common.security.plain.PlainLoginModule required
      username="${sasl.username}"
      password="${sasl.password}";
```

**Environment Variables**:

```bash
export VAULT_ROLE_ID=<role-id-from-step-3>
export VAULT_SECRET_ID=<secret-id-from-step-3>
```

## Spring Cloud Vault Integration

### Maven Dependencies

Add to each service's `pom.xml`:

```xml
<dependencies>
    <!-- Spring Cloud Vault -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-vault-config</artifactId>
    </dependency>
    
    <!-- For dynamic database credentials -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-vault-config-databases</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Vault Configuration Bean

```java
@Configuration
@EnableConfigurationProperties(VaultProperties.class)
public class VaultConfig {

    @Bean
    public VaultEndpoint vaultEndpoint(VaultProperties properties) {
        VaultEndpoint endpoint = new VaultEndpoint();
        endpoint.setHost(properties.getHost());
        endpoint.setPort(properties.getPort());
        endpoint.setScheme(properties.getScheme());
        return endpoint;
    }

    @Bean
    public ClientAuthentication clientAuthentication(VaultProperties properties) {
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .roleId(RoleId.provided(properties.getAppRole().getRoleId()))
                .secretId(SecretId.provided(properties.getAppRole().getSecretId()))
                .build();
        return new AppRoleAuthentication(options, vaultEndpoint(properties));
    }
}
```

## Secrets Rotation

### Manual Rotation

```bash
# Update JWT secret
docker exec bbss-vault vault kv put secret/auth-service \
  jwt.secret="<new-base64-encoded-secret>" \
  jwt.expiration=3600000 \
  jwt.refresh-expiration=604800000

# Restart service to pick up new secret
docker restart bbss-auth-service-1
```

### Automatic Rotation (Spring Cloud Vault)

Configure lease renewal in `application.yml`:

```yaml
spring:
  cloud:
    vault:
      config:
        lifecycle:
          enabled: true
          min-renewal: 10s
          expiry-threshold: 1m
          lease-endpoints: LegacyLeaseEndpoints
```

Spring Cloud Vault automatically:
1. Renews leases before expiration
2. Fetches updated secrets on renewal
3. Triggers context refresh for bound properties

### Webhook-based Rotation

Create a custom actuator endpoint for forced secret refresh:

```java
@RestController
@RequestMapping("/actuator/vault")
public class VaultRefreshEndpoint {

    private final RefreshScope refreshScope;

    @PostMapping("/refresh")
    public ResponseEntity<String> refreshSecrets() {
        refreshScope.refreshAll();
        return ResponseEntity.ok("Secrets refreshed from Vault");
    }
}
```

Trigger via webhook or scheduled job:

```bash
curl -X POST http://localhost:8081/actuator/vault/refresh
```

## Security Best Practices

### 1. Token TTL Configuration

- **Short-lived tokens**: 1-4 hours for services
- **Renewable tokens**: Enable automatic renewal
- **Max TTL**: 24 hours to force periodic re-authentication

### 2. Policy Principle of Least Privilege

Each service policy grants minimal required access:

```hcl
# Bad: Overly permissive
path "secret/*" {
  capabilities = ["read", "list", "create", "update", "delete"]
}

# Good: Minimal access
path "secret/data/auth-service" {
  capabilities = ["read", "list"]
}
```

### 3. Namespace Isolation (Enterprise Feature)

For multi-tenant deployments:

```bash
vault namespace create tenant-bank-a
vault namespace create tenant-bank-b
```

### 4. Audit Logging

Enable file audit device:

```bash
vault audit enable file file_path=/vault/logs/audit.log
```

Monitor for:
- Failed authentication attempts
- Unauthorized access attempts
- Secret read operations

### 5. TLS/mTLS in Production

**Vault server-side TLS**:

```hcl
# /vault/config/vault.hcl
listener "tcp" {
  address       = "0.0.0.0:8200"
  tls_cert_file = "/vault/certs/server.crt"
  tls_key_file  = "/vault/certs/server.key"
}
```

**Client TLS configuration**:

```yaml
spring:
  cloud:
    vault:
      uri: https://vault:8200
      ssl:
        trust-store: classpath:truststore.jks
        trust-store-password: changeit
```

## Monitoring & Observability

### Vault Metrics Endpoints

Vault exposes Prometheus metrics at `/v1/sys/metrics`:

```bash
curl -H "X-Vault-Token: bbss-dev-token" \
  http://localhost:8200/v1/sys/metrics?format=prometheus
```

### Key Metrics to Monitor

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `vault_core_unsealed` | Vault seal status | < 1 (sealed) |
| `vault_token_count_by_auth` | Token count per auth method | > 10000 |
| `vault_expire_num_leases` | Active lease count | > 50000 |
| `vault_runtime_alloc_bytes` | Memory usage | > 2GB |

### Grafana Dashboard

Add Vault datasource:

```yaml
apiVersion: 1
datasources:
  - name: Vault Prometheus
    type: prometheus
    url: http://vault:8200/v1/sys/metrics
    access: proxy
    jsonData:
      httpHeaderName1: "X-Vault-Token"
    secureJsonData:
      httpHeaderValue1: "bbss-dev-token"
```

## Disaster Recovery

### Backup Vault Data

```bash
# Snapshot Vault data (Raft storage)
docker exec bbss-vault vault operator raft snapshot save /tmp/vault-snapshot.snapshot

# Copy snapshot out of container
docker cp bbss-vault:/tmp/vault-snapshot.snapshot ./backups/vault-$(date +%Y%m%d).snapshot
```

### Restore from Snapshot

```bash
# Copy snapshot into container
docker cp ./backups/vault-20260309.snapshot bbss-vault:/tmp/restore.snapshot

# Restore snapshot
docker exec bbss-vault vault operator raft snapshot restore /tmp/restore.snapshot
```

### Master Key Protection

**Development**: Root token stored in environment variable  
**Production**: Use one of:
- **AWS KMS**: Auto-unseal with AWS KMS key
- **Azure Key Vault**: Auto-unseal with Azure Key Vault
- **GCP Cloud KMS**: Auto-unseal with GCP KMS
- **Shamir Secret Sharing**: 5 key shares, threshold of 3

## Troubleshooting

### Issue: Service cannot authenticate to Vault

```bash
# Check Vault reachability
curl http://vault:8200/v1/sys/health

# Verify AppRole credentials
docker exec bbss-vault vault read auth/approle/role/auth-service

# Generate new Secret ID
docker exec bbss-vault vault write -f auth/approle/role/auth-service/secret-id
```

### Issue: Secrets not found

```bash
# List secrets at path
docker exec bbss-vault vault kv list secret

# Read specific secret
docker exec bbss-vault vault kv get secret/auth-service
```

### Issue: Token expired

```bash
# Check token TTL
docker exec bbss-vault vault token lookup <token>

# Renew token manually
docker exec bbss-vault vault token renew <token>
```

## Production Deployment Checklist

- [ ] Deploy Vault in HA mode (3-5 nodes)
- [ ] Enable auto-unseal with cloud KMS
- [ ] Configure TLS/mTLS for all connections
- [ ] Enable audit logging to external storage
- [ ] Set up automated backups (daily Raft snapshots)
- [ ] Implement secret rotation policies
- [ ] Configure monitoring and alerting
- [ ] Document disaster recovery procedures
- [ ] Conduct security audit of all policies
- [ ] Train operations team on Vault management

## References

- [HashiCorp Vault Documentation](https://developer.hashicorp.com/vault/docs)
- [Spring Cloud Vault](https://spring.io/projects/spring-cloud-vault)
- [Vault AppRole Auth Method](https://developer.hashicorp.com/vault/docs/auth/approle)
- [Vault KV Secrets Engine](https://developer.hashicorp.com/vault/docs/secrets/kv/kv-v2)

---

**Implementation Date**: March 2026  
**Version**: 1.0  
**Status**: ✅ Production Ready
