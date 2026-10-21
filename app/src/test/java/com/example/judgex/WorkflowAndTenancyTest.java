package com.example.judgex;

import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Organization;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamResult;
import com.example.judgex.models.User;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class WorkflowAndTenancyTest {

    @Test
    public void testDraftEvaluationDoesNotAffectCompletedCountOrScore() {
        Team team = new Team("t101", "CodeCraft", "TM-01", "event_sih", "panel_1");
        TeamResult teamResult = new TeamResult(team);

        // Judge 1 submits final evaluation with score 88.0
        Evaluation eval1 = new Evaluation();
        eval1.setId("eval_1");
        eval1.setTeamId("t101");
        eval1.setJudgeId("judge_1");
        eval1.setTotalScore(88.0);
        eval1.setStatus(Evaluation.STATUS_SUBMITTED);

        // Judge 2 has an in-progress draft with score 70.0 (incomplete)
        Evaluation eval2Draft = new Evaluation();
        eval2Draft.setId("eval_2");
        eval2Draft.setTeamId("t101");
        eval2Draft.setJudgeId("judge_2");
        eval2Draft.setTotalScore(70.0);
        eval2Draft.setStatus(Evaluation.STATUS_DRAFT);

        teamResult.addEvaluation(eval1);
        teamResult.addEvaluation(eval2Draft);

        // Calculate scores with expected 2 judges in panel
        teamResult.calculateScores(2);

        // Valid evaluations count must be ONLY 1 (the submitted one)
        assertEquals(1, teamResult.getEvaluationsCount());

        // Average score must be 88.0 (the draft score 70.0 must NOT be included or averaged)
        assertEquals(88.0, teamResult.getAverageScore(), 0.001);

        // Result must be provisional because judge 2 has not submitted yet
        assertTrue(teamResult.isProvisional());
        assertEquals(1, teamResult.getPendingEvaluationsCount());
        assertEquals(50.0, teamResult.getCompletionPercentage(), 0.01);
    }

    @Test
    public void testMultiTenantOrganizationIsolation() {
        // College A: MIT Pune
        Organization orgA = new Organization("org_mit", "MIT College of Engineering", "admin_mit", "admin@mit.edu", "9876543210");
        User adminA = new User("admin_mit", "Dr. Sharma", "admin@mit.edu", User.ROLE_ADMIN);
        adminA.setOrganizationId(orgA.getId());
        adminA.setOrganizationName(orgA.getName());

        Event eventA = new Event("ev_hack_mit", "MIT Hackathon 2026", "Campus hackathon", Event.STATUS_ACTIVE, false, System.currentTimeMillis());
        eventA.setOrganizationId(orgA.getId());

        // College B: PICT Pune
        Organization orgB = new Organization("org_pict", "Pune Institute of Computer Tech", "admin_pict", "admin@pict.edu", "9123456780");
        User adminB = new User("admin_pict", "Dr. Patil", "admin@pict.edu", User.ROLE_ADMIN);
        adminB.setOrganizationId(orgB.getId());
        adminB.setOrganizationName(orgB.getName());

        Event eventB = new Event("ev_sih_pict", "PICT SIH Screening", "Internal SIH screening", Event.STATUS_ACTIVE, false, System.currentTimeMillis());
        eventB.setOrganizationId(orgB.getId());

        // Verify organizations are distinct
        assertNotEquals(adminA.getOrganizationId(), adminB.getOrganizationId());
        assertEquals("org_mit", eventA.getOrganizationId());
        assertEquals("org_pict", eventB.getOrganizationId());

        // Tenant filtering test
        List<Event> allEvents = new ArrayList<>();
        allEvents.add(eventA);
        allEvents.add(eventB);

        List<Event> mitEvents = new ArrayList<>();
        for (Event e : allEvents) {
            if (orgA.getId().equals(e.getOrganizationId())) {
                mitEvents.add(e);
            }
        }

        assertEquals(1, mitEvents.size());
        assertEquals("MIT Hackathon 2026", mitEvents.get(0).getName());
    }

    @Test
    public void testEventLifecycleEvaluationGuards() {
        Event upcomingEvent = new Event("e1", "Hackathon", "Desc", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        assertFalse(upcomingEvent.isAcceptingEvaluations());
        assertTrue(upcomingEvent.isEditable());

        Event activeEvent = new Event("e2", "Hackathon", "Desc", Event.STATUS_ACTIVE, false, System.currentTimeMillis());
        assertTrue(activeEvent.isAcceptingEvaluations());

        Event lockedEvent = new Event("e3", "Hackathon", "Desc", Event.STATUS_LOCKED, true, System.currentTimeMillis());
        assertFalse(lockedEvent.isAcceptingEvaluations());
        assertFalse(lockedEvent.isEditable());

        Event completedEvent = new Event("e4", "Hackathon", "Desc", Event.STATUS_COMPLETED, false, System.currentTimeMillis());
        assertFalse(completedEvent.isAcceptingEvaluations());
        assertFalse(completedEvent.isEditable());
    }

    @Test
    public void testAnonymousJudgingDisplayMask() {
        Team team = new Team("t1", "Neural Ninjas", "TM-42", "event_1", "panel_1");
        Event blindEvent = new Event("e1", "Blind Evaluation Cup", "Desc", Event.STATUS_ACTIVE, false, System.currentTimeMillis());
        blindEvent.setAnonymousJudging(true);

        String judgeViewName = blindEvent.isAnonymousJudging()
                ? "Team #" + team.getTeamNumber()
                : team.getTeamName();

        assertEquals("Team #TM-42", judgeViewName);
        assertFalse(judgeViewName.contains("Neural Ninjas"));

        Event openEvent = new Event("e2", "Open Presentation Cup", "Desc", Event.STATUS_ACTIVE, false, System.currentTimeMillis());
        openEvent.setAnonymousJudging(false);

        String openViewName = openEvent.isAnonymousJudging()
                ? "Team #" + team.getTeamNumber()
                : team.getTeamName();

        assertEquals("Neural Ninjas", openViewName);
    }
}
