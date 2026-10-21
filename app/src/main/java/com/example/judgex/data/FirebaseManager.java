package com.example.judgex.data;

import android.content.Context;

import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Organization;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseManager {
    private static FirebaseManager instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private User currentUserProfile;

    private FirebaseManager() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getDb() {
        return db;
    }

    public FirebaseUser getCurrentFirebaseUser() {
        return auth.getCurrentUser();
    }

    public User getCurrentUserProfile() {
        return currentUserProfile;
    }

    public void setCurrentUserProfile(User profile) {
        this.currentUserProfile = profile;
    }

    public boolean isUserLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public void signOut() {
        auth.signOut();
        currentUserProfile = null;
    }

    // --- Authentication & User Profile Operations ---

    public Task<AuthResult> signIn(String email, String password) {
        return auth.signInWithEmailAndPassword(email.trim(), password);
    }

    public Task<DocumentSnapshot> fetchUserProfile(String uid) {
        return db.collection(Constants.COLLECTION_USERS).document(uid).get();
    }

    public Task<Void> saveUserProfile(User user) {
        return db.collection(Constants.COLLECTION_USERS).document(user.getUid()).set(user);
    }

    public static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(java.util.Locale.US) : "";
    }

    /**
     * Creates a Judge user without signing out the currently logged-in Admin,
     * enforcing email normalization, cross-role uniqueness against Team Leaders,
     * and reusing existing judge profiles for multi-event assignments.
     */
    public Task<String> createJudgeAccount(Context context, String email, String password, String name, String panelId) {
        return createJudgeAccount(context, email, password, name, panelId, null);
    }

    public Task<String> createJudgeAccount(Context context, String email, String password, String name, String panelId, String eventId) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        final String normalizedEmail = normalizeEmail(email);

        if (normalizedEmail.isEmpty()) {
            tcs.setException(new IllegalArgumentException("Email address cannot be empty"));
            return tcs.getTask();
        }

        final String cleanPanelId = panelId != null ? panelId.trim() : "";
        final String cleanEventId = eventId != null ? eventId.trim() : "";

        // Check if a user with this email already exists before calling Firebase Auth
        db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("email", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                        User existingUser = userDoc.toObject(User.class);
                        if (existingUser != null) {
                            existingUser.setUid(userDoc.getId());

                            // Priority 5: Enforce role conflict rejection
                            if (existingUser.isTeamLeader()) {
                                tcs.setException(new IllegalStateException(
                                        "This email is already registered as a Judge or Team Leader and cannot be assigned this role."));
                                return;
                            }

                            if (existingUser.isJudge()) {
                                // Priority 5 Requirement 9: Preserve legitimate multi-event judge participation
                                if (!cleanEventId.isEmpty()) {
                                    existingUser.setPanelForEvent(cleanEventId, cleanPanelId);
                                }
                                if (!cleanPanelId.isEmpty()) {
                                    existingUser.setAssignedPanelId(cleanPanelId);
                                }

                                db.collection(Constants.COLLECTION_USERS).document(existingUser.getUid())
                                        .set(existingUser, SetOptions.merge())
                                        .addOnSuccessListener(aVoid -> {
                                            if (!cleanPanelId.isEmpty()) {
                                                Map<String, Object> mergeData = new HashMap<>();
                                                mergeData.put("judgeIds", FieldValue.arrayUnion(existingUser.getUid()));
                                                db.collection(Constants.COLLECTION_PANELS).document(cleanPanelId)
                                                        .set(mergeData, SetOptions.merge())
                                                        .addOnCompleteListener(task -> tcs.setResult(existingUser.getUid()));
                                            } else {
                                                tcs.setResult(existingUser.getUid());
                                            }
                                        })
                                        .addOnFailureListener(tcs::setException);
                                return;
                            }
                        }
                    }

                    // No existing user with this email: Provision new Firebase Auth account
                    provisionNewJudgeAuthAccount(context, normalizedEmail, password, name, cleanPanelId, cleanEventId, tcs);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    private void provisionNewJudgeAuthAccount(Context context, String normalizedEmail, String password, 
                                              String name, String cleanPanelId, String cleanEventId, 
                                              TaskCompletionSource<String> tcs) {
        String uniqueAppName = "JudgeCreator_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        try {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            FirebaseApp secondaryApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, uniqueAppName);
            FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

            secondaryAuth.createUserWithEmailAndPassword(normalizedEmail, password)
                    .addOnSuccessListener(authResult -> {
                        if (authResult.getUser() == null) {
                            cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                            tcs.setException(new Exception("Failed to create judge authentication record"));
                            return;
                        }

                        String newUid = authResult.getUser().getUid();
                        User judgeUser = new User(newUid, normalizedEmail, name.trim(), User.ROLE_JUDGE, cleanPanelId, System.currentTimeMillis());
                        if (!cleanEventId.isEmpty()) {
                            judgeUser.setPanelForEvent(cleanEventId, cleanPanelId);
                        }

                        // Attach organization ID from current admin if available
                        if (currentUserProfile != null && currentUserProfile.getOrganizationId() != null) {
                            judgeUser.setOrganizationId(currentUserProfile.getOrganizationId());
                            judgeUser.setOrganizationName(currentUserProfile.getOrganizationName());
                        }

                        // Save judge user profile in Firestore
                        db.collection(Constants.COLLECTION_USERS).document(newUid).set(judgeUser)
                                .addOnSuccessListener(aVoid -> {
                                    if (!cleanPanelId.isEmpty()) {
                                        Map<String, Object> mergeData = new HashMap<>();
                                        mergeData.put("judgeIds", FieldValue.arrayUnion(newUid));
                                        db.collection(Constants.COLLECTION_PANELS).document(cleanPanelId)
                                                .set(mergeData, SetOptions.merge())
                                                .addOnCompleteListener(panelTask -> {
                                                    cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                                                    tcs.setResult(newUid);
                                                });
                                    } else {
                                        cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                                        tcs.setResult(newUid);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                                    tcs.setException(e);
                                });
                    })
                    .addOnFailureListener(e -> {
                        cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                        tcs.setException(e);
                    });
        } catch (Exception e) {
            tcs.setException(e);
        }
    }

    /**
     * Creates a Team Leader user without signing out the currently logged-in Admin,
     * enforcing email normalization and pre-checking for cross-role collisions with Judges.
     */
    public Task<String> createTeamLeaderAccount(Context context, String email, String password, String name, String teamId) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        final String normalizedEmail = normalizeEmail(email);

        if (normalizedEmail.isEmpty()) {
            tcs.setException(new IllegalArgumentException("Email address cannot be empty"));
            return tcs.getTask();
        }

        // Priority 5: Pre-check role conflicts before invoking Firebase Auth
        db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("email", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                        User existingUser = userDoc.toObject(User.class);
                        if (existingUser != null) {
                            if (existingUser.isJudge()) {
                                tcs.setException(new IllegalStateException(
                                        "This email is already registered as a Judge or Team Leader and cannot be assigned this role."));
                                return;
                            }
                            if (existingUser.isTeamLeader()) {
                                tcs.setException(new IllegalStateException(
                                        "This email is already registered as a Judge or Team Leader and cannot be assigned this role."));
                                return;
                            }
                        }
                    }

                    // Safe to proceed with Firebase Auth creation
                    provisionNewTeamLeaderAuthAccount(context, normalizedEmail, password, name, teamId, tcs);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    private void provisionNewTeamLeaderAuthAccount(Context context, String normalizedEmail, String password, 
                                                   String name, String teamId, TaskCompletionSource<String> tcs) {
        String uniqueAppName = "LeaderCreator_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        try {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            FirebaseApp secondaryApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, uniqueAppName);
            FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

            secondaryAuth.createUserWithEmailAndPassword(normalizedEmail, password)
                    .addOnSuccessListener(authResult -> {
                        if (authResult.getUser() == null) {
                            cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                            tcs.setException(new Exception("Failed to create leader authentication record"));
                            return;
                        }

                        String newUid = authResult.getUser().getUid();
                        User leaderUser = new User(newUid, normalizedEmail, name.trim(), User.ROLE_TEAM_LEADER, "", System.currentTimeMillis());
                        leaderUser.setAssignedTeamId(teamId);

                        if (currentUserProfile != null && currentUserProfile.getOrganizationId() != null) {
                            leaderUser.setOrganizationId(currentUserProfile.getOrganizationId());
                            leaderUser.setOrganizationName(currentUserProfile.getOrganizationName());
                        }

                        // Save leader user profile in Firestore
                        db.collection(Constants.COLLECTION_USERS).document(newUid).set(leaderUser)
                                .addOnSuccessListener(aVoid -> {
                                    if (teamId != null && !teamId.isEmpty()) {
                                        db.collection(Constants.COLLECTION_TEAMS).document(teamId)
                                                .update("leaderUid", newUid)
                                                .addOnCompleteListener(teamTask -> {
                                                    cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                                                    tcs.setResult(newUid);
                                                });
                                    } else {
                                        cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                                        tcs.setResult(newUid);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                                    tcs.setException(e);
                                });
                    })
                    .addOnFailureListener(e -> {
                        cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                        tcs.setException(e);
                    });
        } catch (Exception e) {
            tcs.setException(e);
        }
    }

    private void cleanUpSecondaryApp(FirebaseApp app, FirebaseAuth auth) {
        try {
            if (auth != null) auth.signOut();
        } catch (Exception ignored) {}
        try {
            if (app != null) app.delete();
        } catch (Exception ignored) {}
    }

    // --- Event Operations ---

    public CollectionReference getEventsCollection() {
        return db.collection(Constants.COLLECTION_EVENTS);
    }

    public Task<DocumentReference> createEvent(Event event) {
        DocumentReference docRef = db.collection(Constants.COLLECTION_EVENTS).document();
        event.setId(docRef.getId());
        return docRef.set(event).continueWith(task -> docRef);
    }

    public Task<Void> updateEvent(Event event) {
        return db.collection(Constants.COLLECTION_EVENTS).document(event.getId()).set(event, SetOptions.merge());
    }

    public Task<Void> setEventLockStatus(String eventId, boolean isLocked) {
        return db.collection(Constants.COLLECTION_EVENTS).document(eventId).update(
                "locked", isLocked,
                "status", isLocked ? Event.STATUS_LOCKED : Event.STATUS_ACTIVE
        );
    }

    public Task<DocumentSnapshot> getEvent(String eventId) {
        return db.collection(Constants.COLLECTION_EVENTS).document(eventId).get();
    }

    public Task<Void> deleteEvent(String eventId) {
        return db.collection(Constants.COLLECTION_EVENTS).document(eventId).delete();
    }

    // --- Panel Operations ---

    public CollectionReference getPanelsCollection() {
        return db.collection(Constants.COLLECTION_PANELS);
    }

    public Task<DocumentReference> createPanel(Panel panel) {
        DocumentReference docRef = db.collection(Constants.COLLECTION_PANELS).document();
        panel.setId(docRef.getId());
        return docRef.set(panel).continueWith(task -> docRef);
    }

    public Task<Void> updatePanel(Panel panel) {
        return db.collection(Constants.COLLECTION_PANELS).document(panel.getId()).set(panel, SetOptions.merge());
    }

    public Task<QuerySnapshot> getPanelsForEvent(String eventId) {
        return db.collection(Constants.COLLECTION_PANELS)
                .whereEqualTo("eventId", eventId)
                .get();
    }

    public Task<DocumentSnapshot> getPanel(String panelId) {
        return db.collection(Constants.COLLECTION_PANELS).document(panelId).get();
    }

    // --- Judge Management Operations ---

    public Task<QuerySnapshot> getAllJudges() {
        return db.collection(Constants.COLLECTION_USERS).get();
    }

    public Task<Void> assignJudgeToPanel(String judgeUid, String panelId) {
        return assignJudgeToPanel(judgeUid, null, panelId);
    }

    public Task<Void> assignJudgeToPanel(String judgeUid, String eventId, String panelId) {
        String cleanPanelId = panelId != null ? panelId.trim() : "";
        return db.collection(Constants.COLLECTION_USERS).document(judgeUid)
                .get()
                .continueWithTask(task -> {
                    String oldPanelId = "";
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        oldPanelId = task.getResult().getString("assignedPanelId");
                    }

                    WriteBatch batch = db.batch();

                    // 1. Update user assignedPanelId and eventPanelAssignments
                    DocumentReference userRef = db.collection(Constants.COLLECTION_USERS).document(judgeUid);
                    batch.update(userRef, "assignedPanelId", cleanPanelId);
                    if (eventId != null && !eventId.isEmpty()) {
                        batch.update(userRef, "eventPanelAssignments." + eventId, cleanPanelId);
                    }

                    // 2. Remove from old panel if changing
                    if (oldPanelId != null && !oldPanelId.isEmpty() && !oldPanelId.equals(cleanPanelId)) {
                        DocumentReference oldPanelRef = db.collection(Constants.COLLECTION_PANELS).document(oldPanelId);
                        batch.update(oldPanelRef, "judgeIds", FieldValue.arrayRemove(judgeUid));
                    }

                    // 3. Add to new panel if specified
                    if (!cleanPanelId.isEmpty()) {
                        DocumentReference newPanelRef = db.collection(Constants.COLLECTION_PANELS).document(cleanPanelId);
                        batch.update(newPanelRef, "judgeIds", FieldValue.arrayUnion(judgeUid));
                    }

                    return batch.commit();
                });
    }

    /**
     * Atomically batch-assigns multiple judges across panels for an event.
     */
    public Task<Void> batchAssignJudges(Map<String, String> judgeToPanelMap, List<Panel> allPanels, String eventId) {
        WriteBatch batch = db.batch();

        // 1. Clear judges in panels for this event first
        for (Panel p : allPanels) {
            DocumentReference pRef = db.collection(Constants.COLLECTION_PANELS).document(p.getId());
            batch.update(pRef, "judgeIds", new ArrayList<String>());
        }

        // 2. Add each judge to assigned panel and update user document
        Map<String, List<String>> panelToJudges = new HashMap<>();
        for (Map.Entry<String, String> entry : judgeToPanelMap.entrySet()) {
            String judgeId = entry.getKey();
            String panelId = entry.getValue();

            if (panelId != null && !panelId.isEmpty()) {
                if (!panelToJudges.containsKey(panelId)) {
                    panelToJudges.put(panelId, new ArrayList<>());
                }
                panelToJudges.get(panelId).add(judgeId);

                DocumentReference userRef = db.collection(Constants.COLLECTION_USERS).document(judgeId);
                batch.update(userRef, "assignedPanelId", panelId);
                if (eventId != null && !eventId.isEmpty()) {
                    batch.update(userRef, "eventPanelAssignments." + eventId, panelId);
                }
            }
        }

        for (Map.Entry<String, List<String>> pEntry : panelToJudges.entrySet()) {
            DocumentReference pRef = db.collection(Constants.COLLECTION_PANELS).document(pEntry.getKey());
            batch.update(pRef, "judgeIds", pEntry.getValue());
        }

        return batch.commit();
    }

    /**
     * Atomically batch-assigns multiple teams across panels.
     */
    public Task<Void> batchAssignTeams(Map<String, String> teamToPanelMap, List<Panel> allPanels) {
        WriteBatch batch = db.batch();

        // Clear teamIds in panels
        for (Panel p : allPanels) {
            DocumentReference pRef = db.collection(Constants.COLLECTION_PANELS).document(p.getId());
            batch.update(pRef, "teamIds", new ArrayList<String>());
        }

        Map<String, List<String>> panelToTeams = new HashMap<>();
        for (Map.Entry<String, String> entry : teamToPanelMap.entrySet()) {
            String teamId = entry.getKey();
            String panelId = entry.getValue();

            if (panelId != null && !panelId.isEmpty()) {
                if (!panelToTeams.containsKey(panelId)) {
                    panelToTeams.put(panelId, new ArrayList<>());
                }
                panelToTeams.get(panelId).add(teamId);

                DocumentReference teamRef = db.collection(Constants.COLLECTION_TEAMS).document(teamId);
                batch.update(teamRef, "panelId", panelId);
            }
        }

        for (Map.Entry<String, List<String>> pEntry : panelToTeams.entrySet()) {
            DocumentReference pRef = db.collection(Constants.COLLECTION_PANELS).document(pEntry.getKey());
            batch.update(pRef, "teamIds", pEntry.getValue());
        }

        return batch.commit();
    }

    // --- Criteria Operations ---

    public CollectionReference getCriteriaCollection() {
        return db.collection(Constants.COLLECTION_CRITERIA);
    }

    public Task<DocumentReference> createCriterion(Criterion criterion) {
        DocumentReference docRef = db.collection(Constants.COLLECTION_CRITERIA).document();
        criterion.setId(docRef.getId());
        return docRef.set(criterion).continueWith(task -> docRef);
    }

    public Task<Void> updateCriterion(Criterion criterion) {
        return db.collection(Constants.COLLECTION_CRITERIA).document(criterion.getId()).set(criterion, SetOptions.merge());
    }

    public Task<Void> deleteCriterion(String criterionId) {
        return db.collection(Constants.COLLECTION_CRITERIA).document(criterionId).delete();
    }

    public Task<QuerySnapshot> getCriteriaForEvent(String eventId) {
        return db.collection(Constants.COLLECTION_CRITERIA)
                .whereEqualTo("eventId", eventId)
                .get();
    }

    // --- Team Operations ---

    public CollectionReference getTeamsCollection() {
        return db.collection(Constants.COLLECTION_TEAMS);
    }

    public Task<DocumentReference> createTeam(Team team) {
        DocumentReference docRef = db.collection(Constants.COLLECTION_TEAMS).document();
        team.setId(docRef.getId());
        return docRef.set(team).continueWithTask(task -> {
            if (team.getPanelId() != null && !team.getPanelId().isEmpty()) {
                return db.collection(Constants.COLLECTION_PANELS).document(team.getPanelId())
                        .update("teamIds", FieldValue.arrayUnion(team.getId()))
                        .continueWith(t -> docRef);
            }
            return Tasks.forResult(docRef);
        });
    }

    public Task<Void> updateTeam(Team team) {
        return db.collection(Constants.COLLECTION_TEAMS).document(team.getId()).set(team, SetOptions.merge());
    }

    public Task<QuerySnapshot> getTeamsForEvent(String eventId) {
        return db.collection(Constants.COLLECTION_TEAMS)
                .whereEqualTo("eventId", eventId)
                .get();
    }

    public Task<QuerySnapshot> getTeamsForPanel(String panelId) {
        return db.collection(Constants.COLLECTION_TEAMS)
                .whereEqualTo("panelId", panelId)
                .get();
    }

    public Task<DocumentSnapshot> getTeam(String teamId) {
        return db.collection(Constants.COLLECTION_TEAMS).document(teamId).get();
    }

    public Task<Void> deleteTeam(String teamId) {
        return db.collection(Constants.COLLECTION_TEAMS).document(teamId).delete();
    }

    public Task<QuerySnapshot> getTeamForLeader(String leaderUid) {
        return db.collection(Constants.COLLECTION_TEAMS)
                .whereEqualTo("leaderUid", leaderUid)
                .limit(1)
                .get();
    }

    // --- Evaluation Operations ---

    public CollectionReference getEvaluationsCollection() {
        return db.collection(Constants.COLLECTION_EVALUATIONS);
    }

    public Task<DocumentSnapshot> checkExistingEvaluation(String eventId, String teamId, String judgeId) {
        String evalId = Evaluation.generateEvaluationId(eventId, teamId, judgeId);
        return db.collection(Constants.COLLECTION_EVALUATIONS).document(evalId).get();
    }

    /**
     * Submits an evaluation atomically with duplicate checking.
     */
    public Task<Void> submitEvaluation(Evaluation evaluation) {
        String evalId = Evaluation.generateEvaluationId(
                evaluation.getEventId(), 
                evaluation.getTeamId(), 
                evaluation.getJudgeId()
        );
        evaluation.setId(evalId);

        DocumentReference evalRef = db.collection(Constants.COLLECTION_EVALUATIONS).document(evalId);
        
        // Execute atomic transaction to prevent duplicates and race conditions
        return db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(evalRef);
            if (snapshot.exists()) {
                Evaluation existing = snapshot.toObject(Evaluation.class);
                if (existing != null && existing.isSubmitted()) {
                    throw new IllegalStateException("An evaluation for this team by you has already been submitted and cannot be changed.");
                }
            }
            transaction.set(evalRef, evaluation);
            return null;
        });
    }

    public Task<QuerySnapshot> getEvaluationsForTeam(String teamId) {
        return db.collection(Constants.COLLECTION_EVALUATIONS)
                .whereEqualTo("teamId", teamId)
                .get();
    }

    public Task<QuerySnapshot> getEvaluationsForEvent(String eventId) {
        return db.collection(Constants.COLLECTION_EVALUATIONS)
                .whereEqualTo("eventId", eventId)
                .get();
    }

    public Task<QuerySnapshot> getEvaluationsByJudge(String judgeId) {
        return db.collection(Constants.COLLECTION_EVALUATIONS)
                .whereEqualTo("judgeId", judgeId)
                .get();
    }

    public CollectionReference getOrganizationsCollection() {
        return db.collection(Constants.COLLECTION_ORGANIZATIONS);
    }

    public Task<DocumentSnapshot> getOrganization(String orgId) {
        return db.collection(Constants.COLLECTION_ORGANIZATIONS).document(orgId).get();
    }

    public Task<Void> saveOrganization(Organization organization) {
        return db.collection(Constants.COLLECTION_ORGANIZATIONS).document(organization.getId()).set(organization);
    }

    public Task<QuerySnapshot> getEventsForOrganization(String orgId) {
        if (orgId != null && !orgId.isEmpty()) {
            return db.collection(Constants.COLLECTION_EVENTS)
                    .whereEqualTo("organizationId", orgId)
                    .get();
        }
        return db.collection(Constants.COLLECTION_EVENTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
    }
}
