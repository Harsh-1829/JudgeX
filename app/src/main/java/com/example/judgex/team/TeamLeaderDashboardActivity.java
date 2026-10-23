package com.example.judgex.team;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.auth.LoginActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamResult;
import com.example.judgex.models.User;
import com.example.judgex.qr.QRCodeGenerator;
import com.example.judgex.qr.QRViewActivity;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TeamLeaderDashboardActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvLeaderGreeting, tvEventName, tvEventStatus;
    private TextView tvTeamNumber, tvRegistrationBadge, tvTeamName, tvProjectTitle;
    private TextView tvCategory, tvMemberCount, tvAssignedPanel;
    private ImageView ivQRCode;
    private MaterialButton btnLogout, btnShareQR, btnFullScreenQR;

    // Results card views
    private MaterialCardView cardTeamResult;
    private TextView tvResultStatusBadge, tvResultStatusSummary;
    private LinearLayout llScoreDetails;
    private TextView tvTeamRank, tvTeamScore, tvEvaluationsCount;
    private MaterialButton btnSwitchEvent;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Team> leaderParticipations = new ArrayList<>();
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

        cardTeamResult = findViewById(R.id.cardTeamResult);
        tvResultStatusBadge = findViewById(R.id.tvResultStatusBadge);
        tvResultStatusSummary = findViewById(R.id.tvResultStatusSummary);
        llScoreDetails = findViewById(R.id.llScoreDetails);
        tvTeamRank = findViewById(R.id.tvTeamRank);
        tvTeamScore = findViewById(R.id.tvTeamScore);
        tvEvaluationsCount = findViewById(R.id.tvEvaluationsCount);
        btnSwitchEvent = findViewById(R.id.btnSwitchEvent);

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

        btnSwitchEvent.setOnClickListener(v -> showSwitchParticipationDialog());
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

                            queryAllParticipations(uid, currentLeader.getEmail(), currentLeader.getAssignedTeamId());
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

    private void queryAllParticipations(String uid, String email, String assignedTeamId) {
        leaderParticipations.clear();

        // Query by leaderUid first
        firebaseManager.getTeamsCollection().whereEqualTo("leaderUid", uid).get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        Team t = doc.toObject(Team.class);
                        if (t != null && !t.isDeleted()) {
                            t.setId(doc.getId());
                            leaderParticipations.add(t);
                        }
                    }

                    if (!leaderParticipations.isEmpty()) {
                        updateSwitchEventButton();
                        onTeamLoaded(leaderParticipations.get(0));
                        return;
                    }

                    // Fallback 1: Query by leaderEmail
                    if (email != null && !email.isEmpty()) {
                        firebaseManager.getTeamsCollection().whereEqualTo("leaderEmail", email.toLowerCase().trim()).get()
                                .addOnSuccessListener(eqs -> {
                                    for (DocumentSnapshot doc : eqs.getDocuments()) {
                                        Team t = doc.toObject(Team.class);
                                        if (t != null && !t.isDeleted()) {
                                            t.setId(doc.getId());
                                            leaderParticipations.add(t);
                                        }
                                    }

                                    if (!leaderParticipations.isEmpty()) {
                                        updateSwitchEventButton();
                                        onTeamLoaded(leaderParticipations.get(0));
                                        return;
                                    }

                                    // Fallback 2: Check directly assignedTeamId
                                    if (assignedTeamId != null && !assignedTeamId.isEmpty()) {
                                        loadTeamById(assignedTeamId);
                                    } else {
                                        showNoTeamAssigned();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (assignedTeamId != null && !assignedTeamId.isEmpty()) {
                                        loadTeamById(assignedTeamId);
                                    } else {
                                        showNoTeamAssigned();
                                    }
                                });
                    } else if (assignedTeamId != null && !assignedTeamId.isEmpty()) {
                        loadTeamById(assignedTeamId);
                    } else {
                        showNoTeamAssigned();
                    }
                })
                .addOnFailureListener(e -> {
                    if (assignedTeamId != null && !assignedTeamId.isEmpty()) {
                        loadTeamById(assignedTeamId);
                    } else {
                        showNoTeamAssigned();
                    }
                });
    }

    private void updateSwitchEventButton() {
        if (leaderParticipations.size() > 1) {
            btnSwitchEvent.setVisibility(View.VISIBLE);
            btnSwitchEvent.setText("Switch Event (" + leaderParticipations.size() + " Participations)");
        } else {
            btnSwitchEvent.setVisibility(View.GONE);
        }
    }

    private void showSwitchParticipationDialog() {
        if (leaderParticipations.size() <= 1) return;

        List<String> items = new ArrayList<>();
        for (Team t : leaderParticipations) {
            items.add(t.getTeamNumber() + " - " + t.getTeamName() + " (" + t.getProjectTitle() + ")");
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Competition Participation")
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    Team selected = leaderParticipations.get(which);
                    onTeamLoaded(selected);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadTeamById(String teamId) {
        firebaseManager.getTeam(teamId)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Team team = doc.toObject(Team.class);
                        if (team != null && !team.isDeleted()) {
                            team.setId(doc.getId());
                            leaderParticipations.clear();
                            leaderParticipations.add(team);
                            updateSwitchEventButton();
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
            tvRegistrationBadge.setText("Unregistered");
            tvRegistrationBadge.setBackgroundResource(R.drawable.bg_badge_locked);
            tvRegistrationBadge.setTextColor(ContextCompat.getColor(this, R.color.danger));
        } else {
            tvRegistrationBadge.setText("Registered");
            tvRegistrationBadge.setBackgroundResource(R.drawable.bg_badge_success);
            tvRegistrationBadge.setTextColor(ContextCompat.getColor(this, R.color.success));
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
                        loadTeamResults();
                    })
                    .addOnFailureListener(e -> loadTeamResults());
        } else {
            loadTeamResults();
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
                                tvAssignedPanel.setText(currentPanel.getName() + venue);
                                return;
                            }
                        }
                        tvAssignedPanel.setText("Panel Assigned");
                    })
                    .addOnFailureListener(e -> {
                        swipeRefreshLayout.setRefreshing(false);
                        tvAssignedPanel.setText("Panel Assigned");
                    });
        } else {
            swipeRefreshLayout.setRefreshing(false);
            tvAssignedPanel.setText("Awaiting Panel Assignment");
        }
    }

    private void loadTeamResults() {
        if (currentTeam == null || currentTeam.getId() == null) return;

        firebaseManager.getEvaluationsForTeam(currentTeam.getId())
                .addOnSuccessListener(querySnapshot -> {
                    List<Evaluation> validSubmitted = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Evaluation eval = doc.toObject(Evaluation.class);
                        if (eval != null && eval.isSubmitted()) {
                            validSubmitted.add(eval);
                        }
                    }

                    int count = validSubmitted.size();
                    boolean isPublished = currentEvent != null && (currentEvent.isCompleted() || currentEvent.isLocked());

                    if (count > 0) {
                        double sum = 0.0;
                        double maxPossible = 100.0;
                        for (Evaluation ev : validSubmitted) {
                            sum += ev.getTotalScore();
                            if (ev.getMaxPossibleScore() > 0) {
                                maxPossible = ev.getMaxPossibleScore();
                            }
                        }
                        double avg = sum / count;

                        llScoreDetails.setVisibility(View.VISIBLE);
                        tvTeamScore.setText(String.format(Locale.US, "%.1f", avg));
                        tvEvaluationsCount.setText(String.valueOf(count));

                        if (isPublished) {
                            tvResultStatusBadge.setText("FINAL");
                            tvResultStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                            tvResultStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.success));
                            tvResultStatusSummary.setText("Official competition standings are finalized.");
                            computeRankAndDisplay(avg);
                        } else {
                            tvResultStatusBadge.setText("PROVISIONAL");
                            tvResultStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                            tvResultStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.warning));
                            tvResultStatusSummary.setText("Evaluations in progress (" + count + " submitted). Detailed standings will be published once the event concludes.");
                            tvTeamRank.setText("—");
                        }
                    } else {
                        llScoreDetails.setVisibility(View.GONE);
                        tvResultStatusBadge.setText(isPublished ? "UNRANKED" : "PENDING");
                        tvResultStatusBadge.setBackgroundResource(R.drawable.bg_badge_locked);
                        tvResultStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                        tvResultStatusSummary.setText(isPublished
                                ? "No evaluations were submitted for this team before the competition closed."
                                : "Your team has not been evaluated yet. Present your QR pass to the panel when invited.");
                    }
                })
                .addOnFailureListener(e -> {
                    llScoreDetails.setVisibility(View.GONE);
                    tvResultStatusSummary.setText("Unable to load team evaluation status.");
                });
    }

    private void computeRankAndDisplay(double myAverageScore) {
        if (currentTeam == null || currentTeam.getEventId() == null) return;

        firebaseManager.getEvaluationsForEvent(currentTeam.getEventId())
                .addOnSuccessListener(allEvalsSnapshots -> {
                    Map<String, List<Double>> teamScores = new HashMap<>();
                    for (DocumentSnapshot doc : allEvalsSnapshots.getDocuments()) {
                        Evaluation ev = doc.toObject(Evaluation.class);
                        if (ev != null && ev.isSubmitted() && ev.getTeamId() != null) {
                            if (!teamScores.containsKey(ev.getTeamId())) {
                                teamScores.put(ev.getTeamId(), new ArrayList<>());
                            }
                            teamScores.get(ev.getTeamId()).add(ev.getTotalScore());
                        }
                    }

                    List<Double> teamAverages = new ArrayList<>();
                    for (Map.Entry<String, List<Double>> entry : teamScores.entrySet()) {
                        List<Double> scores = entry.getValue();
                        if (!scores.isEmpty()) {
                            double s = 0.0;
                            for (Double d : scores) s += d;
                            teamAverages.add(s / scores.size());
                        }
                    }

                    Collections.sort(teamAverages, Collections.reverseOrder());

                    int computedRank = 1;
                    for (int i = 0; i < teamAverages.size(); i++) {
                        if (myAverageScore < teamAverages.get(i) - 0.001) {
                            computedRank = i + 2;
                        } else {
                            break;
                        }
                    }

                    tvTeamRank.setText("#" + computedRank);
                });
    }

    private void renderQRCode() {
        if (currentTeam == null) return;
        String qrPayload = currentTeam.getQrCodeData();
        if (qrPayload == null || qrPayload.isEmpty() || !qrPayload.startsWith("{")) {
            try {
                JSONObject json = new JSONObject();
                json.put("v", 2);
                json.put("teamId", currentTeam.getId());
                json.put("teamNumber", currentTeam.getTeamNumber());
                json.put("teamName", currentTeam.getTeamName());
                json.put("eventId", currentTeam.getEventId());
                json.put("panelId", currentTeam.getPanelId());
                json.put("issuedAt", System.currentTimeMillis());
                json.put("token", UUID.randomUUID().toString());
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
        cardTeamResult.setVisibility(View.GONE);
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
