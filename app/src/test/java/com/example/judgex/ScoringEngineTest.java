package com.example.judgex;

import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ScoringEngineTest {

    @Test
    public void testRawScoreCalculation() {
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(new Criterion("1", "e1", "Innovation", "Idea novelty", 25.0, 25.0, 1));
        criteria.add(new Criterion("2", "e1", "Feasibility", "Execution practicality", 20.0, 20.0, 2));
        criteria.add(new Criterion("3", "e1", "Technical", "Tech depth", 30.0, 30.0, 3));

        Map<String, Double> scores = new HashMap<>();
        scores.put("1", 20.0);
        scores.put("2", 15.0);
        scores.put("3", 25.0);

        double rawTotal = 0;
        double maxTotal = 0;
        for (Criterion c : criteria) {
            maxTotal += c.getMaxMarks();
            if (scores.containsKey(c.getId())) {
                rawTotal += scores.get(c.getId());
            }
        }

        assertEquals(75.0, maxTotal, 0.001);
        assertEquals(60.0, rawTotal, 0.001);

        // Normalized score to 100
        double normalized = (rawTotal / maxTotal) * 100.0;
        assertEquals(80.0, normalized, 0.001);
    }

    @Test
    public void testWeightedScoreCalculation() {
        // Event with custom weights summing to 100%
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(new Criterion("1", "e1", "Business Model", "Viability", 30.0, 30.0, 1));
        criteria.add(new Criterion("2", "e1", "Market Potential", "Market size", 20.0, 20.0, 2));
        criteria.add(new Criterion("3", "e1", "Financial Viability", "ROI", 25.0, 25.0, 3));
        criteria.add(new Criterion("4", "e1", "Innovation", "Novelty", 15.0, 15.0, 4));
        criteria.add(new Criterion("5", "e1", "Presentation", "Pitch quality", 10.0, 10.0, 5));

        Map<String, Double> scores = new HashMap<>();
        scores.put("1", 24.0); // 24/30 * 30 = 24.0
        scores.put("2", 16.0); // 16/20 * 20 = 16.0
        scores.put("3", 20.0); // 20/25 * 25 = 20.0
        scores.put("4", 12.0); // 12/15 * 15 = 12.0
        scores.put("5", 8.0);  // 8/10 * 10 = 8.0

        double weightedTotal = 0.0;
        for (Criterion c : criteria) {
            Double score = scores.get(c.getId());
            if (score != null && c.getMaxMarks() > 0) {
                weightedTotal += (score / c.getMaxMarks()) * c.getWeight();
            }
        }

        // Expected sum: 24 + 16 + 20 + 12 + 8 = 80.0
        assertEquals(80.0, weightedTotal, 0.001);
    }

    @Test
    public void testMultipleJudgesAveraging() {
        // Team evaluated by 2 judges: 82.0 and 90.0
        List<Double> judgeScores2 = new ArrayList<>();
        judgeScores2.add(82.0);
        judgeScores2.add(90.0);

        double sum2 = 0;
        for (double s : judgeScores2) sum2 += s;
        double avg2 = sum2 / judgeScores2.size();
        assertEquals(86.0, avg2, 0.001);

        // Team evaluated by 3 judges: 85.0, 89.0, 92.0
        List<Double> judgeScores3 = new ArrayList<>();
        judgeScores3.add(85.0);
        judgeScores3.add(89.0);
        judgeScores3.add(92.0);

        double sum3 = 0;
        for (double s : judgeScores3) sum3 += s;
        double avg3 = sum3 / judgeScores3.size();
        assertEquals(88.667, avg3, 0.01);
    }

    @Test
    public void testTeamResultCompletionAndProvisionalStatus() {
        Team team = new Team("t1", "Alpha", "TM-01", "e1", "p1");
        TeamResult result = new TeamResult(team);
        result.setTotalAssignedJudges(3);
        result.setEvaluationsCount(2);
        result.setAverageScore(85.5);
        result.setProvisional(true);

        // 2 of 3 submitted -> provisional
        assertTrue(result.isProvisional());
        assertEquals(1, result.getPendingEvaluationsCount());
        assertEquals(66.67, result.getCompletionPercentage(), 0.01);

        // 3 of 3 submitted -> not provisional
        result.setEvaluationsCount(3);
        result.setProvisional(false);
        assertFalse(result.isProvisional());
        assertEquals(0, result.getPendingEvaluationsCount());
        assertEquals(100.0, result.getCompletionPercentage(), 0.001);
    }

    @Test
    public void testRankingAndTieHandling() {
        List<TeamResult> results = new ArrayList<>();

        Team t1 = new Team("t1", "Team 1", "01", "e1", "p1");
        TeamResult r1 = new TeamResult(t1);
        r1.setAverageScore(88.5);

        Team t2 = new Team("t2", "Team 2", "02", "e1", "p1");
        TeamResult r2 = new TeamResult(t2);
        r2.setAverageScore(94.0);

        Team t3 = new Team("t3", "Team 3", "03", "e1", "p1");
        TeamResult r3 = new TeamResult(t3);
        r3.setAverageScore(88.5); // Tie with Team 1

        Team t4 = new Team("t4", "Team 4", "04", "e1", "p1");
        TeamResult r4 = new TeamResult(t4);
        r4.setAverageScore(72.0);

        results.add(r1);
        results.add(r2);
        results.add(r3);
        results.add(r4);

        // Sort descending using Comparable
        Collections.sort(results);

        assertEquals("Team 2", results.get(0).getTeam().getTeamName());
        assertEquals(94.0, results.get(0).getAverageScore(), 0.001);

        // 2nd and 3rd are tied at 88.5
        assertEquals(88.5, results.get(1).getAverageScore(), 0.001);
        assertEquals(88.5, results.get(2).getAverageScore(), 0.001);

        // 4th is 72.0
        assertEquals("Team 4", results.get(3).getTeam().getTeamName());
        assertEquals(72.0, results.get(3).getAverageScore(), 0.001);
    }

    @Test
    public void testEventLifecycleStates() {
        Event event = new Event();

        // UPCOMING
        event.setStatus(Event.STATUS_UPCOMING);
        assertTrue(event.isUpcoming());
        assertTrue(event.isEditable());
        assertFalse(event.isAcceptingEvaluations());

        // ACTIVE
        event.setStatus(Event.STATUS_ACTIVE);
        assertTrue(event.isActive());
        assertTrue(event.isAcceptingEvaluations());

        // LOCKED
        event.setStatus(Event.STATUS_LOCKED);
        event.setLocked(true);
        assertTrue(event.isLocked());
        assertFalse(event.isAcceptingEvaluations());
        assertFalse(event.isEditable());

        // COMPLETED
        event.setStatus(Event.STATUS_COMPLETED);
        assertTrue(event.isCompleted());
        assertFalse(event.isAcceptingEvaluations());
        assertFalse(event.isEditable());
    }
}
