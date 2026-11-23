package com.sclera.sclerago.services.syncManager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientBarCodeDTO {
    private String id;
    @JsonProperty("clientBarCodeId")
    private String client_bar_code_id;
    @JsonProperty("deviceId")
    private String device_id;
    @JsonProperty("vdmsId")
    private String vdms_id;
    @JsonProperty("addedAt")
    private String added_at;
    @JsonProperty("addedBy")
    private String added_by;
    @JsonProperty("batchId")
    private String batch_id;
    @JsonProperty("updatedTime")
    private BigInteger updated_time;
    @JsonProperty("updatedBy")
    private String updated_by;
    @JsonProperty("locationId")
    private String location_id;

    public ClientBarCodeDTO() {
    }

    public ClientBarCodeDTO(String id, String client_bar_code_id, String device_id, String vdms_id, String added_at, String added_by, String batch_id, BigInteger updated_time, String updated_by) {
        this.id = id;
        this.client_bar_code_id = client_bar_code_id;
        this.device_id = device_id;
        this.vdms_id = vdms_id;
        this.added_at = added_at;
        this.added_by = added_by;
        this.batch_id = batch_id;
        this.updated_time = updated_time;
        this.updated_by = updated_by;
    }

    public ClientBarCodeDTO(String id, String client_bar_code_id, String device_id, String vdms_id, String added_at, String added_by, BigInteger updated_time, String updated_by, String location_id) {
        this.id = id;
        this.client_bar_code_id = client_bar_code_id;
        this.device_id = device_id;
        this.vdms_id = vdms_id;
        this.added_at = added_at;
        this.added_by = added_by;
        this.updated_time = updated_time;
        this.updated_by = updated_by;
        this.location_id = location_id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClient_bar_code_id() {
        return client_bar_code_id;
    }

    public void setClient_bar_code_id(String client_bar_code_id) {
        this.client_bar_code_id = client_bar_code_id;
    }

    public String getDevice_id() {
        return device_id;
    }

    public void setDevice_id(String device_id) {
        this.device_id = device_id;
    }

    public String getVdms_id() {
        return vdms_id;
    }

    public void setVdms_id(String vdms_id) {
        this.vdms_id = vdms_id;
    }

    public String getAdded_at() {
        return added_at;
    }

    public void setAdded_at(String added_at) {
        this.added_at = added_at;
    }

    public String getAdded_by() {
        return added_by;
    }

    public void setAdded_by(String added_by) {
        this.added_by = added_by;
    }

    public String getBatch_id() {
        return batch_id;
    }

    public void setBatch_id(String batch_id) {
        this.batch_id = batch_id;
    }

    public BigInteger getUpdated_time() {
        return updated_time;
    }

    public void setUpdated_time(BigInteger updated_time) {
        this.updated_time = updated_time;
    }

    public String getUpdated_by() {
        return updated_by;
    }

    public void setUpdated_by(String updated_by) {
        this.updated_by = updated_by;
    }

    public String getLocation_id() {
        return location_id;
    }

    public void setLocation_id(String location_id) {
        this.location_id = location_id;
    }
}