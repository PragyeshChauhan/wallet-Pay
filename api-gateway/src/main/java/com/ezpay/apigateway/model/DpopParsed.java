package com.ezpay.apigateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable Data Transfer Object representing parsed and verified DPoP proof data.
 * Encapsulates claims extracted from a DPoP JWT along with its JWK, thumbprint, and nonce.
 * Designed for secure usage in payment API validation logic.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DpopParsed {

    @JsonProperty("claims")
    @NotNull(message = "Claims cannot be null")
    private final Map<String, Object> claims;

    @JsonProperty("jwkJson")
    @NotBlank(message = "JWK JSON cannot be blank")
    @Size(max = 8192, message = "JWK JSON must not exceed 8192 characters")
    private final String jwkJson;

    @JsonProperty("thumbprint")
    @NotBlank(message = "Thumbprint cannot be blank")
    @Size(max = 256, message = "Thumbprint must not exceed 256 characters")
    private final String thumbprint;

    @JsonProperty("nonce")
    @NotBlank(message = "Nonce cannot be blank")
    @Size(min = 36, max = 36, message = "Nonce must be a 36-character UUID")
    private final String nonce;

    @JsonProperty("iat")
    private final Instant iat;

    /**
     * Private constructor for immutability.
     */
    private DpopParsed(Builder builder) {
        this.claims = builder.claims;
        this.jwkJson = builder.jwkJson;
        this.thumbprint = builder.thumbprint;
        this.nonce = builder.nonce;
        this.iat = builder.iat;
    }

    // Getters
    public Map<String, Object> getClaims() { return claims; }
    public String getJwkJson() { return jwkJson; }
    public String getThumbprint() { return thumbprint; }
    public String getJti() { return (String) claims.get("jti"); }
    public Instant getIat() { return iat; }
    public String getHtm() { return (String) claims.get("htm"); }
    public String getHtu() { return (String) claims.get("htu"); }
    public String getNonce() { return nonce; }

    /**
     * Builder for constructing immutable DpopParsed instances.
     */
    public static class Builder {
        private Map<String, Object> claims;
        private String jwkJson;
        private String thumbprint;
        private String nonce;
        private Instant iat;

        public Builder claims(Map<String, Object> claims) {
            this.claims = claims;
            return this;
        }

        public Builder jwkJson(String jwkJson) {
            this.jwkJson = jwkJson;
            return this;
        }

        public Builder thumbprint(String thumbprint) {
            this.thumbprint = thumbprint;
            return this;
        }

        public Builder nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder iat(Instant iat) {
            this.iat = iat;
            return this;
        }

        public Builder iatFromEpoch(Object iatClaim) {
            if (iatClaim != null) {
                long epoch = (iatClaim instanceof Number)
                        ? ((Number) iatClaim).longValue()
                        : Long.parseLong(iatClaim.toString());
                this.iat = Instant.ofEpochSecond(epoch);
            }
            return this;
        }

        public DpopParsed build() {
            return new DpopParsed(this);
        }
    }

    @Override
    public String toString() {
        return "DpopParsed{claims=" + claims +
                ", jwkJson=[REDACTED]" +
                ", thumbprint='" + thumbprint + '\'' +
                ", nonce='" + nonce + '\'' +
                ", iat=" + iat + '}';
    }
}
