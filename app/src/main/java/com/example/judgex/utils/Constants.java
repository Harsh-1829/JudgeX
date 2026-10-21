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
}
