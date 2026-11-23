package com.sclera.sclerago.services.syncManager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRCodeDTO {
    private String id;

    private String image_url;

    @JsonProperty("locationId")
    private String location_id;

    @JsonProperty("vdmsId")
    private String vdms_id;

    @JsonProperty("deviceId")
    private String device_id;

    private String created_by;

    @JsonProperty("creationTime")
    private BigInteger creation_time;

    private String batch_id;

    private String qr_code_link;

    private String updated_time;

    private String updated_by;
    private String direction;
    private Integer movable;
    @JsonProperty("adcQrCodeCheck")
    private Integer adcQrCodeCheck;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLocation_id() {
        return location_id;
    }

    public void setLocation_id(String location_id) {
        this.location_id = location_id;
    }

    public String getImage_url() {
        return image_url;
    }

    public void setImage_url(String image_url) {
        this.image_url = image_url;
    }

    public String getVdms_id() {
        return vdms_id;
    }

    public void setVdms_id(String vdms_id) {
        this.vdms_id = vdms_id;
    }

    public String getDevice_id() {
        return device_id;
    }

    public void setDevice_id(String device_id) {
        this.device_id = device_id;
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

    public String getQr_code_link() {
        return qr_code_link;
    }

    public void setQr_code_link(String qr_code_link) {
        this.qr_code_link = qr_code_link;
    }

    public String getUpdated_time() {
        return updated_time;
    }

    public void setUpdated_time(String updated_time) {
        this.updated_time = updated_time;
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

    public void setadcQrCodeCheck(int adcQrCodeCheck) {
        this.adcQrCodeCheck = adcQrCodeCheck;
    }
    public Integer getadcQrCodeCheck() {
        return adcQrCodeCheck;
    }

    public String getBatch_id() {
        return batch_id;
    }

    public void setBatch_id(String batch_id) {
        this.batch_id = batch_id;
    }

    public QRCodeDTO() {
    }

    public QRCodeDTO(String id, String device_id, String vdms_id, String qr_code_link, String updated_time, String updated_by, BigInteger creation_time) {
        this.id = id;
        this.device_id = device_id;
        this.vdms_id = vdms_id;
        this.qr_code_link = qr_code_link;
        this.updated_time = updated_time;
        this.updated_by = updated_by;
        this.creation_time = creation_time;
    }

    public QRCodeDTO(String id, String location_id, String vdms_id, BigInteger creation_time, String updated_time, String updated_by, String qr_code_link) {
        this.id = id;
        this.location_id = location_id;
        this.vdms_id = vdms_id;
        this.creation_time = creation_time;
        this.updated_time = updated_time;
        this.updated_by = updated_by;
        this.qr_code_link = qr_code_link;
    }

    public QRCodeDTO(String location_id, String vdms_id, String device_id) {
        this.location_id = location_id;
        this.vdms_id = vdms_id;
        this.device_id = device_id;
    }

    public QRCodeDTO(String id, String imageUrl, String locationId, String deviceId, String vdmsId, String createdBy, BigInteger creationTime, String updatedTime, String updatedBy, int movable, String direction) {
        this.id = id;
        this.image_url = imageUrl;
        this.location_id = locationId;
        this.device_id = deviceId;
        this.vdms_id = vdmsId;
        this.created_by = createdBy;
        this.creation_time = creationTime;
        this.updated_time = updatedTime;
        this.updated_by = updatedBy;
        this.movable = movable;
        this.direction = direction;
    }
}
