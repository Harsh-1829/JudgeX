package com.example.judgex.models;

import java.io.Serializable;

public class User implements Serializable {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_JUDGE = "JUDGE";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private String uid;
    private String email;
    private String name;
    private String role; // ADMIN or JUDGE
    private String assignedPanelId;
    private String status; // ACTIVE or INACTIVE
    private boolean active;
    private long createdAt;

    public User() {
        this.active = true;
        this.status = STATUS_ACTIVE;
    }

    public User(String uid, String email, String name, String role, String assignedPanelId, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.role = role;
        this.assignedPanelId = assignedPanelId;
        this.status = STATUS_ACTIVE;
        this.active = true;
        this.createdAt = createdAt;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAssignedPanelId() {
        return assignedPanelId;
    }

    public void setAssignedPanelId(String assignedPanelId) {
        this.assignedPanelId = assignedPanelId;
    }

    public String getStatus() {
        return status != null ? status : STATUS_ACTIVE;
    }

    public void setStatus(String status) {
        this.status = status;
        this.active = STATUS_ACTIVE.equalsIgnoreCase(status);
    }

    public boolean isActive() {
        return active && !STATUS_INACTIVE.equalsIgnoreCase(this.status);
    }

    public void setActive(boolean active) {
        this.active = active;
        this.status = active ? STATUS_ACTIVE : STATUS_INACTIVE;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(this.role);
    }

    public boolean isJudge() {
        return ROLE_JUDGE.equalsIgnoreCase(this.role);
    }
}
