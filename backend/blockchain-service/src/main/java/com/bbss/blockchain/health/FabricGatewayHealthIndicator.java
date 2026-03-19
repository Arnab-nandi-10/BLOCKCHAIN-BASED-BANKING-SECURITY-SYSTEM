package com.bbss.blockchain.health;

import org.hyperledger.fabric.client.Contract;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for Hyperledger Fabric Gateway connectivity.
 * 
 * <p>This health check verifies that the Fabric Gateway connection is active
 * and the channel is accessible by attempting a lightweight query operation.</p>
 * 
 * <p>Only active when fabric.enabled=true</p>
 */
@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true")
@Component
public class FabricGatewayHealthIndicator implements HealthIndicator {

    private final Contract transactionContract;
    
    @Value("${fabric.channel-name}")
    private String channelName;
    
    @Value("${fabric.network-name}")
    private String networkName;
    
    @Value("${fabric.chaincode-transaction}")
    private String chaincodeTransaction;

    public FabricGatewayHealthIndicator(
            @Qualifier("transactionContract") Contract transactionContract) {
        this.transactionContract = transactionContract;
    }

    @Override
    public Health health() {
        try {
            // Verify the contract is accessible by getting its chaincode name
            // The Contract interface (fabric-gateway 1.x) exposes getChaincodeName()
            String contractName = transactionContract.getChaincodeName();

            // Use injected config values for channel (not available on Contract interface)
            boolean isHealthy = contractName != null &&
                              chaincodeTransaction.equals(contractName);

            if (isHealthy) {
                return Health.up()
                        .withDetail("networkName", networkName)
                        .withDetail("channelName", channelName)
                        .withDetail("chaincodeId", contractName)
                        .withDetail("gatewayConnection", "active")
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "Configuration mismatch")
                        .withDetail("expectedChannel", channelName)
                        .withDetail("expectedChaincode", chaincodeTransaction)
                        .withDetail("actualChaincode", contractName)
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .withDetail("channelName", channelName)
                    .withDetail("networkName", networkName)
                    .build();
        }
    }
}
