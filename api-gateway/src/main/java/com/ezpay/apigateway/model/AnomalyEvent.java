package com.ezpay.apigateway.model;

import java.time.Instant;

public class AnomalyEvent {
    private long requestId;
    private String deviceId;
    private String reason;
    private String uri;
    private Instant timestamp;

    public AnomalyEvent() {}

    public AnomalyEvent(long requestId, String deviceId, String reason, String uri, Instant timestamp) {
        this.requestId = requestId;
        this.deviceId = deviceId;
        this.reason = reason;
        this.uri = uri;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

