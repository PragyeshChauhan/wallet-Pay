package com.ezpay.wallet.auth_service.config;

import com.ezpay.infraservice.exception.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
@EnableScheduling
public class KeyStoreConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyStoreConfig.class);
    private static final long RENEWAL_THRESHOLD_DAYS = 7;

    private final AtomicReference<SslContext> nettySslContextRef = new AtomicReference<>();
    private final ResourceLoader resourceLoader;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${vault.keystore.secret-path:secret/ezpay/keystore}")
    private String keystoreSecretPath;

    @Value("${vault.truststore.secret-path:secret/ezpay/truststore}")
    private String truststoreSecretPath;

    @Value("${spring.ssl.key-store}")
    private String testKeystoreSecretPath;

    @Value("${spring.ssl.trust-store}")
    private String testTruststoreSecretPath;

    @Value("${spring.ssl.key-store-type}")
    private String keystoreType;

    @Value("${spring.ssl.key-store-password}")
    private String keystorePassword;

    private boolean isDevEnv = false;

    private X509TrustManager x509TrustManager;

    private KeyStore trustStore;
    private KeyStore keyStore;

    public KeyStoreConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        LOGGER.info("KeyStoreConfig initialized at {}", Instant.now());
    }

    @PostConstruct
    @Profile("dev")
    public void setDevEnvironment() {
        isDevEnv = true;
        LOGGER.info("Env is DEV initialized successfully at {}", Instant.now());
    }

    private KeyStore loadKeyStoreFromSecret(String secretPath) throws Exception {
        KeyStore ks = KeyStore.getInstance(keystoreType);
        if (isDevEnv) {
            try (InputStream is = resourceLoader.getResource(secretPath).getInputStream()) {
                ks.load(is, keystorePassword.toCharArray());
            } catch (Exception e) {
                LOGGER.error("Failed to load key store at {} IST: {}", Instant.now(), e.getMessage());
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Key store loading failed: " + e.getMessage());
            }
        } else {
            throw new UnsupportedOperationException("Vault integration not implemented for non-dev environments");
        }
        return ks;
    }
    @Bean
    public KeyStore keyStore() throws Exception {
        String path = isDevEnv ? testKeystoreSecretPath : keystoreSecretPath;
        this.keyStore = loadKeyStoreFromSecret(path);
        validateAndLogCertificateStatus(this.keyStore, "keystore");
        meterRegistry.counter("keystore.load.success", "status", "success").increment();
        return this.keyStore;
    }

    @Bean
    public KeyStore trustStore() throws Exception {
        String path = isDevEnv ? testTruststoreSecretPath : truststoreSecretPath;
        this.trustStore = loadKeyStoreFromSecret(path);
        validateAndLogCertificateStatus(this.trustStore, "truststore");
        meterRegistry.counter("truststore.load.success", "status", "success").increment();
        return this.trustStore;
    }

    @Bean
    public SslContext nettySslContext(@Qualifier("keyStore") KeyStore keyStore,  @Qualifier("trustStore") KeyStore trustStore) throws Exception {
        // Init KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        // Init TrustManagerFactory
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Extract X509TrustManager and store it
        for (var tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509Tm) {
                this.x509TrustManager = x509Tm;
                break;
            }
        }

        if (this.x509TrustManager == null) {
            throw new IllegalStateException("No X509TrustManager found in trust store.");
        }

        // Build SSL context
        SslContext sslContext = SslContextBuilder.forClient()
                .keyManager(kmf)
                .trustManager(tmf)
                .protocols("TLSv1.3")
                .build();

        nettySslContextRef.set(sslContext);
        LOGGER.info("Netty SslContext initialized successfully at {}", Instant.now());
        meterRegistry.counter("netty.sslcontext.init.success", "status", "success").increment();
        return sslContext;
    }

    private void validateAndLogCertificateStatus(KeyStore store, String type) throws Exception {
        for (String alias : Collections.list(store.aliases())) {
            Certificate cert = store.getCertificate(alias);
            if (cert instanceof X509Certificate x509Cert) {
                try {
                    x509Cert.checkValidity();
                    long daysUntilExpiry = (x509Cert.getNotAfter().toInstant().getEpochSecond() - Instant.now().getEpochSecond()) / (24 * 3600);
                    if (daysUntilExpiry <= RENEWAL_THRESHOLD_DAYS) {
                        LOGGER.warn("{} certificate {} expiring in {} days", type, alias, daysUntilExpiry);
                    }
                } catch (CertificateException e) {
                    LOGGER.error("{} certificate {} invalid: {}", type, alias, e.getMessage());
                }
            }
        }
    }

    @Scheduled(fixedRate = 24 * 60 * 60 * 1000)
    public void reloadCertificatesIfNeeded() {
        try {
            KeyStore newKeyStore = keyStore();
            KeyStore newTrustStore = trustStore();
            SslContext newNettySslContext = nettySslContext(newKeyStore, newTrustStore);
            nettySslContextRef.set(newNettySslContext);
            LOGGER.info("Certificates reloaded successfully at {}", Instant.now());
            meterRegistry.counter("certificate.reload.success", "status", "success").increment();
        } catch (Exception e) {
            LOGGER.error("Failed to reload certificates at {} IST: {}", Instant.now(), e.getMessage());
            meterRegistry.counter("certificate.reload.errors", "reason", "reload_failure").increment();
        }
    }

    public SslContext getNettySslContext() {
        if (nettySslContextRef.get() == null) {
            reloadCertificatesIfNeeded();
        }
        return nettySslContextRef.get();
    }

    public boolean validateCertificate(X509Certificate cert) {
        try {
            cert.checkValidity(); // check expiry etc.
            if (x509TrustManager != null) {
                x509TrustManager.checkClientTrusted(new X509Certificate[]{cert}, "RSA");
                return true;
            } else {
                LOGGER.warn("X509TrustManager not initialized");
            }
        } catch (Exception e) {
            LOGGER.warn("Certificate validation failed: {}", e.getMessage());
        }
        return false;
    }

    public KeyStore getLoadedTrustStore() {
        return trustStore;
    }

    public KeyStore getLoadedKeyStore() {
        return keyStore;
    }
}


