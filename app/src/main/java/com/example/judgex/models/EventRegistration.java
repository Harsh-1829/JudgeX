package com.example.judgex.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class EventRegistration implements Serializable {
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WAITLISTED = "WAITLISTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_TEAM_LEADER = "TEAM_LEADER";

    private String id;
    private String eventId;
    private String eventName;
    private String teamId;
    private String teamName;
    private String teamNumber;
    private String organizationId;
    private String teamLeaderId;
    private String teamLeaderName;
    private String teamLeaderEmail;
    private List<String> memberIds;
    private int memberCount;
    private List<TeamMember> members; // Snapshot of members at registration time
    private String projectTitle;
    private String category;
    private String registrationSource; // ADMIN or TEAM_LEADER
    private String createdById;
    private String status; // CONFIRMED, PENDING, REJECTED, WAITLISTED, CANCELLED
    private long createdAt;
    private long updatedAt;
    private String remarks;

    public EventRegistration() {
        this.status = STATUS_CONFIRMED;
        this.registrationSource = SOURCE_TEAM_LEADER;
        this.memberIds = new ArrayList<>();
        this.members = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public static String generateRegistrationId() {
        return generateRegistrationId(null, null);
    }

    public static String generateRegistrationId(String eventId, String teamId) {
        String cleanEvent = eventId != null && eventId.length() >= 4 ? eventId.substring(0, 4).toUpperCase(Locale.US) : "EVT";
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.US);
        return "REG-" + cleanEvent + "-" + randomSuffix;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName != null ? eventName : "Competition Event";
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamNumber() {
        return teamNumber;
    }

    public void setTeamNumber(String teamNumber) {
        this.teamNumber = teamNumber;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getTeamLeaderId() {
        return teamLeaderId;
    }

    public void setTeamLeaderId(String teamLeaderId) {
        this.teamLeaderId = teamLeaderId;
    }

    public String getTeamLeaderName() {
        return teamLeaderName;
    }

    public void setTeamLeaderName(String teamLeaderName) {
        this.teamLeaderName = teamLeaderName;
    }

    public String getTeamLeaderEmail() {
        return teamLeaderEmail;
    }

    public void setTeamLeaderEmail(String teamLeaderEmail) {
        this.teamLeaderEmail = teamLeaderEmail;
    }

    public List<String> getMemberIds() {
        if (memberIds == null) memberIds = new ArrayList<>();
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }

    public int getMemberCount() {
        return memberCount > 0 ? memberCount : (members != null ? members.size() : 0);
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public List<TeamMember> getMembers() {
        if (members == null) members = new ArrayList<>();
        return members;
    }

    public void setMembers(List<TeamMember> members) {
        this.members = members;
        if (members != null) {
            this.memberCount = members.size();
            List<String> ids = new ArrayList<>();
            for (TeamMember m : members) {
                if (m.getId() != null) ids.add(m.getId());
                else if (m.getEmail() != null) ids.add(m.getEmail());
            }
            this.memberIds = ids;
        }
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRegistrationSource() {
        return registrationSource != null ? registrationSource : SOURCE_TEAM_LEADER;
    }

    public void setRegistrationSource(String registrationSource) {
        this.registrationSource = registrationSource;
    }

    public String getCreatedById() {
        return createdById;
    }

    public void setCreatedById(String createdById) {
        this.createdById = createdById;
    }

    public String getCreatorUid() {
        return createdById;
    }

    public void setCreatorUid(String creatorUid) {
        this.createdById = creatorUid;
    }

    public String getStatus() {
        return status != null ? status : STATUS_CONFIRMED;
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

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return STATUS_PENDING.equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return STATUS_REJECTED.equalsIgnoreCase(status);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equalsIgnoreCase(status);
    }

    public boolean isWaitlisted() {
        return STATUS_WAITLISTED.equalsIgnoreCase(status);
    }

    public boolean canBeCancelled() {
        return isConfirmed() || isPending() || isWaitlisted();
    }

    public void setStatusReason(String statusReason) {
        this.remarks = statusReason;
    }

    public String getStatusReason() {
        return remarks;
    }

    public void setRegisteredMemberCount(int count) {
        this.memberCount = count;
    }

    public int getRegisteredMemberCount() {
        return getMemberCount();
    }

    public void setTeamSnapshot(Team team) {
        if (team != null) {
            this.teamId = team.getId();
            this.teamName = team.getTeamName();
            this.teamNumber = team.getTeamNumber();
            this.projectTitle = team.getProjectTitle();
            this.category = team.getCategory();
            this.organizationId = team.getOrganizationId();
            if (team.getLeaderName() != null && this.teamLeaderName == null) {
                this.teamLeaderName = team.getLeaderName();
            }
            if (team.getLeaderEmail() != null && this.teamLeaderEmail == null) {
                this.teamLeaderEmail = team.getLeaderEmail();
            }
            if (team.getLeaderUid() != null && this.teamLeaderId == null) {
                this.teamLeaderId = team.getLeaderUid();
            }
            if (team.getMembers() != null && !team.getMembers().isEmpty()) {
                setMembers(new ArrayList<>(team.getMembers()));
            } else {
                this.memberCount = team.getMemberCount();
            }
        }
    }

    public Team getTeamSnapshot() {
        Team snapshot = new Team();
        snapshot.setId(this.teamId);
        snapshot.setTeamName(this.teamName);
        snapshot.setTeamNumber(this.teamNumber);
        snapshot.setEventId(this.eventId);
        snapshot.setProjectTitle(this.projectTitle);
        snapshot.setCategory(this.category);
        snapshot.setLeaderName(this.teamLeaderName);
        snapshot.setLeaderEmail(this.teamLeaderEmail);
        snapshot.setLeaderUid(this.teamLeaderId);
        snapshot.setOrganizationId(this.organizationId);
        snapshot.setMemberCount(getMemberCount());
        if (this.members != null) {
            snapshot.setMembers(new ArrayList<>(this.members));
        }
        return snapshot;
    }
}
