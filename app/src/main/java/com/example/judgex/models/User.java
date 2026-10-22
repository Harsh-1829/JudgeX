package com.example.judgex.models;

import java.io.Serializable;

public class User implements Serializable {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_JUDGE = "JUDGE";
    public static final String ROLE_TEAM_LEADER = "TEAM_LEADER";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_DEACTIVATED = "DEACTIVATED";
    public static final String STATUS_DELETED = "DELETED";

    private String uid;
    private String email;
    private String name;
    private String role; // ADMIN, JUDGE, or TEAM_LEADER
    private String assignedPanelId; // Global/default panel ID for backward compatibility
    private java.util.Map<String, String> eventPanelAssignments; // eventId -> panelId
    private String assignedTeamId; // For Team Leader: associated teamId
    private String organizationId; // Multi-college tenant isolation
    private String organizationName;
    private String department;
    private String rollNumber;
    private String phone;
    private String school; // e.g. STME, SOC, SPTM, SBM, SOL
    private String yearOfStudy; // e.g. 1st Year, 2nd Year, etc.
    private String status; // ACTIVE or INACTIVE
    private boolean active;
    private boolean deleted;
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
        if (STATUS_DELETED.equalsIgnoreCase(status)) {
            this.deleted = true;
            this.active = false;
        }
    }

    public boolean isDeleted() {
        return deleted || STATUS_DELETED.equalsIgnoreCase(this.status);
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        if (deleted) {
            this.status = STATUS_DELETED;
            this.active = false;
        }
    }

    public boolean isActive() {
        return active && !isDeleted() && !STATUS_INACTIVE.equalsIgnoreCase(this.status) && !STATUS_DEACTIVATED.equalsIgnoreCase(this.status);
    }

    public void setActive(boolean active) {
        this.active = active;
        this.status = active ? STATUS_ACTIVE : STATUS_DEACTIVATED;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public static String normalizeRole(String rawRole) {
        if (rawRole == null) return "";
        String clean = rawRole.trim().toUpperCase(java.util.Locale.US);
        if (clean.startsWith("ROLE_")) {
            clean = clean.substring(5).trim();
        }
        return clean;
    }

    public boolean isAdmin() {
        String normalized = normalizeRole(this.role);
        return ROLE_ADMIN.equals(normalized);
    }

    public boolean isJudge() {
        String normalized = normalizeRole(this.role);
        if (ROLE_JUDGE.equals(normalized) || "EVALUATOR".equals(normalized) 
                || "JURY".equals(normalized) || "PANEL_JUDGE".equals(normalized)) {
            return true;
        }
        // Legacy fallback: role is empty/generic, but user has panel assignments and no team assignment
        if (normalized.isEmpty() || "USER".equals(normalized) || "MEMBER".equals(normalized)) {
            boolean hasPanel = (assignedPanelId != null && !assignedPanelId.trim().isEmpty())
                    || (eventPanelAssignments != null && !eventPanelAssignments.isEmpty());
            boolean hasTeam = assignedTeamId != null && !assignedTeamId.trim().isEmpty();
            return hasPanel && !hasTeam;
        }
        return false;
    }

    public boolean isTeamLeader() {
        String normalized = normalizeRole(this.role);
        if (ROLE_TEAM_LEADER.equals(normalized) || "LEADER".equals(normalized) || "TEAM".equals(normalized)) {
            return true;
        }
        if (assignedTeamId != null && !assignedTeamId.trim().isEmpty()) {
            return !isJudge() && !isAdmin();
        }
        return false;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getOrganizationName() {
        return organizationName != null ? organizationName : "Institution";
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school != null ? school.trim().toUpperCase(java.util.Locale.US) : null;
    }

    public String getYearOfStudy() {
        return yearOfStudy;
    }

    public void setYearOfStudy(String yearOfStudy) {
        this.yearOfStudy = yearOfStudy;
    }
}
