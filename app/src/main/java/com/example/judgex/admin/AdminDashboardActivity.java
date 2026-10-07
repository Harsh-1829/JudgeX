package com.example.judgex.admin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.auth.LoginActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

public class AdminDashboardActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvAdminEmail, tvActiveEventName, tvEventLockStatus;
    private TextView tvCountPanels, tvCountTeams, tvCountJudges, tvCountEvaluations;
    private MaterialButton btnToggleLock, btnManageEvents, btnLogout;
    private MaterialCardView cardPanels, cardJudges, cardTeams, cardCriteria, cardProgress, cardRankings;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private Event activeEvent;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);

        initViews();
        setupClickListeners();
        loadActiveEventAndData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadActiveEventAndData();
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvAdminEmail = findViewById(R.id.tvAdminEmail);
        tvActiveEventName = findViewById(R.id.tvActiveEventName);
        tvEventLockStatus = findViewById(R.id.tvEventLockStatus);
        tvCountPanels = findViewById(R.id.tvCountPanels);
        tvCountTeams = findViewById(R.id.tvCountTeams);
        tvCountJudges = findViewById(R.id.tvCountJudges);
        tvCountEvaluations = findViewById(R.id.tvCountEvaluations);
        btnToggleLock = findViewById(R.id.btnToggleLock);
        btnManageEvents = findViewById(R.id.btnManageEvents);
        btnLogout = findViewById(R.id.btnLogout);

        cardPanels = findViewById(R.id.cardPanels);
        cardJudges = findViewById(R.id.cardJudges);
        cardTeams = findViewById(R.id.cardTeams);
        cardCriteria = findViewById(R.id.cardCriteria);
        cardProgress = findViewById(R.id.cardProgress);
        cardRankings = findViewById(R.id.cardRankings);

        User profile = firebaseManager.getCurrentUserProfile();
        if (profile != null && profile.getEmail() != null) {
            tvAdminEmail.setText(profile.getEmail());
        } else if (firebaseManager.getCurrentFirebaseUser() != null) {
            tvAdminEmail.setText(firebaseManager.getCurrentFirebaseUser().getEmail());
        }

        swipeRefreshLayout.setOnRefreshListener(this::loadActiveEventAndData);
    }

    private void setupClickListeners() {
        btnLogout.setOnClickListener(v -> {
            UIUtils.showConfirmationDialog(this, "Sign Out", "Are you sure you want to sign out?", "Sign Out", () -> {
                firebaseManager.signOut();
                Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });

        btnManageEvents.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, EventManagerActivity.class);
            startActivity(intent);
        });

        btnToggleLock.setOnClickListener(v -> toggleEventLock());

        cardPanels.setOnClickListener(v -> navigateIfEventSelected(PanelManagerActivity.class));
        cardJudges.setOnClickListener(v -> navigateIfEventSelected(JudgeManagerActivity.class));
        cardTeams.setOnClickListener(v -> navigateIfEventSelected(TeamManagerActivity.class));
        cardCriteria.setOnClickListener(v -> navigateIfEventSelected(CriteriaManagerActivity.class));
        cardProgress.setOnClickListener(v -> navigateIfEventSelected(EvaluationProgressActivity.class));
        cardRankings.setOnClickListener(v -> navigateIfEventSelected(ResultsRankingActivity.class));
    }

    private void navigateIfEventSelected(Class<?> activityClass) {
        if (activeEvent == null) {
            UIUtils.showToast(this, "Please create or select an active event first!");
            startActivity(new Intent(AdminDashboardActivity.this, EventManagerActivity.class));
            return;
        }
        Intent intent = new Intent(AdminDashboardActivity.this, activityClass);
        intent.putExtra(Constants.EXTRA_EVENT, activeEvent);
        intent.putExtra(Constants.EXTRA_EVENT_ID, activeEvent.getId());
        startActivity(intent);
    }

    private void loadActiveEventAndData() {
        swipeRefreshLayout.setRefreshing(true);
        String savedEventId = prefs.getString(Constants.PREF_ACTIVE_EVENT_ID, null);

        if (savedEventId != null) {
            firebaseManager.getEvent(savedEventId)
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            activeEvent = doc.toObject(Event.class);
                            if (activeEvent != null) {
                                activeEvent.setId(doc.getId());
                                updateActiveEventUI();
                                loadDashboardMetrics();
                                return;
                            }
                        }
                        fetchFallbackEvent();
                    })
                    .addOnFailureListener(e -> fetchFallbackEvent());
        } else {
            fetchFallbackEvent();
        }
    }

    private void fetchFallbackEvent() {
        firebaseManager.getEventsCollection()
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        activeEvent = doc.toObject(Event.class);
                        if (activeEvent != null) {
                            activeEvent.setId(doc.getId());
                            prefs.edit().putString(Constants.PREF_ACTIVE_EVENT_ID, activeEvent.getId()).apply();
                            updateActiveEventUI();
                            loadDashboardMetrics();
                            return;
                        }
                    }
                    // No event found in system
                    activeEvent = null;
                    tvActiveEventName.setText("No Active Event Found");
                    tvEventLockStatus.setText("N/A");
                    btnToggleLock.setEnabled(false);
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load events: " + e.getMessage());
                });
    }

    private void updateActiveEventUI() {
        if (activeEvent == null) return;

        tvActiveEventName.setText(activeEvent.getName());
        btnToggleLock.setEnabled(true);

        if (activeEvent.isLocked()) {
            tvEventLockStatus.setText("EVALUATIONS LOCKED");
            tvEventLockStatus.setBackgroundResource(R.drawable.bg_badge_locked);
            tvEventLockStatus.setTextColor(getResources().getColor(R.color.danger));
            btnToggleLock.setText("🔓 Unlock Evaluations");
        } else {
            tvEventLockStatus.setText("OPEN FOR EVALUATION");
            tvEventLockStatus.setBackgroundResource(R.drawable.bg_badge_success);
            tvEventLockStatus.setTextColor(getResources().getColor(R.color.success));
            btnToggleLock.setText("🔒 Lock Evaluations");
        }
    }

    private void toggleEventLock() {
        if (activeEvent == null) return;
        boolean newLockStatus = !activeEvent.isLocked();
        String actionName = newLockStatus ? "Lock" : "Unlock";

        UIUtils.showConfirmationDialog(this, actionName + " Evaluations",
                "Are you sure you want to " + actionName.toLowerCase() + " evaluations for " + activeEvent.getName() + "?",
                actionName, () -> {
                    firebaseManager.setEventLockStatus(activeEvent.getId(), newLockStatus)
                            .addOnSuccessListener(aVoid -> {
                                activeEvent.setLocked(newLockStatus);
                                updateActiveEventUI();
                                UIUtils.showToast(this, "Evaluations are now " + (newLockStatus ? "locked" : "unlocked"));
                            })
                            .addOnFailureListener(e -> UIUtils.showToast(this, "Failed to update lock status: " + e.getMessage()));
                });
    }

    private void loadDashboardMetrics() {
        if (activeEvent == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        String eventId = activeEvent.getId();

        // 1. Panels Count
        firebaseManager.getPanelsForEvent(eventId)
                .addOnSuccessListener(qs -> tvCountPanels.setText(String.valueOf(qs.size())))
                .addOnFailureListener(e -> tvCountPanels.setText("0"));

        // 2. Teams Count
        firebaseManager.getTeamsForEvent(eventId)
                .addOnSuccessListener(qs -> tvCountTeams.setText(String.valueOf(qs.size())))
                .addOnFailureListener(e -> tvCountTeams.setText("0"));

        // 3. Judges Count
        firebaseManager.getAllJudges()
                .addOnSuccessListener(qs -> {
                    int count = 0;
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        User u = doc.toObject(User.class);
                        if (u != null && (u.isJudge() || (u.getRole() != null && !u.isAdmin()))) {
                            count++;
                        }
                    }
                    tvCountJudges.setText(String.valueOf(count));
                })
                .addOnFailureListener(e -> tvCountJudges.setText("0"));

        // 4. Evaluations Count
        firebaseManager.getEvaluationsForEvent(eventId)
                .addOnSuccessListener(qs -> {
                    tvCountEvaluations.setText(String.valueOf(qs.size()));
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    tvCountEvaluations.setText("0");
                    swipeRefreshLayout.setRefreshing(false);
                });
    }
}
