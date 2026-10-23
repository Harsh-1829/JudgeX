package com.example.judgex.data;

import android.content.Context;
import android.text.TextUtils;

import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Organization;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.CryptoUtils;
import com.example.judgex.utils.ScoringValidationUtils;
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
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.security.SecureRandom;
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

    public static String normalizeJudgeName(String name) {
        return User.normalizeJudgeName(name);
    }

    /**
     * Checks if a judge name is unique across existing judges, excluding an optional UID.
     * Treats names differing only by whitespace or capitalization as duplicates.
     */
    public Task<Boolean> isJudgeNameUnique(String name, String excludeUid) {
        TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();
        final String targetNormalized = normalizeJudgeName(name);
        if (targetNormalized.isEmpty()) {
            tcs.setResult(false);
            return tcs.getTask();
        }

        db.collection(Constants.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    boolean isUnique = true;
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            if (excludeUid != null && doc.getId().equals(excludeUid)) {
                                continue;
                            }
                            User u = doc.toObject(User.class);
                            if (u != null) {
                                if (u.isDeleted()) {
                                    continue;
                                }
                                if (u.isJudge()) {
                                    String existingNorm = normalizeJudgeName(u.getName());
                                    if (existingNorm.equals(targetNormalized)) {
                                        isUnique = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    tcs.setResult(isUnique);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    /**
     * Creates a Judge user without signing out the currently logged-in Admin,
     * enforcing name uniqueness, email normalization, cross-role uniqueness against Team Leaders,
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

        final String cleanName = name != null ? name.trim() : "";
        if (normalizeJudgeName(cleanName).isEmpty()) {
            tcs.setException(new IllegalArgumentException("Judge name cannot be empty"));
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
                                existingUser.setName(cleanName);
                                if (!cleanEventId.isEmpty()) {
                                    existingUser.setEventId(cleanEventId);
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
                                // Preserve multi-event judge participation
                                existingUser.setName(cleanName);
                                if (!cleanEventId.isEmpty()) {
                                    existingUser.setEventId(cleanEventId);
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
                    provisionNewJudgeAuthAccount(context, normalizedEmail, password, cleanName, cleanPanelId, cleanEventId, tcs);
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
                            judgeUser.setEventId(cleanEventId);
                            judgeUser.setPanelForEvent(cleanEventId, cleanPanelId);
                        }

                        // Attach organization ID from current admin if available
                        if (currentUserProfile != null && currentUserProfile.getOrganizationId() != null) {
                            judgeUser.setOrganizationId(currentUserProfile.getOrganizationId());
                            judgeUser.setOrganizationName(currentUserProfile.getOrganizationName());
                        }

                        if (password != null && !password.isEmpty()) {
                            judgeUser.setEncryptedSecret(CryptoUtils.encrypt(password));
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

    // --- Team Leader Username & Simplified Registration Operations ---

    /**
     * Sanitizes team name into lowercase alphanumeric string suitable for usernames.
     * e.g., "Code Warriors" -> "codewarriors"
     */
    public static String sanitizeTeamNameForUsername(String teamName) {
        if (teamName == null) return "team";
        String sanitized = teamName.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]", "");
        return sanitized.isEmpty() ? "team" : sanitized;
    }

    /**
     * Generates a unique username for a team (e.g. codewarriors4821) by verifying against
     * existing users and teams in Firestore.
     */
    public Task<String> generateUniqueTeamUsername(String teamName) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        final String base = sanitizeTeamNameForUsername(teamName);
        findUniqueUsernameRecursive(base, 0, tcs);
        return tcs.getTask();
    }

    /**
     * Sanitizes judge name into lowercase alphanumeric string suitable for usernames.
     * e.g., "Dr. Jane Smith" -> "drjanesmith"
     */
    public static String sanitizeJudgeNameForUsername(String judgeName) {
        if (judgeName == null) return "judge";
        String sanitized = judgeName.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]", "");
        return sanitized.isEmpty() ? "judge" : sanitized;
    }

    /**
     * Generates a unique username for a judge (e.g. drjanesmith4821) by verifying against
     * existing users in Firestore.
     */
    public Task<String> generateUniqueJudgeUsername(String judgeName) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        final String base = sanitizeJudgeNameForUsername(judgeName);
        findUniqueUsernameRecursive(base, 0, tcs);
        return tcs.getTask();
    }

    private void findUniqueUsernameRecursive(String base, int attempt, TaskCompletionSource<String> tcs) {
        if (attempt >= 10) {
            // After 10 attempts, append timestamp to guarantee uniqueness
            String fallback = base + (System.currentTimeMillis() % 100000);
            tcs.setResult(fallback);
            return;
        }

        int randomNum = 1000 + new java.security.SecureRandom().nextInt(9000);
        String candidate = base + randomNum;

        db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("username", candidate)
                .limit(1)
                .get()
                .addOnSuccessListener(userSnap -> {
                    if (userSnap != null && !userSnap.isEmpty()) {
                        // Collision in users, retry
                        findUniqueUsernameRecursive(base, attempt + 1, tcs);
                    } else {
                        // Check teams collection
                        db.collection(Constants.COLLECTION_TEAMS)
                                .whereEqualTo("username", candidate)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(teamSnap -> {
                                    if (teamSnap != null && !teamSnap.isEmpty()) {
                                        findUniqueUsernameRecursive(base, attempt + 1, tcs);
                                    } else {
                                        tcs.setResult(candidate);
                                    }
                                })
                                .addOnFailureListener(tcs::setException);
                    }
                })
                .addOnFailureListener(tcs::setException);
    }

    /**
     * Generates an 8-character secure random temporary password containing
     * uppercase, lowercase, and digits. Plaintext passwords are never stored in Firestore.
     */
    public static String generateSecureTemporaryPassword() {
        final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // removed easily confused O, I
        final String LOWER = "abcdefghjkmnpqrstuvwxyz"; // removed l, o
        final String DIGITS = "23456789";               // removed 0, 1
        final String ALL = UPPER + LOWER + DIGITS;

        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(8);

        // Ensure at least 1 uppercase, 1 lowercase, 1 digit
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(random.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));

        for (int i = 3; i < 8; i++) {
            sb.append(ALL.charAt(random.nextInt(ALL.length())));
        }

        // Shuffle
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }

    /**
     * Creates a Team Leader user using a unique username and temporary password without
     * signing out the currently logged-in Admin. Maps the username to a synthetic internal
     * email identifier. Plaintext passwords are never stored in Firestore.
     */
    public Task<String> createTeamLeaderAccountWithUsername(Context context, String username, String password, String name, String teamId) {
        return createTeamLeaderAccountWithUsername(context, username, password, null, name, teamId);
    }

    public Task<String> createTeamLeaderAccountWithUsername(Context context, String username, String password, String encryptedPassword, String name, String teamId) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        final String cleanUsername = username != null ? username.trim().toLowerCase(java.util.Locale.US) : "";
        final String syntheticEmail = Constants.buildTeamSyntheticEmail(cleanUsername);

        if (cleanUsername.isEmpty()) {
            tcs.setException(new IllegalArgumentException("Username cannot be empty"));
            return tcs.getTask();
        }

        if (password == null || password.length() < 6) {
            tcs.setException(new IllegalArgumentException("Password must be at least 6 characters"));
            return tcs.getTask();
        }

        // Check if username already exists in users collection
        db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("username", cleanUsername)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        tcs.setException(new IllegalStateException("Username '" + cleanUsername + "' is already in use."));
                        return;
                    }

                    // Provision secondary Auth user
                    String uniqueAppName = "LeaderCreator_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
                    try {
                        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                        FirebaseApp secondaryApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, uniqueAppName);
                        FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

                        secondaryAuth.createUserWithEmailAndPassword(syntheticEmail, password)
                                .addOnSuccessListener(authResult -> {
                                    if (authResult.getUser() == null) {
                                        cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                                        tcs.setException(new Exception("Failed to create leader authentication record"));
                                        return;
                                    }

                                    String newUid = authResult.getUser().getUid();
                                    User leaderUser = new User(newUid, syntheticEmail, name.trim(), User.ROLE_TEAM_LEADER, "", System.currentTimeMillis());
                                    leaderUser.setUsername(cleanUsername);
                                    leaderUser.setAssignedTeamId(teamId);

                                    if (currentUserProfile != null && currentUserProfile.getOrganizationId() != null) {
                                        leaderUser.setOrganizationId(currentUserProfile.getOrganizationId());
                                        leaderUser.setOrganizationName(currentUserProfile.getOrganizationName());
                                    }

                                    // Save leader user profile in Firestore
                                    db.collection(Constants.COLLECTION_USERS).document(newUid).set(leaderUser)
                                            .addOnSuccessListener(aVoid -> {
                                                if (teamId != null && !teamId.isEmpty()) {
                                                    Map<String, Object> teamUpdates = new HashMap<>();
                                                    teamUpdates.put("leaderUid", newUid);
                                                    teamUpdates.put("username", cleanUsername);
                                                    teamUpdates.put("leaderEmail", syntheticEmail);
                                                    if (encryptedPassword != null && !encryptedPassword.isEmpty()) {
                                                        teamUpdates.put("encryptedPassword", encryptedPassword);
                                                    }

                                                    db.collection(Constants.COLLECTION_TEAMS).document(teamId)
                                                            .update(teamUpdates)
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
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    /**
     * Creates an event-specific Judge account with a unique username and secure temporary password
     * without signing out the currently logged-in Admin. Maps the username to a synthetic internal
     * authentication identifier. Plaintext passwords are never stored in Firestore.
     */
    public Task<String> createJudgeAccountWithUsername(Context context, String username, String password, String name, String panelId, String eventId) {
        return createJudgeAccountWithUsername(context, username, password, null, name, panelId, eventId);
    }

    public Task<String> createJudgeAccountWithUsername(Context context, String username, String password, String encryptedPassword, String name, String panelId, String eventId) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        final String cleanUsername = username != null ? username.trim().toLowerCase(java.util.Locale.US) : "";
        final String syntheticEmail = Constants.buildSyntheticEmail(cleanUsername);

        if (cleanUsername.isEmpty()) {
            tcs.setException(new IllegalArgumentException("Username cannot be empty"));
            return tcs.getTask();
        }

        if (password == null || password.length() < 6) {
            tcs.setException(new IllegalArgumentException("Password must be at least 6 characters"));
            return tcs.getTask();
        }

        final String cleanName = name != null && !name.trim().isEmpty() ? name.trim() : "Judge";
        final String cleanPanelId = panelId != null ? panelId.trim() : "";
        final String cleanEventId = eventId != null ? eventId.trim() : "";

        // Verify username is unique in users collection
        db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("username", cleanUsername)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        tcs.setException(new IllegalStateException("Username '" + cleanUsername + "' is already in use."));
                        return;
                    }

                    // Provision secondary Auth user without logging out Admin
                    String uniqueAppName = "JudgeCreator_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
                    try {
                        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                        FirebaseApp secondaryApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, uniqueAppName);
                        FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

                        secondaryAuth.createUserWithEmailAndPassword(syntheticEmail, password)
                                .addOnSuccessListener(authResult -> {
                                    if (authResult.getUser() == null) {
                                        cleanUpSecondaryApp(secondaryApp, secondaryAuth);
                                        tcs.setException(new Exception("Failed to create judge authentication record"));
                                        return;
                                    }

                                    String newUid = authResult.getUser().getUid();
                                    User judgeUser = new User(newUid, syntheticEmail, cleanName, User.ROLE_JUDGE, cleanPanelId, System.currentTimeMillis());
                                    judgeUser.setUsername(cleanUsername);
                                    if (!cleanEventId.isEmpty()) {
                                        judgeUser.setEventId(cleanEventId);
                                        if (!cleanPanelId.isEmpty()) {
                                            judgeUser.setPanelForEvent(cleanEventId, cleanPanelId);
                                        }
                                    }
                                    if (currentUserProfile != null && currentUserProfile.getOrganizationId() != null) {
                                        judgeUser.setOrganizationId(currentUserProfile.getOrganizationId());
                                        judgeUser.setOrganizationName(currentUserProfile.getOrganizationName());
                                    }

                                    if (encryptedPassword != null && !encryptedPassword.isEmpty()) {
                                        judgeUser.setEncryptedSecret(encryptedPassword);
                                    } else if (password != null && !password.isEmpty()) {
                                        judgeUser.setEncryptedSecret(CryptoUtils.encrypt(password));
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
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
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
        if (eventId == null || eventId.trim().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Event ID cannot be empty."));
        }
        return db.collection(Constants.COLLECTION_EVENTS).document(eventId.trim()).delete();
    }

    /**
     * Cascading event deletion: atomically deletes dependent panels, criteria, teams,
     * and optionally evaluations along with the event document.
     */
    public Task<Void> cascadeDeleteEvent(String eventId, boolean deleteEvaluations) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Event ID cannot be empty."));
        }
        final String cleanEventId = eventId.trim();
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();

        Task<QuerySnapshot> panelsTask = db.collection(Constants.COLLECTION_PANELS).whereEqualTo("eventId", cleanEventId).get();
        Task<QuerySnapshot> criteriaTask = db.collection(Constants.COLLECTION_CRITERIA).whereEqualTo("eventId", cleanEventId).get();
        Task<QuerySnapshot> teamsTask = db.collection(Constants.COLLECTION_TEAMS).whereEqualTo("eventId", cleanEventId).get();
        Task<QuerySnapshot> evalsTask = db.collection(Constants.COLLECTION_EVALUATIONS).whereEqualTo("eventId", cleanEventId).get();

        Tasks.whenAllSuccess(panelsTask, criteriaTask, teamsTask, evalsTask)
                .addOnSuccessListener(results -> {
                    QuerySnapshot panelsSnap = (QuerySnapshot) results.get(0);
                    QuerySnapshot criteriaSnap = (QuerySnapshot) results.get(1);
                    QuerySnapshot teamsSnap = (QuerySnapshot) results.get(2);
                    QuerySnapshot evalsSnap = (QuerySnapshot) results.get(3);

                    WriteBatch batch = db.batch();

                    if (panelsSnap != null) {
                        for (DocumentSnapshot doc : panelsSnap.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                    }
                    if (criteriaSnap != null) {
                        for (DocumentSnapshot doc : criteriaSnap.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                    }
                    if (teamsSnap != null) {
                        for (DocumentSnapshot doc : teamsSnap.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                    }
                    if (deleteEvaluations && evalsSnap != null) {
                        for (DocumentSnapshot doc : evalsSnap.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                    }

                    batch.delete(db.collection(Constants.COLLECTION_EVENTS).document(cleanEventId));

                    batch.commit()
                            .addOnSuccessListener(aVoid -> tcs.setResult(null))
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
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

    /**
     * Atomically deletes a panel and safely unassigns any judges and teams mapped to it.
     */
    public Task<Void> deletePanel(Panel panel) {
        if (panel == null || panel.getId() == null || panel.getId().trim().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Panel ID cannot be empty."));
        }
        final String panelId = panel.getId().trim();
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();

        Task<QuerySnapshot> judgesTask = db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("assignedPanelId", panelId)
                .get();

        Task<QuerySnapshot> teamsTask = db.collection(Constants.COLLECTION_TEAMS)
                .whereEqualTo("panelId", panelId)
                .get();

        Tasks.whenAllSuccess(judgesTask, teamsTask)
                .addOnSuccessListener(results -> {
                    QuerySnapshot judgesSnap = (QuerySnapshot) results.get(0);
                    QuerySnapshot teamsSnap = (QuerySnapshot) results.get(1);

                    WriteBatch batch = db.batch();

                    if (judgesSnap != null) {
                        for (DocumentSnapshot jDoc : judgesSnap.getDocuments()) {
                            batch.update(jDoc.getReference(), "assignedPanelId", "");
                        }
                    }

                    if (teamsSnap != null) {
                        for (DocumentSnapshot tDoc : teamsSnap.getDocuments()) {
                            batch.update(tDoc.getReference(), "panelId", "");
                        }
                    }

                    batch.delete(db.collection(Constants.COLLECTION_PANELS).document(panelId));

                    batch.commit()
                            .addOnSuccessListener(aVoid -> tcs.setResult(null))
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
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
     * Atomically assigns only the specified selected judges to panels without wiping unselected judges.
     * Moves reassigned judges cleanly by removing them from old panels and adding to new panels.
     */
    public Task<Void> batchAssignSelectedJudges(Map<String, String> judgeToPanelMap, List<Panel> allPanels, String eventId) {
        WriteBatch batch = db.batch();

        Map<String, List<String>> panelToJudgesToAdd = new HashMap<>();

        for (Map.Entry<String, String> entry : judgeToPanelMap.entrySet()) {
            String judgeId = entry.getKey();
            String newPanelId = entry.getValue();

            if (newPanelId != null && !newPanelId.isEmpty()) {
                if (!panelToJudgesToAdd.containsKey(newPanelId)) {
                    panelToJudgesToAdd.put(newPanelId, new ArrayList<>());
                }
                panelToJudgesToAdd.get(newPanelId).add(judgeId);

                DocumentReference userRef = db.collection(Constants.COLLECTION_USERS).document(judgeId);
                batch.update(userRef, "assignedPanelId", newPanelId);
                if (eventId != null && !eventId.isEmpty()) {
                    batch.update(userRef, "eventPanelAssignments." + eventId, newPanelId);
                }

                // If judge was previously assigned to another panel in this event, remove from that old panel
                if (allPanels != null) {
                    for (Panel p : allPanels) {
                        if (p != null && p.getId() != null && !p.getId().equals(newPanelId)) {
                            if (p.getJudgeIds() != null && p.getJudgeIds().contains(judgeId)) {
                                DocumentReference oldPanelRef = db.collection(Constants.COLLECTION_PANELS).document(p.getId());
                                batch.update(oldPanelRef, "judgeIds", FieldValue.arrayRemove(judgeId));
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, List<String>> pEntry : panelToJudgesToAdd.entrySet()) {
            DocumentReference pRef = db.collection(Constants.COLLECTION_PANELS).document(pEntry.getKey());
            batch.update(pRef, "judgeIds", FieldValue.arrayUnion(pEntry.getValue().toArray()));
        }

        return batch.commit();
    }

    /**
     * Resets a judge's password directly via the privileged 'resetJudgePassword' Firebase Cloud Function.
     * Passes the target judge's Firebase Authentication UID and new password.
     * The Cloud Function uses Firebase Admin SDK to update the password in Firebase Auth after verifying
     * the caller's JWT and verifying their ADMIN role in Firestore.
     * Plaintext passwords are never stored in Firestore or logged.
     */
    public Task<String> resetJudgePassword(String judgeUid, String newPassword) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        if (judgeUid == null || judgeUid.trim().isEmpty()) {
            tcs.setException(new IllegalArgumentException("Judge identifier (UID) is required."));
            return tcs.getTask();
        }
        if (newPassword == null || newPassword.length() < 6) {
            tcs.setException(new IllegalArgumentException("Password must be at least 6 characters."));
            return tcs.getTask();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("judgeUid", judgeUid.trim());
        data.put("newPassword", newPassword);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("resetJudgePassword")
                .call(data)
                .addOnSuccessListener(httpsCallableResult -> {
                    String message = "Judge password successfully updated in Firebase Authentication.";
                    if (httpsCallableResult != null && httpsCallableResult.getData() instanceof Map) {
                        Map<?, ?> resMap = (Map<?, ?>) httpsCallableResult.getData();
                        if (resMap.get("message") != null) {
                            message = resMap.get("message").toString();
                        }
                    }
                    tcs.setResult(message);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    /**
     * Backward-compatible wrapper delegating to resetJudgePassword.
     */
    public Task<Void> changeJudgePasswordBackend(String judgeUid, String newPassword) {
        return resetJudgePassword(judgeUid, newPassword).continueWith(task -> {
            if (!task.isSuccessful() && task.getException() != null) {
                throw task.getException();
            }
            return null;
        });
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
        if (criterionId == null || criterionId.trim().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Criterion ID cannot be empty."));
        }
        return db.collection(Constants.COLLECTION_CRITERIA).document(criterionId.trim()).delete();
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
        if (teamId == null || teamId.trim().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Team ID cannot be empty."));
        }
        Team dummy = new Team();
        dummy.setId(teamId.trim());
        return deleteTeam(dummy, false);
    }

    /**
     * Permanently deletes a team record, safely removes it from panel mappings,
     * deletes associated team leader profiles, and optionally deletes evaluations.
     */
    public Task<Void> deleteTeam(Team team, boolean deleteEvaluations) {
        if (team == null || team.getId() == null || team.getId().trim().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Team record is invalid."));
        }
        final String teamId = team.getId().trim();
        final String panelId = team.getPanelId();
        final String leaderUid = team.getLeaderUid();

        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();

        Task<QuerySnapshot> evalsTask = db.collection(Constants.COLLECTION_EVALUATIONS)
                .whereEqualTo("teamId", teamId)
                .get();

        Task<QuerySnapshot> leaderUserTask = db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("teamId", teamId)
                .get();

        Tasks.whenAllSuccess(evalsTask, leaderUserTask)
                .addOnSuccessListener(results -> {
                    QuerySnapshot evalSnap = (QuerySnapshot) results.get(0);
                    QuerySnapshot leaderSnap = (QuerySnapshot) results.get(1);

                    WriteBatch batch = db.batch();

                    if (panelId != null && !panelId.trim().isEmpty()) {
                        DocumentReference panelRef = db.collection(Constants.COLLECTION_PANELS).document(panelId.trim());
                        batch.update(panelRef, "teamIds", FieldValue.arrayRemove(teamId));
                    }

                    if (deleteEvaluations && evalSnap != null) {
                        for (DocumentSnapshot eDoc : evalSnap.getDocuments()) {
                            batch.delete(eDoc.getReference());
                        }
                    }

                    batch.delete(db.collection(Constants.COLLECTION_TEAMS).document(teamId));

                    String authUidToDelete = leaderUid;
                    if (leaderSnap != null && !leaderSnap.isEmpty()) {
                        for (DocumentSnapshot uDoc : leaderSnap.getDocuments()) {
                            if (authUidToDelete == null || authUidToDelete.isEmpty()) {
                                authUidToDelete = uDoc.getId();
                            }
                            batch.delete(uDoc.getReference());
                        }
                    } else if (leaderUid != null && !leaderUid.trim().isEmpty()) {
                        batch.delete(db.collection(Constants.COLLECTION_USERS).document(leaderUid.trim()));
                    }

                    final String finalAuthUid = authUidToDelete;

                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                if (finalAuthUid != null && !finalAuthUid.trim().isEmpty()) {
                                    deleteUserAuthAccount(finalAuthUid)
                                            .addOnCompleteListener(authTask -> tcs.setResult(null));
                                } else {
                                    tcs.setResult(null);
                                }
                            })
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    /**
     * Soft-deletes a team while unassigning it from its panel and deactivating the team leader account.
     */
    public Task<Void> softDeleteTeam(Team team) {
        if (team == null || team.getId() == null || team.getId().trim().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Team record is invalid."));
        }
        final String teamId = team.getId().trim();
        final String oldPanelId = team.getPanelId();

        team.setDeleted(true);
        team.setStatus(Team.STATUS_DELETED);
        team.setPanelId("");

        WriteBatch batch = db.batch();

        if (oldPanelId != null && !oldPanelId.trim().isEmpty()) {
            DocumentReference panelRef = db.collection(Constants.COLLECTION_PANELS).document(oldPanelId.trim());
            batch.update(panelRef, "teamIds", FieldValue.arrayRemove(teamId));
        }

        DocumentReference teamRef = db.collection(Constants.COLLECTION_TEAMS).document(teamId);
        batch.set(teamRef, team, SetOptions.merge());

        if (team.getLeaderUid() != null && !team.getLeaderUid().trim().isEmpty()) {
            DocumentReference leaderRef = db.collection(Constants.COLLECTION_USERS).document(team.getLeaderUid().trim());
            Map<String, Object> leaderUpdates = new HashMap<>();
            leaderUpdates.put("deleted", true);
            leaderUpdates.put("status", User.STATUS_DELETED);
            leaderUpdates.put("active", false);
            batch.update(leaderRef, leaderUpdates);
        }

        return batch.commit();
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
        if (evaluation == null || evaluation.getEventId() == null || evaluation.getEventId().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Evaluation record or eventId is missing."));
        }

        String evalId = Evaluation.generateEvaluationId(
                evaluation.getEventId(), 
                evaluation.getTeamId(), 
                evaluation.getJudgeId()
        );
        evaluation.setId(evalId);

        DocumentReference evalRef = db.collection(Constants.COLLECTION_EVALUATIONS).document(evalId);
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();

        // Validate event criteria weights before committing evaluation to Firestore
        Tasks.whenAllSuccess(getEvent(evaluation.getEventId()), getCriteriaForEvent(evaluation.getEventId()))
                .addOnSuccessListener(results -> {
                    DocumentSnapshot eventDoc = (DocumentSnapshot) results.get(0);
                    QuerySnapshot criteriaSnap = (QuerySnapshot) results.get(1);

                    Event event = eventDoc != null && eventDoc.exists() ? eventDoc.toObject(Event.class) : null;
                    if (ScoringValidationUtils.isWeightedScoring(event)) {
                        List<Criterion> list = new ArrayList<>();
                        if (criteriaSnap != null) {
                            for (DocumentSnapshot doc : criteriaSnap.getDocuments()) {
                                Criterion c = doc.toObject(Criterion.class);
                                if (c != null) list.add(c);
                            }
                        }
                        if (!ScoringValidationUtils.isTotalWeightValid(list)) {
                            double total = ScoringValidationUtils.calculateTotalWeight(list);
                            tcs.setException(new IllegalStateException(
                                    "Cannot submit evaluation: Event criteria weights total " 
                                    + ScoringValidationUtils.formatWeight(total) 
                                    + ", but must total exactly 100%."));
                            return;
                        }
                    }

                    db.runTransaction(transaction -> {
                        DocumentSnapshot snapshot = transaction.get(evalRef);
                        if (snapshot.exists()) {
                            Evaluation existing = snapshot.toObject(Evaluation.class);
                            if (existing != null && existing.isSubmitted()) {
                                throw new IllegalStateException("An evaluation for this team by you has already been submitted and cannot be changed.");
                            }
                        }
                        transaction.set(evalRef, evaluation);
                        return null;
                    }).addOnSuccessListener(aVoid -> tcs.setResult(null))
                      .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
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

    /**
     * Privileged backend call: Deletes a user from Firebase Authentication via the deleteUserAccount Cloud Function.
     */
    public Task<String> deleteUserAuthAccount(String targetUid) {
        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
        if (targetUid == null || targetUid.trim().isEmpty()) {
            tcs.setException(new IllegalArgumentException("User identifier (UID) is required."));
            return tcs.getTask();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("targetUid", targetUid.trim());

        FirebaseFunctions.getInstance()
                .getHttpsCallable("deleteUserAccount")
                .call(data)
                .addOnSuccessListener(httpsCallableResult -> {
                    String message = "User account successfully deleted from Firebase Authentication.";
                    if (httpsCallableResult != null && httpsCallableResult.getData() instanceof Map) {
                        Map<?, ?> resMap = (Map<?, ?>) httpsCallableResult.getData();
                        if (resMap.get("message") != null) {
                            message = resMap.get("message").toString();
                        }
                    }
                    tcs.setResult(message);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
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
        public final boolean authDeleted;
        public final String message;

        public DeleteJudgeResult(String judgeUid, String judgeName, String judgeEmail,
                                 boolean success, boolean evaluationsPreserved, int evaluationCount,
                                 boolean profileDeleted, boolean authRequiresBackend, String message) {
            this(judgeUid, judgeName, judgeEmail, success, evaluationsPreserved, evaluationCount, profileDeleted, authRequiresBackend, false, message);
        }

        public DeleteJudgeResult(String judgeUid, String judgeName, String judgeEmail,
                                 boolean success, boolean evaluationsPreserved, int evaluationCount,
                                 boolean profileDeleted, boolean authRequiresBackend, boolean authDeleted, String message) {
            this.judgeUid = judgeUid;
            this.judgeName = judgeName;
            this.judgeEmail = judgeEmail;
            this.success = success;
            this.evaluationsPreserved = evaluationsPreserved;
            this.evaluationCount = evaluationCount;
            this.profileDeleted = profileDeleted;
            this.authRequiresBackend = authRequiresBackend;
            this.authDeleted = authDeleted;
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
     * 3. Deletes Firebase Authentication record via Cloud Function or notifies Admin if Cloud Function requires deployment.
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
                                            deleteUserAuthAccount(judgeUid).addOnCompleteListener(authTask -> {
                                                boolean authDeleted = authTask.isSuccessful();
                                                boolean authRequiresBackend = !authDeleted;

                                                String outcomeMsg = hasEvaluations
                                                        ? "Judge profile unassigned and removed from active lists. " + evalCount + " historical evaluation(s) preserved for competition scoring."
                                                        : "Judge profile and panel assignments completely deleted.";

                                                if (authDeleted) {
                                                    outcomeMsg += "\n\nFirebase Authentication account was also deleted.";
                                                } else {
                                                    outcomeMsg += "\n\nNote: To remove the login credential from Firebase Authentication without a deployed Cloud Function, delete the user in Firebase Console (Authentication > Users).";
                                                }

                                                tcs.setResult(new DeleteJudgeResult(
                                                        judgeUid, judgeName, judgeEmail,
                                                        true, hasEvaluations, evalCount,
                                                        !hasEvaluations, authRequiresBackend, authDeleted, outcomeMsg
                                                ));
                                            });
                                        })
                                        .addOnFailureListener(tcs::setException);
                            })
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    /**
     * Batch deletes multiple judge accounts safely and collects all results.
     */
    public Task<List<DeleteJudgeResult>> deleteJudgeAccounts(List<User> judges) {
        if (judges == null || judges.isEmpty()) {
            return Tasks.forResult(new ArrayList<DeleteJudgeResult>());
        }

        TaskCompletionSource<List<DeleteJudgeResult>> tcs = new TaskCompletionSource<>();
        List<DeleteJudgeResult> results = new ArrayList<>();
        List<Task<DeleteJudgeResult>> tasks = new ArrayList<>();

        for (User j : judges) {
            tasks.add(deleteJudgeAccount(j));
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener(allTask -> {
            for (Task<DeleteJudgeResult> t : tasks) {
                if (t.isSuccessful() && t.getResult() != null) {
                    results.add(t.getResult());
                }
            }
            tcs.setResult(results);
        });

        return tcs.getTask();
    }
}

