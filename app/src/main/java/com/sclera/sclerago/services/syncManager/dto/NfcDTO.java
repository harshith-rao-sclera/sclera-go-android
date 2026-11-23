package com.sclera.sclerago.services.syncManager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NfcDTO {
    private String id;

    @JsonProperty("createdBy")
    private String created_by;

    @JsonProperty("creationTime")
    private BigInteger creation_time;

    @JsonProperty("deviceId")
    private String device_id;

    @JsonProperty("locationId")
    private String location_id;

    private String uid;

    @JsonProperty("vdmsId")
    private String vdms_id;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCreated_by() {
        return created_by;
    }

    public void setCreated_by(String created_by) {
        this.created_by = created_by;
    }

    public BigInteger getCreation_time() {
        return creation_time;
    }

    public void setCreation_time(BigInteger creation_time) {
        this.creation_time = creation_time;
    }

    public String getDevice_id() {
        return device_id;
    }

    public void setDevice_id(String device_id) {
        this.device_id = device_id;
    }

    public String getLocation_id() {
        return location_id;
    }

    public void setLocation_id(String location_id) {
        this.location_id = location_id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getVdms_id() {
        return vdms_id;
    }

    public void setVdms_id(String vdms_id) {
        this.vdms_id = vdms_id;
    }

    public NfcDTO() {
    }

    public NfcDTO(String id, String created_by, BigInteger creation_time, String device_id, String location_id, String uid, String vdms_id) {
        this.id = id;
        this.created_by = created_by;
        this.creation_time = creation_time;
        this.device_id = device_id;
        this.location_id = location_id;
        this.uid = uid;
        this.vdms_id = vdms_id;
    }

    @Override
    public String toString() {
        return "NfcDTO{" +
                "id='" + id + '\'' +
                ", created_by='" + created_by + '\'' +
                ", creation_time=" + creation_time +
                ", device_id='" + device_id + '\'' +
                ", location_id='" + location_id + '\'' +
                ", uid='" + uid + '\'' +
                ", vdms_id='" + vdms_id + '\'' +
                '}';
    }
}
