package com.example.judgex.utils;

import com.example.judgex.admin.TeamManagerActivity;
import com.example.judgex.models.Team;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Robust CSV parser and validator for bulk team creation in JudgeX.
 * Supports RFC-4180 quotes, delimiters, header detection, missing field detection,
 * and duplicate pair validation both within the CSV and against existing teams.
 */
public class CsvTeamParser {

    public static class CsvRow {
        public final int rowNumber;
        public final String rawLine;
        public final String teamName;
        public final String leaderName;
        public final List<String> errors = new ArrayList<>();

        public CsvRow(int rowNumber, String rawLine, String teamName, String leaderName) {
            this.rowNumber = rowNumber;
            this.rawLine = rawLine != null ? rawLine : "";
            this.teamName = teamName != null ? teamName.trim() : "";
            this.leaderName = leaderName != null ? leaderName.trim() : "";
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public void addError(String error) {
            if (error != null && !error.isEmpty()) {
                errors.add(error);
            }
        }
    }

    public static class ParseResult {
        public final List<CsvRow> allRows = new ArrayList<>();
        public final List<CsvRow> validRows = new ArrayList<>();
        public final List<CsvRow> errorRows = new ArrayList<>();
        public int totalRows = 0;

        public boolean hasErrors() {
            return !errorRows.isEmpty();
        }
    }

    /**
     * Parses CSV content from a String.
     */
    public static ParseResult parse(String csvContent, List<Team> existingTeams) {
        if (csvContent == null) {
            return new ParseResult();
        }
        return parse(new StringReader(csvContent), existingTeams);
    }

    /**
     * Parses CSV content from a Reader.
     */
    public static ParseResult parse(Reader reader, List<Team> existingTeams) {
        ParseResult result = new ParseResult();
        if (reader == null) {
            return result;
        }

        BufferedReader br = new BufferedReader(reader);
        List<String> rawLines = new ArrayList<>();
        String line;
        try {
            while ((line = br.readLine()) != null) {
                rawLines.add(line);
            }
        } catch (IOException e) {
            CsvRow errRow = new CsvRow(1, "", "", "");
            errRow.addError("Failed to read CSV stream: " + e.getMessage());
            result.allRows.add(errRow);
            result.errorRows.add(errRow);
            result.totalRows = 1;
            return result;
        }

        if (rawLines.isEmpty()) {
            return result;
        }

        // Determine delimiter from first non-empty line
        char delimiter = ',';
        for (String l : rawLines) {
            if (!l.trim().isEmpty()) {
                delimiter = detectDelimiter(l);
                break;
            }
        }

        int startLineIndex = 0;
        int teamColIndex = 0;
        int leaderColIndex = 1;

        // Find first non-empty line to check for header
        while (startLineIndex < rawLines.size() && rawLines.get(startLineIndex).trim().isEmpty()) {
            startLineIndex++;
        }

        if (startLineIndex < rawLines.size()) {
            List<String> firstTokens = parseTokens(rawLines.get(startLineIndex), delimiter);
            int detectedTeamCol = -1;
            int detectedLeaderCol = -1;

            for (int i = 0; i < firstTokens.size(); i++) {
                String tokenLower = firstTokens.get(i).toLowerCase(Locale.US).replaceAll("[_\\s]+", " ").trim();
                if (isTeamHeader(tokenLower) && detectedTeamCol == -1) {
                    detectedTeamCol = i;
                } else if (isLeaderHeader(tokenLower) && detectedLeaderCol == -1) {
                    detectedLeaderCol = i;
                }
            }

            // Both team and leader columns matched header pattern -> Consume header line
            if (detectedTeamCol != -1 && detectedLeaderCol != -1 && detectedTeamCol != detectedLeaderCol) {
                teamColIndex = detectedTeamCol;
                leaderColIndex = detectedLeaderCol;
                startLineIndex++; // skip header
            }
        }

        Map<String, Integer> seenInCsv = new HashMap<>();

        for (int i = startLineIndex; i < rawLines.size(); i++) {
            String currentLine = rawLines.get(i);
            int rowNumber = i + 1; // 1-based line number

            // Skip completely empty lines (e.g. at end of file)
            if (currentLine.trim().isEmpty()) {
                continue;
            }

            List<String> tokens = parseTokens(currentLine, delimiter);
            String teamName = teamColIndex < tokens.size() ? tokens.get(teamColIndex) : "";
            String leaderName = leaderColIndex < tokens.size() ? tokens.get(leaderColIndex) : "";

            CsvRow row = new CsvRow(rowNumber, currentLine, teamName, leaderName);

            // Validation 1: Missing Team Name
            if (row.teamName.isEmpty()) {
                row.addError("Row " + rowNumber + ": Team Name is required");
            }

            // Validation 2: Missing Team Leader Name
            if (row.leaderName.isEmpty()) {
                row.addError("Row " + rowNumber + ": Team Leader Name is required");
            }

            // Validation 3 & 4: Duplicate validation (only if both names are provided)
            if (!row.teamName.isEmpty() && !row.leaderName.isEmpty()) {
                String normTeam = TeamManagerActivity.normalizeTeamString(row.teamName);
                String normLeader = TeamManagerActivity.normalizeTeamString(row.leaderName);
                String duplicateKey = normTeam + ":::" + normLeader;

                // Check duplicate within the CSV itself
                if (seenInCsv.containsKey(duplicateKey)) {
                    int originalRow = seenInCsv.get(duplicateKey);
                    row.addError("Row " + rowNumber + ": Duplicate within CSV (same team and leader as Row " + originalRow + ")");
                } else {
                    seenInCsv.put(duplicateKey, rowNumber);
                }

                // Check duplicate against existing teams in the event
                if (TeamManagerActivity.isDuplicateTeam(existingTeams, row.teamName, row.leaderName)) {
                    row.addError("Row " + rowNumber + ": Team '" + row.teamName + "' with leader '" + row.leaderName + "' already exists in this event");
                }
            }

            result.allRows.add(row);
            result.totalRows++;
            if (row.isValid()) {
                result.validRows.add(row);
            } else {
                result.errorRows.add(row);
            }
        }

        return result;
    }

    public static char detectDelimiter(String sample) {
        if (sample == null) return ',';
        int commas = 0;
        int semicolons = 0;
        int tabs = 0;
        boolean inQuotes = false;
        for (int i = 0; i < sample.length(); i++) {
            char c = sample.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == ',') commas++;
                else if (c == ';') semicolons++;
                else if (c == '\t') tabs++;
            }
        }
        if (semicolons > commas && semicolons > tabs) return ';';
        if (tabs > commas && tabs > semicolons) return '\t';
        return ',';
    }

    private static boolean isTeamHeader(String header) {
        return header.equals("team name") || header.equals("team") || header.equals("teamname")
                || header.equals("project team") || header.equals("team title");
    }

    private static boolean isLeaderHeader(String header) {
        return header.equals("team leader name") || header.equals("team leader") || header.equals("leader name")
                || header.equals("leader") || header.equals("teamleader") || header.equals("lead");
    }

    public static List<String> parseTokens(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        if (line == null) return tokens;

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }
}
