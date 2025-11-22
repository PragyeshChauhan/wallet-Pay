package com.ezpay.notificationservice.dto;


import java.io.Serializable;
import java.util.Map;

public class UserEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private String UserId;
    private String email;
    private String name;
    private String eventType;
    private String redirectUrl;

    public String getUserId() {
        return UserId;
    }

    public void setUserId(String userId) {
        UserId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
}
