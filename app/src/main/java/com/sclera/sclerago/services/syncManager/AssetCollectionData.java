package com.sclera.sclerago.services.syncManager;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetCollectionData {
    String id;
    String url;
    String email;
    String organisationId;
    String role;
    String accessToken;
    String sessionUrl;
    String refreshToken;
    String sessionId;
    String cloudUrl;

    public AssetCollectionData(String id, String url, String email, String organisationId, String role, String accessToken, String sessionUrl, String refreshToken, String sessionId, String cloudUrl) {
        this.id = id;
        this.url = url;
        this.email = email;
        this.organisationId = organisationId;
        this.role = role;
        this.accessToken = accessToken;
        this.sessionUrl = sessionUrl;
        this.refreshToken = refreshToken;
        this.sessionId = sessionId;
        this.cloudUrl = cloudUrl;
    }

    public AssetCollectionData() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getSessionUrl() {
        return sessionUrl;
    }

    public void setSessionUrl(String sessionUrl) {
        this.sessionUrl = sessionUrl;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCloudUrl() {
        return cloudUrl;
    }

    public void setCloudUrl(String cloudUrl) {
        this.cloudUrl = cloudUrl;
    }
}
