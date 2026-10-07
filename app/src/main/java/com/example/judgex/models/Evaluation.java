package com.example.judgex.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Evaluation implements Serializable {
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_DRAFT = "DRAFT";

    private String id; // format: eventId_teamId_judgeId
    private String eventId;
    private String teamId;
    private String panelId;
    private String judgeId;
    private String judgeName;
    private Map<String, Double> scores; // criterionId -> mark awarded
    private Map<String, String> criterionComments; // criterionId -> comment
    private double totalScore; // Weighted final score out of 100
    private double maxPossibleScore;
    private String remarks;
    private long submittedAt;
    private String status; // SUBMITTED or DRAFT

    public Evaluation() {
        this.scores = new HashMap<>();
        this.criterionComments = new HashMap<>();
        this.status = STATUS_SUBMITTED;
    }

    public Evaluation(String id, String eventId, String teamId, String panelId, String judgeId, 
                      String judgeName, Map<String, Double> scores, Map<String, String> criterionComments,
                      double totalScore, double maxPossibleScore, String remarks, long submittedAt, String status) {
        this.id = id;
        this.eventId = eventId;
        this.teamId = teamId;
        this.panelId = panelId;
        this.judgeId = judgeId;
        this.judgeName = judgeName;
        this.scores = scores != null ? scores : new HashMap<>();
        this.criterionComments = criterionComments != null ? criterionComments : new HashMap<>();
        this.totalScore = totalScore;
        this.maxPossibleScore = maxPossibleScore;
        this.remarks = remarks;
        this.submittedAt = submittedAt;
        this.status = status != null ? status : STATUS_SUBMITTED;
    }

    public Evaluation(String id, String eventId, String teamId, String panelId, String judgeId, 
                      String judgeName, Map<String, Double> scores, double totalScore, 
                      double maxPossibleScore, String remarks, long submittedAt, String status) {
        this(id, eventId, teamId, panelId, judgeId, judgeName, scores, new HashMap<>(), totalScore, maxPossibleScore, remarks, submittedAt, status);
    }

    public static String generateEvaluationId(String eventId, String teamId, String judgeId) {
        return eventId + "_" + teamId + "_" + judgeId;
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

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getPanelId() {
        return panelId;
    }

    public void setPanelId(String panelId) {
        this.panelId = panelId;
    }

    public String getJudgeId() {
        return judgeId;
    }

    public void setJudgeId(String judgeId) {
        this.judgeId = judgeId;
    }

    public String getJudgeName() {
        return judgeName;
    }

    public void setJudgeName(String judgeName) {
        this.judgeName = judgeName;
    }

    public Map<String, Double> getScores() {
        if (scores == null) scores = new HashMap<>();
        return scores;
    }

    public void setScores(Map<String, Double> scores) {
        this.scores = scores;
    }

    public Map<String, String> getCriterionComments() {
        if (criterionComments == null) criterionComments = new HashMap<>();
        return criterionComments;
    }

    public void setCriterionComments(Map<String, String> criterionComments) {
        this.criterionComments = criterionComments;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public double getMaxPossibleScore() {
        return maxPossibleScore;
    }

    public void setMaxPossibleScore(double maxPossibleScore) {
        this.maxPossibleScore = maxPossibleScore;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(long submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getStatus() {
        return status != null ? status : STATUS_SUBMITTED;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isDraft() {
        return STATUS_DRAFT.equalsIgnoreCase(this.status);
    }

    public boolean isSubmitted() {
        return STATUS_SUBMITTED.equalsIgnoreCase(this.status);
    }
}
