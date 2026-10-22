package com.example.judgex.models;

import java.io.Serializable;

public class Organization implements Serializable {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private String id;
    private String name;
    private String adminUid;
    private String adminEmail;
    private String contactPhone;
    private String status;
    private long createdAt;

    public Organization() {
        this.status = STATUS_ACTIVE;
        this.createdAt = System.currentTimeMillis();
    }

    public Organization(String id, String name, String adminUid, String adminEmail, String contactPhone) {
        this();
        this.id = id;
        this.name = name;
        this.adminUid = adminUid;
        this.adminEmail = adminEmail;
        this.contactPhone = contactPhone != null ? contactPhone : "";
    }

    public Organization(String id, String name, String adminUid, String adminEmail) {
        this(id, name, adminUid, adminEmail, "");
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

    public String getAdminUid() {
        return adminUid;
    }

    public void setAdminUid(String adminUid) {
        this.adminUid = adminUid;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getStatus() {
        return status != null ? status : STATUS_ACTIVE;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
