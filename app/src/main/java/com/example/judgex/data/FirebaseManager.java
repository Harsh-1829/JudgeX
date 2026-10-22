package com.example.judgex.data;

import android.content.Context;
import android.text.TextUtils;

import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.Organization;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamMember;
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

    public FirebaseUser getCurrentUser() {
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

                            if (existingUser.isDeleted()) {
                                // Tombstoned judge account: Reactivate with active status and new assignments
                                existingUser.setDeleted(false);
                                existingUser.setStatus(User.STATUS_ACTIVE);
                                existingUser.setActive(true);
                                existingUser.setName(name.trim());
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
                        if (e instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            String msg = "The email address '" + normalizedEmail + "' is already in use in Firebase Authentication. " +
                                    "If this was a previously deleted test judge, please delete the authentication record in Firebase Console (Authentication > Users) " +
                                    "or via Cloud Functions before re-registering.";
                            tcs.setException(new Exception(msg, e));
                        } else {
                            tcs.setException(e);
                        }
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

    /**
     * Registers a new organization and provisions its initial College Admin user account:
     * 1. Validates required fields and normalizes administrator email.
     * 2. Creates the Firebase Authentication account.
     * 3. Prepares Organization and User profile models with tenant linkage (orgId, adminUid).
     * 4. Commits both Organization and Admin User documents atomically in Firestore via WriteBatch.
     * 5. If Firestore commit fails, rolls back the Firebase Authentication account to prevent orphaned credentials.
     */
    public Task<User> registerOrganizationAndAdmin(String orgName, String adminName, String rawEmail, String password) {
        return registerOrganizationAndAdmin(orgName, adminName, rawEmail, "", password);
    }

    public Task<User> registerOrganizationAndAdmin(String orgName, String adminName, String rawEmail, String phone, String password) {
        TaskCompletionSource<User> tcs = new TaskCompletionSource<>();
        final String normalizedEmail = normalizeEmail(rawEmail);

        if (TextUtils.isEmpty(orgName) || orgName.trim().length() < 3) {
            tcs.setException(new IllegalArgumentException("Organization name must be at least 3 characters."));
            return tcs.getTask();
        }
        if (TextUtils.isEmpty(adminName) || adminName.trim().length() < 2) {
            tcs.setException(new IllegalArgumentException("Administrator name must be at least 2 characters."));
            return tcs.getTask();
        }
        if (TextUtils.isEmpty(normalizedEmail) || !android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            tcs.setException(new IllegalArgumentException("A valid official email address is required."));
            return tcs.getTask();
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            tcs.setException(new IllegalArgumentException("Password must be at least 6 characters."));
            return tcs.getTask();
        }

        // 1. Create Firebase Auth account
        auth.createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        tcs.setException(new IllegalStateException("Failed to initialize Firebase Authentication user session."));
                        return;
                    }

                    String uid = firebaseUser.getUid();
                    String cleanOrgName = orgName.trim();
                    String cleanAdminName = adminName.trim();
                    String cleanPhone = phone != null ? phone.trim() : "";
                    String orgId = "org_" + System.currentTimeMillis();

                    Organization org = new Organization(orgId, cleanOrgName, uid, normalizedEmail, cleanPhone);
                    org.setStatus(Organization.STATUS_ACTIVE);
                    org.setCreatedAt(System.currentTimeMillis());

                    User adminUser = new User();
                    adminUser.setUid(uid);
                    adminUser.setEmail(normalizedEmail);
                    adminUser.setName(cleanAdminName);
                    adminUser.setRole(User.ROLE_ADMIN);
                    adminUser.setOrganizationId(orgId);
                    adminUser.setOrganizationName(cleanOrgName);
                    adminUser.setStatus(User.STATUS_ACTIVE);
                    adminUser.setActive(true);
                    adminUser.setCreatedAt(System.currentTimeMillis());

                    // 2. Commit organization first so security rules can verify organization ownership for admin role creation
                    DocumentReference orgRef = db.collection(Constants.COLLECTION_ORGANIZATIONS).document(orgId);
                    DocumentReference userRef = db.collection(Constants.COLLECTION_USERS).document(uid);

                    orgRef.set(org)
                            .addOnSuccessListener(aVoid -> {
                                userRef.set(adminUser)
                                        .addOnSuccessListener(uVoid -> {
                                            currentUserProfile = adminUser;
                                            tcs.setResult(adminUser);
                                        })
                                        .addOnFailureListener(userErr -> {
                                            // Rollback organization and auth user on failure
                                            orgRef.delete();
                                            firebaseUser.delete().addOnCompleteListener(deleteTask -> {
                                                auth.signOut();
                                                currentUserProfile = null;
                                                tcs.setException(userErr);
                                            });
                                        });
                            })
                            .addOnFailureListener(orgErr -> {
                                // Rollback auth user on failure
                                firebaseUser.delete().addOnCompleteListener(deleteTask -> {
                                    auth.signOut();
                                    currentUserProfile = null;
                                    tcs.setException(orgErr);
                                });
                            });
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
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

    public static class DeleteJudgeResult {
        public final String judgeUid;
        public final String judgeName;
        public final String judgeEmail;
        public final boolean success;
        public final boolean evaluationsPreserved;
        public final int evaluationCount;
        public final boolean profileDeleted;
        public final boolean authRequiresBackend;
        public final String message;

        public DeleteJudgeResult(String judgeUid, String judgeName, String judgeEmail,
                                 boolean success, boolean evaluationsPreserved, int evaluationCount,
                                 boolean profileDeleted, boolean authRequiresBackend, String message) {
            this.judgeUid = judgeUid;
            this.judgeName = judgeName;
            this.judgeEmail = judgeEmail;
            this.success = success;
            this.evaluationsPreserved = evaluationsPreserved;
            this.evaluationCount = evaluationCount;
            this.profileDeleted = profileDeleted;
            this.authRequiresBackend = authRequiresBackend;
            this.message = message;
        }
    }

    /**
     * Permanently deletes a judge account or safely tombtones it if evaluation history exists:
     * 1. Unassigns the judge from all panel assignments in Firestore.
     * 2. Checks for historical submitted evaluations.
     *    - If evaluations exist: Preserves them for leaderboard auditability while tombstoning the user
     *      profile (status=DELETED, isActive=false) and clearing panel mappings.
     *    - If no evaluations exist (clean test judge): Permanently deletes the Firestore user document.
     * 3. Explains clearly that client-side Android SDK cannot delete Firebase Authentication records
     *    belonging to other users without a trusted backend service (Firebase Admin SDK / Cloud Function).
     */
    public Task<DeleteJudgeResult> deleteJudgeAccount(User judge) {
        TaskCompletionSource<DeleteJudgeResult> tcs = new TaskCompletionSource<>();

        if (judge == null || judge.getUid() == null || judge.getUid().isEmpty()) {
            tcs.setException(new IllegalArgumentException("Invalid judge record"));
            return tcs.getTask();
        }

        final String judgeUid = judge.getUid();
        final String judgeName = judge.getName() != null ? judge.getName() : "Judge";
        final String judgeEmail = judge.getEmail() != null ? judge.getEmail() : "";

        // Step 1: Check for historical evaluations
        db.collection(Constants.COLLECTION_EVALUATIONS)
                .whereEqualTo("judgeId", judgeUid)
                .get()
                .addOnSuccessListener(evalSnap -> {
                    final int evalCount = (evalSnap != null) ? evalSnap.size() : 0;
                    final boolean hasEvaluations = evalCount > 0;

                    // Step 2: Query panels that contain this judge in judgeIds
                    db.collection(Constants.COLLECTION_PANELS)
                            .whereArrayContains("judgeIds", judgeUid)
                            .get()
                            .addOnSuccessListener(panelSnap -> {
                                WriteBatch batch = db.batch();

                                if (panelSnap != null && !panelSnap.isEmpty()) {
                                    for (DocumentSnapshot pDoc : panelSnap.getDocuments()) {
                                        batch.update(pDoc.getReference(), "judgeIds", FieldValue.arrayRemove(judgeUid));
                                    }
                                }

                                if (judge.getAssignedPanelId() != null && !judge.getAssignedPanelId().isEmpty()) {
                                    DocumentReference assignedPanelRef = db.collection(Constants.COLLECTION_PANELS).document(judge.getAssignedPanelId());
                                    batch.update(assignedPanelRef, "judgeIds", FieldValue.arrayRemove(judgeUid));
                                }

                                DocumentReference userRef = db.collection(Constants.COLLECTION_USERS).document(judgeUid);

                                if (hasEvaluations) {
                                    // Preserve historical evaluations: tombstone profile
                                    judge.setDeleted(true);
                                    judge.setStatus(User.STATUS_DELETED);
                                    judge.setActive(false);
                                    judge.setAssignedPanelId("");
                                    if (judge.getEventPanelAssignments() != null) {
                                        judge.getEventPanelAssignments().clear();
                                    }
                                    batch.set(userRef, judge, SetOptions.merge());
                                } else {
                                    // Clean test judge with no evaluations: delete user document completely
                                    batch.delete(userRef);
                                }

                                batch.commit()
                                        .addOnSuccessListener(aVoid -> {
                                            String outcomeMsg = hasEvaluations
                                                    ? "Judge profile unassigned and removed from active lists. " + evalCount + " historical evaluation(s) preserved for competition scoring."
                                                    : "Judge profile and panel assignments completely deleted.";
                                            tcs.setResult(new DeleteJudgeResult(
                                                    judgeUid, judgeName, judgeEmail,
                                                    true, hasEvaluations, evalCount,
                                                    !hasEvaluations, true, outcomeMsg
                                            ));
                                        })
                                        .addOnFailureListener(tcs::setException);
                            })
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    /**
     * Batch deletes multiple judge accounts safely.
     */
    public Task<List<DeleteJudgeResult>> deleteJudgeAccounts(List<User> judges) {
        if (judges == null || judges.isEmpty()) {
            return Tasks.forResult(new ArrayList<DeleteJudgeResult>());
        }

        List<Task<DeleteJudgeResult>> tasks = new ArrayList<>();
        for (User j : judges) {
            tasks.add(deleteJudgeAccount(j));
        }

        return Tasks.whenAllSuccess(tasks);
    }

    // --- Team Leader & Registration Operations ---

    public CollectionReference getRegistrationsCollection() {
        return db.collection(Constants.COLLECTION_REGISTRATIONS);
    }

    /**
     * Registers a new Team Leader:
     * 1. Creates Firebase Auth account with email and password.
     * 2. Sets role as User.ROLE_TEAM_LEADER securely in Firestore.
     * 3. Creates default team roster with leader as first member.
     * 4. Batches user profile and team initialization atomically.
     */
    public Task<User> registerTeamLeaderAccount(String name, String rawEmail, String password,
                                               String orgId, String orgName, String dept,
                                               String rollNo, String phone, String defaultTeamName) {
        TaskCompletionSource<User> tcs = new TaskCompletionSource<>();
        final String normalizedEmail = normalizeEmail(rawEmail);

        if (TextUtils.isEmpty(name) || name.trim().length() < 2) {
            tcs.setException(new IllegalArgumentException("Leader name must be at least 2 characters."));
            return tcs.getTask();
        }
        if (TextUtils.isEmpty(normalizedEmail) || !android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            tcs.setException(new IllegalArgumentException("A valid email address is required."));
            return tcs.getTask();
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            tcs.setException(new IllegalArgumentException("Password must be at least 6 characters."));
            return tcs.getTask();
        }

        // Check if email already registered before calling Auth
        db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("email", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        tcs.setException(new IllegalStateException("An account with this email address already exists."));
                        return;
                    }

                    auth.createUserWithEmailAndPassword(normalizedEmail, password)
                            .addOnSuccessListener(authResult -> {
                                FirebaseUser firebaseUser = authResult.getUser();
                                if (firebaseUser == null) {
                                    tcs.setException(new IllegalStateException("Failed to initialize authentication session."));
                                    return;
                                }

                                String uid = firebaseUser.getUid();
                                String teamId = db.collection(Constants.COLLECTION_TEAMS).document().getId();

                                // Prepare User
                                User leaderUser = new User(uid, name, normalizedEmail, User.ROLE_TEAM_LEADER);
                                leaderUser.setOrganizationId(orgId);
                                leaderUser.setOrganizationName(orgName != null && !orgName.isEmpty() ? orgName : "Institution");
                                leaderUser.setDepartment(dept);
                                leaderUser.setRollNumber(rollNo);
                                leaderUser.setPhone(phone);
                                leaderUser.setAssignedTeamId(teamId);
                                leaderUser.setStatus(User.STATUS_ACTIVE);
                                leaderUser.setActive(true);

                                // Prepare Initial Team
                                String cleanTeamName = !TextUtils.isEmpty(defaultTeamName) ? defaultTeamName.trim() : (name + "'s Team");
                                Team initialTeam = new Team();
                                initialTeam.setId(teamId);
                                initialTeam.setTeamName(cleanTeamName);
                                initialTeam.setTeamNumber("TM-" + (int) (100 + Math.random() * 900));
                                initialTeam.setLeaderName(name);
                                initialTeam.setLeaderEmail(normalizedEmail);
                                initialTeam.setLeaderUid(uid);
                                initialTeam.setOrganizationId(orgId);
                                initialTeam.setProjectTitle("Project " + cleanTeamName);
                                initialTeam.setCategory("General");

                                TeamMember leaderMember = new TeamMember(
                                        java.util.UUID.randomUUID().toString(),
                                        name, normalizedEmail, rollNo, dept, "Undergraduate", phone, true
                                );
                                initialTeam.addMember(leaderMember);

                                WriteBatch batch = db.batch();
                                batch.set(db.collection(Constants.COLLECTION_USERS).document(uid), leaderUser);
                                batch.set(db.collection(Constants.COLLECTION_TEAMS).document(teamId), initialTeam);

                                batch.commit()
                                        .addOnSuccessListener(aVoid -> {
                                            currentUserProfile = leaderUser;
                                            tcs.setResult(leaderUser);
                                        })
                                        .addOnFailureListener(e -> {
                                            firebaseUser.delete();
                                            tcs.setException(e);
                                        });
                            })
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    public Task<List<Event>> getPublishedEventsForOrganization(String organizationId) {
        return db.collection(Constants.COLLECTION_EVENTS).get()
                .continueWith(task -> {
                    List<Event> list = new ArrayList<>();
                    if (!task.isSuccessful() || task.getResult() == null) return list;
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null && !e.isDeleted() && e.isPublished()) {
                            e.setId(doc.getId());
                            if (organizationId == null || organizationId.isEmpty()
                                    || e.getOrganizationId() == null || e.getOrganizationId().isEmpty()
                                    || organizationId.equals(e.getOrganizationId())) {
                                list.add(e);
                            }
                        }
                    }
                    return list;
                });
    }

    public Task<QuerySnapshot> getTeamLeaderRegistrations(String teamLeaderId) {
        return db.collection(Constants.COLLECTION_REGISTRATIONS)
                .whereEqualTo("teamLeaderId", teamLeaderId)
                .get();
    }

    public Task<QuerySnapshot> getRegistrationsForEvent(String eventId) {
        return db.collection(Constants.COLLECTION_REGISTRATIONS)
                .whereEqualTo("eventId", eventId)
                .get();
    }

    public Task<DocumentSnapshot> getRegistration(String registrationId) {
        return db.collection(Constants.COLLECTION_REGISTRATIONS).document(registrationId).get();
    }

    /**
     * Atomically registers a team for an event:
     * - Validates event existence, open status, deadline, and team size boundaries.
     * - Ensures duplicate registrations for the same event and team are prevented.
     * - Saves event registration snapshot and updates the team record.
     */
    public Task<Void> registerTeamForEvent(EventRegistration registration, Team team) {
        if (registration == null || team == null || registration.getEventId() == null || team.getId() == null) {
            return Tasks.forException(new IllegalArgumentException("Registration and team parameters cannot be null."));
        }

        DocumentReference regRef = db.collection(Constants.COLLECTION_REGISTRATIONS).document(registration.getId());
        DocumentReference teamRef = db.collection(Constants.COLLECTION_TEAMS).document(team.getId());
        DocumentReference eventRef = db.collection(Constants.COLLECTION_EVENTS).document(registration.getEventId());

        return db.runTransaction(transaction -> {
            DocumentSnapshot eventSnap = transaction.get(eventRef);
            if (!eventSnap.exists()) {
                throw new IllegalStateException("Event not found or has been removed.");
            }

            Event event = eventSnap.toObject(Event.class);
            if (event != null) {
                event.setId(eventSnap.getId());
                if (!event.isRegistrationOpen()) {
                    throw new IllegalStateException("Registration for this event is closed or the deadline has passed.");
                }

                int memberCount = registration.getMemberCount();
                if (memberCount < event.getMinTeamSize()) {
                    throw new IllegalStateException("Team must have at least " + event.getMinTeamSize() + " members (current: " + memberCount + ").");
                }
                if (memberCount > event.getMaxTeamSize()) {
                    throw new IllegalStateException("Team cannot exceed " + event.getMaxTeamSize() + " members (current: " + memberCount + ").");
                }

                // If event requires approval, set status to PENDING
                if (event.isRegistrationApprovalRequired() && !EventRegistration.SOURCE_ADMIN.equals(registration.getRegistrationSource())) {
                    registration.setStatus(EventRegistration.STATUS_PENDING);
                } else if (registration.getStatus() == null || registration.getStatus().isEmpty()) {
                    registration.setStatus(EventRegistration.STATUS_CONFIRMED);
                }
            }

            // Check if registration doc already exists
            DocumentSnapshot existingReg = transaction.get(regRef);
            if (existingReg.exists()) {
                EventRegistration existing = existingReg.toObject(EventRegistration.class);
                if (existing != null && !existing.isCancelled()) {
                    throw new IllegalStateException("This team is already registered for this event.");
                }
            }

            // Write registration record
            transaction.set(regRef, registration);

            // Update team with eventId and updatedAt
            Map<String, Object> teamUpdates = new HashMap<>();
            teamUpdates.put("eventId", registration.getEventId());
            teamUpdates.put("updatedAt", System.currentTimeMillis());
            if (registration.getProjectTitle() != null && !registration.getProjectTitle().isEmpty()) {
                teamUpdates.put("projectTitle", registration.getProjectTitle());
            }
            if (registration.getCategory() != null && !registration.getCategory().isEmpty()) {
                teamUpdates.put("category", registration.getCategory());
            }
            transaction.update(teamRef, teamUpdates);

            return null;
        });
    }

    public Task<Void> cancelRegistration(String registrationId, String reason) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", EventRegistration.STATUS_CANCELLED);
        updates.put("updatedAt", System.currentTimeMillis());
        if (reason != null && !reason.isEmpty()) {
            updates.put("remarks", reason);
        }
        return db.collection(Constants.COLLECTION_REGISTRATIONS).document(registrationId).update(updates);
    }

    public Task<Void> updateRegistrationStatus(String registrationId, String status, String remarks) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("updatedAt", System.currentTimeMillis());
        if (remarks != null) {
            updates.put("remarks", remarks);
        }
        return db.collection(Constants.COLLECTION_REGISTRATIONS).document(registrationId).update(updates);
    }
}
