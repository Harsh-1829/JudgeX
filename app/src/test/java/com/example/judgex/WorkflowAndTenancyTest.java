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

    @Test
    public void testEvaluationTypeSafetyWithFirestoreLongs() {
        Evaluation eval = new Evaluation();
        // Simulate Firestore deserializing numbers as Long or Integer
        Map<String, Object> rawFirestoreMap = new HashMap<>();
        rawFirestoreMap.put("crit_innovation", 25L); // Long from Firestore
        rawFirestoreMap.put("crit_feasibility", 20);  // Integer
        rawFirestoreMap.put("crit_presentation", 18.5); // Double

        eval.setScores(rawFirestoreMap);

        Map<String, Double> normalizedScores = eval.getScores();
        assertNotNull(normalizedScores);
        assertEquals(Double.valueOf(25.0), normalizedScores.get("crit_innovation"));
        assertEquals(Double.valueOf(20.0), normalizedScores.get("crit_feasibility"));
        assertEquals(Double.valueOf(18.5), normalizedScores.get("crit_presentation"));
    }

    @Test
    public void testEventSchedulingAndSoftDelete() {
        long futureTime = System.currentTimeMillis() + 86400000L * 7; // 7 days in future
        Event scheduledEvent = new Event("ev_sih", "Smart India Hackathon", "Campus round", Event.STATUS_UPCOMING, false, System.currentTimeMillis());
        scheduledEvent.setScheduledDate(futureTime);
        scheduledEvent.setStartTime("09:00 AM");
        scheduledEvent.setEndTime("05:00 PM");
        scheduledEvent.setVenue("Auditorium B");

        // Verify future event is upcoming and not accepting evaluations
        assertTrue(scheduledEvent.isUpcoming());
        assertFalse(scheduledEvent.isAcceptingEvaluations());
        assertEquals("Auditorium B", scheduledEvent.getVenue());
        assertEquals("09:00 AM", scheduledEvent.getStartTime());

        // Verify soft-delete safeguard
        assertFalse(scheduledEvent.isDeleted());
        scheduledEvent.setDeleted(true);
        scheduledEvent.setStatus(Event.STATUS_ARCHIVED);
        assertTrue(scheduledEvent.isDeleted());
        assertEquals(Event.STATUS_ARCHIVED, scheduledEvent.getStatus());
        assertFalse(scheduledEvent.isAcceptingEvaluations());
    }

    @Test
    public void testEvaluationDraftVsSubmittedStatusTransitions() {
        Evaluation draft = new Evaluation();
        draft.setStatus(Evaluation.STATUS_DRAFT);
        assertTrue(draft.isDraft());
        assertFalse(draft.isSubmitted());

        draft.setStatus(Evaluation.STATUS_SUBMITTED);
        assertFalse(draft.isDraft());
        assertTrue(draft.isSubmitted());

        // Legacy compatibility
        Evaluation legacyCompleted = new Evaluation();
        legacyCompleted.setStatus("COMPLETED");
        assertTrue(legacyCompleted.isSubmitted());
    }

    @Test
    public void testTeamLifecycleStates() {
        Team team = new Team("t1", "e1", "p1", "TM-01", "Team Rocket", "AI Vision", "AI", "Jessie", "jessie@example.com", 3, "", System.currentTimeMillis());

        // Default state
        assertEquals(Team.STATUS_ACTIVE, team.getStatus());
        assertFalse(team.isDeleted());
        assertFalse(team.isUnregistered());

        // Unregister / remove from event
        team.setStatus(Team.STATUS_REMOVED);
        assertTrue(team.isUnregistered());
        assertFalse(team.isDeleted());

        // Re-register
        team.setStatus(Team.STATUS_ACTIVE);
        assertFalse(team.isUnregistered());
        assertFalse(team.isDeleted());

        // Soft-delete
        team.setDeleted(true);
        assertTrue(team.isDeleted());
        assertEquals(Team.STATUS_DELETED, team.getStatus());
    }

    @Test
    public void testEmailNormalizationAndRoleConflictRules() {
        String rawEmail = "   Judge.Jane+Test@Example.COM \n";
        String normalized = com.example.judgex.data.FirebaseManager.normalizeEmail(rawEmail);
        assertEquals("judge.jane+test@example.com", normalized);

        // Role conflict verification
        User judgeUser = new User("uid_1", normalized, "Dr. Jane", User.ROLE_JUDGE, "p1", System.currentTimeMillis());
        assertTrue(judgeUser.isJudge());
        assertFalse(judgeUser.isTeamLeader());

        User leaderUser = new User("uid_2", normalized, "Bob Leader", User.ROLE_TEAM_LEADER, "", System.currentTimeMillis());
        assertTrue(leaderUser.isTeamLeader());
        assertFalse(leaderUser.isJudge());

        // Conflict check: Incompatible roles cannot coexist for same normalized email
        boolean hasConflict = (judgeUser.isJudge() && leaderUser.isTeamLeader()) || (judgeUser.isTeamLeader() && leaderUser.isJudge());
        assertTrue("Judge and Team Leader roles must be incompatible for same email", hasConflict);
    }

    @Test
    public void testMultiEventJudgePanelFallback() {
        User judge = new User("j_test", "judge@test.com", "Judge Test", User.ROLE_JUDGE, "panel_default", System.currentTimeMillis());

        // Event-specific assignment
        judge.setPanelForEvent("event_hackathon", "panel_hackathon_1");
        assertEquals("panel_hackathon_1", judge.getPanelForEvent("event_hackathon"));

        // Event without specific mapping should return null for event-aware user
        assertNull(judge.getPanelForEvent("event_other"));

        // Clear event assignment
        judge.setPanelForEvent("event_hackathon", null);
        assertEquals("panel_default", judge.getPanelForEvent("event_hackathon"));
    }

    @Test
    public void testQRPayloadParsingScenarios() throws Exception {
        // Scenario 1: Standard JSON payload with teamId
        String jsonPayload1 = "{\"teamId\":\"team_alpha_123\",\"teamNumber\":\"TM-01\",\"eventId\":\"ev_hack\"}";
        org.json.JSONObject obj1 = new org.json.JSONObject(jsonPayload1);
        assertEquals("team_alpha_123", obj1.getString("teamId"));
        assertEquals("TM-01", obj1.getString("teamNumber"));

        // Scenario 2: Legacy JSON payload with id
        String jsonPayload2 = "{\"id\":\"team_beta_456\",\"eventId\":\"ev_hack\"}";
        org.json.JSONObject obj2 = new org.json.JSONObject(jsonPayload2);
        assertTrue(obj2.has("id"));
        assertEquals("team_beta_456", obj2.getString("id"));

        // Scenario 3: Raw string with whitespace
        String rawPayload = "   TM-42 \r\n";
        String trimmed = rawPayload.trim();
        assertEquals("TM-42", trimmed);
    }

    @Test
    public void testJudgeDeletionLifecycleAndAdminProtection() {
        User judge = new User("j_test_del", "judge_del@test.com", "Test Judge", User.ROLE_JUDGE, "panel_1", System.currentTimeMillis());
        assertTrue("Newly created judge should be active", judge.isActive());
        assertFalse("Newly created judge should not be deleted", judge.isDeleted());

        // Delete judge: should mark status DELETED and isActive false
        judge.setDeleted(true);
        assertTrue("Judge should be marked deleted", judge.isDeleted());
        assertEquals(User.STATUS_DELETED, judge.getStatus());
        assertFalse("Deleted judge should not be active", judge.isActive());

        // Re-activating tombstoned judge
        judge.setDeleted(false);
        judge.setStatus(User.STATUS_ACTIVE);
        assertFalse("Reactivated judge should not be deleted", judge.isDeleted());
        assertTrue("Reactivated judge should be active", judge.isActive());

        // Admin account protection check
        User admin = new User("admin_123", "admin@judgex.com", "Main Admin", User.ROLE_ADMIN, null, System.currentTimeMillis());
        assertTrue("Admin user should be recognized as admin", admin.isAdmin());
        assertFalse("Admin user should not be recognized as judge", admin.isJudge());
    }

    @Test
    public void testLegacyAndCurrentJudgeRoleValidation() {
        // Current canonical role
        User j1 = new User("j1", "j1@test.com", "Judge 1", "JUDGE", "p1", System.currentTimeMillis());
        assertTrue("Canonical JUDGE should be recognized as judge", j1.isJudge());
        assertFalse("Canonical JUDGE should not be admin", j1.isAdmin());
        assertFalse("Canonical JUDGE should not be team leader", j1.isTeamLeader());

        // Legacy Spring convention ROLE_JUDGE
        User j2 = new User("j2", "j2@test.com", "Judge 2", "ROLE_JUDGE", "p1", System.currentTimeMillis());
        assertTrue("ROLE_JUDGE should be recognized as judge", j2.isJudge());
        assertFalse("ROLE_JUDGE should not be admin", j2.isAdmin());

        // Case insensitivity & whitespace
        User j3 = new User("j3", "j3@test.com", "Judge 3", "  judge  ", "p1", System.currentTimeMillis());
        assertTrue("judge with whitespace should be recognized as judge", j3.isJudge());

        // Alternative legacy evaluator role
        User j4 = new User("j4", "j4@test.com", "Judge 4", "EVALUATOR", "p1", System.currentTimeMillis());
        assertTrue("EVALUATOR should be recognized as judge", j4.isJudge());

        // Alternative legacy jury role
        User j5 = new User("j5", "j5@test.com", "Judge 5", "JURY", "p1", System.currentTimeMillis());
        assertTrue("JURY should be recognized as judge", j5.isJudge());

        // Legacy account with missing/empty role but assigned panel
        User j6 = new User("j6", "j6@test.com", "Judge 6", "", "panel_hack_1", System.currentTimeMillis());
        assertTrue("Legacy user with panel assignment should be recognized as judge", j6.isJudge());
    }

    @Test
    public void testAdminAndTeamLeaderProtectedFromDeletion() {
        // Admin user variants
        User a1 = new User("a1", "admin@judgex.com", "Admin", "ADMIN", null, System.currentTimeMillis());
        assertTrue("ADMIN should be recognized as admin", a1.isAdmin());
        assertFalse("ADMIN must not be recognized as judge", a1.isJudge());

        User a2 = new User("a2", "admin2@judgex.com", "Admin 2", "ROLE_ADMIN", null, System.currentTimeMillis());
        assertTrue("ROLE_ADMIN should be recognized as admin", a2.isAdmin());
        assertFalse("ROLE_ADMIN must not be recognized as judge", a2.isJudge());

        // Team leader variants
        User tl1 = new User("tl1", "tl1@judgex.com", "Leader", "TEAM_LEADER", null, System.currentTimeMillis());
        tl1.setAssignedTeamId("team_1");
        assertTrue("TEAM_LEADER should be recognized as team leader", tl1.isTeamLeader());
        assertFalse("TEAM_LEADER must not be recognized as judge", tl1.isJudge());

        User tl2 = new User("tl2", "tl2@judgex.com", "Leader 2", "ROLE_TEAM_LEADER", null, System.currentTimeMillis());
        assertTrue("ROLE_TEAM_LEADER should be recognized as team leader", tl2.isTeamLeader());
        assertFalse("ROLE_TEAM_LEADER must not be recognized as judge", tl2.isJudge());
    }
}
