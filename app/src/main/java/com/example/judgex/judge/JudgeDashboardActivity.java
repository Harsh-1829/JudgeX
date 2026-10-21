package com.example.judgex.judge;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.TeamAdapter;
import com.example.judgex.auth.LoginActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.qr.QRViewActivity;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JudgeDashboardActivity extends AppCompatActivity implements TeamAdapter.OnTeamClickListener {

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvJudgeName, tvJudgeEmail, tvAssignedPanel, tvEventName, tvLockedBanner;
    private TextView tvCountAssigned, tvCountEvaluated, tvCountPending, tvEmptyTeams;
    private MaterialButton btnLogout, btnScan;
    private MaterialCardView cardScanQR;
    private RecyclerView recyclerViewAssignedTeams;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Team> assignedTeams = new ArrayList<>();
    private final Set<String> evaluatedTeamIds = new HashSet<>();
    private final Set<String> draftTeamIds = new HashSet<>();
    private TeamAdapter adapter;

    private User currentJudge;
    private Panel currentPanel;
    private Event currentEvent;
    private boolean isEvaluationsLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_judge_dashboard);

        initViews();
        loadJudgeProfileAndData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadJudgeProfileAndData();
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvJudgeName = findViewById(R.id.tvJudgeName);
        tvJudgeEmail = findViewById(R.id.tvJudgeEmail);
        tvAssignedPanel = findViewById(R.id.tvAssignedPanel);
        tvEventName = findViewById(R.id.tvEventName);
        tvLockedBanner = findViewById(R.id.tvLockedBanner);
        tvCountAssigned = findViewById(R.id.tvCountAssigned);
        tvCountEvaluated = findViewById(R.id.tvCountEvaluated);
        tvCountPending = findViewById(R.id.tvCountPending);
        tvEmptyTeams = findViewById(R.id.tvEmptyTeams);
        btnLogout = findViewById(R.id.btnLogout);
        btnScan = findViewById(R.id.btnScan);
        cardScanQR = findViewById(R.id.cardScanQR);
        recyclerViewAssignedTeams = findViewById(R.id.recyclerViewAssignedTeams);

        recyclerViewAssignedTeams.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TeamAdapter(assignedTeams, evaluatedTeamIds, draftTeamIds, this);
        recyclerViewAssignedTeams.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadJudgeProfileAndData);

        btnLogout.setOnClickListener(v -> {
            UIUtils.showConfirmationDialog(this, "Sign Out", "Are you sure you want to sign out?", "Sign Out", () -> {
                firebaseManager.signOut();
                Intent intent = new Intent(JudgeDashboardActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });

        View.OnClickListener scanClickListener = v -> {
            if (isEvaluationsLocked) {
                UIUtils.showToast(this, "Evaluations are currently locked by the Admin.");
                return;
            }
            if (currentJudge == null || currentJudge.getAssignedPanelId() == null || currentJudge.getAssignedPanelId().isEmpty()) {
                UIUtils.showToast(this, "You have not been assigned to an evaluation panel yet. Please contact the Admin.");
                return;
            }
            Intent intent = new Intent(JudgeDashboardActivity.this, QRScannerActivity.class);
            intent.putExtra(Constants.EXTRA_PANEL_ID, currentJudge.getAssignedPanelId());
            if (currentEvent != null) {
                intent.putExtra(Constants.EXTRA_EVENT_ID, currentEvent.getId());
            }
            startActivity(intent);
        };

        cardScanQR.setOnClickListener(scanClickListener);
        btnScan.setOnClickListener(scanClickListener);
    }

    private void loadJudgeProfileAndData() {
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
                        currentJudge = doc.toObject(User.class);
                        if (currentJudge != null) {
                            currentJudge.setUid(doc.getId());
                            firebaseManager.setCurrentUserProfile(currentJudge);

                            tvJudgeName.setText(currentJudge.getName() != null ? currentJudge.getName() : "Judge");
                            tvJudgeEmail.setText(currentJudge.getEmail() != null ? currentJudge.getEmail() : "");

                            String panelId = currentJudge.getAssignedPanelId();
                            if (panelId != null && !panelId.isEmpty()) {
                                loadPanelDetails(panelId);
                            } else {
                                tvAssignedPanel.setText("Panel: Unassigned");
                                tvEventName.setText("Contact admin for assignment");
                                updateTeamsUI();
                                swipeRefreshLayout.setRefreshing(false);
                            }
                            return;
                        }
                    }
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Error loading profile: " + e.getMessage());
                });
    }

    private void loadPanelDetails(String panelId) {
        firebaseManager.getPanel(panelId)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentPanel = doc.toObject(Panel.class);
                        if (currentPanel != null) {
                            currentPanel.setId(doc.getId());
                            String venue = currentPanel.getVenue() != null && !currentPanel.getVenue().isEmpty() 
                                    ? " (" + currentPanel.getVenue() + ")" : "";
                            tvAssignedPanel.setText("🏛️ " + currentPanel.getName() + venue);

                            if (currentPanel.getEventId() != null) {
                                loadEventDetails(currentPanel.getEventId());
                            }
                            loadPanelTeams(panelId);
                            return;
                        }
                    }
                    tvAssignedPanel.setText("Panel: Assigned");
                    loadPanelTeams(panelId);
                })
                .addOnFailureListener(e -> loadPanelTeams(panelId));
    }

    private void loadEventDetails(String eventId) {
        firebaseManager.getEvent(eventId)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentEvent = doc.toObject(Event.class);
                        if (currentEvent != null) {
                            currentEvent.setId(doc.getId());
                            tvEventName.setText("Event: " + currentEvent.getName());
                            isEvaluationsLocked = currentEvent.isLocked();

                            if (isEvaluationsLocked) {
                                tvLockedBanner.setVisibility(View.VISIBLE);
                            } else {
                                tvLockedBanner.setVisibility(View.GONE);
                            }
                        }
                    }
                });
    }

    private void loadPanelTeams(String panelId) {
        // First load evaluations by this judge to check which teams are evaluated vs in-progress drafts
        firebaseManager.getEvaluationsByJudge(currentJudge.getUid())
                .addOnSuccessListener(evalSnapshots -> {
                    evaluatedTeamIds.clear();
                    draftTeamIds.clear();
                    for (DocumentSnapshot doc : evalSnapshots.getDocuments()) {
                        Evaluation eval = doc.toObject(Evaluation.class);
                        if (eval != null && eval.getTeamId() != null) {
                            if (eval.isSubmitted()) {
                                evaluatedTeamIds.add(eval.getTeamId());
                            } else if (eval.isDraft()) {
                                draftTeamIds.add(eval.getTeamId());
                            }
                        }
                    }

                    // Now load teams for this panel
                    firebaseManager.getTeamsForPanel(panelId)
                            .addOnSuccessListener(teamSnapshots -> {
                                assignedTeams.clear();
                                for (DocumentSnapshot doc : teamSnapshots.getDocuments()) {
                                    Team team = doc.toObject(Team.class);
                                    if (team != null) {
                                        team.setId(doc.getId());
                                        assignedTeams.add(team);
                                    }
                                }

                                updateTeamsUI();
                                swipeRefreshLayout.setRefreshing(false);
                            })
                            .addOnFailureListener(e -> {
                                swipeRefreshLayout.setRefreshing(false);
                                UIUtils.showToast(this, "Failed to load assigned teams: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to check evaluations: " + e.getMessage());
                });
    }

    private void updateTeamsUI() {
        int totalAssigned = assignedTeams.size();
        int totalEvaluated = 0;
        int totalDrafts = 0;
        for (Team team : assignedTeams) {
            if (evaluatedTeamIds.contains(team.getId())) {
                totalEvaluated++;
            } else if (draftTeamIds.contains(team.getId())) {
                totalDrafts++;
            }
        }
        int totalPending = totalAssigned - totalEvaluated;

        tvCountAssigned.setText(String.valueOf(totalAssigned));
        tvCountEvaluated.setText(String.valueOf(totalEvaluated));
        tvCountPending.setText(String.valueOf(totalPending));

        if (assignedTeams.isEmpty()) {
            tvEmptyTeams.setVisibility(View.VISIBLE);
            recyclerViewAssignedTeams.setVisibility(View.GONE);
        } else {
            tvEmptyTeams.setVisibility(View.GONE);
            recyclerViewAssignedTeams.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public void onTeamClick(Team team) {
        if (evaluatedTeamIds.contains(team.getId())) {
            // Already evaluated by this judge - show existing evaluation receipt
            Intent intent = new Intent(this, SubmissionConfirmationActivity.class);
            intent.putExtra(Constants.EXTRA_TEAM, team);
            intent.putExtra(Constants.EXTRA_JUDGE_ID, currentJudge.getUid());
            intent.putExtra(Constants.EXTRA_EVENT_ID, team.getEventId());
            startActivity(intent);
            return;
        }

        if (isEvaluationsLocked) {
            UIUtils.showToast(this, "Evaluations are currently locked by the Admin.");
            return;
        }

        // Open evaluation form
        Intent intent = new Intent(this, EvaluationFormActivity.class);
        intent.putExtra(Constants.EXTRA_TEAM, team);
        if (currentEvent != null) {
            intent.putExtra(Constants.EXTRA_EVENT, currentEvent);
        }
        startActivity(intent);
    }

    @Override
    public void onQRClick(Team team) {
        Intent intent = new Intent(this, QRViewActivity.class);
        intent.putExtra(Constants.EXTRA_TEAM, team);
        startActivity(intent);
    }
}
