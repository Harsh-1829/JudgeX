package com.example.judgex.utils;

public class Constants {
    // Firestore Collections
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_EVENTS = "events";
    public static final String COLLECTION_PANELS = "panels";
    public static final String COLLECTION_CRITERIA = "criteria";
    public static final String COLLECTION_TEAMS = "teams";
    public static final String COLLECTION_EVALUATIONS = "evaluations";
    public static final String COLLECTION_ORGANIZATIONS = "organizations";

    // Intent Extra Keys
    public static final String EXTRA_EVENT = "extra_event";
    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_PANEL = "extra_panel";
    public static final String EXTRA_PANEL_ID = "extra_panel_id";
    public static final String EXTRA_TEAM = "extra_team";
    public static final String EXTRA_TEAM_ID = "extra_team_id";
    public static final String EXTRA_JUDGE_ID = "extra_judge_id";
    public static final String EXTRA_EVALUATION = "extra_evaluation";
    public static final String EXTRA_CRITERION = "extra_criterion";

    // Shared Preferences
    public static final String PREF_NAME = "judgex_prefs";
    public static final String PREF_ACTIVE_EVENT_ID = "active_event_id";
    public static final String PREF_USER_ROLE = "user_role";

    // Username & Synthetic Auth Identifier
    public static final String SYNTHETIC_EMAIL_DOMAIN = "@team.judgex.internal";

    public static String buildSyntheticEmail(String username) {
        if (username == null) return "";
        String trimmed = username.trim().toLowerCase(java.util.Locale.US);
        if (trimmed.endsWith(SYNTHETIC_EMAIL_DOMAIN)) {
            return trimmed;
        }
        return trimmed + SYNTHETIC_EMAIL_DOMAIN;
    }

    public static String buildTeamSyntheticEmail(String username) {
        return buildSyntheticEmail(username);
    }

    public static boolean isSyntheticEmail(String email) {
        if (email == null) return false;
        String lower = email.toLowerCase(java.util.Locale.US);
        return lower.endsWith(SYNTHETIC_EMAIL_DOMAIN) || lower.endsWith(".judgex.internal");
    }

    public static String extractUsernameFromSyntheticEmail(String email) {
        if (email == null) return "";
        String trimmed = email.trim().toLowerCase(java.util.Locale.US);
        if (trimmed.endsWith(SYNTHETIC_EMAIL_DOMAIN)) {
            return trimmed.substring(0, trimmed.length() - SYNTHETIC_EMAIL_DOMAIN.length());
        }
        if (trimmed.contains("@")) {
            return trimmed.substring(0, trimmed.indexOf('@'));
        }
        return trimmed;
    }
}
