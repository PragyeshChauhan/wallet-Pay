package com.ezpay.wallet.auth_service.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class RefreshToken extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tokenHash;
    private Long userId;
    private Instant expiresAt;
    private boolean revoked;
    private Instant rotatedAt;
    private String ip;
    private String userAgent;
    private String mobileNumber;
    private String deviceId;
    public RefreshToken() {
    }

    public RefreshToken(String deviceId, Instant expiresAt, Long id, String ip, String mobileNumber, boolean revoked, Instant rotatedAt, String tokenHash, String userAgent, Long userId) {
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
        this.id = id;
        this.ip = ip;
        this.mobileNumber = mobileNumber;
        this.revoked = revoked;
        this.rotatedAt = rotatedAt;
        this.tokenHash = tokenHash;
        this.userAgent = userAgent;
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(Instant rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}

