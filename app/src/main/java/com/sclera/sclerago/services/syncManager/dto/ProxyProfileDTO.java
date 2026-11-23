package com.sclera.sclerago.services.syncManager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProxyProfileDTO {
    private String id;
    private String name;
    private String public_ip;
    private String tcp_port;
    private String udp_port;
    private String host_machine;
    private String customer_org_id;
    private Integer is_deleted;
    private Integer is_global;
    private Integer ssl_enabled;
    private String latitude;
    private String longitude;

    public ProxyProfileDTO() {
    }

    public ProxyProfileDTO(String id, String name, String public_ip, String tcp_port, String udp_port,
                           String host_machine, String customer_org_id , Integer is_global , Integer ssl_enabled) {
        this.id = id;
        this.name = name;
        this.public_ip = public_ip;
        this.tcp_port = tcp_port;
        this.udp_port = udp_port;
        this.host_machine = host_machine;
        this.customer_org_id = customer_org_id;
        this.is_global = is_global;
        this.ssl_enabled = ssl_enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPublic_ip() {
        return public_ip;
    }

    public void setPublic_ip(String public_ip) {
        this.public_ip = public_ip;
    }

    public String getTcp_port() {
        return tcp_port;
    }

    public void setTcp_port(String tcp_port) {
        this.tcp_port = tcp_port;
    }

    public String getUdp_port() {
        return udp_port;
    }

    public void setUdp_port(String udp_port) {
        this.udp_port = udp_port;
    }

    public String getHost_machine() {
        return host_machine;
    }

    public void setHost_machine(String host_machine) {
        this.host_machine = host_machine;
    }

    public String getCustomer_org_id() {
        return customer_org_id;
    }

    public void setCustomer_org_id(String customer_org_id) {
        this.customer_org_id = customer_org_id;
    }

    public Integer getIs_deleted() {
        return is_deleted;
    }

    public void setIs_deleted(Integer is_deleted) {
        this.is_deleted = is_deleted;
    }

    public Integer getIs_global() {
        return is_global;
    }

    public void setIs_global(Integer is_global) {
        this.is_global = is_global;
    }

    public Integer getSsl_enabled() {
        return ssl_enabled;
    }

    public void setSsl_enabled(Integer ssl_enabled) {
        this.ssl_enabled = ssl_enabled;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return "ProxyProfileDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", public_ip='" + public_ip + '\'' +
                ", tcp_port='" + tcp_port + '\'' +
                ", udp_port='" + udp_port + '\'' +
                ", host_machine='" + host_machine + '\'' +
                ", customer_org_id='" + customer_org_id + '\'' +
                ", is_deleted=" + is_deleted +
                ", is_global=" + is_global +
                ", ssl_enabled=" + ssl_enabled +
                ", latitude='" + latitude + '\'' +
                ", longitude='" + longitude + '\'' +
                '}';
    }
}