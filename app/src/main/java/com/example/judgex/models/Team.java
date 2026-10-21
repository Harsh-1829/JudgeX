package com.example.judgex.models;

import java.io.Serializable;

public class Team implements Serializable {
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
    private long createdAt;

    public Team() {
        // Required for Firestore
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
}
