package com.example.judgex.models;

import java.io.Serializable;

public class User implements Serializable {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_JUDGE = "JUDGE";
    public static final String ROLE_TEAM_LEADER = "TEAM_LEADER";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private String uid;
    private String email;
    private String name;
    private String role; // ADMIN, JUDGE, or TEAM_LEADER
    private String assignedPanelId; // Global/default panel ID for backward compatibility
    private java.util.Map<String, String> eventPanelAssignments; // eventId -> panelId
    private String assignedTeamId; // For Team Leader: associated teamId
    private String status; // ACTIVE or INACTIVE
    private boolean active;
    private long createdAt;

    public User() {
        this.active = true;
        this.status = STATUS_ACTIVE;
        this.eventPanelAssignments = new java.util.HashMap<>();
    }

    public User(String uid, String email, String name, String role, String assignedPanelId, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.role = role;
        this.assignedPanelId = assignedPanelId;
        this.eventPanelAssignments = new java.util.HashMap<>();
        if (assignedPanelId != null && !assignedPanelId.isEmpty()) {
            // Can be mapped once event is known
        }
        this.status = STATUS_ACTIVE;
        this.active = true;
        this.createdAt = createdAt;
    }

    public User(String uid, String name, String email, String role) {
        this();
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
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

    public java.util.Map<String, String> getEventPanelAssignments() {
        if (eventPanelAssignments == null) eventPanelAssignments = new java.util.HashMap<>();
        return eventPanelAssignments;
    }

    public void setEventPanelAssignments(java.util.Map<String, String> eventPanelAssignments) {
        this.eventPanelAssignments = eventPanelAssignments;
    }

    public String getPanelForEvent(String eventId) {
        if (eventPanelAssignments != null && !eventPanelAssignments.isEmpty()) {
            if (eventId != null && eventPanelAssignments.containsKey(eventId)) {
                return eventPanelAssignments.get(eventId);
            }
            return null; // Event-aware user has no assignment for this event
        }
        return assignedPanelId; // Fallback only for legacy accounts without multi-event mappings
    }

    public void setPanelForEvent(String eventId, String panelId) {
        if (eventPanelAssignments == null) eventPanelAssignments = new java.util.HashMap<>();
        if (eventId != null) {
            if (panelId != null && !panelId.isEmpty()) {
                eventPanelAssignments.put(eventId, panelId);
            } else {
                eventPanelAssignments.remove(eventId);
            }
        }
        if (this.assignedPanelId == null || this.assignedPanelId.isEmpty()) {
            this.assignedPanelId = panelId;
        }
    }

    public String getAssignedTeamId() {
        return assignedTeamId;
    }

    public void setAssignedTeamId(String assignedTeamId) {
        this.assignedTeamId = assignedTeamId;
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

    public boolean isTeamLeader() {
        return ROLE_TEAM_LEADER.equalsIgnoreCase(this.role);
    }
}
