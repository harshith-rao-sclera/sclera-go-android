package com.sclera.sclerago.services.syncManager.dto;

import androidx.annotation.NonNull;

public class NfcData {
    private String emailId;
    private String vdmsId;
    private String url;
    private Integer isReadOnly = 0;

    private String deviceId;

    private  String locationId;

    private String cloudApiUrl;

    private String uid;

    public NfcData() {
    }

    public NfcData(String emailId, String vdmsId, String url, Integer isReadOnly, String deviceId, String locationId, String cloudApiUrl) {
        this.emailId = emailId;
        this.vdmsId = vdmsId;
        this.url = url;
        this.isReadOnly = isReadOnly;
        this.deviceId = deviceId;
        this.locationId = locationId;
        this.cloudApiUrl = cloudApiUrl;
    }

    public NfcData(String deviceId, String vdmsId, String locationId) {
        this.deviceId = deviceId;
        this.vdmsId = vdmsId;
        this.locationId = locationId;
    }

    public NfcData(String deviceId, String vdmsId, String locationId, String nfcUniqueId) {
        this.deviceId = deviceId;
        this.vdmsId = vdmsId;
        this.locationId = locationId;
        this.uid = nfcUniqueId;
    }

    public String getEmailId() {
        return emailId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public String getVdmsId() {
        return vdmsId;
    }

    public void setVdmsId(String vdmsId) {
        this.vdmsId = vdmsId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(Integer isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getCloudApiUrl() {
        return cloudApiUrl;
    }

    public void setCloudApiUrl(String cloudApiUrl) {
        this.cloudApiUrl = cloudApiUrl;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    @NonNull
    @Override
    public String toString() {
        return "NfcData{" +
                "emailId='" + emailId + '\'' +
                ", vdmsId='" + vdmsId + '\'' +
                ", url='" + url + '\'' +
                ", isReadOnly=" + isReadOnly +
                ", deviceId='" + deviceId + '\'' +
                ", locationId='" + locationId + '\'' +
                ", cloudApiUrl='" + cloudApiUrl + '\'' +
                ", nfcUniqueId='" + uid + '\'' +
                '}';
    }
}
