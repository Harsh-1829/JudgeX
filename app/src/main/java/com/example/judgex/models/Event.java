package com.example.judgex.models;

import java.io.Serializable;

public class Event implements Serializable {
    public static final String STATUS_UPCOMING = "UPCOMING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    public static final String SCORING_WEIGHTED = "WEIGHTED";
    public static final String SCORING_RAW = "RAW";

    private String id;
    private String name;
    private String description;
    private String status;
    private String scoringType; // WEIGHTED or RAW
    private boolean isAnonymousJudging;
    private boolean isLocked;
    private String organizationId; // Multi-college tenant isolation
    private long createdAt;
    private long scheduledDate;
    private String startTime;
    private String endTime;
    private String venue;
    private boolean deleted;

    public Event() {
        this.status = STATUS_UPCOMING;
        this.scoringType = SCORING_WEIGHTED;
        this.isAnonymousJudging = false;
        this.isLocked = false;
        this.deleted = false;
    }

    public Event(String id, String name, String description, String status, boolean isLocked, long createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status != null ? status : STATUS_UPCOMING;
        this.scoringType = SCORING_WEIGHTED;
        this.isAnonymousJudging = false;
        this.isLocked = isLocked || STATUS_LOCKED.equalsIgnoreCase(status);
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
        return status != null ? status : STATUS_UPCOMING;
    }

    public void setStatus(String status) {
        this.status = status;
        if (STATUS_LOCKED.equalsIgnoreCase(status)) {
            this.isLocked = true;
        }
    }

    public String getScoringType() {
        return scoringType != null ? scoringType : SCORING_WEIGHTED;
    }

    public void setScoringType(String scoringType) {
        this.scoringType = scoringType;
    }

    public boolean isAnonymousJudging() {
        return isAnonymousJudging;
    }

    public void setAnonymousJudging(boolean anonymousJudging) {
        isAnonymousJudging = anonymousJudging;
    }

    public boolean isLocked() {
        return isLocked || STATUS_LOCKED.equalsIgnoreCase(status);
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
        if (locked && STATUS_ACTIVE.equalsIgnoreCase(this.status)) {
            this.status = STATUS_LOCKED;
        }
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isUpcoming() {
        return STATUS_UPCOMING.equalsIgnoreCase(this.status);
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

    public boolean isAcceptingEvaluations() {
        return isActive() && !isLocked;
    }

    public boolean isEditable() {
        return isUpcoming() && !isLocked && !isCompleted();
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public long getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(long scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
