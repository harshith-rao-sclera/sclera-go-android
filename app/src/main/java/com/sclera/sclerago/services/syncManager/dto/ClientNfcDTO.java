package com.sclera.sclerago.services.syncManager.dto;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigInteger;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientNfcDTO {

    private String id;
    private String nfc_id;
    private String uid;
    private String locationId;
    private String deviceId;
    private BigInteger creationTime;
    private String createdBy;
    private String vdmsId;

    public ClientNfcDTO() {
    }

    public ClientNfcDTO(String id, String nfc_id, String uid, String locationId, String deviceId, BigInteger creationTime, String createdBy, String vdmsId) {
        this.id = id;
        this.nfc_id = nfc_id;
        this.uid = uid;
        this.locationId = locationId;
        this.deviceId = deviceId;
        this.creationTime = creationTime;
        this.createdBy = createdBy;
        this.vdmsId = vdmsId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNfc_id() {
        return nfc_id;
    }

    public void setNfc_id(String nfc_id) {
        this.nfc_id = nfc_id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public BigInteger getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(BigInteger creationTime) {
        this.creationTime = creationTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getVdmsId() {
        return vdmsId;
    }

    public void setVdmsId(String vdmsId) {
        this.vdmsId = vdmsId;
    }

    @NonNull
    @Override
    public String toString() {
        return "ClientNfcDTO{" +
                "id='" + id + '\'' +
                ", nfc_id='" + nfc_id + '\'' +
                ", uid='" + uid + '\'' +
                ", locationId='" + locationId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", creationTime=" + creationTime +
                ", createdBy='" + createdBy + '\'' +
                ", vdmsId='" + vdmsId + '\'' +
                '}';
    }
}
