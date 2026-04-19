# Implementation Roadmap: Phases 3-7

## Overview

This document provides detailed implementation plans for Phases 3-7 of the BBSS platform modernization.

---

## Phase 3: Professional UI/UX Design System

### Objective
Transform the Next.js dashboard with a comprehensive design system, dark mode, responsive layouts, and accessibility compliance.

### Implementation Strategy

#### 1. Design Tokens

**File**: `frontend/dashboard/src/styles/tokens.css`

```css
:root {
  /* Colors - Primary Palette */
  --color-primary-50: #eff6ff;
  --color-primary-100: #dbeafe;
  --color-primary-500: #3b82f6;
  --color-primary-600: #2563eb;
  --color-primary-900: #1e3a8a;

  /* Colors - Semantic */
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --color-info: #3b82f6;

  /* Typography */
  --font-family-sans: 'Inter', system-ui, sans-serif;
  --font-family-mono: 'JetBrains Mono', monospace;
  
  --font-size-xs: 0.75rem;    /* 12px */
  --font-size-sm: 0.875rem;   /* 14px */
  --font-size-base: 1rem;     /* 16px */
  --font-size-lg: 1.125rem;   /* 18px */
  --font-size-xl: 1.25rem;    /* 20px */
  --font-size-2xl: 1.5rem;    /* 24px */
  
  /* Spacing */
  --spacing-1: 0.25rem;  /* 4px */
  --spacing-2: 0.5rem;   /* 8px */
  --spacing-4: 1rem;     /* 16px */
  --spacing-8: 2rem;     /* 32px */
  
  /* Shadows */
  --shadow-sm: 0 1px 2px 0 rgb(0 0 0 / 0.05);
  --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1);
  --shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.1);
  
  /* Borders */
  --border-radius-sm: 0.25rem;
  --border-radius-md: 0.5rem;
  --border-radius-lg: 1rem;
}

[data-theme="dark"] {
  --color-bg-primary: #0f172a;
  --color-bg-secondary: #1e293b;
  --color-text-primary: #f1f5f9;
  --color-text-secondary: #cbd5e1;
}
```

#### 2. Component Library

**Button Component**: `frontend/dashboard/src/components/ui/Button.tsx`

```typescript
import { ButtonHTMLAttributes, forwardRef } from 'react';
import { cva, VariantProps } from 'class-variance-authority';

const buttonVariants = cva(
  'inline-flex items-center justify-center rounded-md font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-blue-600 text-white hover:bg-blue-700',
        secondary: 'bg-gray-200 text-gray-900 hover:bg-gray-300',
        outline: 'border border-gray-300 bg-transparent hover:bg-gray-100',
        ghost: 'hover:bg-gray-100',
        danger: 'bg-red-600 text-white hover:bg-red-700',
      },
      size: {
        sm: 'h-9 px-3 text-sm',
        md: 'h-10 px-4 text-base',
        lg: 'h-11 px-6 text-lg',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'md',
    },
  }
);

interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  isLoading?: boolean;
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, isLoading, children, ...props }, ref) => {
    return (
      <button
        className={buttonVariants({ variant, size, className })}
        ref={ref}
        disabled={isLoading}
        {...props}
      >
        {isLoading && <Spinner className="mr-2" />}
        {children}
      </button>
    );
  }
);

Button.displayName = 'Button';
export { Button, buttonVariants };
```

#### 3. Dark Mode Implementation

**Theme Provider**: `frontend/dashboard/src/contexts/ThemeContext.tsx`

```typescript
'use client';

import { createContext, useContext, useEffect, useState } from 'react';

type Theme = 'light' | 'dark' | 'system';

interface ThemeContextProps {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  resolvedTheme: 'light' | 'dark';
}

const ThemeContext = createContext<ThemeContextProps | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<Theme>('system');
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>('light');

  useEffect(() => {
    const stored = localStorage.getItem('theme') as Theme;
    if (stored) setTheme(stored);
  }, []);

  useEffect(() => {
    localStorage.setItem('theme', theme);

    if (theme === 'system') {
      const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches
        ? 'dark'
        : 'light';
      setResolvedTheme(systemTheme);
      document.documentElement.setAttribute('data-theme', systemTheme);
    } else {
      setResolvedTheme(theme);
      document.documentElement.setAttribute('data-theme', theme);
    }
  }, [theme]);

  return (
    <ThemeContext.Provider value={{ theme, setTheme, resolvedTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
};
```

#### 4. Responsive Layout System

**Tailwind Configuration**: `frontend/dashboard/tailwind.config.ts`

```typescript
import type { Config } from 'tailwindcss';

const config: Config = {
  darkMode: ['class', '[data-theme="dark"]'],
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: 'var(--color-primary-50)',
          100: 'var(--color-primary-100)',
          500: 'var(--color-primary-500)',
          600: 'var(--color-primary-600)',
          900: 'var(--color-primary-900)',
        },
      },
      fontFamily: {
        sans: ['var(--font-family-sans)'],
        mono: ['var(--font-family-mono)'],
      },
      screens: {
        'xs': '475px',
        'sm': '640px',
        'md': '768px',
        'lg': '1024px',
        'xl': '1280px',
        '2xl': '1536px',
      },
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
  ],
};

export default config;
```

**Estimated Effort**: 2-3 weeks  
**Dependencies**: Tailwind CSS, Radix UI, class-variance-authority

---

## Phase 4: Complete Feature Set

### 1. WebSocket Notification System

**Backend**: `notification-service` (new microservice)

```java
@Service
public class NotificationService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendTransactionAlert(String tenantId, TransactionEvent event) {
        String destination = "/topic/transactions/" + tenantId;
        messagingTemplate.convertAndSend(destination, event);
    }
}

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3002")
                .withSockJS();
    }
}
```

**Frontend**: WebSocket client

```typescript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export function useTransactionNotifications(tenantId: string) {
  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8085/ws'),
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      onConnect: () => {
        client.subscribe(`/topic/transactions/${tenantId}`, (message) => {
          const event = JSON.parse(message.body);
          toast.success(`Transaction ${event.transactionId} updated`);
        });
      },
    });

    client.activate();
    return () => client.deactivate();
  }, [tenantId]);
}
```

### 2. Report Generation Service

**Dependencies**:
```xml
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext7-core</artifactId>
    <version>8.0.3</version>
</dependency>
```

**Implementation**:
```java
@Service
public class ReportGenerationService {
    public byte[] generateTransactionReport(String tenantId, LocalDate start, LocalDate end) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // Add title
        document.add(new Paragraph("Transaction Report")
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                .setFontSize(20));

        // Add transaction table
        Table table = new Table(new float[]{2, 3, 2, 2, 2});
        table.addHeaderCell("Transaction ID");
        table.addHeaderCell("Timestamp");
        table.addHeaderCell("Amount");
        table.addHeaderCell("Status");
        table.addHeaderCell("Fraud Score");

        List<Transaction> transactions = transactionRepository
                .findByTenantIdAndCreatedAtBetween(tenantId, start.atStartOfDay(), end.atTime(23, 59, 59));

        transactions.forEach(tx -> {
            table.addCell(tx.getTransactionId());
            table.addCell(tx.getCreatedAt().toString());
            table.addCell(tx.getAmount().toString());
            table.addCell(tx.getStatus().toString());
            table.addCell(tx.getFraudScore() != null ? tx.getFraudScore().toString() : "N/A");
        });

        document.add(table);
        document.close();

        return baos.toByteArray();
    }
}
```

**Estimated Effort**: 2-3 weeks

---

## Phase 5: Backend Service Improvements

### 1. Redis Caching Strategy

**Cache Configuration**:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(60))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("transactions", config.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put("users", config.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("fraud-scores", config.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
```

**Service Layer**:

```java
@Service
public class TransactionService {

    @Cacheable(value = "transactions", key = "#transactionId")
    public Transaction getTransaction(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    @CacheEvict(value = "transactions", key = "#transaction.transactionId")
    public Transaction updateTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    @Caching(evict = {
            @CacheEvict(value = "transactions", allEntries = true),
            @CacheEvict(value = "fraud-scores", allEntries = true)
    })
    public void clearAllCaches() {
        log.info("All caches cleared");
    }
}
```

### 2. Database Indexing Strategy

```sql
-- Transaction table indexes
CREATE INDEX idx_transactions_tenant_created ON transactions(tenant_id, created_at DESC);
CREATE INDEX idx_transactions_status ON transactions(status) WHERE status IN ('PENDING', 'FRAUD_HOLD');
CREATE INDEX idx_transactions_fraud_score ON transactions(fraud_score) WHERE fraud_score > 0.7;

-- User table indexes
CREATE INDEX idx_users_email_tenant ON users(email, tenant_id);
CREATE INDEX idx_users_tenant_enabled ON users(tenant_id, enabled);

-- Blockchain records indexes
CREATE INDEX idx_blockchain_records_txid ON blockchain_records(transaction_id);
CREATE INDEX idx_blockchain_records_tenant_timestamp ON blockchain_records(tenant_id, timestamp DESC);

-- Audit logs indexes
CREATE INDEX idx_audit_logs_entity_type_action ON audit_logs(entity_type, action);
CREATE INDEX idx_audit_logs_tenant_timestamp ON audit_logs(tenant_id, timestamp DESC);
```

**Estimated Effort**: 1-2 weeks

---

## Phase 6: Deployment & Operations

### 1. Kubernetes Manifests

**Namespace**: `infrastructure/k8s/namespace.yaml`

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: bbss
  labels:
    name: bbss
    environment: production
```

**Deployment Example**: `infrastructure/k8s/auth-service/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: bbss
spec:
  replicas: 3
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
        version: v1.0.0
    spec:
      containers:
      - name: auth-service
        image: ghcr.io/bbss/auth-service:1.0.0
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: VAULT_ADDR
          value: "http://vault.bbss.svc.cluster.local:8200"
        - name: VAULT_ROLE_ID
          valueFrom:
            secretKeyRef:
              name: vault-approle
              key: role-id
        - name: VAULT_SECRET_ID
          valueFrom:
            secretKeyRef:
              name: vault-approle
              key: secret-id
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 20
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: bbss
spec:
  selector:
    app: auth-service
  ports:
  - port: 8081
    targetPort: 8081
  type: ClusterIP
```

### 2. Helm Chart Structure

```
helm/bbss/
├── Chart.yaml
├── values.yaml
├── values-production.yaml
├── templates/
│   ├── _helpers.tpl
│   ├── auth-service/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── ingress.yaml
│   │   └── configmap.yaml
│   ├── transaction-service/
│   ├── blockchain-service/
│   └── ingress-nginx/
└── charts/
    ├── postgresql/
    ├── redis/
    └── kafka/
```

### 3. CI/CD Pipeline (GitHub Actions)

`.github/workflows/deploy-production.yml`:

```yaml
name: Deploy to Production

on:
  push:
    tags:
      - 'v*'

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 25
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build with Maven
        run: cd backend && mvn clean package -DskipTests

      - name: Build Docker images
        run: |
          docker build -t ghcr.io/bbss/auth-service:${{ github.ref_name }} backend/auth-service
          docker build -t ghcr.io/bbss/transaction-service:${{ github.ref_name }} backend/transaction-service
          docker build -t ghcr.io/bbss/blockchain-service:${{ github.ref_name }} backend/blockchain-service

      - name: Push to GitHub Container Registry
        run: |
          echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          docker push ghcr.io/bbss/auth-service:${{ github.ref_name }}
          docker push ghcr.io/bbss/transaction-service:${{ github.ref_name }}
          docker push ghcr.io/bbss/blockchain-service:${{ github.ref_name }}

      - name: Deploy to Kubernetes
        uses: azure/k8s-deploy@v4
        with:
          manifests: |
            infrastructure/k8s/auth-service/
            infrastructure/k8s/transaction-service/
            infrastructure/k8s/blockchain-service/
          images: |
            ghcr.io/bbss/auth-service:${{ github.ref_name }}
            ghcr.io/bbss/transaction-service:${{ github.ref_name }}
            ghcr.io/bbss/blockchain-service:${{ github.ref_name }}
          namespace: bbss
```

**Estimated Effort**: 2-3 weeks

---

## Phase 7: Blockchain Production Readiness

### 1. Hyperledger Fabric Network Configuration

**Production Network Topology**:
- 3 organizations (Org1Bank, Org2Bank, Org3Bank)
- 2 peers per organization (6 peers total)
- 5 ordering nodes (Raft consensus)
- 2 channels: `transactions-channel`, `audit-channel`

**Consensus Configuration**: `blockchain/network/config/orderer.yaml`

```yaml
General:
  ListenAddress: 0.0.0.0
  ListenPort: 7050
  ConnectionTimeout: 0s
  TLS:
    Enabled: true
    PrivateKey: /var/hyperledger/orderer/tls/server.key
    Certificate: /var/hyperledger/orderer/tls/server.crt
    RootCAs:
      - /var/hyperledger/orderer/tls/ca.crt

Consensus:
  Type: etcdraft
  EtcdRaft:
    Options:
      TickInterval: 500ms
      ElectionTick: 10
      HeartbeatTick: 1
      MaxInflightBlocks: 5
      SnapshotIntervalSize: 16 MB
    
FileLedger:
  Location: /var/hyperledger/production/orderer

Kafka:
  Retry:
    ShortInterval: 5s
    ShortTotal: 10m
    LongInterval: 5m
    LongTotal: 12h
```

### 2. Disaster Recovery Procedures

**Backup Script**: `blockchain/network/scripts/backup-ledger.sh`

```bash
#!/bin/bash

BACKUP_DIR="/backups/fabric-$(date +%Y%m%d-%H%M%S)"
mkdir -p $BACKUP_DIR

# Backup peer ledger data
for PEER in peer0.org1.example.com peer0.org2.example.com; do
    docker exec $PEER tar czf /tmp/ledger-backup.tar.gz \
        /var/hyperledger/production/ledgerData
    docker cp $PEER:/tmp/ledger-backup.tar.gz \
        $BACKUP_DIR/${PEER}-ledger.tar.gz
done

# Backup orderer ledger
docker exec orderer.example.com tar czf /tmp/orderer-backup.tar.gz \
    /var/hyperledger/production/orderer
docker cp orderer.example.com:/tmp/orderer-backup.tar.gz \
    $BACKUP_DIR/orderer-ledger.tar.gz

echo "Backup completed: $BACKUP_DIR"
```

### 3. Performance Tuning

**Chaincode Optimization**:

```go
// Use CouchDB rich queries efficiently
func (cc *TransactionChaincode) QueryTransactionsByDateRange(
    ctx contractapi.TransactionContextInterface,
    startDate string,
    endDate string,
) ([]*Transaction, error) {
    
    // Use pagination for large result sets
    queryString := fmt.Sprintf(`{
        "selector": {
            "timestamp": {
                "$gte": "%s",
                "$lte": "%s"
            }
        },
        "limit": 100,
        "bookmark": "%s"
    }`, startDate, endDate, bookmark)
    
    resultsIterator, responseMetadata, err := ctx.GetStub().
        GetQueryResultWithPagination(queryString, 100, bookmark)
    if err != nil {
        return nil, err
    }
    defer resultsIterator.Close()
    
    // Process results...
}
```

**Estimated Effort**: 3-4 weeks

---

## Complete Implementation Timeline

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1.5: Kafka Reliability | ✅ Complete | - |
| Phase 2: Security Hardening | ✅ Complete | Vault, Spring Security |
| Phase 3: UI/UX Design System | 2-3 weeks | Tailwind, Radix UI |
| Phase 4: Feature Completion | 2-3 weeks | WebSocket, iText |
| Phase 5: Backend Improvements | 1-2 weeks | Redis, PostgreSQL |
| Phase 6: K8s Deployment | 2-3 weeks | Kubernetes, Helm |
| Phase 7: Blockchain Prod | 3-4 weeks | Fabric 2.5 |

**Total Estimated Time**: 10-15 weeks

---

**Status**: 📋 Implementation Guide Complete  
**Last Updated**: March 2026
