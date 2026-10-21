package com.example.judgex.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Panel implements Serializable {
    private String id;
    private String eventId;
    private String name;
    private String venue;
    private List<String> judgeIds;
    private List<String> teamIds;
    private long createdAt;

    public Panel() {
        this.judgeIds = new ArrayList<>();
        this.teamIds = new ArrayList<>();
    }

    public Panel(String id, String eventId, String name, String venue, List<String> judgeIds, List<String> teamIds, long createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.venue = venue;
        this.judgeIds = judgeIds != null ? judgeIds : new ArrayList<>();
        this.teamIds = teamIds != null ? teamIds : new ArrayList<>();
        this.createdAt = createdAt;
    }

    public Panel(String id, String name, String eventId) {
        this();
        this.id = id;
        this.name = name;
        this.eventId = eventId;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public List<String> getJudgeIds() {
        if (judgeIds == null) judgeIds = new ArrayList<>();
        return judgeIds;
    }

    public void setJudgeIds(List<String> judgeIds) {
        this.judgeIds = judgeIds;
    }

    public List<String> getTeamIds() {
        if (teamIds == null) teamIds = new ArrayList<>();
        return teamIds;
    }

    public void setTeamIds(List<String> teamIds) {
        this.teamIds = teamIds;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
