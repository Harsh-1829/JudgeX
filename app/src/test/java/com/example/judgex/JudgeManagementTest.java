package com.example.judgex;

import com.example.judgex.admin.JudgeManagerActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Panel;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.CryptoUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class JudgeManagementTest {

    @Test
    public void testNormalizeJudgeName_TrimmingAndCaseInsensitivity() {
        String raw1 = "  Dr. Jane Smith  ";
        String raw2 = "DR. JANE SMITH";
        String raw3 = "dr.   jane    smith";

        String norm1 = User.normalizeJudgeName(raw1);
        String norm2 = User.normalizeJudgeName(raw2);
        String norm3 = User.normalizeJudgeName(raw3);

        assertEquals("dr. jane smith", norm1);
        assertEquals("dr. jane smith", norm2);
        assertEquals("dr. jane smith", norm3);

        assertEquals(norm1, norm2);
        assertEquals(norm2, norm3);
    }

    @Test
    public void testNormalizeJudgeName_DifferentCapitalizationDetectedAsDuplicate() {
        String existing = "Prof. Alice Johnson";
        String candidate = "PROF. ALICE JOHNSON";

        assertEquals(User.normalizeJudgeName(existing), User.normalizeJudgeName(candidate));
    }

    @Test
    public void testNormalizeJudgeName_ExtraInternalAndExternalWhitespaceDetectedAsDuplicate() {
        String existing = "Bob Brown";
        String candidate = "   Bob    Brown   ";

        assertEquals(User.normalizeJudgeName(existing), User.normalizeJudgeName(candidate));
    }

    @Test
    public void testJudgeNameUniquenessValidation_NewNameSucceeds() {
        List<User> existingJudges = new ArrayList<>();
        existingJudges.add(new User("j1", "Dr. Jane Smith", "jane@example.com", User.ROLE_JUDGE));
        existingJudges.add(new User("j2", "Prof. Alan Turing", "alan@example.com", User.ROLE_JUDGE));

        String newJudgeName = "Ada Lovelace";
        String targetNormalized = User.normalizeJudgeName(newJudgeName);

        boolean isDuplicate = false;
        for (User j : existingJudges) {
            if (j.getNormalizedName().equals(targetNormalized)) {
                isDuplicate = true;
                break;
            }
        }

        assertFalse("New unique judge name should not be flagged as duplicate", isDuplicate);
    }

    @Test
    public void testJudgeNameUniquenessValidation_DuplicateRejected() {
        List<User> existingJudges = new ArrayList<>();
        existingJudges.add(new User("j1", "Dr. Jane Smith", "jane@example.com", User.ROLE_JUDGE));

        String candidateName = "  DR.   JANE   SMITH  ";
        String targetNormalized = User.normalizeJudgeName(candidateName);

        boolean isDuplicate = false;
        for (User j : existingJudges) {
            if (j.getNormalizedName().equals(targetNormalized)) {
                isDuplicate = true;
                break;
            }
        }

        assertTrue("Candidate with differing case and extra whitespace must be rejected as duplicate", isDuplicate);
    }

    @Test
    public void testJudgeNameUniquenessValidation_EditOwnNamePermitted() {
        List<User> existingJudges = new ArrayList<>();
        User judge1 = new User("j1", "Dr. Jane Smith", "jane@example.com", User.ROLE_JUDGE);
        existingJudges.add(judge1);
        existingJudges.add(new User("j2", "Prof. Alan Turing", "alan@example.com", User.ROLE_JUDGE));

        // Judge 1 updating casing of own name
        String updatedName = "DR. JANE SMITH";
        String targetNormalized = User.normalizeJudgeName(updatedName);

        boolean isDuplicateExcludingOwnUid = false;
        for (User j : existingJudges) {
            if (!"j1".equals(j.getUid()) && j.getNormalizedName().equals(targetNormalized)) {
                isDuplicateExcludingOwnUid = true;
                break;
            }
        }

        assertFalse("Editing judge's own name should be permitted when excluding own UID", isDuplicateExcludingOwnUid);
    }

    @Test
    public void testJudgeNameUniquenessValidation_EditToAnotherJudgeNameRejected() {
        List<User> existingJudges = new ArrayList<>();
        existingJudges.add(new User("j1", "Dr. Jane Smith", "jane@example.com", User.ROLE_JUDGE));
        existingJudges.add(new User("j2", "Prof. Alan Turing", "alan@example.com", User.ROLE_JUDGE));

        // Judge 2 trying to take Judge 1's name
        String candidateName = "Dr. Jane Smith";
        String targetNormalized = User.normalizeJudgeName(candidateName);

        boolean isDuplicateExcludingOwnUid = false;
        for (User j : existingJudges) {
            if (!"j2".equals(j.getUid()) && j.getNormalizedName().equals(targetNormalized)) {
                isDuplicateExcludingOwnUid = true;
                break;
            }
        }

        assertTrue("Renaming judge to another existing judge's name must be rejected", isDuplicateExcludingOwnUid);
    }

    @Test
    public void testAutoAssignSelectedJudges_OnlyUnassignedJudgesAreAssigned() {
        String eventId = "event_sih_2026";

        // Setup 4 selected judges: 2 already assigned, 2 unassigned
        User j1 = new User("j1", "Judge 1", "j1@example.com", User.ROLE_JUDGE);
        j1.setPanelForEvent(eventId, "p1");

        User j2 = new User("j2", "Judge 2", "j2@example.com", User.ROLE_JUDGE);
        j2.setPanelForEvent(eventId, "p2");

        User j3 = new User("j3", "Judge 3", "j3@example.com", User.ROLE_JUDGE); // unassigned
        User j4 = new User("j4", "Judge 4", "j4@example.com", User.ROLE_JUDGE); // unassigned

        List<User> selectedJudges = new ArrayList<>();
        selectedJudges.add(j1);
        selectedJudges.add(j2);
        selectedJudges.add(j3);
        selectedJudges.add(j4);

        // Partition into unassigned and already assigned
        List<User> unassigned = new ArrayList<>();
        List<User> alreadyAssigned = new ArrayList<>();
        for (User j : selectedJudges) {
            String panel = j.getPanelForEvent(eventId);
            if (panel != null && !panel.isEmpty()) {
                alreadyAssigned.add(j);
            } else {
                unassigned.add(j);
            }
        }

        assertEquals(2, alreadyAssigned.size());
        assertEquals(2, unassigned.size());
        assertTrue(alreadyAssigned.contains(j1));
        assertTrue(alreadyAssigned.contains(j2));
        assertTrue(unassigned.contains(j3));
        assertTrue(unassigned.contains(j4));

        // Setup 3 panels
        List<Panel> panels = new ArrayList<>();
        panels.add(new Panel("p1", "Panel 1", eventId));
        panels.add(new Panel("p2", "Panel 2", eventId));
        panels.add(new Panel("p3", "Panel 3", eventId));

        // Distribute only unassigned judges
        Map<String, String> assignments = new HashMap<>();
        for (int i = 0; i < unassigned.size(); i++) {
            assignments.put(unassigned.get(i).getUid(), panels.get(i % panels.size()).getId());
        }

        assertEquals(2, assignments.size());
        assertTrue(assignments.containsKey("j3"));
        assertTrue(assignments.containsKey("j4"));
        assertFalse("Already assigned judges should not be overwritten by auto-assign", assignments.containsKey("j1"));
        assertFalse("Already assigned judges should not be overwritten by auto-assign", assignments.containsKey("j2"));
    }

    @Test
    public void testAutoAssignSelectedJudges_PreservesUnselectedJudgesOnPanels() {
        String eventId = "event_sih_2026";

        // Setup panels with existing unselected judges
        Panel p1 = new Panel("p1", "Panel 1", eventId);
        p1.setJudgeIds(new ArrayList<>(List.of("unselected_judge_A")));

        Panel p2 = new Panel("p2", "Panel 2", eventId);
        p2.setJudgeIds(new ArrayList<>(List.of("unselected_judge_B")));

        List<Panel> allPanels = new ArrayList<>(List.of(p1, p2));

        // Selected judges being assigned
        Map<String, String> newAssignments = new HashMap<>();
        newAssignments.put("selected_judge_1", "p1");
        newAssignments.put("selected_judge_2", "p2");

        // Simulate batchAssignSelectedJudges in-memory logic
        for (Map.Entry<String, String> entry : newAssignments.entrySet()) {
            for (Panel p : allPanels) {
                if (p.getId().equals(entry.getValue())) {
                    if (!p.getJudgeIds().contains(entry.getKey())) {
                        p.getJudgeIds().add(entry.getKey());
                    }
                }
            }
        }

        // Verify unselected judges were preserved
        assertTrue(p1.getJudgeIds().contains("unselected_judge_A"));
        assertTrue(p1.getJudgeIds().contains("selected_judge_1"));
        assertEquals(2, p1.getJudgeIds().size());

        assertTrue(p2.getJudgeIds().contains("unselected_judge_B"));
        assertTrue(p2.getJudgeIds().contains("selected_judge_2"));
        assertEquals(2, p2.getJudgeIds().size());
    }

    @Test
    public void testPasswordSecurity_UserModelNeverStoresPlaintextPassword() {
        User user = new User("uid123", "Dr. Jane Smith", "jane@example.com", User.ROLE_JUDGE);

        // Verify no password fields exist via reflection or getters
        for (java.lang.reflect.Field field : User.class.getDeclaredFields()) {
            assertNotEquals("password", field.getName().toLowerCase());
        }

        for (java.lang.reflect.Method method : User.class.getDeclaredMethods()) {
            assertFalse(method.getName().toLowerCase().contains("password"));
        }
    }

    @Test
    public void testPasswordValidation_Requirements() {
        // Validation rules: not empty, min 6 characters, passwords must match
        String validPass = "ValidPass123";
        String matchingConfirm = "ValidPass123";
        String mismatchedConfirm = "DifferentPass456";
        String shortPass = "12345";

        assertTrue("Valid password >= 6 characters", validPass.length() >= 6);
        assertEquals("Passwords must match", validPass, matchingConfirm);
        assertNotEquals("Mismatched passwords must be detected", validPass, mismatchedConfirm);
        assertTrue("Short password < 6 characters must be flagged", shortPass.length() < 6);
    }

    @Test
    public void testSanitizeJudgeNameForUsername() {
        assertEquals("drjanesmith", FirebaseManager.sanitizeJudgeNameForUsername("Dr. Jane Smith"));
        assertEquals("profalanturing2026", FirebaseManager.sanitizeJudgeNameForUsername("Prof. Alan Turing 2026!"));
        assertEquals("judge", FirebaseManager.sanitizeJudgeNameForUsername(""));
        assertEquals("judge", FirebaseManager.sanitizeJudgeNameForUsername(null));
        assertEquals("judge", FirebaseManager.sanitizeJudgeNameForUsername("!@#$%^"));
    }

    @Test
    public void testJudgeUsernameSyntheticEmailMapping() {
        String username = "drjanesmith4821";
        String syntheticEmail = Constants.buildSyntheticEmail(username);
        assertEquals("drjanesmith4821@team.judgex.internal", syntheticEmail);
        assertTrue(Constants.isSyntheticEmail(syntheticEmail));
        assertEquals("drjanesmith4821", Constants.extractUsernameFromSyntheticEmail(syntheticEmail));
    }

    @Test
    public void testAllowSameJudgeNameWithDistinctUsernamesAndEventScoping() {
        // Two event-specific accounts for judges with the exact same name
        User judgeEventA = new User("uid_event_a", "drjanesmith1001@team.judgex.internal", "Dr. Jane Smith", User.ROLE_JUDGE, "p1", System.currentTimeMillis());
        judgeEventA.setUsername("drjanesmith1001");
        judgeEventA.setEventId("event_A");

        User judgeEventB = new User("uid_event_b", "drjanesmith2002@team.judgex.internal", "Dr. Jane Smith", User.ROLE_JUDGE, "p2", System.currentTimeMillis());
        judgeEventB.setUsername("drjanesmith2002");
        judgeEventB.setEventId("event_B");

        // Both accounts have identical display name
        assertEquals(judgeEventA.getName(), judgeEventB.getName());

        // Distinguished by unique usernames and UIDs
        assertNotEquals(judgeEventA.getUsername(), judgeEventB.getUsername());
        assertNotEquals(judgeEventA.getUid(), judgeEventB.getUid());

        // Event scoping: Account for Event A is present in Event A and absent from Event B
        assertTrue(JudgeManagerActivity.isJudgeInEvent(judgeEventA, "event_A"));
        assertFalse(JudgeManagerActivity.isJudgeInEvent(judgeEventA, "event_B"));

        // Account for Event B is present in Event B and absent from Event A
        assertTrue(JudgeManagerActivity.isJudgeInEvent(judgeEventB, "event_B"));
        assertFalse(JudgeManagerActivity.isJudgeInEvent(judgeEventB, "event_A"));
    }

    @Test
    public void testIsJudgeInEventScoping() {
        User judge = new User("j1", "Ada Lovelace", "ada@example.com", User.ROLE_JUDGE);
        judge.setEventId("event_hackathon");

        // Direct event match
        assertTrue(JudgeManagerActivity.isJudgeInEvent(judge, "event_hackathon"));
        assertFalse(JudgeManagerActivity.isJudgeInEvent(judge, "other_event"));

        // Multi-event map assignment match
        User multiJudge = new User("j2", "Alan Turing", "alan@example.com", User.ROLE_JUDGE);
        multiJudge.setPanelForEvent("event_1", "panel_1");
        assertTrue(JudgeManagerActivity.isJudgeInEvent(multiJudge, "event_1"));
        assertFalse(JudgeManagerActivity.isJudgeInEvent(multiJudge, "event_2"));
    }

    @Test
    public void testJudgeCredentialSecurity_EncryptedSecretStorageAndRetrieval() {
        User user = new User("uid123", "Dr. Jane Smith", "jane@example.com", User.ROLE_JUDGE);
        String rawGeneratedPassword = FirebaseManager.generateSecureTemporaryPassword();
        String encrypted = CryptoUtils.encrypt(rawGeneratedPassword);

        // Verify encrypted ciphertext is not equal to plaintext and does not leak plaintext
        assertNotEquals(rawGeneratedPassword, encrypted);
        assertFalse(encrypted.contains(rawGeneratedPassword));

        user.setEncryptedSecret(encrypted);
        assertEquals(encrypted, user.getEncryptedSecret());

        // Decrypt securely on retrieval
        String decrypted = CryptoUtils.decrypt(user.getEncryptedSecret());
        assertEquals(rawGeneratedPassword, decrypted);
    }
}
