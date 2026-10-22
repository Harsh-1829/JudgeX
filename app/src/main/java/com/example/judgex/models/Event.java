package com.example.judgex.models;

import java.io.Serializable;

public class Event implements Serializable {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_UPCOMING = "UPCOMING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    public static final String SCORING_WEIGHTED = "WEIGHTED";
    public static final String SCORING_RAW = "RAW";

    public static final String SCOPE_ALL_SCHOOLS = "ALL";
    public static final String SCOPE_SELECTED_SCHOOLS = "SELECTED";

    public static final String SCHOOL_STME = "STME";
    public static final String SCHOOL_SOC = "SOC";
    public static final String SCHOOL_SPTM = "SPTM";
    public static final String SCHOOL_SBM = "SBM";
    public static final String SCHOOL_SOL = "SOL";
    public static final java.util.List<String> ALL_SUPPORTED_SCHOOLS = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(SCHOOL_STME, SCHOOL_SOC, SCHOOL_SPTM, SCHOOL_SBM, SCHOOL_SOL)
    );

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

    // School Eligibility
    private String schoolScope; // ALL or SELECTED
    private java.util.List<String> eligibleSchools;

    // Discovery & Registration configurations
    private boolean published;
    private long registrationDeadline;
    private int minTeamSize;
    private int maxTeamSize;
    private String participationType; // TEAM or INDIVIDUAL
    private String eligibility;
    private boolean registrationApprovalRequired;
    private String category;
    private String department;
    private String bannerUrl;

    public Event() {
        this.status = STATUS_UPCOMING;
        this.scoringType = SCORING_WEIGHTED;
        this.isAnonymousJudging = false;
        this.isLocked = false;
        this.deleted = false;
        this.published = true; // Default published so existing events remain visible
        this.schoolScope = SCOPE_ALL_SCHOOLS;
        this.eligibleSchools = new java.util.ArrayList<>(ALL_SUPPORTED_SCHOOLS);
        this.minTeamSize = 1;
        this.maxTeamSize = 6;
        this.participationType = "TEAM";
        this.registrationApprovalRequired = false;
        this.category = "Hackathon";
        this.createdAt = System.currentTimeMillis();
    }

    public Event(String id, String name, String description, String status, boolean isLocked, long createdAt) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status != null ? status : STATUS_UPCOMING;
        this.scoringType = SCORING_WEIGHTED;
        this.isAnonymousJudging = false;
        this.isLocked = isLocked || STATUS_LOCKED.equalsIgnoreCase(status);
        if (createdAt > 0) {
            this.createdAt = createdAt;
        }
        this.published = !STATUS_DRAFT.equalsIgnoreCase(this.status);
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

    public boolean isPublished() {
        if (deleted) return false;
        if (STATUS_DRAFT.equalsIgnoreCase(status)) return false;
        if (STATUS_ARCHIVED.equalsIgnoreCase(status)) return false;
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public long getRegistrationDeadline() {
        return registrationDeadline;
    }

    public void setRegistrationDeadline(long registrationDeadline) {
        this.registrationDeadline = registrationDeadline;
    }

    public int getMinTeamSize() {
        return minTeamSize > 0 ? minTeamSize : 1;
    }

    public void setMinTeamSize(int minTeamSize) {
        this.minTeamSize = minTeamSize;
    }

    public int getMaxTeamSize() {
        return maxTeamSize > 0 ? maxTeamSize : 6;
    }

    public void setMaxTeamSize(int maxTeamSize) {
        this.maxTeamSize = maxTeamSize;
    }

    public String getParticipationType() {
        return participationType != null ? participationType : "TEAM";
    }

    public void setParticipationType(String participationType) {
        this.participationType = participationType;
    }

    public String getEligibility() {
        return eligibility != null ? eligibility : "Open to all verified college students.";
    }

    public void setEligibility(String eligibility) {
        this.eligibility = eligibility;
    }

    public boolean isRegistrationApprovalRequired() {
        return registrationApprovalRequired;
    }

    public void setRegistrationApprovalRequired(boolean registrationApprovalRequired) {
        this.registrationApprovalRequired = registrationApprovalRequired;
    }

    public String getCategory() {
        return category != null ? category : "Competition";
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDepartment() {
        return department != null ? department : "General";
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public boolean isRegistrationOpen() {
        if (!isPublished() || isLocked() || isCompleted() || isArchived()) {
            return false;
        }
        if (registrationDeadline > 0 && System.currentTimeMillis() > registrationDeadline) {
            return false;
        }
        return true;
    }

    public boolean canTeamRegister(int teamSize) {
        return isRegistrationOpen() && teamSize >= getMinTeamSize() && teamSize <= getMaxTeamSize();
    }

    public String getSchoolScope() {
        return schoolScope != null ? schoolScope : SCOPE_ALL_SCHOOLS;
    }

    public void setSchoolScope(String schoolScope) {
        this.schoolScope = schoolScope;
    }

    public java.util.List<String> getEligibleSchools() {
        if (eligibleSchools == null) {
            eligibleSchools = new java.util.ArrayList<>(ALL_SUPPORTED_SCHOOLS);
        }
        return eligibleSchools;
    }

    public void setEligibleSchools(java.util.List<String> eligibleSchools) {
        this.eligibleSchools = eligibleSchools;
    }

    public boolean isSchoolEligible(String school) {
        if (school == null || school.trim().isEmpty()) {
            return true; // Backward compatibility for unassigned or legacy profiles
        }
        String cleanSchool = school.trim().toUpperCase(java.util.Locale.US);
        if (schoolScope == null || SCOPE_ALL_SCHOOLS.equalsIgnoreCase(schoolScope)) {
            return true;
        }
        if (eligibleSchools == null || eligibleSchools.isEmpty()) {
            return true; // Default to open if no specific restriction is populated
        }
        for (String s : eligibleSchools) {
            if (s != null) {
                String cleanS = s.trim().toUpperCase(java.util.Locale.US);
                if (cleanS.equals(cleanSchool) || cleanS.equals("ALL") || cleanS.equals("ALL SCHOOLS")) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getEligibleSchoolsSummary() {
        if (schoolScope == null || SCOPE_ALL_SCHOOLS.equalsIgnoreCase(schoolScope)) {
            return "All Schools (STME, SOC, SPTM, SBM, SOL)";
        }
        if (eligibleSchools == null || eligibleSchools.isEmpty()) {
            return "All Schools";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < eligibleSchools.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(eligibleSchools.get(i));
        }
        return sb.toString();
    }
}
