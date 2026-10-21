package com.example.judgex.team;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.auth.LoginActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.qr.QRCodeGenerator;
import com.example.judgex.qr.QRViewActivity;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONObject;

public class TeamLeaderDashboardActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvLeaderGreeting, tvEventName, tvEventStatus;
    private TextView tvTeamNumber, tvRegistrationBadge, tvTeamName, tvProjectTitle;
    private TextView tvCategory, tvMemberCount, tvAssignedPanel;
    private ImageView ivQRCode;
    private MaterialButton btnLogout, btnShareQR, btnFullScreenQR;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private User currentLeader;
    private Team currentTeam;
    private Event currentEvent;
    private Panel currentPanel;
    private Bitmap qrBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_leader_dashboard);

        initViews();
        loadLeaderAndTeamData();
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvLeaderGreeting = findViewById(R.id.tvLeaderGreeting);
        tvEventName = findViewById(R.id.tvEventName);
        tvEventStatus = findViewById(R.id.tvEventStatus);
        tvTeamNumber = findViewById(R.id.tvTeamNumber);
        tvRegistrationBadge = findViewById(R.id.tvRegistrationBadge);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvProjectTitle = findViewById(R.id.tvProjectTitle);
        tvCategory = findViewById(R.id.tvCategory);
        tvMemberCount = findViewById(R.id.tvMemberCount);
        tvAssignedPanel = findViewById(R.id.tvAssignedPanel);
        ivQRCode = findViewById(R.id.ivQRCode);
        btnLogout = findViewById(R.id.btnLogout);
        btnShareQR = findViewById(R.id.btnShareQR);
        btnFullScreenQR = findViewById(R.id.btnFullScreenQR);

        swipeRefreshLayout.setOnRefreshListener(this::loadLeaderAndTeamData);

        btnLogout.setOnClickListener(v -> {
            UIUtils.showConfirmationDialog(this, "Sign Out", "Are you sure you want to sign out?", "Sign Out", () -> {
                firebaseManager.signOut();
                Intent intent = new Intent(TeamLeaderDashboardActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });

        btnShareQR.setOnClickListener(v -> sharePass());
        btnFullScreenQR.setOnClickListener(v -> {
            if (currentTeam != null) {
                Intent intent = new Intent(TeamLeaderDashboardActivity.this, QRViewActivity.class);
                intent.putExtra(Constants.EXTRA_TEAM, currentTeam);
                startActivity(intent);
            }
        });
    }

    private void loadLeaderAndTeamData() {
        swipeRefreshLayout.setRefreshing(true);
        if (firebaseManager.getCurrentFirebaseUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String uid = firebaseManager.getCurrentFirebaseUser().getUid();
        firebaseManager.fetchUserProfile(uid)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentLeader = doc.toObject(User.class);
                        if (currentLeader != null) {
                            currentLeader.setUid(doc.getId());
                            firebaseManager.setCurrentUserProfile(currentLeader);
                            tvLeaderGreeting.setText("Welcome, " + (currentLeader.getName() != null ? currentLeader.getName() : "Team Leader"));

                            String teamId = currentLeader.getAssignedTeamId();
                            if (teamId != null && !teamId.isEmpty()) {
                                loadTeamById(teamId);
                            } else {
                                // Fallback: query team by leaderUid
                                firebaseManager.getTeamForLeader(uid)
                                        .addOnSuccessListener(qs -> {
                                            if (!qs.isEmpty()) {
                                                DocumentSnapshot tDoc = qs.getDocuments().get(0);
                                                Team team = tDoc.toObject(Team.class);
                                                if (team != null) {
                                                    team.setId(tDoc.getId());
                                                    onTeamLoaded(team);
                                                    return;
                                                }
                                            }
                                            showNoTeamAssigned();
                                        })
                                        .addOnFailureListener(e -> showNoTeamAssigned());
                            }
                            return;
                        }
                    }
                    showNoTeamAssigned();
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load leader profile: " + e.getMessage());
                });
    }

    private void loadTeamById(String teamId) {
        firebaseManager.getTeam(teamId)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Team team = doc.toObject(Team.class);
                        if (team != null) {
                            team.setId(doc.getId());
                            onTeamLoaded(team);
                            return;
                        }
                    }
                    showNoTeamAssigned();
                })
                .addOnFailureListener(e -> showNoTeamAssigned());
    }

    private void onTeamLoaded(Team team) {
        currentTeam = team;
        tvTeamNumber.setText(team.getTeamNumber() != null ? team.getTeamNumber() : "TM");
        tvTeamName.setText(team.getTeamName() != null ? team.getTeamName() : "Team");
        tvProjectTitle.setText(team.getProjectTitle() != null ? team.getProjectTitle() : "Project Title");
        tvCategory.setText(team.getCategory() != null ? team.getCategory() : "General");
        if (team.isUnregistered()) {
            tvRegistrationBadge.setText("⚠️ Unregistered");
            tvRegistrationBadge.setBackgroundResource(R.drawable.bg_badge_locked);
            tvRegistrationBadge.setTextColor(getResources().getColor(R.color.danger));
        } else {
            tvRegistrationBadge.setText("✓ Registered");
            tvRegistrationBadge.setBackgroundResource(R.drawable.bg_badge_success);
            tvRegistrationBadge.setTextColor(getResources().getColor(R.color.success));
        }

        renderQRCode();

        // Load Event Details
        if (team.getEventId() != null && !team.getEventId().isEmpty()) {
            firebaseManager.getEvent(team.getEventId())
                    .addOnSuccessListener(eDoc -> {
                        if (eDoc.exists()) {
                            currentEvent = eDoc.toObject(Event.class);
                            if (currentEvent != null) {
                                currentEvent.setId(eDoc.getId());
                                tvEventName.setText("Event: " + currentEvent.getName());
                                String status = currentEvent.getStatus() != null ? currentEvent.getStatus().toUpperCase() : "ACTIVE";
                                tvEventStatus.setText("Event Status: " + status);
                            }
                        }
                    });
        }

        // Load Panel Details
        if (team.getPanelId() != null && !team.getPanelId().isEmpty()) {
            firebaseManager.getPanel(team.getPanelId())
                    .addOnSuccessListener(pDoc -> {
                        swipeRefreshLayout.setRefreshing(false);
                        if (pDoc.exists()) {
                            currentPanel = pDoc.toObject(Panel.class);
                            if (currentPanel != null) {
                                currentPanel.setId(pDoc.getId());
                                String venue = currentPanel.getVenue() != null && !currentPanel.getVenue().isEmpty()
                                        ? " (" + currentPanel.getVenue() + ")" : "";
                                tvAssignedPanel.setText("🏛️ " + currentPanel.getName() + venue);
                                return;
                            }
                        }
                        tvAssignedPanel.setText("🏛️ Panel Assigned");
                    })
                    .addOnFailureListener(e -> {
                        swipeRefreshLayout.setRefreshing(false);
                        tvAssignedPanel.setText("🏛️ Panel Assigned");
                    });
        } else {
            swipeRefreshLayout.setRefreshing(false);
            tvAssignedPanel.setText("⏳ Awaiting Panel Assignment");
        }
    }

    private void renderQRCode() {
        if (currentTeam == null) return;
        String qrPayload = currentTeam.getQrCodeData();
        if (qrPayload == null || qrPayload.isEmpty()) {
            try {
                JSONObject json = new JSONObject();
                json.put("teamId", currentTeam.getId());
                json.put("teamNumber", currentTeam.getTeamNumber());
                json.put("teamName", currentTeam.getTeamName());
                json.put("eventId", currentTeam.getEventId());
                json.put("panelId", currentTeam.getPanelId());
                qrPayload = json.toString();
            } catch (Exception e) {
                qrPayload = currentTeam.getId();
            }
        }

        try {
            qrBitmap = QRCodeGenerator.generateQRCodeBitmap(qrPayload, 500, 500);
            ivQRCode.setImageBitmap(qrBitmap);
        } catch (Exception e) {
            UIUtils.showToast(this, "Could not render QR: " + e.getMessage());
        }
    }

    private void showNoTeamAssigned() {
        swipeRefreshLayout.setRefreshing(false);
        tvTeamName.setText("No Team Linked");
        tvProjectTitle.setText("Your account has not been linked to a registered team yet. Please contact the administrator.");
        tvRegistrationBadge.setText("Pending");
        tvAssignedPanel.setText("Unassigned");
    }

    private void sharePass() {
        if (currentTeam == null) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareBody = "JudgeX Team Evaluation Pass\n"
                + "Event: " + (currentEvent != null ? currentEvent.getName() : "Hackathon / Competition") + "\n"
                + "Team Number: " + currentTeam.getTeamNumber() + "\n"
                + "Team Name: " + currentTeam.getTeamName() + "\n"
                + "Project: " + currentTeam.getProjectTitle() + "\n"
                + "Panel: " + (currentPanel != null ? currentPanel.getName() : "Assigned Panel") + "\n"
                + "Leader: " + currentTeam.getLeaderName() + "\n"
                + "Scan via JudgeX App to evaluate.";
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "JudgeX Pass - " + currentTeam.getTeamNumber());
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(shareIntent, "Share Evaluation Pass"));
    }
}
