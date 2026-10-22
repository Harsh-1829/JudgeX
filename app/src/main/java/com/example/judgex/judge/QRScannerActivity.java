package com.example.judgex.judge;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.json.JSONObject;

import java.util.List;

public class QRScannerActivity extends AppCompatActivity {

    private static final String TAG = "QRScannerActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 101;

    private DecoratedBarcodeView barcodeScannerView;
    private ImageButton btnClose, btnFlash;
    private boolean isFlashOn = false;
    private boolean isProcessing = false;
    private Dialog loadingDialog;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private String judgePanelId;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        judgePanelId = getIntent().getStringExtra(Constants.EXTRA_PANEL_ID);
        eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);

        initViews();
        checkCameraPermissionAndStart();
    }

    private void initViews() {
        barcodeScannerView = findViewById(R.id.barcodeScannerView);
        btnClose = findViewById(R.id.btnClose);
        btnFlash = findViewById(R.id.btnFlash);

        btnClose.setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> toggleFlash());
    }

    private void checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            startScanning();
        }
    }

    private void startScanning() {
        barcodeScannerView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() != null && !isProcessing) {
                    isProcessing = true;
                    runOnUiThread(() -> processScannedData(result.getText()));
                }
            }

            @Override
            public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {}
        });
        barcodeScannerView.resume();
    }

    private void toggleFlash() {
        if (isFlashOn) {
            barcodeScannerView.setTorchOff();
            isFlashOn = false;
        } else {
            barcodeScannerView.setTorchOn();
            isFlashOn = true;
        }
    }

    private void processScannedData(String rawData) {
        if (rawData == null || rawData.trim().isEmpty()) {
            isProcessing = false;
            return;
        }

        String cleanedData = rawData.trim();
        Log.d(TAG, "Processing scanned raw data: " + cleanedData);

        if (!firebaseManager.isUserLoggedIn()) {
            showErrorDialog("Unauthorized", "You must be signed in as an authorized judge to evaluate teams.");
            return;
        }

        showLoading("Validating team badge...");

        String candidateTeamId = cleanedData;
        String scannedEventId = null;
        String scannedPanelId = null;
        String scannedTeamNumber = null;

        try {
            JSONObject json = new JSONObject(cleanedData);
            if (json.has("teamId")) {
                candidateTeamId = json.getString("teamId").trim();
            } else if (json.has("id")) {
                candidateTeamId = json.getString("id").trim();
            }

            if (json.has("teamNumber")) {
                scannedTeamNumber = json.getString("teamNumber").trim();
            }
            if (json.has("eventId")) {
                scannedEventId = json.getString("eventId").trim();
            }
            if (json.has("panelId")) {
                scannedPanelId = json.getString("panelId").trim();
            }
        } catch (Exception ignored) {
            // Raw text fallback
        }

        final String finalTeamId = candidateTeamId;
        final String finalScannedEventId = scannedEventId;
        final String finalTeamNumber = scannedTeamNumber;

        // Step 1: Look up team by document ID
        firebaseManager.getTeam(finalTeamId)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Team team = doc.toObject(Team.class);
                        if (team != null) {
                            team.setId(doc.getId());
                            proceedWithValidatedTeam(team, finalScannedEventId);
                            return;
                        }
                    }

                    // Step 1b: Fallback to querying by teamNumber (e.g. if QR contained TM-01 instead of doc ID)
                    String lookupNumber = finalTeamNumber != null ? finalTeamNumber : finalTeamId;
                    firebaseManager.getTeamsCollection().whereEqualTo("teamNumber", lookupNumber).limit(1).get()
                            .addOnSuccessListener(querySnapshot -> {
                                if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                    Team team = querySnapshot.getDocuments().get(0).toObject(Team.class);
                                    if (team != null) {
                                        team.setId(querySnapshot.getDocuments().get(0).getId());
                                        proceedWithValidatedTeam(team, finalScannedEventId);
                                        return;
                                    }
                                }
                                hideLoading();
                                showErrorDialog("Invalid Team QR",
                                        "No registered team found in the competition database matching code: " + finalTeamId);
                            })
                            .addOnFailureListener(e -> {
                                hideLoading();
                                showErrorDialog("Lookup Error", "Failed to resolve team by identifier: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    showErrorDialog("Scan Error", "Network error retrieving team details: " + e.getMessage());
                });
    }

    private void proceedWithValidatedTeam(Team team, String scannedEventId) {
        // Step 2: Check team lifecycle status
        if (team.isDeleted() || team.isUnregistered()) {
            hideLoading();
            showErrorDialog("Team Inactive", 
                    "Team " + team.getTeamNumber() + " (" + team.getTeamName() + 
                    ") has been unregistered or removed from this competition. Evaluations cannot be started for this team.");
            return;
        }

        // Step 3: Check event ID consistency and backward compatibility
        String teamEventId = team.getEventId();
        if (teamEventId == null || teamEventId.trim().isEmpty()) {
            hideLoading();
            showErrorDialog("Invalid Pass",
                    "This QR pass is not linked to any active competition event. Please regenerate a new event-specific pass from the team portal.");
            return;
        }

        if (eventId != null && !eventId.isEmpty() && !eventId.equals(teamEventId)) {
            hideLoading();
            showErrorDialog("Event Mismatch", 
                    "This team belongs to a different competition event. You are currently judging in a separate event.");
            return;
        }

        if (scannedEventId != null && !scannedEventId.isEmpty() && !scannedEventId.equals(teamEventId)) {
            hideLoading();
            showErrorDialog("Invalid Badge", "The QR code event identifier does not match the registered team record.");
            return;
        }

        // Step 4: Verify Event Status
        firebaseManager.getEvent(teamEventId)
                .addOnSuccessListener(eDoc -> {
                    if (!eDoc.exists()) {
                        hideLoading();
                        showErrorDialog("Event Not Found", "The competition event for this team could not be found.");
                        return;
                    }

                    Event event = eDoc.toObject(Event.class);
                    if (event == null) {
                        hideLoading();
                        showErrorDialog("Error", "Could not load event data.");
                        return;
                    }
                    event.setId(eDoc.getId());

                    if (event.isUpcoming()) {
                        hideLoading();
                        showErrorDialog("Event Upcoming", 
                                "Event '" + event.getName() + "' is scheduled for a future date and is not currently open for evaluations.");
                        return;
                    }

                    if (event.isLocked()) {
                        hideLoading();
                        showErrorDialog("Event Locked", 
                                "Evaluations for '" + event.getName() + "' are currently locked. New submissions are disabled.");
                        return;
                    }

                    if (event.isCompleted() || Event.STATUS_ARCHIVED.equalsIgnoreCase(event.getStatus())) {
                        hideLoading();
                        showErrorDialog("Event Closed", 
                                "This event has ended and is marked as " + event.getStatus() + ". New evaluations cannot be accepted.");
                        return;
                    }

                    if (!event.isAcceptingEvaluations()) {
                        hideLoading();
                        showErrorDialog("Evaluations Closed", "This event is currently not accepting evaluations.");
                        return;
                    }

                    // Step 5: Verify Judge Authorization and Panel Assignment
                    verifyJudgeAuthorizationAndProceed(team, event);
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    showErrorDialog("Network Error", "Failed to verify event status: " + e.getMessage());
                });
    }

    private void verifyJudgeAuthorizationAndProceed(Team team, Event event) {
        User currentJudge = firebaseManager.getCurrentUserProfile();
        if (currentJudge == null && firebaseManager.getCurrentFirebaseUser() != null) {
            // Fetch profile if in-memory cache is cold
            firebaseManager.fetchUserProfile(firebaseManager.getCurrentFirebaseUser().getUid())
                    .addOnSuccessListener(uDoc -> {
                        if (uDoc.exists()) {
                            User loadedJudge = uDoc.toObject(User.class);
                            if (loadedJudge != null) {
                                loadedJudge.setUid(uDoc.getId());
                                firebaseManager.setCurrentUserProfile(loadedJudge);
                                checkJudgePanelAndExistingEvaluation(loadedJudge, team, event);
                                return;
                            }
                        }
                        hideLoading();
                        showErrorDialog("Unauthorized", "Unable to load judge account authorization profile.");
                    })
                    .addOnFailureListener(e -> {
                        hideLoading();
                        showErrorDialog("Authorization Error", "Failed to retrieve judge permissions: " + e.getMessage());
                    });
        } else if (currentJudge != null) {
            checkJudgePanelAndExistingEvaluation(currentJudge, team, event);
        } else {
            hideLoading();
            showErrorDialog("Unauthorized", "You must be signed in as a judge to evaluate teams.");
        }
    }

    private void checkJudgePanelAndExistingEvaluation(User currentJudge, Team team, Event event) {
        if (!currentJudge.isJudge() && !currentJudge.isAdmin()) {
            hideLoading();
            showErrorDialog("Unauthorized", "Your account does not have Judge privileges.");
            return;
        }

        if (!currentJudge.isActive()) {
            hideLoading();
            showErrorDialog("Account Deactivated", "Your judge account has been deactivated. Please contact the administrator.");
            return;
        }

        // Security check: Verify panel assignment (Admins can evaluate any panel)
        if (!currentJudge.isAdmin()) {
            String assignedPanel = currentJudge.getPanelForEvent(team.getEventId());
            if (assignedPanel == null || assignedPanel.isEmpty()) {
                assignedPanel = currentJudge.getAssignedPanelId();
            }
            if ((assignedPanel == null || assignedPanel.isEmpty()) && judgePanelId != null && !judgePanelId.isEmpty()) {
                assignedPanel = judgePanelId;
            }

            if (assignedPanel == null || assignedPanel.isEmpty()) {
                hideLoading();
                showErrorDialog("Unassigned Judge", 
                        "You have not been assigned to an evaluation panel for this event. Please contact the administrator.");
                return;
            }

            if (team.getPanelId() != null && !team.getPanelId().isEmpty() && !assignedPanel.equals(team.getPanelId())) {
                hideLoading();
                String teamDisplay = event.isAnonymousJudging()
                        ? "Team #" + team.getTeamNumber()
                        : team.getTeamNumber() + " - " + team.getTeamName();
                showErrorDialog("Panel Mismatch", 
                        "This team (" + teamDisplay + ") is assigned to a different panel.\n\nYou are only authorized to evaluate teams in your assigned panel.");
                return;
            }
        }

        // Step 6: Check for existing submitted evaluation or draft
        String judgeId = currentJudge.getUid();
        firebaseManager.checkExistingEvaluation(team.getEventId(), team.getId(), judgeId)
                .addOnSuccessListener(evalDoc -> {
                    hideLoading();
                    if (evalDoc.exists()) {
                        Evaluation existingEval = evalDoc.toObject(Evaluation.class);
                        if (existingEval != null && existingEval.isSubmitted()) {
                            showEvaluatedDialog(team, event);
                            return;
                        }
                        // Existing draft found: proceed to form to resume draft!
                    }

                    // Open evaluation form directly!
                    openEvaluationForm(team, event);
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    showErrorDialog("Verification Error", 
                            "Unable to verify existing evaluation records: " + e.getMessage() + 
                            "\n\nEvaluation cannot proceed without verification.");
                });
    }

    private void openEvaluationForm(Team team, Event event) {
        Intent intent = new Intent(QRScannerActivity.this, EvaluationFormActivity.class);
        intent.putExtra(Constants.EXTRA_TEAM, team);
        intent.putExtra(Constants.EXTRA_EVENT, event);
        intent.putExtra(Constants.EXTRA_TEAM_ID, team.getId());
        intent.putExtra(Constants.EXTRA_EVENT_ID, team.getEventId());
        startActivity(intent);
        finish();
    }

    private void showLoading(String message) {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
            loadingDialog = UIUtils.showLoadingDialog(this, message);
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    private void showErrorDialog(String title, String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Try Again", (dialog, which) -> {
                        dialog.dismiss();
                        isProcessing = false;
                        if (barcodeScannerView != null) {
                            barcodeScannerView.resume();
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    private void showEvaluatedDialog(Team team, Event event) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            String teamDisplay = (event != null && event.isAnonymousJudging()) 
                    ? "Team #" + team.getTeamNumber() 
                    : team.getTeamNumber() + " (" + team.getTeamName() + ")";
            new AlertDialog.Builder(this)
                    .setTitle("Already Evaluated")
                    .setMessage("You have already submitted an evaluation for " + teamDisplay + ". Duplicate submissions are not permitted.")
                    .setPositiveButton("View Receipt", (dialog, which) -> {
                        dialog.dismiss();
                        User currentJudge = firebaseManager.getCurrentUserProfile();
                        String judgeId = currentJudge != null ? currentJudge.getUid() : 
                                (firebaseManager.getCurrentFirebaseUser() != null ? firebaseManager.getCurrentFirebaseUser().getUid() : "");

                        Intent intent = new Intent(QRScannerActivity.this, SubmissionConfirmationActivity.class);
                        intent.putExtra(Constants.EXTRA_TEAM, team);
                        intent.putExtra(Constants.EXTRA_JUDGE_ID, judgeId);
                        intent.putExtra(Constants.EXTRA_EVENT_ID, team.getEventId());
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Done", (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                UIUtils.showToast(this, "Camera permission is required to scan QR codes.");
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeScannerView != null) {
            barcodeScannerView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeScannerView != null) {
            barcodeScannerView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideLoading();
    }
}
