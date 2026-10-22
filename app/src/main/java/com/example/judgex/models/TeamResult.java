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
        calculateScores(totalAssignedJudges, Event.SCORING_WEIGHTED, null);
    }

    public void calculateScores(int totalAssignedJudges, String scoringType, List<Criterion> criteriaList) {
        this.totalAssignedJudges = totalAssignedJudges;
        
        // Count only VALID submitted evaluations (Drafts must never count toward rankings)
        List<Evaluation> validSubmitted = new ArrayList<>();
        if (evaluations != null) {
            for (Evaluation eval : evaluations) {
                if (eval != null && eval.isSubmitted()) {
                    validSubmitted.add(eval);
                }
            }
        }

        this.evaluationsCount = validSubmitted.size();
        this.isProvisional = totalAssignedJudges > 0 && evaluationsCount < totalAssignedJudges;
        
        if (evaluationsCount > 0) {
            double sum = 0.0;
            double maxScore = 100.0;
            boolean isRaw = Event.SCORING_RAW.equalsIgnoreCase(scoringType);

            for (Evaluation eval : validSubmitted) {
                double evalScore = 0.0;
                double evalMax = isRaw ? 0.0 : 100.0;

                if (criteriaList != null && !criteriaList.isEmpty() && eval.getScores() != null && !eval.getScores().isEmpty()) {
                    if (isRaw) {
                        for (Criterion c : criteriaList) {
                            Double mark = eval.getScores().get(c.getId());
                            if (mark != null) {
                                evalScore += mark;
                            }
                            evalMax += c.getMaxMarks();
                        }
                        if (evalMax <= 0.0) {
                            evalMax = eval.getMaxPossibleScore() > 0 ? eval.getMaxPossibleScore() : 100.0;
                        }
                    } else {
                        // Weighted aggregation
                        double totalWeight = 0.0;
                        for (Criterion c : criteriaList) {
                            totalWeight += c.getWeight();
                        }
                        if (totalWeight <= 0.0) totalWeight = 100.0;

                        for (Criterion c : criteriaList) {
                            Double mark = eval.getScores().get(c.getId());
                            double weight = c.getWeight();
                            if (mark != null && c.getMaxMarks() > 0) {
                                evalScore += (mark / c.getMaxMarks()) * weight;
                            }
                        }
                        evalMax = 100.0;
                    }
                } else {
                    // Direct score from evaluation record
                    evalScore = eval.getTotalScore();
                    evalMax = eval.getMaxPossibleScore() > 0 ? eval.getMaxPossibleScore() : 100.0;
                }

                sum += evalScore;
                maxScore = evalMax;
            }

            this.totalScoreSum = sum;
            // Task requirement: Aggregate multiple judges' evaluations according to scoring policy.
            // Do not treat missing evaluations as zero. Divisor is valid submitted count only.
            this.averageScore = sum / evaluationsCount;
            this.maxPossibleScore = maxScore > 0 ? maxScore : 100.0;
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
        if (other == null) return -1;
        // Primary sort: descending by average score
        if (Math.abs(other.averageScore - this.averageScore) > 0.0001) {
            return Double.compare(other.averageScore, this.averageScore);
        }
        // Consistent tie-breaking by team number, then team name
        if (this.team != null && other.team != null) {
            String num1 = this.team.getTeamNumber() != null ? this.team.getTeamNumber() : "";
            String num2 = other.team.getTeamNumber() != null ? other.team.getTeamNumber() : "";
            int numCmp = num1.compareToIgnoreCase(num2);
            if (numCmp != 0) return numCmp;

            String name1 = this.team.getTeamName() != null ? this.team.getTeamName() : "";
            String name2 = other.team.getTeamName() != null ? other.team.getTeamName() : "";
            return name1.compareToIgnoreCase(name2);
        }
        return 0;
    }
}
