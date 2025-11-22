package com.ezpay.wallet.auth_service.dto.response;


public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private long accessExp;
    private long refreshExp;

    public TokenResponse(String accessToken, String refreshToken, long accessExp, long refreshExp) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessExp = accessExp;
        this.refreshExp = refreshExp;
    }

    public long getAccessExp() {
        return accessExp;
    }

    public void setAccessExp(long accessExp) {
        this.accessExp = accessExp;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public long getRefreshExp() {
        return refreshExp;
    }

    public void setRefreshExp(long refreshExp) {
        this.refreshExp = refreshExp;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
