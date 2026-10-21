package com.example.judgex.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TeamResult implements Serializable, Comparable<TeamResult> {
    private Team team;
    private List<Evaluation> evaluations;
    private double averageScore;
    private double totalScoreSum;
    private double maxPossibleScore;
    private int evaluationsCount;
    private int totalAssignedJudges;
    private int rank;
    private boolean isProvisional; // True if pending evaluations remain, False if all completed or event locked/finalized

    public TeamResult(Team team) {
        this.team = team;
        this.evaluations = new ArrayList<>();
        this.averageScore = 0.0;
        this.totalScoreSum = 0.0;
        this.maxPossibleScore = 100.0;
        this.evaluationsCount = 0;
        this.totalAssignedJudges = 0;
        this.rank = 0;
        this.isProvisional = true;
    }

    public void calculateScores(int totalAssignedJudges) {
        this.totalAssignedJudges = totalAssignedJudges;
        
        // Count only VALID submitted evaluations
        List<Evaluation> validSubmitted = new ArrayList<>();
        if (evaluations != null) {
            for (Evaluation eval : evaluations) {
                if (eval.isSubmitted()) {
                    validSubmitted.add(eval);
                }
            }
        }

        this.evaluationsCount = validSubmitted.size();
        this.isProvisional = totalAssignedJudges > 0 && evaluationsCount < totalAssignedJudges;
        
        if (evaluationsCount > 0) {
            double sum = 0.0;
            double maxScore = 100.0;
            for (Evaluation eval : validSubmitted) {
                sum += eval.getTotalScore();
                if (eval.getMaxPossibleScore() > 0) {
                    maxScore = eval.getMaxPossibleScore();
                }
            }
            this.totalScoreSum = sum;
            // Task requirement: "When multiple judges evaluate a team, calculate the average using valid submitted evaluations only. Pending evaluations must not count as zero."
            this.averageScore = sum / evaluationsCount;
            this.maxPossibleScore = maxScore;
        } else {
            this.totalScoreSum = 0.0;
            this.averageScore = 0.0;
            this.maxPossibleScore = 100.0;
        }
    }

    public double getNormalizedScore() {
        if (maxPossibleScore > 0) {
            return (averageScore / maxPossibleScore) * 100.0;
        }
        return averageScore;
    }

    public int getPendingEvaluationsCount() {
        int pending = totalAssignedJudges - evaluationsCount;
        return Math.max(0, pending);
    }

    public double getCompletionPercentage() {
        if (totalAssignedJudges <= 0) return 0.0;
        double pct = ((double) evaluationsCount / totalAssignedJudges) * 100.0;
        return Math.min(100.0, pct);
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public List<Evaluation> getEvaluations() {
        if (evaluations == null) evaluations = new ArrayList<>();
        return evaluations;
    }

    public void setEvaluations(List<Evaluation> evaluations) {
        this.evaluations = evaluations;
    }

    public void addEvaluation(Evaluation evaluation) {
        if (this.evaluations == null) this.evaluations = new ArrayList<>();
        this.evaluations.add(evaluation);
    }

    public double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public double getTotalScoreSum() {
        return totalScoreSum;
    }

    public void setTotalScoreSum(double totalScoreSum) {
        this.totalScoreSum = totalScoreSum;
    }

    public double getMaxPossibleScore() {
        return maxPossibleScore;
    }

    public void setMaxPossibleScore(double maxPossibleScore) {
        this.maxPossibleScore = maxPossibleScore;
    }

    public int getEvaluationsCount() {
        return evaluationsCount;
    }

    public void setEvaluationsCount(int evaluationsCount) {
        this.evaluationsCount = evaluationsCount;
    }

    public int getTotalAssignedJudges() {
        return totalAssignedJudges;
    }

    public void setTotalAssignedJudges(int totalAssignedJudges) {
        this.totalAssignedJudges = totalAssignedJudges;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public boolean isProvisional() {
        return isProvisional;
    }

    public void setProvisional(boolean provisional) {
        isProvisional = provisional;
    }

    @Override
    public int compareTo(TeamResult other) {
        // Sort descending by average score
        return Double.compare(other.averageScore, this.averageScore);
    }
}
