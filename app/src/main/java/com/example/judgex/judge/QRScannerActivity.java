package com.example.judgex.judge;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.json.JSONObject;

import java.util.List;

public class QRScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 101;

    private DecoratedBarcodeView barcodeScannerView;
    private ImageButton btnClose, btnFlash;
    private CaptureManager capture;
    private boolean isFlashOn = false;
    private boolean isProcessing = false;

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
        checkCameraPermissionAndStart(savedInstanceState);
    }

    private void initViews() {
        barcodeScannerView = findViewById(R.id.barcodeScannerView);
        btnClose = findViewById(R.id.btnClose);
        btnFlash = findViewById(R.id.btnFlash);

        btnClose.setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> toggleFlash());
    }

    private void checkCameraPermissionAndStart(Bundle savedInstanceState) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            startScanning(savedInstanceState);
        }
    }

    private void startScanning(Bundle savedInstanceState) {
        capture = new CaptureManager(this, barcodeScannerView);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        
        barcodeScannerView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() != null && !isProcessing) {
                    isProcessing = true;
                    processScannedData(result.getText());
                }
            }

            @Override
            public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {}
        });
        
        capture.decode();
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
        if (!firebaseManager.isUserLoggedIn()) {
            showErrorDialog("Unauthorized", "You must be signed in as an authorized judge to evaluate teams.");
            return;
        }

        String teamId = rawData;
        String scannedEventId = null;
        String scannedPanelId = null;

        try {
            JSONObject json = new JSONObject(rawData);
            if (json.has("teamId")) {
                teamId = json.getString("teamId");
            }
            if (json.has("eventId")) {
                scannedEventId = json.getString("eventId");
            }
            if (json.has("panelId")) {
                scannedPanelId = json.getString("panelId");
            }
        } catch (Exception ignored) {
            // Raw text is assumed to be direct team ID
        }

        final String finalTeamId = teamId;
        final String finalScannedEventId = scannedEventId;

        // Fetch team record from Firestore
        firebaseManager.getTeam(finalTeamId)
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        showErrorDialog("Invalid QR Code", "No team found corresponding to this QR code in the system.");
                        return;
                    }

                    Team team = doc.toObject(Team.class);
                    if (team == null) {
                        showErrorDialog("Error", "Could not load team data.");
                        return;
                    }
                    team.setId(doc.getId());

                    // Check event consistency
                    String teamEventId = team.getEventId();
                    if (eventId != null && !eventId.isEmpty() && !eventId.equals(teamEventId)) {
                        showErrorDialog("Event Mismatch", 
                                "This team belongs to a different event. You are currently judging in a separate event.");
                        return;
                    }

                    if (finalScannedEventId != null && !finalScannedEventId.equals(teamEventId)) {
                        showErrorDialog("Invalid Badge", "The QR code event identifier does not match the team record.");
                        return;
                    }

                    // Verify event exists and is open
                    firebaseManager.getEvent(teamEventId)
                            .addOnSuccessListener(eDoc -> {
                                if (!eDoc.exists()) {
                                    showErrorDialog("Event Not Found", "The event for this team could not be found.");
                                    return;
                                }

                                Event event = eDoc.toObject(Event.class);
                                if (event == null) {
                                    showErrorDialog("Error", "Could not load event data.");
                                    return;
                                }
                                event.setId(eDoc.getId());

                                if (event.isLocked() || event.isCompleted()) {
                                    showErrorDialog("Event Locked", 
                                            "This event (" + event.getName() + ") is " + event.getStatus() + 
                                            ". Evaluations are closed and cannot accept new submissions.");
                                    return;
                                }

                                // Security check: Verify panel assignment
                                User currentJudge = firebaseManager.getCurrentUserProfile();
                                String assignedPanel = currentJudge != null 
                                        ? currentJudge.getPanelForEvent(teamEventId) 
                                        : judgePanelId;

                                if (assignedPanel != null && !assignedPanel.isEmpty() && team.getPanelId() != null) {
                                    if (!assignedPanel.equals(team.getPanelId())) {
                                        showErrorDialog("Panel Mismatch", 
                                                "This team (" + team.getTeamNumber() + " - " + team.getTeamName() + 
                                                ") is assigned to a different panel.\n\nYou are only authorized to evaluate teams in your assigned panel.");
                                        return;
                                    }
                                }

                                // Check if already evaluated by this judge
                                String judgeId = currentJudge != null ? currentJudge.getUid() : 
                                        (firebaseManager.getCurrentFirebaseUser() != null ? firebaseManager.getCurrentFirebaseUser().getUid() : "");

                                firebaseManager.checkExistingEvaluation(team.getEventId(), team.getId(), judgeId)
                                        .addOnSuccessListener(evalDoc -> {
                                            if (evalDoc.exists()) {
                                                showEvaluatedDialog(team);
                                            } else {
                                                // Proceed to evaluation form!
                                                Intent intent = new Intent(QRScannerActivity.this, EvaluationFormActivity.class);
                                                intent.putExtra(Constants.EXTRA_TEAM, team);
                                                intent.putExtra(Constants.EXTRA_EVENT, event);
                                                startActivity(intent);
                                                finish();
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            // Strict security: Never bypass essential authorization checks on network errors
                                            showErrorDialog("Network Error", 
                                                    "Unable to verify evaluation status due to connection error: " + e.getMessage() + 
                                                    "\n\nEvaluation cannot proceed without verification.");
                                        });
                            })
                            .addOnFailureListener(e -> showErrorDialog("Network Error", "Failed to verify event status: " + e.getMessage()));
                })
                .addOnFailureListener(e -> showErrorDialog("Scan Error", "Failed to retrieve team details: " + e.getMessage()));
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Try Again", (dialog, which) -> {
                    dialog.dismiss();
                    isProcessing = false;
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showEvaluatedDialog(Team team) {
        new AlertDialog.Builder(this)
                .setTitle("Already Evaluated")
                .setMessage("You have already submitted an evaluation for " + team.getTeamNumber() + " (" + team.getTeamName() + "). Duplicate submissions are not permitted.")
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning(null);
            } else {
                UIUtils.showToast(this, "Camera permission is required to scan QR codes.");
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (capture != null) capture.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (capture != null) capture.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (capture != null) capture.onDestroy();
    }
}
