package com.ezpay.wallet.auth_service.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ezpay.infraservice.exception.ApiException;
import com.ezpay.wallet.auth_service.config.KeyStoreConfig;
import com.ezpay.wallet.auth_service.entity.Device;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Service for JWT token generation and validation.
 * Supports DPoP integration with consistent issuer and claims.
 */
@Service
public class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);


    @Value("${jwt.key.alias:ssl.key-alias}")
    private String keyAlias;

    @Value("${jwt.access-exp:3600}")
    private long accessExp;

    @Value("${jwt.issuer:https://ezpay-auth.com}")
    private String issuer;

    @Value("${jwt.audience}")
    private String jwtAudience;

    @Value("${spring.ssl.key-store-password}")
    private String keystorePassword;

    @Autowired
    private KeyStoreConfig keyStoreConfig;

    @Autowired
    private DeviceService deviceService;

    @Value("${dpop.validation.enabled:false}")
    private boolean dpopValidationEnabled;

    @Autowired
    private  RedisTemplate<String, String> redisTemplate;

    public String issueAccess(String mobileNumber, String userId, String deviceId) throws JOSEException {
        logger.debug("Issuing access token for user: {}, device: {}", userId, deviceId);

        var jwtBuilder = JWT.create()
                .withSubject(userId)
                .withClaim("mobileNumber", mobileNumber)
                .withClaim("deviceId", deviceId)
                .withIssuer(issuer)
                .withAudience(jwtAudience)
                .withExpiresAt(new Date(System.currentTimeMillis() + accessExp * 1000));

        if (dpopValidationEnabled) {
            String publicKeyPem = deviceService.getPublicKeyFromCache(deviceId);
            RSAPublicKey publicKey = parseRSAPublicKeyFromPEM(publicKeyPem);

            // Compute JWK + thumbprint (RFC 7638)
            RSAKey rsaJwk = new RSAKey.Builder(publicKey).build();
            String jkt = rsaJwk.computeThumbprint().toString();

            jwtBuilder.withClaim("cnf", Map.of("jkt", jkt));
            logger.debug("Added cnf.jkt claim for DPoP binding: {}", jkt);
        }

        String token = jwtBuilder.sign(algorithm());
        storeToken(token,deviceId);
        return token ;
    }

    private void storeToken(String token, String deviceId) {
        if (deviceId == null) {
            logger.debug("Device id id null");
            return ;
        }
        String sessionKey = "session:token:" + deviceId;
        String storedToken = redisTemplate.opsForValue().setGet(sessionKey,token, Duration.ofMillis(accessExp * 1000));
    }


    /**
     * Computes JWK SHA-256 thumbprint for the given RSA public key.
     */
    private String computeJwkThumbprint(RSAPublicKey publicKey) {
        try {
            // Create JWK representation (RFC 7638)
            String jwkJson = String.format(
                    "{\"e\":\"%s\",\"kty\":\"RSA\",\"n\":\"%s\"}",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray()),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray())
            );
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256.digest(jwkJson.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compute JWK thumbprint: " + e.getMessage());
        }
    }

    public String issueAccessByExp(String mobileNumber, String userId, String deviceId, long customExp) {
        logger.debug("Issuing custom exp access token for user: {}, device: {}", userId, deviceId);

        var jwtBuilder =   JWT.create()
                .withSubject(userId)
                .withClaim("mobileNumber", mobileNumber)
                .withClaim("deviceId", deviceId)
                .withIssuer(issuer)
                .withAudience(jwtAudience)
                .withExpiresAt(new Date(System.currentTimeMillis() + customExp * 1000));

        if (dpopValidationEnabled) {
            String publickey = deviceService.getPublicKeyFromCache(deviceId);
            String jkt = computeJwkThumbprint(parseRSAPublicKeyFromPEM(publickey));
            jwtBuilder.withClaim("cnf", Map.of("jkt", jkt));
            logger.debug("Added cnf.jkt claim for DPoP binding: {}", jkt);
        }
        return jwtBuilder.sign(algorithm());
    }

    public DecodedJWT parseToken(String token) {
        try {
            return JWT.require(algorithmToDecodeJWT())
                    .withIssuer(issuer)
                    .build()
                    .verify(token);
        } catch (JWTVerificationException e) {
            logger.warn("Invalid JWT: {}", e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private Algorithm algorithmToDecodeJWT() {
        try {
            KeyStore keyStore = keyStoreConfig.trustStore();
            String alias = keyStore.aliases().nextElement();
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) throw new KeyStoreException("No certificate found for alias: " + alias);
            return Algorithm.RSA256((RSAPublicKey) cert.getPublicKey(), null);
        } catch (KeyStoreException  | RuntimeException e) {
            logger.error("Failed to load RSA public key at {} IST: {}", Instant.now(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RSA public key loading failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unknown Error Failed to load RSA public key at {} IST: {}", Instant.now(), e.getMessage());
        }
        return null;
    }

    public String getSubject(String token) {
        try {
            return parseToken(token).getSubject();
        } catch (ApiException e) {
            logger.warn("Invalid JWT subject: {}", e.getMessage());
            throw e;
        }
    }

    public String getMobileNumber(String token) {
        try {
            return parseToken(token).getClaim("mobileNumber").asString();
        } catch (ApiException e) {
            logger.warn("Failed to get mobileNumber from token: {}", e.getMessage());
            throw e;
        }
    }

    public String getDeviceId(String token) {
        try {
            return parseToken(token).getClaim("deviceId").asString();
        } catch (ApiException e) {
            logger.warn("Failed to get deviceId from token: {}", e.getMessage());
            throw e;
        }
    }

    private Algorithm algorithm() {
        try {
            KeyStore keyStore = keyStoreConfig.keyStore();
            Key key = keyStore.getKey(keyAlias, keystorePassword.toCharArray());
            return Algorithm.RSA256(null , (RSAPrivateKey) key);
        } catch (KeyStoreException  | RuntimeException e) {
            logger.error("Failed to load RSA Private key at {} IST: {}", Instant.now(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RSA public key loading failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unknown Error Failed to load RSA Private key at {} IST: {}", Instant.now(), e.getMessage());
        }
        return null;
    }

    public static RSAPublicKey parseRSAPublicKeyFromPEM(String pem) {
        try {
            // Remove PEM headers/footers and whitespace
            String sanitized = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(sanitized);

            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid PEM public key", e);
        }
    }
}