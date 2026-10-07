package com.example.judgex.models;

import java.io.Serializable;

public class Event implements Serializable {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private String id;
    private String name;
    private String description;
    private String status;
    private boolean isLocked;
    private long createdAt;

    public Event() {
        this.status = STATUS_ACTIVE;
    }

    public Event(String id, String name, String description, String status, boolean isLocked, long createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status != null ? status : STATUS_ACTIVE;
        this.isLocked = isLocked;
        this.createdAt = createdAt;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status != null ? status : STATUS_ACTIVE;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equalsIgnoreCase(this.status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equalsIgnoreCase(this.status);
    }

    public boolean isArchived() {
        return STATUS_ARCHIVED.equalsIgnoreCase(this.status);
    }
}
