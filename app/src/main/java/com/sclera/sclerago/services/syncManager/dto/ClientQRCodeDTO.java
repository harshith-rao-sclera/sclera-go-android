package com.sclera.sclerago.services.syncManager.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientQRCodeDTO {
    @JsonIgnore
    private String id;

    @JsonProperty("creationTime")
    private BigInteger added_at;

    @JsonProperty("createdBy")
    private String added_by;

    @JsonProperty("id")
    private String client_qr_code_id;

    @JsonProperty("deviceId")
    private String device_id;

    @JsonProperty("locationId")
    private String location_id;

    @JsonProperty("updated_time")
    private BigInteger updated_at;

    private String updated_by;

    @JsonProperty("vdmsId")
    private String vdms_id;

    private String batch_id;
    private String direction;
    private Integer movable;

    @JsonProperty("adcClientQrCodeCheck")
    private Integer adcClientQrCodeCheck;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigInteger getAdded_at() {
        return added_at;
    }

    public void setAdded_at(BigInteger added_at) {
        this.added_at = added_at;
    }

    public String getAdded_by() {
        return added_by;
    }

    public void setAdded_by(String added_by) {
        this.added_by = added_by;
    }

    public String getLocation_id() {
        return location_id;
    }

    public void setLocation_id(String location_id) {
        this.location_id = location_id;
    }

    public String getClient_qr_code_id() {
        return client_qr_code_id;
    }

    public void setClient_qr_code_id(String client_qr_code_id) {
        this.client_qr_code_id = client_qr_code_id;
    }

    public String getDevice_id() {
        return device_id;
    }

    public void setDevice_id(String device_id) {
        this.device_id = device_id;
    }

    public BigInteger getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(BigInteger updated_at) {
        this.updated_at = updated_at;
    }

    public String getUpdated_by() {
        return updated_by;
    }

    public void setUpdated_by(String updated_by) {
        this.updated_by = updated_by;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Integer getMovable() {
        return movable;
    }

    public void setMovable(int movable) {
        this.movable = movable;
    }

    public String getVdms_id() {
        return vdms_id;
    }

    public void setVdms_id(String vdms_id) {
        this.vdms_id = vdms_id;
    }

    public String getBatch_id() {
        return batch_id;
    }

    public void setBatch_id(String batch_id) {
        this.batch_id = batch_id;
    }

    public Integer getAdcClientQrCodeCheck() {
        return adcClientQrCodeCheck;
    }

    public void setAdcClientQrCodeCheck(Integer adcClientQrCodeCheck) {
        this.adcClientQrCodeCheck = adcClientQrCodeCheck;
    }

    public ClientQRCodeDTO() {
    }

    public ClientQRCodeDTO(String client_qr_code_id, String device_id, String vdms_id, String added_by, BigInteger added_at) {
        this.client_qr_code_id = client_qr_code_id;
        this.device_id = device_id;
        this.vdms_id = vdms_id;
        this.added_by = added_by;
        this.added_at = added_at;
    }

    public ClientQRCodeDTO(String client_qr_code_id, String device_id, String vdms_id, String added_by, BigInteger updated_at, String updated_by, BigInteger added_at) {
        this.client_qr_code_id = client_qr_code_id;
        this.device_id = device_id;
        this.vdms_id = vdms_id;
        this.added_by = added_by;
        this.updated_at = updated_at;
        this.updated_by = updated_by;
        this.added_at = added_at;
    }

    public ClientQRCodeDTO(String id, String client_qr_code_id, String location_id, String device_id, String vdms_id, String added_by, BigInteger updated_at, String updated_by, BigInteger added_at,int movable, String direction) {
        this.id = id;
        this.client_qr_code_id = client_qr_code_id;
        this.location_id = location_id;
        this.device_id = device_id;
        this.vdms_id = vdms_id;
        this.added_by = added_by;
        this.updated_at = updated_at;
        this.updated_by = updated_by;
        this.added_at = added_at;
        this.movable = movable;
        this.direction = direction;
    }
}
