package com.example.judgex;

import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;

import com.example.judgex.admin.TeamManagerActivity;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class TeamRegistrationAndLoginTest {

    @Test
    public void testSanitizeTeamNameForUsername() {
        assertEquals("codewarriors", FirebaseManager.sanitizeTeamNameForUsername("Code Warriors"));
        assertEquals("codewarriors", FirebaseManager.sanitizeTeamNameForUsername("  Code-Warriors!  "));
        assertEquals("bytebuilders2026", FirebaseManager.sanitizeTeamNameForUsername("Byte Builders #2026"));
        assertEquals("alphabeta", FirebaseManager.sanitizeTeamNameForUsername("Alpha & Beta"));
        assertEquals("team", FirebaseManager.sanitizeTeamNameForUsername("!!! @@@ ###"));
        assertEquals("team", FirebaseManager.sanitizeTeamNameForUsername(""));
        assertEquals("team", FirebaseManager.sanitizeTeamNameForUsername(null));
    }

    @Test
    public void testSyntheticEmailMapping() {
        String username = "codewarriors4821";
        String syntheticEmail = Constants.buildTeamSyntheticEmail(username);

        assertEquals("codewarriors4821@team.judgex.internal", syntheticEmail);
        assertTrue(Constants.isSyntheticEmail(syntheticEmail));
        assertFalse(Constants.isSyntheticEmail("admin@college.edu"));
        assertFalse(Constants.isSyntheticEmail("judge@judgex.com"));

        // Idempotency check: formatting already-formatted synthetic email should not duplicate domain
        assertEquals(syntheticEmail, Constants.buildTeamSyntheticEmail(syntheticEmail));

        // Extraction check
        assertEquals("codewarriors4821", Constants.extractUsernameFromSyntheticEmail(syntheticEmail));
    }

    @Test
    public void testLoginIdentifierResolution() {
        // When leader enters username without @, map to synthetic auth email
        String leaderInput = "codewarriors4821";
        String resolvedEmail;
        if (!leaderInput.contains("@")) {
            resolvedEmail = Constants.buildTeamSyntheticEmail(leaderInput);
        } else {
            resolvedEmail = leaderInput;
        }
        assertEquals("codewarriors4821@team.judgex.internal", resolvedEmail);

        // When admin/judge enters standard email with @, preserve entered email
        String adminInput = "admin@university.edu";
        if (!adminInput.contains("@")) {
            resolvedEmail = Constants.buildTeamSyntheticEmail(adminInput);
        } else {
            resolvedEmail = adminInput;
        }
        assertEquals("admin@university.edu", resolvedEmail);
    }

    @Test
    public void testGenerateSecureTemporaryPassword() {
        Set<String> generatedPasswords = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            String password = FirebaseManager.generateSecureTemporaryPassword();

            // Firebase requirement: at least 6 characters
            assertTrue("Password must be at least 6 characters", password.length() >= 6);
            assertEquals("Password length should be 8 characters", 8, password.length());

            boolean hasUpper = false;
            boolean hasLower = false;
            boolean hasDigit = false;

            for (char c : password.toCharArray()) {
                if (Character.isUpperCase(c)) hasUpper = true;
                if (Character.isLowerCase(c)) hasLower = true;
                if (Character.isDigit(c)) hasDigit = true;
            }

            assertTrue("Password must contain at least one uppercase letter: " + password, hasUpper);
            assertTrue("Password must contain at least one lowercase letter: " + password, hasLower);
            assertTrue("Password must contain at least one digit: " + password, hasDigit);

            generatedPasswords.add(password);
        }

        // Check randomness: 50 iterations should generate 50 unique passwords
        assertEquals("Generated passwords must be randomized and unique", 50, generatedPasswords.size());
    }

    @Test
    public void testModelsStoreUsernameAndNeverPlaintextPassword() {
        Team team = new Team();
        team.setTeamName("Code Warriors");
        team.setUsername("codewarriors4821");

        assertEquals("codewarriors4821", team.getUsername());
        assertEquals("Code Warriors", team.getTeamName());

        User user = new User();
        user.setName("Alice Smith");
        user.setRole(User.ROLE_TEAM_LEADER);
        user.setUsername("codewarriors4821");

        assertEquals("codewarriors4821", user.getUsername());
        assertEquals("Alice Smith", user.getName());
        assertTrue(user.isTeamLeader());

        // Verify neither class has a password getter/setter to prevent plaintext leakage
        try {
            Team.class.getMethod("getPassword");
            fail("Team class must NEVER have getPassword method");
        } catch (NoSuchMethodException expected) {
            // Success
        }

        try {
            User.class.getMethod("getPassword");
            fail("User class must NEVER have getPassword method");
        } catch (NoSuchMethodException expected) {
            // Success
        }
    }

    @Test
    public void testCryptoUtilsEncryptionAndDecryption() {
        String originalPassword = "X7mK9p2w";

        // Encryption produces a non-empty ciphertext different from plaintext
        String ciphertext = com.example.judgex.utils.CryptoUtils.encrypt(originalPassword);
        assertNotNull(ciphertext);
        assertFalse(ciphertext.isEmpty());
        assertNotEquals(originalPassword, ciphertext);

        // Decryption recovers the exact original password
        String decrypted = com.example.judgex.utils.CryptoUtils.decrypt(ciphertext);
        assertEquals(originalPassword, decrypted);

        // Multiple encryptions use unique IVs (ciphertexts must differ)
        String ciphertext2 = com.example.judgex.utils.CryptoUtils.encrypt(originalPassword);
        assertNotEquals(ciphertext, ciphertext2);
        assertEquals(originalPassword, com.example.judgex.utils.CryptoUtils.decrypt(ciphertext2));

        // Tampered ciphertext fails gracefully without throwing an exception
        String tampered = "invalid_cipher_text";
        assertEquals("", com.example.judgex.utils.CryptoUtils.decrypt(tampered));
        assertEquals("", com.example.judgex.utils.CryptoUtils.decrypt(""));
        assertEquals("", com.example.judgex.utils.CryptoUtils.decrypt(null));

        // Team model stores encrypted password, not plaintext
        Team team = new Team();
        team.setEncryptedPassword(ciphertext);
        assertEquals(ciphertext, team.getEncryptedPassword());
        assertEquals(originalPassword, com.example.judgex.utils.CryptoUtils.decrypt(team.getEncryptedPassword()));
    }

    @Test
    public void testDuplicateTeamCheckWithinEvent() {
        List<Team> teams = new ArrayList<>();

        Team team1 = new Team();
        team1.setId("team_1");
        team1.setTeamName("Code Warriors");
        team1.setLeaderName("John Doe");
        teams.add(team1);

        // Exact match -> Duplicate
        assertTrue(TeamManagerActivity.isDuplicateTeam(teams, "Code Warriors", "John Doe"));

        // Match ignoring capitalization -> Duplicate
        assertTrue(TeamManagerActivity.isDuplicateTeam(teams, "code warriors", "john doe"));
        assertTrue(TeamManagerActivity.isDuplicateTeam(teams, "CODE WARRIORS", "JOHN DOE"));

        // Match ignoring extra leading/trailing and internal spaces -> Duplicate
        assertTrue(TeamManagerActivity.isDuplicateTeam(teams, "  Code   Warriors  ", "  John    Doe  "));

        // Team Name differs -> Allowed (not duplicate)
        assertFalse(TeamManagerActivity.isDuplicateTeam(teams, "Code Titans", "John Doe"));

        // Leader Name differs -> Allowed (not duplicate)
        assertFalse(TeamManagerActivity.isDuplicateTeam(teams, "Code Warriors", "Jane Smith"));

        // Both differ -> Allowed (not duplicate)
        assertFalse(TeamManagerActivity.isDuplicateTeam(teams, "Byte Builders", "Alice Bob"));

        // Deleted team in the list -> Allowed (not duplicate)
        team1.setDeleted(true);
        assertFalse(TeamManagerActivity.isDuplicateTeam(teams, "Code Warriors", "John Doe"));
    }
}
