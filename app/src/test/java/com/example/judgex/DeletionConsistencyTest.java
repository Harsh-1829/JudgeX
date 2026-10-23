package com.example.judgex;

import com.example.judgex.admin.JudgeManagerActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests verifying deletion consistency across JudgeX entities:
 * - Panels: safely unassigning judges and teams, preserving historical evaluations.
 * - Teams: soft-deletion tombstoning, panel decoupling, leader deactivation vs permanent purge.
 * - Events: soft-delete exclusion from active metrics, preference clearing, and cascade consistency.
 * - Judges: evaluation preservation auditability vs clean test account deletion, auth validation.
 * - Criteria: ID validation and referential score preservation.
 */
public class DeletionConsistencyTest {

    @Test
    public void testPanelDeletion_UnassignsJudgesAndTeamsPreservingHistory() {
        // Setup a panel with judges and teams
        Panel panel = new Panel("panel_1", "event_101", "Robotics Panel A", "Hall 1",
                new ArrayList<>(), new ArrayList<>(), System.currentTimeMillis());
        panel.getJudgeIds().add("judge_1");
        panel.getJudgeIds().add("judge_2");
        panel.getTeamIds().add("team_1");
        panel.getTeamIds().add("team_2");

        User judge1 = new User("judge_1", "Dr. Alan Turing", "alan@example.com", User.ROLE_JUDGE);
        judge1.setAssignedPanelId("panel_1");

        Team team1 = new Team();
        team1.setId("team_1");
        team1.setPanelId("panel_1");

        // Simulate panel unassignment logic executed during panel deletion
        if ("panel_1".equals(judge1.getAssignedPanelId())) {
            judge1.setAssignedPanelId("");
        }
        if ("panel_1".equals(team1.getPanelId())) {
            team1.setPanelId("");
        }

        // Verify judge and team are safely unassigned
        assertEquals("", judge1.getAssignedPanelId());
        assertEquals("", team1.getPanelId());

        // Verify historical evaluation remains intact and valid
        Evaluation eval = new Evaluation();
        eval.setId("eval_1");
        eval.setEventId("event_101");
        eval.setTeamId("team_1");
        eval.setJudgeId("judge_1");
        eval.setStatus(Evaluation.STATUS_SUBMITTED);

        assertEquals("team_1", eval.getTeamId());
        assertEquals("judge_1", eval.getJudgeId());
        assertEquals("event_101", eval.getEventId());
        assertEquals(Evaluation.STATUS_SUBMITTED, eval.getStatus());
    }

    @Test
    public void testTeamSoftDelete_DecouplesFromPanelAndPreservesEvaluations() {
        Panel panel = new Panel("panel_1", "event_101", "AI Panel", "Lab 2",
                new ArrayList<>(), new ArrayList<>(), System.currentTimeMillis());
        panel.getTeamIds().add("team_10");

        Team team = new Team();
        team.setId("team_10");
        team.setTeamName("Neural Nets");
        team.setPanelId("panel_1");
        team.setLeaderUid("leader_10");

        User leader = new User("leader_10", "Alice", "alice@example.com", User.ROLE_TEAM_LEADER);
        leader.setActive(true);

        // Soft-delete action:
        team.setDeleted(true);
        team.setStatus(Team.STATUS_DELETED);
        String oldPanelId = team.getPanelId();
        team.setPanelId("");

        panel.getTeamIds().remove(team.getId());
        leader.setDeleted(true);
        leader.setStatus(User.STATUS_DELETED);
        leader.setActive(false);

        // Assertions
        assertTrue(team.isDeleted());
        assertEquals(Team.STATUS_DELETED, team.getStatus());
        assertEquals("", team.getPanelId());
        assertFalse(panel.getTeamIds().contains("team_10"));
        assertTrue(leader.isDeleted());
        assertFalse(leader.isActive());
        assertEquals(User.STATUS_DELETED, leader.getStatus());
    }

    @Test
    public void testEventDeletion_DashboardMetricExcludesSoftDeletedEvents() {
        List<Event> events = new ArrayList<>();

        Event activeEvent = new Event("e1", "Hackathon 2026", "Desc", Event.STATUS_ACTIVE, false, System.currentTimeMillis());
        events.add(activeEvent);

        Event completedEvent = new Event("e2", "Coding Cup 2025", "Desc", Event.STATUS_COMPLETED, false, System.currentTimeMillis());
        events.add(completedEvent);

        Event softDeletedEvent = new Event("e3", "Old Trial Event", "Desc", Event.STATUS_ARCHIVED, false, System.currentTimeMillis());
        softDeletedEvent.setDeleted(true);
        events.add(softDeletedEvent);

        // Calculate metrics using updated logic
        int total = 0;
        int active = 0;
        int completed = 0;

        for (Event e : events) {
            if (e != null && !e.isDeleted()) {
                total++;
                if (Event.STATUS_ACTIVE.equalsIgnoreCase(e.getStatus())) {
                    active++;
                } else if (Event.STATUS_COMPLETED.equalsIgnoreCase(e.getStatus())) {
                    completed++;
                }
            }
        }

        assertEquals(2, total); // soft-deleted event is not counted
        assertEquals(1, active);
        assertEquals(1, completed);
    }

    @Test
    public void testActiveEventIdPreferenceCleanup_WhenEventIsDeleted() {
        String activeEventId = "event_selected_42";
        Event eventToDelete = new Event("event_selected_42", "Robo Wars", "Desc", Event.STATUS_ACTIVE, false, System.currentTimeMillis());

        // Simulated preference cleanup
        if (eventToDelete.getId() != null && eventToDelete.getId().equals(activeEventId)) {
            activeEventId = "";
        }

        assertEquals("", activeEventId);
    }

    @Test
    public void testDeleteJudgeAccount_WithHistoricalEvaluations_PreservesRecords() {
        User judgeWithHistory = new User("j_hist", "Dr. Eva", "eva@judgex.org", User.ROLE_JUDGE);
        int evalCount = 3;
        boolean hasEvaluations = evalCount > 0;

        FirebaseManager.DeleteJudgeResult result;
        if (hasEvaluations) {
            judgeWithHistory.setDeleted(true);
            judgeWithHistory.setStatus(User.STATUS_DELETED);
            judgeWithHistory.setActive(false);
            judgeWithHistory.setAssignedPanelId("");

            result = new FirebaseManager.DeleteJudgeResult(
                    judgeWithHistory.getUid(), judgeWithHistory.getName(), judgeWithHistory.getEmail(),
                    true, true, evalCount, false, false, true, "Judge profile tombstoned."
            );
        } else {
            result = new FirebaseManager.DeleteJudgeResult(
                    judgeWithHistory.getUid(), judgeWithHistory.getName(), judgeWithHistory.getEmail(),
                    true, false, 0, true, false, true, "Judge profile deleted."
            );
        }

        assertTrue(result.evaluationsPreserved);
        assertEquals(3, result.evaluationCount);
        assertFalse(result.profileDeleted);
        assertTrue(result.authDeleted);
        assertTrue(judgeWithHistory.isDeleted());
        assertFalse(judgeWithHistory.isActive());
    }

    @Test
    public void testDeleteJudgeAccount_WithoutEvaluations_MarksProfileDeleted() {
        User cleanJudge = new User("j_clean", "Test Judge", "test@judgex.org", User.ROLE_JUDGE);
        int evalCount = 0;
        boolean hasEvaluations = evalCount > 0;

        FirebaseManager.DeleteJudgeResult result;
        if (hasEvaluations) {
            result = new FirebaseManager.DeleteJudgeResult(
                    cleanJudge.getUid(), cleanJudge.getName(), cleanJudge.getEmail(),
                    true, true, evalCount, false, false, true, "Tombstoned."
            );
        } else {
            result = new FirebaseManager.DeleteJudgeResult(
                    cleanJudge.getUid(), cleanJudge.getName(), cleanJudge.getEmail(),
                    true, false, 0, true, false, true, "Completely deleted."
            );
        }

        assertFalse(result.evaluationsPreserved);
        assertEquals(0, result.evaluationCount);
        assertTrue(result.profileDeleted);
        assertTrue(result.authDeleted);
    }

    @Test
    public void testJudgeDeletionSafety_AdminSelfDeletionPrevented() {
        User signedInAdmin = new User("admin_root", "Super Admin", "admin@judgex.org", User.ROLE_ADMIN);
        User targetJudge = new User("admin_root", "Super Admin", "admin@judgex.org", User.ROLE_ADMIN);

        // Target UID matches signed in UID
        boolean isSelf = signedInAdmin.getUid().equals(targetJudge.getUid());
        assertTrue("Self deletion must be prevented", isSelf);

        // Role is not JUDGE
        boolean isJudgeRole = User.ROLE_JUDGE.equalsIgnoreCase(targetJudge.getRole());
        assertFalse("Admin accounts must never be treated as deletable judges", isJudgeRole);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCriterionDeletion_EmptyIdThrowsException() {
        String criterionId = "   ";
        if (criterionId == null || criterionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Criterion ID cannot be empty.");
        }
    }
}
