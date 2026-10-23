package com.example.judgex;

import com.example.judgex.models.Team;
import com.example.judgex.utils.CsvTeamParser;
import com.example.judgex.utils.CsvTeamParser.CsvRow;
import com.example.judgex.utils.CsvTeamParser.ParseResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CsvTeamImportTest {

    @Test
    public void testValidCsvWithStandardHeader() {
        String csv = "Team Name,Team Leader Name\n"
                + "Byte Builders,Alice Smith\n"
                + "Code Warriors,Bob Jones\n";

        ParseResult result = CsvTeamParser.parse(csv, new ArrayList<>());

        assertFalse(result.hasErrors());
        assertEquals(2, result.totalRows);
        assertEquals(2, result.validRows.size());
        assertEquals(0, result.errorRows.size());

        CsvRow row1 = result.validRows.get(0);
        assertEquals(2, row1.rowNumber);
        assertEquals("Byte Builders", row1.teamName);
        assertEquals("Alice Smith", row1.leaderName);

        CsvRow row2 = result.validRows.get(1);
        assertEquals(3, row2.rowNumber);
        assertEquals("Code Warriors", row2.teamName);
        assertEquals("Bob Jones", row2.leaderName);
    }

    @Test
    public void testReverseHeaderColumns() {
        String csv = "Team Leader Name,Team Name\n"
                + "Alice Smith,Byte Builders\n"
                + "Bob Jones,Code Warriors\n";

        ParseResult result = CsvTeamParser.parse(csv, new ArrayList<>());

        assertFalse(result.hasErrors());
        assertEquals(2, result.validRows.size());
        assertEquals("Byte Builders", result.validRows.get(0).teamName);
        assertEquals("Alice Smith", result.validRows.get(0).leaderName);
        assertEquals("Code Warriors", result.validRows.get(1).teamName);
        assertEquals("Bob Jones", result.validRows.get(1).leaderName);
    }

    @Test
    public void testAlternateHeadersAndSemicolonDelimiter() {
        String csv = "team;leader\n"
                + "RoboKnights;Charlie Brown\n"
                + "DataDynamos;Diana Prince\n";

        ParseResult result = CsvTeamParser.parse(csv, new ArrayList<>());

        assertFalse(result.hasErrors());
        assertEquals(2, result.validRows.size());
        assertEquals("RoboKnights", result.validRows.get(0).teamName);
        assertEquals("Charlie Brown", result.validRows.get(0).leaderName);
    }

    @Test
    public void testQuotedValuesAndEscapes() {
        String csv = "Team Name,Team Leader Name\n"
                + "\"The Champions, Inc.\",\"Dr. Smith, Jr.\"\n"
                + "\"Team \"\"Alpha\"\"\",Bob\n";

        ParseResult result = CsvTeamParser.parse(csv, new ArrayList<>());

        assertFalse(result.hasErrors());
        assertEquals(2, result.validRows.size());
        assertEquals("The Champions, Inc.", result.validRows.get(0).teamName);
        assertEquals("Dr. Smith, Jr.", result.validRows.get(0).leaderName);
        assertEquals("Team \"Alpha\"", result.validRows.get(1).teamName);
        assertEquals("Bob", result.validRows.get(1).leaderName);
    }

    @Test
    public void testHeaderlessCsv() {
        String csv = "Byte Builders,Alice Smith\n"
                + "Code Warriors,Bob Jones\n";

        ParseResult result = CsvTeamParser.parse(csv, new ArrayList<>());

        assertFalse(result.hasErrors());
        assertEquals(2, result.validRows.size());
        assertEquals(1, result.validRows.get(0).rowNumber);
        assertEquals("Byte Builders", result.validRows.get(0).teamName);
        assertEquals("Alice Smith", result.validRows.get(0).leaderName);
    }

    @Test
    public void testMissingFieldsValidation() {
        String csv = "Team Name,Team Leader Name\n"
                + ",Alice Smith\n"             // Row 2: Missing team name
                + "Code Warriors,\n"           // Row 3: Missing leader name
                + "JustOneColumn\n"            // Row 4: Only 1 column (missing leader name)
                + "Valid Team,Valid Leader\n";  // Row 5: Valid

        ParseResult result = CsvTeamParser.parse(csv, new ArrayList<>());

        assertTrue(result.hasErrors());
        assertEquals(4, result.totalRows);
        assertEquals(1, result.validRows.size());
        assertEquals(3, result.errorRows.size());

        // Check Row 2
        CsvRow row2 = result.errorRows.get(0);
        assertEquals(2, row2.rowNumber);
        assertTrue(row2.errors.get(0).contains("Team Name is required"));

        // Check Row 3
        CsvRow row3 = result.errorRows.get(1);
        assertEquals(3, row3.rowNumber);
        assertTrue(row3.errors.get(0).contains("Team Leader Name is required"));

        // Check Row 4
        CsvRow row4 = result.errorRows.get(2);
        assertEquals(4, row4.rowNumber);
        assertTrue(row4.errors.get(0).contains("Team Leader Name is required"));

        // Check Valid Row 5
        assertEquals("Valid Team", result.validRows.get(0).teamName);
    }

    @Test
    public void testInCsvDuplicateDetectionCaseAndWhitespace() {
        String csv = "Team Name,Team Leader Name\n"
                + "Code Warriors,John Doe\n"                 // Row 2: Original
                + "code warriors,john doe\n"                 // Row 3: Duplicate of Row 2 (case insensitive)
                + "  Code   Warriors  ,   John    Doe   \n"  // Row 4: Duplicate of Row 2 (whitespace)
                + "Code Warriors,Jane Smith\n"               // Row 5: Allowed (different leader)
                + "Code Titans,John Doe\n";                  // Row 6: Allowed (different team)

        ParseResult result = CsvTeamParser.parse(csv, new ArrayList<>());

        assertEquals(5, result.totalRows);
        assertEquals(3, result.validRows.size());
        assertEquals(2, result.errorRows.size());

        CsvRow row3 = result.errorRows.get(0);
        assertEquals(3, row3.rowNumber);
        assertTrue(row3.errors.get(0).contains("Duplicate within CSV (same team and leader as Row 2)"));

        CsvRow row4 = result.errorRows.get(1);
        assertEquals(4, row4.rowNumber);
        assertTrue(row4.errors.get(0).contains("Duplicate within CSV (same team and leader as Row 2)"));

        // Rows 5 and 6 are valid
        assertEquals("Jane Smith", result.validRows.get(1).leaderName);
        assertEquals("Code Titans", result.validRows.get(2).teamName);
    }

    @Test
    public void testExistingTeamConflictDetection() {
        List<Team> existingTeams = new ArrayList<>();
        Team t1 = new Team();
        t1.setId("team_1");
        t1.setTeamName("Apex Legends");
        t1.setLeaderName("Sarah Connor");
        existingTeams.add(t1);

        Team t2 = new Team();
        t2.setId("team_2");
        t2.setTeamName("Terminators");
        t2.setLeaderName("John Connor");
        t2.setDeleted(true); // Deleted team in event
        existingTeams.add(t2);

        String csv = "Team Name,Team Leader Name\n"
                + "Apex Legends,Sarah Connor\n"       // Row 2: Conflict with existing team
                + "apex legends,SARAH CONNOR\n"       // Row 3: In-CSV duplicate AND existing conflict
                + "Terminators,John Connor\n"         // Row 4: Matches deleted team -> ALLOWED
                + "CyberDyne,Miles Dyson\n";          // Row 5: Brand new team -> ALLOWED

        ParseResult result = CsvTeamParser.parse(csv, existingTeams);

        assertEquals(4, result.totalRows);
        assertEquals(2, result.validRows.size());
        assertEquals(2, result.errorRows.size());

        // Row 2 conflicts with existing event team
        CsvRow row2 = result.errorRows.get(0);
        assertEquals(2, row2.rowNumber);
        assertTrue(row2.errors.get(0).contains("already exists in this event"));

        // Row 3 flagged
        CsvRow row3 = result.errorRows.get(1);
        assertEquals(3, row3.rowNumber);

        // Row 4 (deleted in event) is valid
        assertEquals("Terminators", result.validRows.get(0).teamName);

        // Row 5 is valid
        assertEquals("CyberDyne", result.validRows.get(1).teamName);
    }

    @Test
    public void testEmptyAndMalformedInputs() {
        ParseResult r1 = CsvTeamParser.parse("", new ArrayList<>());
        assertEquals(0, r1.totalRows);
        assertFalse(r1.hasErrors());

        ParseResult r2 = CsvTeamParser.parse((String) null, new ArrayList<>());
        assertEquals(0, r2.totalRows);
        assertFalse(r2.hasErrors());

        String blankLines = "\n\n   \n\n";
        ParseResult r3 = CsvTeamParser.parse(blankLines, new ArrayList<>());
        assertEquals(0, r3.totalRows);
        assertFalse(r3.hasErrors());
    }

    @Test
    public void testBatchTeamNumberAllocationNoDuplicates() {
        List<Team> initialTeams = new ArrayList<>();
        Team t1 = new Team();
        t1.setTeamNumber("TM-101");
        initialTeams.add(t1);

        Team t2 = new Team();
        t2.setTeamNumber("TM-105");
        initialTeams.add(t2);

        java.util.Set<String> allocated = new java.util.HashSet<>();
        List<String> generatedBatch = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            int maxNum = 100;
            for (Team t : initialTeams) {
                if (t.getTeamNumber() != null && t.getTeamNumber().startsWith("TM-")) {
                    try {
                        int num = Integer.parseInt(t.getTeamNumber().substring(3).trim());
                        if (num > maxNum) maxNum = num;
                    } catch (Exception ignored) {}
                }
            }
            for (String s : allocated) {
                if (s != null && s.startsWith("TM-")) {
                    try {
                        int num = Integer.parseInt(s.substring(3).trim());
                        if (num > maxNum) maxNum = num;
                    } catch (Exception ignored) {}
                }
            }
            String next = "TM-" + (maxNum + 1);
            allocated.add(next);
            generatedBatch.add(next);
        }

        assertEquals("TM-106", generatedBatch.get(0));
        assertEquals("TM-107", generatedBatch.get(1));
        assertEquals("TM-108", generatedBatch.get(2));
        assertEquals("TM-109", generatedBatch.get(3));
        assertEquals("TM-110", generatedBatch.get(4));
        assertEquals(5, new java.util.HashSet<>(generatedBatch).size());
    }

    @Test
    public void testCredentialGenerationAndEncryptionSafety() {
        String teamName = "Byte Builders";
        String leaderName = "Alice Smith";
        String username = "bytebuilders4821";
        String tempPassword = com.example.judgex.data.FirebaseManager.generateSecureTemporaryPassword();

        // 1. Password must meet Firebase security standard
        assertTrue(tempPassword.length() >= 6);

        // 2. Encrypt password for Firestore storage
        String encrypted = com.example.judgex.utils.CryptoUtils.encrypt(tempPassword);
        assertNotEquals(tempPassword, encrypted);

        // 3. Team model only accepts and stores encrypted password
        Team team = new Team();
        team.setTeamName(teamName);
        team.setLeaderName(leaderName);
        team.setUsername(username);
        team.setEncryptedPassword(encrypted);

        assertEquals(encrypted, team.getEncryptedPassword());
        assertEquals(tempPassword, com.example.judgex.utils.CryptoUtils.decrypt(team.getEncryptedPassword()));

        // 4. TeamCredential object holds values for one-time display/export to admin
        com.example.judgex.admin.TeamManagerActivity.TeamCredential cred =
                new com.example.judgex.admin.TeamManagerActivity.TeamCredential(teamName, leaderName, username, tempPassword);
        assertEquals(teamName, cred.teamName);
        assertEquals(leaderName, cred.leaderName);
        assertEquals(username, cred.username);
        assertEquals(tempPassword, cred.temporaryPassword);
    }
}
