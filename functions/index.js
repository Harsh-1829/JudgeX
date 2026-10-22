const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/**
 * Cloud Function: adminChangeUserPassword
 *
 * Secure backend callable function allowing authorized administrators to directly
 * change the Firebase Authentication password for a Judge or Team Leader
 * belonging strictly to the same organization.
 *
 * Security guarantees:
 * 1. Requires caller to be authenticated via Firebase Auth.
 * 2. Fetches caller profile from Firestore and verifies role === 'ADMIN'.
 * 3. Enforces that caller belongs to a valid organizationId.
 * 4. Fetches target user profile from Firestore and verifies target belongs to the caller's organizationId.
 * 5. Restricts targets to 'JUDGE' and 'TEAM_LEADER' roles (prevents admin-on-admin or unauthorized escalations).
 * 6. Enforces minimum password policy (at least 6 characters).
 * 7. Calls Firebase Admin SDK to update the password in Firebase Auth.
 * 8. Never logs, persists, or returns the password.
 * 9. Writes an immutable audit entry in 'audit_logs' collection.
 */
exports.adminChangeUserPassword = functions.https.onCall(async (data, context) => {
  // 1. Verify caller authentication
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "The function must be called while authenticated."
    );
  }

  const callerUid = context.auth.uid;
  const { targetUid, newPassword } = data || {};

  // 2. Validate input parameters
  if (!targetUid || typeof targetUid !== "string" || targetUid.trim() === "") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "A valid targetUid must be provided."
    );
  }

  if (!newPassword || typeof newPassword !== "string" || newPassword.length < 6) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "New password must be a string of at least 6 characters."
    );
  }

  const cleanTargetUid = targetUid.trim();

  // 3. Verify caller is an active ADMIN and resolve their organization
  const callerDoc = await db.collection("users").doc(callerUid).get();
  if (!callerDoc.exists) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Caller profile does not exist."
    );
  }

  const callerData = callerDoc.data();
  if (callerData.role !== "ADMIN") {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Permission denied. Only administrators may invoke password changes."
    );
  }

  const callerOrgId = callerData.organizationId;
  if (!callerOrgId) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Administrator profile is not associated with any organization."
    );
  }

  // 4. Verify target user exists, is in the same organization, and has an allowed role
  const targetDoc = await db.collection("users").doc(cleanTargetUid).get();
  if (!targetDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Target user account was not found in the organization."
    );
  }

  const targetData = targetDoc.data();
  const targetOrgId = targetData.organizationId;
  const targetRole = targetData.role;

  if (targetOrgId !== callerOrgId) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Cross-organization access violation: target user belongs to a different organization."
    );
  }

  if (targetRole !== "JUDGE" && targetRole !== "TEAM_LEADER") {
    throw new functions.https.HttpsError(
      "permission-denied",
      `Administrators may only change passwords for Judges and Team Leaders (target role: ${targetRole}).`
    );
  }

  // 5. Update user password via Firebase Admin Auth SDK
  try {
    await admin.auth().updateUser(cleanTargetUid, {
      password: newPassword,
    });

    // 6. Write audit log (NEVER store or log the password)
    await db.collection("audit_logs").add({
      action: "ADMIN_PASSWORD_CHANGE",
      callerUid: callerUid,
      callerEmail: callerData.email || (context.auth.token ? context.auth.token.email : null),
      targetUid: cleanTargetUid,
      targetEmail: targetData.email || null,
      targetRole: targetRole,
      organizationId: callerOrgId,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      outcome: "SUCCESS",
    });

    return {
      success: true,
      message: `Password successfully updated for ${targetData.name || targetData.email || cleanTargetUid}.`,
    };
  } catch (error) {
    // Record failed audit attempt
    await db.collection("audit_logs").add({
      action: "ADMIN_PASSWORD_CHANGE",
      callerUid: callerUid,
      targetUid: cleanTargetUid,
      organizationId: callerOrgId,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      outcome: "FAILED",
      errorMessage: error.message || "Unknown error",
    });

    throw new functions.https.HttpsError(
      "internal",
      `Failed to update user password: ${error.message}`
    );
  }
});
