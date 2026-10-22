package com.example.judgex.models;

import java.io.Serializable;

public class Team implements Serializable {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String STATUS_DELETED = "DELETED";

    private String id;
    private String eventId;
    private String panelId;
    private String teamNumber; // e.g. TM-101
    private String teamName;   // e.g. CodeCrafters
    private String projectTitle;
    private String category;   // e.g. Smart Automation, AI/ML
    private String leaderName;
    private String leaderEmail;
    private String leaderUid;
    private int memberCount;
    private String qrCodeData;
    private String organizationId;
    private java.util.List<TeamMember> members;
    private long updatedAt;
    private String status;
    private boolean deleted;
    private long createdAt;

    public Team() {
        this.status = STATUS_ACTIVE;
        this.deleted = false;
        this.members = new java.util.ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Team(String id, String eventId, String panelId, String teamNumber, String teamName, 
                String projectTitle, String category, String leaderName, String leaderEmail, 
                int memberCount, String qrCodeData, long createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.panelId = panelId;
        this.teamNumber = teamNumber;
        this.teamName = teamName;
        this.projectTitle = projectTitle;
        this.category = category;
        this.leaderName = leaderName;
        this.leaderEmail = leaderEmail;
        this.leaderUid = null;
        this.memberCount = memberCount;
        this.qrCodeData = qrCodeData;
        this.createdAt = createdAt;
    }

    public Team(String id, String teamName, String teamNumber, String eventId, String panelId) {
        this.id = id;
        this.teamName = teamName;
        this.teamNumber = teamNumber;
        this.eventId = eventId;
        this.panelId = panelId;
        this.createdAt = System.currentTimeMillis();
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

    public String getPanelId() {
        return panelId;
    }

    public void setPanelId(String panelId) {
        this.panelId = panelId;
    }

    public String getTeamNumber() {
        return teamNumber;
    }

    public void setTeamNumber(String teamNumber) {
        this.teamNumber = teamNumber;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
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

    public String getLeaderName() {
        return leaderName;
    }

    public void setLeaderName(String leaderName) {
        this.leaderName = leaderName;
    }

    public String getLeaderEmail() {
        return leaderEmail;
    }

    public void setLeaderEmail(String leaderEmail) {
        this.leaderEmail = leaderEmail;
    }

    public String getLeaderUid() {
        return leaderUid;
    }

    public void setLeaderUid(String leaderUid) {
        this.leaderUid = leaderUid;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public String getQrCodeData() {
        return qrCodeData;
    }

    public void setQrCodeData(String qrCodeData) {
        this.qrCodeData = qrCodeData;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status != null ? status : STATUS_ACTIVE;
    }

    public void setStatus(String status) {
        this.status = status;
        if (STATUS_DELETED.equalsIgnoreCase(status)) {
            this.deleted = true;
        }
    }

    public boolean isDeleted() {
        return deleted || STATUS_DELETED.equalsIgnoreCase(status);
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        if (deleted) {
            this.status = STATUS_DELETED;
        }
    }

    public boolean isUnregistered() {
        return STATUS_REMOVED.equalsIgnoreCase(status);
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public java.util.List<TeamMember> getMembers() {
        if (members == null) members = new java.util.ArrayList<>();
        return members;
    }

    public void setMembers(java.util.List<TeamMember> members) {
        this.members = members;
        if (members != null && !members.isEmpty()) {
            this.memberCount = members.size();
        }
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void addMember(TeamMember member) {
        if (member == null) return;
        if (members == null) members = new java.util.ArrayList<>();
        
        // Remove existing if matching id/email/rollNumber
        for (int i = 0; i < members.size(); i++) {
            TeamMember existing = members.get(i);
            if (member.equals(existing)) {
                members.set(i, member);
                this.memberCount = members.size();
                this.updatedAt = System.currentTimeMillis();
                return;
            }
        }
        members.add(member);
        this.memberCount = members.size();
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean removeMember(String memberId) {
        if (members == null || memberId == null) return false;
        for (int i = 0; i < members.size(); i++) {
            TeamMember m = members.get(i);
            if (memberId.equals(m.getId()) || memberId.equalsIgnoreCase(m.getEmail()) || memberId.equalsIgnoreCase(m.getRollNumber())) {
                members.remove(i);
                this.memberCount = members.size();
                this.updatedAt = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    public TeamMember getLeaderMember() {
        if (members == null) return null;
        for (TeamMember m : members) {
            if (m.isLeader()) return m;
        }
        return !members.isEmpty() ? members.get(0) : null;
    }

    public boolean hasMemberWithEmail(String email) {
        if (members == null || email == null) return false;
        String clean = email.trim().toLowerCase(java.util.Locale.US);
        for (TeamMember m : members) {
            if (clean.equalsIgnoreCase(m.getEmail())) return true;
        }
        return false;
    }

    public boolean hasMemberWithRollNumber(String rollNumber) {
        if (members == null || rollNumber == null) return false;
        String clean = rollNumber.trim().toUpperCase(java.util.Locale.US);
        for (TeamMember m : members) {
            if (clean.equalsIgnoreCase(m.getRollNumber())) return true;
        }
        return false;
    }
}
