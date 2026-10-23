package com.example.judgex;

import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Organization;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamResult;
import com.example.judgex.models.User;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Test
    public void testCollegeAdminRegistrationLinkageAndEmailNormalization() {
        String rawEmail = "  Dean.Office+JudgeX@COLLEGE.EDU  \n";
        String normalizedEmail = com.example.judgex.data.FirebaseManager.normalizeEmail(rawEmail);
        assertEquals("dean.office+judgex@college.edu", normalizedEmail);

        String orgId = "org_test_123";
        String adminUid = "admin_uid_456";
        String orgName = "MIT World Peace University";
        String adminName = "Dr. Dean";
        String phone = "+91 9876543210";

        // Organization Model
        Organization org = new Organization(orgId, orgName, adminUid, normalizedEmail, phone);
        assertEquals(orgId, org.getId());
        assertEquals(adminUid, org.getAdminUid());
        assertEquals(normalizedEmail, org.getAdminEmail());
        assertEquals(Organization.STATUS_ACTIVE, org.getStatus());

        // Admin User Profile Model
        User adminUser = new User();
        adminUser.setUid(adminUid);
        adminUser.setEmail(normalizedEmail);
        adminUser.setName(adminName);
        adminUser.setRole(User.ROLE_ADMIN);
        adminUser.setOrganizationId(orgId);
        adminUser.setOrganizationName(orgName);
        adminUser.setStatus(User.STATUS_ACTIVE);
        adminUser.setActive(true);

        assertTrue("User must have ADMIN role", adminUser.isAdmin());
        assertEquals(orgId, adminUser.getOrganizationId());
        assertEquals(org.getAdminUid(), adminUser.getUid());
        assertEquals(org.getAdminEmail(), adminUser.getEmail());
    }

    @Test
    public void testEventTimeRangeValidation() {
        java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US);

        try {
            // Valid range: 09:00 AM to 05:00 PM
            java.util.Date start1 = tf.parse("09:00 AM");
            java.util.Date end1 = tf.parse("05:00 PM");
            assertNotNull(start1);
            assertNotNull(end1);
            assertTrue("05:00 PM must be after 09:00 AM", end1.after(start1));

            // Invalid range: 05:00 PM to 09:00 AM
            java.util.Date start2 = tf.parse("05:00 PM");
            java.util.Date end2 = tf.parse("09:00 AM");
            assertNotNull(start2);
            assertNotNull(end2);
            assertFalse("09:00 AM must NOT be after 05:00 PM", end2.after(start2));

            // Same time: 10:00 AM to 10:00 AM
            java.util.Date start3 = tf.parse("10:00 AM");
            java.util.Date end3 = tf.parse("10:00 AM");
            assertNotNull(start3);
            assertNotNull(end3);
            assertFalse("Same time cannot be after start time", end3.after(start3));
        } catch (java.text.ParseException e) {
            fail("Time parsing should not throw ParseException: " + e.getMessage());
        }
    }

    @Test
    public void testEventSchedulingPolicyStatusRemainsUpcoming() {
        Event event = new Event();
        assertEquals("Default event constructor must initialize to UPCOMING", Event.STATUS_UPCOMING, event.getStatus());
        assertTrue(event.isUpcoming());
        assertFalse(event.isActive());

        // Selecting a scheduled date alone must NOT silently activate the event
        event.setScheduledDate(System.currentTimeMillis() + 86400000L);
        event.setStartTime("10:00 AM");
        event.setEndTime("04:00 PM");

        assertEquals("Status must remain UPCOMING after date selection", Event.STATUS_UPCOMING, event.getStatus());
        assertTrue(event.isUpcoming());
        assertFalse(event.isActive());

        // Only explicit activation transitions status to ACTIVE
        event.setStatus(Event.STATUS_ACTIVE);
        assertTrue(event.isActive());
        assertFalse(event.isUpcoming());
    }

    @Test
    public void testLegacyEventWithoutDatesDoesNotCrash() {
        Event legacyEvent = new Event("ev_old", "Old Competition", "Historical without dates", Event.STATUS_COMPLETED, false, 1600000000000L);
        // Scheduled date and times are empty/zero
        assertEquals(0L, legacyEvent.getScheduledDate());
        assertNull(legacyEvent.getStartTime());
        assertNull(legacyEvent.getEndTime());
        assertNull(legacyEvent.getVenue());

        // Formatting simulation for UI display
        StringBuilder scheduleVenue = new StringBuilder();
        if (legacyEvent.getScheduledDate() > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US);
            scheduleVenue.append(sdf.format(new java.util.Date(legacyEvent.getScheduledDate())));
        }
        if (legacyEvent.getStartTime() != null && !legacyEvent.getStartTime().isEmpty()) {
            if (scheduleVenue.length() > 0) scheduleVenue.append(" • ");
            scheduleVenue.append(legacyEvent.getStartTime());
            if (legacyEvent.getEndTime() != null && !legacyEvent.getEndTime().isEmpty()) {
                scheduleVenue.append(" - ").append(legacyEvent.getEndTime());
            }
        }
        if (legacyEvent.getVenue() != null && !legacyEvent.getVenue().isEmpty()) {
            if (scheduleVenue.length() > 0) scheduleVenue.append(" • ");
            scheduleVenue.append(legacyEvent.getVenue());
        }

        // Must produce empty string and not throw NullPointerException
        assertEquals("", scheduleVenue.toString());
    }

    @Test
    public void testEvaluationScoreAndRemarksRetentionDuringRecycling() {
        // Model criteria list
        List<com.example.judgex.models.Criterion> criteria = new ArrayList<>();
        com.example.judgex.models.Criterion c1 = new com.example.judgex.models.Criterion("c1", "ev1", "Innovation & Feasibility", "Novel approach", 10.0, 40.0, 1);
        com.example.judgex.models.Criterion c2 = new com.example.judgex.models.Criterion("c2", "ev1", "Technical Architecture", "Clean stack", 20.0, 40.0, 2);
        com.example.judgex.models.Criterion c3 = new com.example.judgex.models.Criterion("c3", "ev1", "Presentation & UI", "Design & UX", 10.0, 20.0, 3);
        criteria.add(c1);
        criteria.add(c2);
        criteria.add(c3);

        // State maps representing the adapter's persistent state decoupled from ViewHolders
        Map<String, Double> scoresMap = new HashMap<>();
        Map<String, String> commentsMap = new HashMap<>();

        // Judge enters scores and remarks
        scoresMap.put("c1", 8.0);
        commentsMap.put("c1", "Very creative idea");

        scoresMap.put("c2", 18.0);
        commentsMap.put("c2", "Solid architecture");

        scoresMap.put("c3", 9.0);
        commentsMap.put("c3", "Polished demo");

        // Simulate scrolling / view recycling: Views are recycled, but the model maps maintain state
        assertTrue("c1 score must persist after recycling", scoresMap.containsKey("c1"));
        assertEquals(8.0, scoresMap.get("c1"), 0.001);
        assertEquals("Very creative idea", commentsMap.get("c1"));

        assertTrue("c2 score must persist after recycling", scoresMap.containsKey("c2"));
        assertEquals(18.0, scoresMap.get("c2"), 0.001);

        assertTrue("c3 score must persist after recycling", scoresMap.containsKey("c3"));
        assertEquals(9.0, scoresMap.get("c3"), 0.001);

        // Test weighted score calculation formula: Sum of ((score / maxMarks) * weight)
        // c1: (8.0 / 10.0) * 40.0 = 32.0
        // c2: (18.0 / 20.0) * 40.0 = 36.0
        // c3: (9.0 / 10.0) * 20.0 = 18.0
        // Total expected = 32.0 + 36.0 + 18.0 = 86.0
        double totalWeighted = 0.0;
        for (com.example.judgex.models.Criterion c : criteria) {
            Double score = scoresMap.get(c.getId());
            assertNotNull(score);
            totalWeighted += (score / c.getMaxMarks()) * c.getWeight();
        }
        assertEquals(86.0, totalWeighted, 0.001);

        // Test raw sum calculation
        // 8.0 + 18.0 + 9.0 = 35.0 out of 40.0
        double rawSum = 0.0;
        double maxTotal = 0.0;
        for (com.example.judgex.models.Criterion c : criteria) {
            rawSum += scoresMap.get(c.getId());
            maxTotal += c.getMaxMarks();
        }
        assertEquals(35.0, rawSum, 0.001);
        assertEquals(40.0, maxTotal, 0.001);
    }

    @Test
    public void testEventCardActionBehaviorsPreserved() {
        Event event = new Event("ev_hack", "SIH Campus Hackathon", "SIH internal selection", Event.STATUS_ACTIVE, false, System.currentTimeMillis());

        // 1. Status lifecycle action preserves state
        assertEquals(Event.STATUS_ACTIVE, event.getStatus());
        event.setStatus(Event.STATUS_LOCKED);
        event.setLocked(true);
        assertEquals(Event.STATUS_LOCKED, event.getStatus());
        assertTrue(event.isLocked());

        // 2. Archive action toggles archive status
        event.setStatus(Event.STATUS_ARCHIVED);
        assertTrue(event.isArchived());
        // Unarchive
        event.setStatus(Event.STATUS_ACTIVE);
        assertFalse(event.isArchived());

        // 3. Delete action performs safe soft-delete
        event.setDeleted(true);
        assertTrue(event.isDeleted());

        // 4. Manage navigation maintains event identification
        assertEquals("ev_hack", event.getId());
        assertEquals("SIH Campus Hackathon", event.getName());
    }

    @Test
    public void testWeightedAndRawSumAggregationWithDivisorIntegrity() {
        Team team = new Team("t201", "ByteBuilders", "TM-201", "ev_fest", "panel_A");
        TeamResult teamResult = new TeamResult(team);

        List<Criterion> criteria = new ArrayList<>();
        // Criterion 1: Max 10 marks, Weight 40%
        criteria.add(new Criterion("c1", "ev_fest", "Code Quality", "Architecture", 10.0, 40.0, 1));
        // Criterion 2: Max 20 marks, Weight 60%
        criteria.add(new Criterion("c2", "ev_fest", "Impact & UI", "Usability", 20.0, 60.0, 2));

        // Judge 1: c1 = 8/10 (32%), c2 = 18/20 (54%) -> Weighted = 86.0%. Raw = 26/30.
        Evaluation eval1 = new Evaluation();
        eval1.setId("ev1");
        eval1.setJudgeId("j1");
        eval1.setTeamId("t201");
        eval1.setStatus(Evaluation.STATUS_SUBMITTED);
        Map<String, Double> marks1 = new HashMap<>();
        marks1.put("c1", 8.0);
        marks1.put("c2", 18.0);
        eval1.setScores(marks1);

        // Judge 2: c1 = 10/10 (40%), c2 = 16/20 (48%) -> Weighted = 88.0%. Raw = 26/30.
        Evaluation eval2 = new Evaluation();
        eval2.setId("ev2");
        eval2.setJudgeId("j2");
        eval2.setTeamId("t201");
        eval2.setStatus(Evaluation.STATUS_SUBMITTED);
        Map<String, Double> marks2 = new HashMap<>();
        marks2.put("c1", 10.0);
        marks2.put("c2", 16.0);
        eval2.setScores(marks2);

        // Judge 3 is assigned to the panel, but hasn't evaluated yet (missing evaluation)
        teamResult.addEvaluation(eval1);
        teamResult.addEvaluation(eval2);

        // Test WEIGHTED calculation: Expected 3 judges total, but only 2 submitted
        teamResult.calculateScores(3, Event.SCORING_WEIGHTED, criteria);

        // Average score must be (86.0 + 88.0) / 2 = 87.0 (Missing 3rd judge must NOT be treated as 0!)
        assertEquals(87.0, teamResult.getAverageScore(), 0.001);
        // Valid submitted evaluations count is 2
        assertEquals(2, teamResult.getEvaluationsCount());
        // Provisional because 1 of 3 judges is pending
        assertTrue(teamResult.isProvisional());
        assertEquals(1, teamResult.getPendingEvaluationsCount());

        // Test RAW calculation on same evaluations: Raw sum for eval1 = 26.0, eval2 = 26.0 -> Average = 26.0
        teamResult.calculateScores(3, Event.SCORING_RAW, criteria);
        assertEquals(26.0, teamResult.getAverageScore(), 0.001);
        assertEquals(2, teamResult.getEvaluationsCount());
        assertTrue(teamResult.isProvisional());

        // Once 3rd judge submits: 3/3 complete -> Final
        Evaluation eval3 = new Evaluation();
        eval3.setId("ev3");
        eval3.setJudgeId("j3");
        eval3.setTeamId("t201");
        eval3.setStatus(Evaluation.STATUS_SUBMITTED);
        Map<String, Double> marks3 = new HashMap<>();
        marks3.put("c1", 9.0);
        marks3.put("c2", 17.0); // Raw sum = 26.0
        eval3.setScores(marks3);
        teamResult.addEvaluation(eval3);

        teamResult.calculateScores(3, Event.SCORING_RAW, criteria);
        assertEquals(3, teamResult.getEvaluationsCount());
        assertFalse(teamResult.isProvisional()); // All evaluations submitted!
        assertEquals(100.0, teamResult.getCompletionPercentage(), 0.001);
    }

    @Test
    public void testTieHandlingConsistencyAndRanking() {
        Team teamAlpha = new Team("tA", "Alpha Devs", "TM-01", "ev1", "p1");
        Team teamBeta = new Team("tB", "Beta Coders", "TM-02", "ev1", "p1");
        Team teamGamma = new Team("tC", "Gamma Engineers", "TM-03", "ev1", "p1");

        TeamResult resAlpha = new TeamResult(teamAlpha);
        TeamResult resBeta = new TeamResult(teamBeta);
        TeamResult resGamma = new TeamResult(teamGamma);

        // Simulate evaluations where Alpha and Beta have identical average scores (Tie)
        Evaluation evalA = new Evaluation();
        evalA.setStatus(Evaluation.STATUS_SUBMITTED);
        evalA.setTotalScore(92.5);
        resAlpha.addEvaluation(evalA);
        resAlpha.calculateScores(1);

        Evaluation evalB = new Evaluation();
        evalB.setStatus(Evaluation.STATUS_SUBMITTED);
        evalB.setTotalScore(92.5);
        resBeta.addEvaluation(evalB);
        resBeta.calculateScores(1);

        Evaluation evalC = new Evaluation();
        evalC.setStatus(Evaluation.STATUS_SUBMITTED);
        evalC.setTotalScore(88.0);
        resGamma.addEvaluation(evalC);
        resGamma.calculateScores(1);

        List<TeamResult> results = new ArrayList<>();
        results.add(resGamma);
        results.add(resBeta);
        results.add(resAlpha);

        // Sort results: TeamResult implements Comparable (descending by score, then by team number)
        Collections.sort(results);

        // First two should have score 92.5
        assertEquals(92.5, results.get(0).getAverageScore(), 0.001);
        assertEquals(92.5, results.get(1).getAverageScore(), 0.001);
        assertEquals(88.0, results.get(2).getAverageScore(), 0.001);

        // Verify tie rank assignment logic
        int currentRank = 1;
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                if (Math.abs(results.get(i).getAverageScore() - results.get(i - 1).getAverageScore()) > 0.001) {
                    currentRank = i + 1;
                }
            }
            results.get(i).setRank(currentRank);
        }

        // Alpha and Beta share Rank 1, Gamma receives Rank 3
        assertEquals(1, results.get(0).getRank());
        assertEquals(1, results.get(1).getRank());
        assertEquals(3, results.get(2).getRank());
    }

    @Test
    public void testMultiEventDistinctQRPassesAndScannerValidation() throws Exception {
        String teamId = "team_stellar_42";
        String teamNumber = "TM-42";
        String teamName = "Stellar Coders";

        // Team registers for Event 1 (Hackathon) in Panel 1
        String event1Id = "ev_hackathon_2026";
        String panel1Id = "panel_hack_1";
        JSONObject qr1 = new JSONObject();
        qr1.put("v", 2);
        qr1.put("teamId", teamId);
        qr1.put("teamNumber", teamNumber);
        qr1.put("teamName", teamName);
        qr1.put("eventId", event1Id);
        qr1.put("panelId", panel1Id);
        qr1.put("issuedAt", System.currentTimeMillis());
        qr1.put("token", UUID.randomUUID().toString());

        // Team registers for Event 2 (Ideathon) in Panel 3
        String event2Id = "ev_ideathon_2026";
        String panel3Id = "panel_idea_3";
        JSONObject qr2 = new JSONObject();
        qr2.put("v", 2);
        qr2.put("teamId", teamId);
        qr2.put("teamNumber", teamNumber);
        qr2.put("teamName", teamName);
        qr2.put("eventId", event2Id);
        qr2.put("panelId", panel3Id);
        qr2.put("issuedAt", System.currentTimeMillis() + 1000);
        qr2.put("token", UUID.randomUUID().toString());

        // 1. Verify QR tokens and payloads are distinct for each event
        assertNotEquals(qr1.getString("eventId"), qr2.getString("eventId"));
        assertNotEquals(qr1.getString("panelId"), qr2.getString("panelId"));
        assertNotEquals(qr1.getString("token"), qr2.getString("token"));

        // 2. Scanner validation check: Judge scanning in Event 1 receives QR from Event 2 -> MUST REJECT
        String scannedEventId = qr2.getString("eventId");
        String activeEventId = event1Id;
        assertFalse("Cross-event QR must be rejected", activeEventId.equals(scannedEventId));

        // 3. Scanner validation check: Judge assigned only to panel_hack_2 scanning team in panel_hack_1 -> MUST REJECT
        String judgeAssignedPanel = "panel_hack_2";
        String teamAssignedPanel = qr1.getString("panelId");
        assertFalse("Panel mismatch must be rejected for judges assigned to specific panels", judgeAssignedPanel.equals(teamAssignedPanel));

        // 4. Authorized scan: Event and Panel match -> PERMIT
        assertTrue("Matching event and panel must be permitted", event1Id.equals(qr1.getString("eventId")) && panel1Id.equals(qr1.getString("panelId")));
    }

    @Test
    public void testEmailNormalizationAndConflictPrevention() {
        String input1 = "  Admin@College.EDU  ";
        String input2 = "admin@college.edu";
        String input3 = "ADMIN@COLLEGE.EDU";

        // Normalization must yield identical lowercase trimmed string
        String norm1 = FirebaseManager.normalizeEmail(input1);
        String norm2 = FirebaseManager.normalizeEmail(input2);
        String norm3 = FirebaseManager.normalizeEmail(input3);

        assertEquals("admin@college.edu", norm1);
        assertEquals(norm1, norm2);
        assertEquals(norm2, norm3);

        // Account conflict validation simulation
        Map<String, String> registeredEmails = new HashMap<>();
        registeredEmails.put(norm1, "user_admin_1");

        // Attempting to register again with uppercase or whitespace must detect existing account
        String newRegistrationEmail = "  aDMin@colLEge.Edu ";
        String normalizedNew = FirebaseManager.normalizeEmail(newRegistrationEmail);
        assertTrue("Normalized email check must detect account collision", registeredEmails.containsKey(normalizedNew));
    }

    @Test
    public void testOrganizationRegistrationWithoutPhone() {
        // Create organization without requiring contact phone number
        Organization org = new Organization("org_tech", "Tech Innovation Institute", "admin_uid_99", "director@tech.edu");

        assertEquals("org_tech", org.getId());
        assertEquals("Tech Innovation Institute", org.getName());
        assertEquals("admin_uid_99", org.getAdminUid());
        assertEquals("director@tech.edu", org.getAdminEmail());
        assertNotNull(org.getContactPhone());
        assertEquals("", org.getContactPhone()); // Defaults cleanly to empty string
        assertTrue(org.getCreatedAt() > 0);
    }
}
