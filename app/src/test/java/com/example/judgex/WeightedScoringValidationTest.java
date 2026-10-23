package com.example.judgex;

import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamResult;
import com.example.judgex.utils.ScoringValidationUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class WeightedScoringValidationTest {

    @Test
    public void testWeightBelow100PercentIsInvalid() {
        // Test single criterion with 25% weight (as reported in problem statement)
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(new Criterion("c1", "event1", "Innovation", "Idea", 10.0, 25.0, 0));

        double total = ScoringValidationUtils.calculateTotalWeight(criteria);
        assertEquals(25.0, total, 0.001);
        assertFalse("25% total weight must be rejected", ScoringValidationUtils.isTotalWeightValid(criteria));
        assertFalse("25% raw weight must be rejected", ScoringValidationUtils.isWeightValid(total));

        // Test multiple criteria summing to 80%
        criteria.add(new Criterion("c2", "event1", "Feasibility", "Feasibility", 10.0, 30.0, 1));
        criteria.add(new Criterion("c3", "event1", "Presentation", "Pitch", 10.0, 25.0, 2));

        total = ScoringValidationUtils.calculateTotalWeight(criteria);
        assertEquals(80.0, total, 0.001);
        assertFalse("80% total weight must be rejected", ScoringValidationUtils.isTotalWeightValid(criteria));

        // Test 99% (below 100%)
        criteria.clear();
        criteria.add(new Criterion("c1", "event1", "A", "", 10.0, 50.0, 0));
        criteria.add(new Criterion("c2", "event1", "B", "", 10.0, 49.0, 1));
        assertFalse("99% total weight must be rejected", ScoringValidationUtils.isTotalWeightValid(criteria));

        // Test 0% / empty list
        assertFalse("Empty criteria list must be invalid", ScoringValidationUtils.isTotalWeightValid(new ArrayList<>()));
        assertFalse("Null criteria list must be invalid", ScoringValidationUtils.isTotalWeightValid(null));
    }

    @Test
    public void testWeightAbove100PercentIsInvalid() {
        // Test criteria summing to 105%
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(new Criterion("c1", "event1", "Innovation", "Idea", 10.0, 50.0, 0));
        criteria.add(new Criterion("c2", "event1", "Execution", "Code", 10.0, 55.0, 1));

        double total = ScoringValidationUtils.calculateTotalWeight(criteria);
        assertEquals(105.0, total, 0.001);
        assertFalse("105% total weight must be rejected", ScoringValidationUtils.isTotalWeightValid(criteria));
        assertFalse("105% raw weight must be rejected", ScoringValidationUtils.isWeightValid(total));

        // Test criteria summing to 150%
        criteria.add(new Criterion("c3", "event1", "Design", "UI", 10.0, 45.0, 2));
        assertFalse("150% total weight must be rejected", ScoringValidationUtils.isTotalWeightValid(criteria));

        // Test 101%
        criteria.clear();
        criteria.add(new Criterion("c1", "event1", "A", "", 10.0, 50.0, 0));
        criteria.add(new Criterion("c2", "event1", "B", "", 10.0, 51.0, 1));
        assertFalse("101% total weight must be rejected", ScoringValidationUtils.isTotalWeightValid(criteria));
    }

    @Test
    public void testExact100PercentIsValid() {
        // 4 criteria of 25% each
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(new Criterion("c1", "event1", "Innovation", "Idea", 10.0, 25.0, 0));
        criteria.add(new Criterion("c2", "event1", "Feasibility", "Feasibility", 10.0, 25.0, 1));
        criteria.add(new Criterion("c3", "event1", "Design", "UI", 10.0, 25.0, 2));
        criteria.add(new Criterion("c4", "event1", "Presentation", "Pitch", 10.0, 25.0, 3));

        double total = ScoringValidationUtils.calculateTotalWeight(criteria);
        assertEquals(100.0, total, 0.001);
        assertTrue("Exact 100% total weight must be accepted", ScoringValidationUtils.isTotalWeightValid(criteria));
        assertTrue("Exact 100% raw weight must be accepted", ScoringValidationUtils.isWeightValid(total));

        // 2 criteria: 60% and 40%
        criteria.clear();
        criteria.add(new Criterion("c1", "event1", "Tech", "", 50.0, 60.0, 0));
        criteria.add(new Criterion("c2", "event1", "Business", "", 50.0, 40.0, 1));
        assertTrue("60% + 40% = 100% must be accepted", ScoringValidationUtils.isTotalWeightValid(criteria));
    }

    @Test
    public void testDecimalWeightsPrecisionHandling() {
        // Requirement 7: 33.33% + 33.33% + 33.34% = 100.0%
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(new Criterion("c1", "event1", "Criterion 1", "", 10.0, 33.33, 0));
        criteria.add(new Criterion("c2", "event1", "Criterion 2", "", 10.0, 33.33, 1));
        criteria.add(new Criterion("c3", "event1", "Criterion 3", "", 10.0, 33.34, 2));

        double total = ScoringValidationUtils.calculateTotalWeight(criteria);
        assertEquals(100.0, total, 0.0001);
        assertTrue("33.33% + 33.33% + 33.34% must be accepted as 100%", ScoringValidationUtils.isTotalWeightValid(criteria));
        assertTrue(ScoringValidationUtils.isWeightValid(total));

        // Decimal weights: 12.5% * 8 = 100.0%
        criteria.clear();
        for (int i = 0; i < 8; i++) {
            criteria.add(new Criterion("c" + i, "event1", "C" + i, "", 10.0, 12.5, i));
        }
        assertEquals(100.0, ScoringValidationUtils.calculateTotalWeight(criteria), 0.001);
        assertTrue("12.5% * 8 must be accepted as 100%", ScoringValidationUtils.isTotalWeightValid(criteria));

        // Decimal weights: 49.95% + 50.05% = 100.0%
        criteria.clear();
        criteria.add(new Criterion("c1", "event1", "A", "", 10.0, 49.95, 0));
        criteria.add(new Criterion("c2", "event1", "B", "", 10.0, 50.05, 1));
        assertTrue("49.95% + 50.05% must be accepted as 100%", ScoringValidationUtils.isTotalWeightValid(criteria));
    }

    @Test
    public void testWeightFormatting() {
        assertEquals("100%", ScoringValidationUtils.formatWeight(100.0));
        assertEquals("25%", ScoringValidationUtils.formatWeight(25.0));
        assertEquals("33.33%", ScoringValidationUtils.formatWeight(33.33));
        assertEquals("33.34%", ScoringValidationUtils.formatWeight(33.34));
        assertEquals("12.50%", ScoringValidationUtils.formatWeight(12.50));
    }

    @Test
    public void testEventScopingIsolation() {
        List<Criterion> allCriteria = new ArrayList<>();
        // Event A criteria (totals 100%)
        allCriteria.add(new Criterion("c1", "eventA", "Tech", "", 10.0, 50.0, 0));
        allCriteria.add(new Criterion("c2", "eventA", "Design", "", 10.0, 50.0, 1));
        // Event B criteria (totals 25% - incomplete)
        allCriteria.add(new Criterion("c3", "eventB", "OnlyOne", "", 10.0, 25.0, 0));

        // Filter for eventA only
        List<Criterion> eventACriteria = new ArrayList<>();
        for (Criterion c : allCriteria) {
            if ("eventA".equals(c.getEventId())) {
                eventACriteria.add(c);
            }
        }
        assertTrue("Event A criteria must be valid (100%)", ScoringValidationUtils.isTotalWeightValid(eventACriteria));

        // Filter for eventB only
        List<Criterion> eventBCriteria = new ArrayList<>();
        for (Criterion c : allCriteria) {
            if ("eventB".equals(c.getEventId())) {
                eventBCriteria.add(c);
            }
        }
        assertFalse("Event B criteria must be invalid (25%)", ScoringValidationUtils.isTotalWeightValid(eventBCriteria));
    }

    @Test
    public void testWeightedScoringFormulaPreservedWhen100Percent() {
        // Requirement 6: When weights total exactly 100%, preserve existing weighted scoring formula
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(new Criterion("c1", "e1", "Criterion 1", "", 10.0, 40.0, 0)); // Max 10, weight 40%
        criteria.add(new Criterion("c2", "e1", "Criterion 2", "", 20.0, 60.0, 1)); // Max 20, weight 60%

        assertTrue("Criteria weights must total 100%", ScoringValidationUtils.isTotalWeightValid(criteria));

        // Judge scores: 8/10 on c1 (80% of 40 = 32), 15/20 on c2 (75% of 60 = 45)
        Map<String, Double> scores = new HashMap<>();
        scores.put("c1", 8.0);
        scores.put("c2", 15.0);

        Evaluation eval = new Evaluation();
        eval.setId("eval1");
        eval.setEventId("e1");
        eval.setTeamId("t1");
        eval.setScores(scores);
        eval.setStatus(Evaluation.STATUS_SUBMITTED);

        Team team = new Team("t1", "Test Team", "01", "e1", "p1");
        TeamResult result = new TeamResult(team);
        result.setEvaluations(Collections.singletonList(eval));
        result.calculateScores(1, Event.SCORING_WEIGHTED, criteria);

        // Expected score: 32 + 45 = 77.0
        assertEquals(77.0, result.getAverageScore(), 0.001);
        assertEquals(100.0, result.getMaxPossibleScore(), 0.001);
        assertEquals(77.0, result.getNormalizedScore(), 0.001);
    }

    @Test
    public void testRawScoringEventBypassesWeightCheck() {
        Event rawEvent = new Event();
        rawEvent.setScoringType(Event.SCORING_RAW);

        assertFalse("Raw scoring event should not be treated as weighted",
                ScoringValidationUtils.isWeightedScoring(rawEvent));

        Event weightedEvent = new Event();
        weightedEvent.setScoringType(Event.SCORING_WEIGHTED);
        assertTrue("Weighted event must be identified as weighted",
                ScoringValidationUtils.isWeightedScoring(weightedEvent));

        Event nullTypeEvent = new Event();
        nullTypeEvent.setScoringType(null);
        assertTrue("Default/null scoring type must default to weighted",
                ScoringValidationUtils.isWeightedScoring(nullTypeEvent));
    }
}
