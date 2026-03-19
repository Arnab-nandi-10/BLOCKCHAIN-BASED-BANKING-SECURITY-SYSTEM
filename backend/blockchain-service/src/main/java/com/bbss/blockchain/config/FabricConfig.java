package com.bbss.blockchain.config;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * Configures the Hyperledger Fabric Gateway connection using the
 * fabric-gateway 1.6.0 Java SDK.
 *
 * <p>Credentials are loaded from the PEM files referenced by the
 * {@code fabric.*} configuration properties. All paths are expected to be
 * absolute or relative to the working directory of the running JVM.</p>
 *
 * <p>Configuration properties prefix: {@code fabric}</p>
 */

@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true", matchIfMissing = false)
@Configuration
@ConfigurationProperties(prefix = "fabric")
@Getter
@Setter
@Slf4j
public class FabricConfig {

    /** Logical name of the Hyperledger Fabric network (informational). */
    private String networkName;

    /** Name of the Fabric channel to connect to. */
    private String channelName;

    /** Name of the transaction chaincode installed on the channel. */
    private String chaincodeTransaction;

    /** Name of the audit chaincode installed on the channel. */
    private String chaincodeAudit;

    /** MSP (Membership Service Provider) ID of the organisation. */
    private String mspId;

    /** Absolute path to the TLS root CA certificate for the peer (PEM). */
    private String tlsCertPath;

    /** Absolute path to the signing certificate of the client identity (PEM). */
    private String certPath;

    /** Absolute path to the directory containing the private key (PEM). */
    private String keystorePath;

    /** gRPC endpoint of the peer node, e.g. {@code peer0.org1.example.com:7051}. */
    private String peerEndpoint;

    /**
     * Override authority used by gRPC for TLS SNI matching, typically the peer's
     * hostname as it appears in the TLS certificate.
     */
    private String peerHostAlias;

    /** Hold a reference to the channel so we can close it on shutdown. */
    private ManagedChannel managedChannel;

    /** Hold a reference to the gateway so we can close it on shutdown. */
    private Gateway gateway;

    // -------------------------------------------------------------------------
    // Bean definitions
    // -------------------------------------------------------------------------

    /**
     * Build and return the Hyperledger Fabric {@link Gateway}.
     *
     * <p>The gateway is connected to the peer via a TLS-secured gRPC channel.
     * Call timeouts are configured conservatively for a banking workload.</p>
     *
     * @return connected Gateway instance
     * @throws IOException          when credential files cannot be read
     * @throws CertificateException when the signing certificate is malformed
     * @throws InvalidKeyException  when the private key is malformed
     */
    @Bean
    public Gateway fabricGateway() throws IOException, CertificateException, InvalidKeyException {
        log.info("Initialising Hyperledger Fabric Gateway — peer={} channel={} mspId={}",
                peerEndpoint, channelName, mspId);

        // Load the X.509 signing certificate.
        X509Certificate certificate = loadCertificate(certPath);

        // Load the private key from the keystore directory.
        PrivateKey privateKey = loadPrivateKey(keystorePath);

        // Build the client identity.
        Identity identity = new X509Identity(mspId, certificate);

        // Build the TLS-secured gRPC channel.
        ChannelCredentials tlsCredentials = TlsChannelCredentials.newBuilder()
                .trustManager(Paths.get(tlsCertPath).toFile())
                .build();

        managedChannel = Grpc.newChannelBuilder(peerEndpoint, tlsCredentials)
                .overrideAuthority(peerHostAlias)
                .build();

        // Connect the gateway.
        gateway = Gateway.newInstance()
                .identity(identity)
                .signer(Signers.newPrivateKeySigner(privateKey))
                .connection(managedChannel)
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(60, TimeUnit.SECONDS))
                .connect();

        log.info("Fabric Gateway connected successfully to peer={}", peerEndpoint);
        return gateway;
    }

    /**
     * Obtain a {@link Network} handle for the configured Fabric channel.
     *
     * @param fabricGateway the connected gateway
     * @return Network instance
     */
    @Bean
    public Network fabricNetwork(Gateway fabricGateway) {
        return fabricGateway.getNetwork(channelName);
    }

    /**
     * Get the transaction chaincode {@link Contract} from the Fabric network.
     *
     * @param fabricNetwork the channel network
     * @return Contract for the transaction chaincode
     */
    @Bean("transactionContract")
    public Contract transactionContract(Network fabricNetwork) {
        log.info("Binding to transaction chaincode: {}", chaincodeTransaction);
        return fabricNetwork.getContract(chaincodeTransaction);
    }

    /**
     * Get the audit chaincode {@link Contract} from the Fabric network.
     *
     * @param fabricNetwork the channel network
     * @return Contract for the audit chaincode
     */
    @Bean("auditContract")
    public Contract auditContract(Network fabricNetwork) {
        log.info("Binding to audit chaincode: {}", chaincodeAudit);
        return fabricNetwork.getContract(chaincodeAudit);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Gracefully close the Fabric Gateway and the underlying gRPC channel on
     * application shutdown to avoid resource leaks.
     */
    @PreDestroy
    public void closeFabricResources() {
        log.info("Closing Hyperledger Fabric Gateway and gRPC channel...");
        if (gateway != null) {
            try {
                gateway.close();
            } catch (Exception ex) {
                log.warn("Error closing Fabric Gateway: {}", ex.getMessage());
            }
        }
        if (managedChannel != null && !managedChannel.isShutdown()) {
            try {
                managedChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for gRPC channel shutdown");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private credential loading helpers
    // -------------------------------------------------------------------------

    private X509Certificate loadCertificate(String certFilePath)
            throws IOException, CertificateException {
        Path path = Paths.get(certFilePath);
        try (Reader reader = Files.newBufferedReader(path)) {
            return Identities.readX509Certificate(reader);
        }
    }

    private PrivateKey loadPrivateKey(String keystoreDir)
            throws IOException, InvalidKeyException {
        // Scan the keystore directory for the first PEM file.
        Path dir = Paths.get(keystoreDir);
        try (var stream = Files.list(dir)) {
            Path keyFile = stream
                    .filter(p -> p.toString().endsWith(".pem") || p.toString().endsWith("_sk"))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "No private key file found in keystore directory: " + keystoreDir));

            try (Reader reader = Files.newBufferedReader(keyFile)) {
                return Identities.readPrivateKey(reader);
            }
        }
    }
}
