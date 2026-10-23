const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Privileged Callable Cloud Function: resetJudgePassword
 *
 * Allows a verified Administrator to directly set a new password for a Judge account
 * using the Firebase Admin SDK.
 *
 * Security & Integrity:
 * - Server-side authentication: Automatically verifies the caller's Firebase Auth token (context.auth).
 * - Server-side authorization: Checks the caller's user record in Firestore `users/{uid}` to ensure `role === 'ADMIN'`.
 * - Parameter validation: Requires target judge UID and enforces minimum password length (>= 6 characters).
 * - Direct mutation: Updates the password via admin.auth().updateUser(judgeUid, { password }).
 * - Account preservation: Judge's UID, profile, panel assignments, and scoring history remain completely untouched.
 * - Credential hygiene: Service account keys are never bundled in Android, and plaintext passwords are never stored in Firestore or logged.
 */
exports.resetJudgePassword = functions.https.onCall(async (data, context) => {
    // 1. Verify caller authentication
    if (!context.auth || !context.auth.uid) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "Authentication required: Caller must be signed in to execute this operation."
        );
    }

    const callerUid = context.auth.uid;

    // 2. Verify caller possesses the ADMIN role in Firestore (do not trust client claims)
    const callerDoc = await admin.firestore().collection("users").doc(callerUid).get();
    if (!callerDoc.exists) {
        throw new functions.https.HttpsError(
            "permission-denied",
            "Access denied: Caller profile record does not exist."
        );
    }

    const callerData = callerDoc.data() || {};
    const role = (callerData.role || "").toUpperCase();
    if (role !== "ADMIN") {
        throw new functions.https.HttpsError(
            "permission-denied",
            "Access denied: Only administrators are authorized to reset judge passwords."
        );
    }

    // 3. Validate input parameters
    const judgeUid = data.judgeUid;
    const newPassword = data.newPassword;

    if (!judgeUid || typeof judgeUid !== "string" || judgeUid.trim().length === 0) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "Missing or invalid parameter: 'judgeUid' is required."
        );
    }

    if (!newPassword || typeof newPassword !== "string" || newPassword.length < 6) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "Password does not meet requirements: Minimum 6 characters required."
        );
    }

    try {
        // 4. Update the password directly on the existing Firebase Authentication account
        await admin.auth().updateUser(judgeUid.trim(), {
            password: newPassword
        });

        // 5. Return success confirmation
        return {
            success: true,
            message: "Judge password successfully updated in Firebase Authentication."
        };
    } catch (error) {
        console.error("Failed to update user password in Firebase Auth:", error.code || error.message);
        if (error.code === "auth/user-not-found") {
            throw new functions.https.HttpsError(
                "not-found",
                "Judge account was not found in Firebase Authentication."
            );
        }
        throw new functions.https.HttpsError(
            "internal",
            error.message || "Failed to update judge password in Firebase Authentication."
        );
    }
});

/**
 * Privileged Callable Cloud Function: deleteUserAccount
 *
 * Allows a verified Administrator to delete a User account (Judge or Team Leader)
 * from Firebase Authentication using the Firebase Admin SDK.
 *
 * Security & Integrity:
 * - Server-side authentication: Automatically verifies caller's Firebase Auth token (context.auth).
 * - Server-side authorization: Checks caller record in Firestore `users/{uid}` to ensure `role === 'ADMIN'`.
 * - Safety validation: Prevents an administrator from deleting their own account or missing targetUid.
 * - Idempotency: If the account is already deleted or not found (auth/user-not-found), returns success.
 */
exports.deleteUserAccount = functions.https.onCall(async (data, context) => {
    // 1. Verify caller authentication
    if (!context.auth || !context.auth.uid) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "Authentication required: Caller must be signed in to execute this operation."
        );
    }

    const callerUid = context.auth.uid;

    // 2. Verify caller possesses the ADMIN role in Firestore
    const callerDoc = await admin.firestore().collection("users").doc(callerUid).get();
    if (!callerDoc.exists) {
        throw new functions.https.HttpsError(
            "permission-denied",
            "Access denied: Caller profile record does not exist."
        );
    }

    const callerData = callerDoc.data() || {};
    const role = (callerData.role || "").toUpperCase();
    if (role !== "ADMIN") {
        throw new functions.https.HttpsError(
            "permission-denied",
            "Access denied: Only administrators are authorized to delete user accounts."
        );
    }

    // 3. Validate input parameters
    const targetUid = data.targetUid;
    if (!targetUid || typeof targetUid !== "string" || targetUid.trim().length === 0) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "Missing or invalid parameter: 'targetUid' is required."
        );
    }

    if (targetUid.trim() === callerUid) {
        throw new functions.https.HttpsError(
            "failed-precondition",
            "Safety restriction: An administrator cannot delete their own account."
        );
    }

    try {
        // 4. Delete the user from Firebase Authentication
        await admin.auth().deleteUser(targetUid.trim());
        return {
            success: true,
            message: "User account successfully deleted from Firebase Authentication."
        };
    } catch (error) {
        console.error("Failed to delete user in Firebase Auth:", error.code || error.message);
        if (error.code === "auth/user-not-found") {
            return {
                success: true,
                message: "User already removed or not found in Firebase Authentication."
            };
        }
        throw new functions.https.HttpsError(
            "internal",
            error.message || "Failed to delete user in Firebase Authentication."
        );
    }
});

