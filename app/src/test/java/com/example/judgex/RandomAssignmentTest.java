package com.example.judgex;

import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class RandomAssignmentTest {

    @Test
    public void testBalancedJudgeDistributionAcrossPanels() {
        // Setup 12 judges and 4 panels
        List<User> judges = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            User j = new User("j" + i, "Judge " + i, "judge" + i + "@example.com", User.ROLE_JUDGE);
            judges.add(j);
        }

        List<Panel> panels = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            panels.add(new Panel("p" + i, "Panel " + i, "e1"));
        }

        // Shuffle and round-robin distribute
        List<User> shuffled = new ArrayList<>(judges);
        Collections.shuffle(shuffled);

        Map<String, String> judgeToPanel = new HashMap<>();
        Map<String, List<String>> panelToJudges = new HashMap<>();
        for (Panel p : panels) {
            panelToJudges.put(p.getId(), new ArrayList<>());
        }

        for (int i = 0; i < shuffled.size(); i++) {
            Panel assigned = panels.get(i % panels.size());
            judgeToPanel.put(shuffled.get(i).getUid(), assigned.getId());
            panelToJudges.get(assigned.getId()).add(shuffled.get(i).getUid());
        }

        // Verify: Every judge assigned exactly once
        assertEquals(12, judgeToPanel.size());
        Set<String> uniqueAssignedJudges = new HashSet<>(judgeToPanel.keySet());
        assertEquals(12, uniqueAssignedJudges.size());

        // Verify balance: Each panel gets exactly 12 / 4 = 3 judges
        for (Panel p : panels) {
            assertEquals(3, panelToJudges.get(p.getId()).size());
        }
    }

    @Test
    public void testBalancedTeamDistributionUnevenNumbers() {
        // Setup 31 teams and 3 panels (31 % 3 = 1 -> two panels of 10, one panel of 11)
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= 31; i++) {
            Team t = new Team("t" + i, "Team " + i, "TM-" + i, "e1", "p0");
            teams.add(t);
        }

        List<Panel> panels = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            panels.add(new Panel("p" + i, "Panel " + i, "e1"));
        }

        Map<String, String> teamToPanel = new HashMap<>();
        Map<String, List<String>> panelToTeams = new HashMap<>();
        for (Panel p : panels) {
            panelToTeams.put(p.getId(), new ArrayList<>());
        }

        for (int i = 0; i < teams.size(); i++) {
            Panel assigned = panels.get(i % panels.size());
            teamToPanel.put(teams.get(i).getId(), assigned.getId());
            panelToTeams.get(assigned.getId()).add(teams.get(i).getId());
        }

        // Verify no duplicate team assignments
        assertEquals(31, teamToPanel.size());

        // Verify maximum size difference between any two panels is <= 1
        int minSize = Integer.MAX_VALUE;
        int maxSize = Integer.MIN_VALUE;
        for (Panel p : panels) {
            int size = panelToTeams.get(p.getId()).size();
            if (size < minSize) minSize = size;
            if (size > maxSize) maxSize = size;
        }

        assertTrue((maxSize - minSize) <= 1);
        assertEquals(10, minSize);
        assertEquals(11, maxSize);
    }

    @Test
    public void testEventSpecificJudgeAssignments() {
        User judge = new User("j1", "Dr. Alan", "alan@example.com", User.ROLE_JUDGE);

        // Event A (SIH Internal) -> Panel 1
        judge.setPanelForEvent("event_sih", "panel_sih_1");

        // Event B (Robotics Cup) -> Panel 4
        judge.setPanelForEvent("event_robotics", "panel_robotics_4");

        // Verify independent assignment retrieval
        assertEquals("panel_sih_1", judge.getPanelForEvent("event_sih"));
        assertEquals("panel_robotics_4", judge.getPanelForEvent("event_robotics"));

        // Non-assigned event returns null
        assertNull(judge.getPanelForEvent("event_pitch"));

        // Legacy user without event-specific mappings falls back to assignedPanelId
        User legacyJudge = new User("j2", "Legacy Judge", "legacy@example.com", User.ROLE_JUDGE);
        legacyJudge.setAssignedPanelId("panel_fallback");
        assertEquals("panel_fallback", legacyJudge.getPanelForEvent("event_pitch"));
    }
}
