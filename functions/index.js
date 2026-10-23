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
