package com.example.judgex.admin;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class EventDetailActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvEventName, tvEventDescription;
    private TextView tvStatusBadge, tvScoringBadge, tvAnonymousBadge;
    private MaterialButton btnChangeLifecycle;
    private MaterialCardView cardPanels, cardJudges, cardTeams, cardCriteria, cardProgress, cardRankings, cardSettings;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private Event currentEvent;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
        if (eventId == null && currentEvent != null) {
            eventId = currentEvent.getId();
        }

        if (eventId == null || eventId.isEmpty()) {
            UIUtils.showToast(this, "No event selected.");
            finish();
            return;
        }

        initViews();
        loadEventDetails();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEventDetails();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvEventName = findViewById(R.id.tvEventName);
        tvEventDescription = findViewById(R.id.tvEventDescription);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvScoringBadge = findViewById(R.id.tvScoringBadge);
        tvAnonymousBadge = findViewById(R.id.tvAnonymousBadge);
        btnChangeLifecycle = findViewById(R.id.btnChangeLifecycle);

        cardPanels = findViewById(R.id.cardPanels);
        cardJudges = findViewById(R.id.cardJudges);
        cardTeams = findViewById(R.id.cardTeams);
        cardCriteria = findViewById(R.id.cardCriteria);
        cardProgress = findViewById(R.id.cardProgress);
        cardRankings = findViewById(R.id.cardRankings);
        cardSettings = findViewById(R.id.cardSettings);

        toolbar.setNavigationOnClickListener(v -> finish());
        swipeRefreshLayout.setOnRefreshListener(this::loadEventDetails);

        btnChangeLifecycle.setOnClickListener(v -> showChangeStatusDialog());

        cardPanels.setOnClickListener(v -> navigateToModule(PanelManagerActivity.class));
        cardJudges.setOnClickListener(v -> navigateToModule(JudgeManagerActivity.class));
        cardTeams.setOnClickListener(v -> navigateToModule(TeamManagerActivity.class));
        cardCriteria.setOnClickListener(v -> navigateToModule(CriteriaManagerActivity.class));
        cardProgress.setOnClickListener(v -> navigateToModule(EvaluationProgressActivity.class));
        cardRankings.setOnClickListener(v -> navigateToModule(ResultsRankingActivity.class));
        cardSettings.setOnClickListener(v -> showEditEventDialog());
    }

    private void navigateToModule(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.putExtra(Constants.EXTRA_EVENT, currentEvent);
        intent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
        startActivity(intent);
    }

    private void loadEventDetails() {
        swipeRefreshLayout.setRefreshing(true);
        firebaseManager.getEvent(eventId)
                .addOnSuccessListener(doc -> {
                    swipeRefreshLayout.setRefreshing(false);
                    if (doc.exists()) {
                        currentEvent = doc.toObject(Event.class);
                        if (currentEvent != null) {
                            currentEvent.setId(doc.getId());
                            renderEventUI();
                        }
                    } else {
                        UIUtils.showToast(this, "Event no longer exists.");
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load event: " + e.getMessage());
                });
    }

    private void renderEventUI() {
        tvEventName.setText(currentEvent.getName() != null ? currentEvent.getName() : "Event");
        tvEventDescription.setText(currentEvent.getDescription() != null && !currentEvent.getDescription().isEmpty()
                ? currentEvent.getDescription() : "No description provided");

        // Status Badge
        String status = currentEvent.getStatus() != null ? currentEvent.getStatus() : Event.STATUS_UPCOMING;
        tvStatusBadge.setText(status);
        if (Event.STATUS_ACTIVE.equalsIgnoreCase(status)) {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
        } else if (Event.STATUS_LOCKED.equalsIgnoreCase(status)) {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_locked);
        } else if (Event.STATUS_COMPLETED.equalsIgnoreCase(status)) {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_admin);
        } else {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending);
        }

        // Scoring Badge
        boolean isWeighted = Event.SCORING_WEIGHTED.equalsIgnoreCase(currentEvent.getScoringType());
        tvScoringBadge.setText(isWeighted ? "WEIGHTED (%)" : "RAW MARKS");

        // Anonymous Badge
        if (currentEvent.isAnonymousJudging()) {
            tvAnonymousBadge.setText("BLIND JUDGING");
            tvAnonymousBadge.setVisibility(View.VISIBLE);
        } else {
            tvAnonymousBadge.setText("STANDARD");
            tvAnonymousBadge.setVisibility(View.VISIBLE);
        }
    }

    private void showChangeStatusDialog() {
        String[] statuses = new String[]{
                Event.STATUS_UPCOMING + " (Planning & Setup)",
                Event.STATUS_ACTIVE + " (Open for Evaluations)",
                Event.STATUS_LOCKED + " (Evaluations Closed)",
                Event.STATUS_COMPLETED + " (Final Results Published)",
                Event.STATUS_ARCHIVED + " (Archived History)"
        };
        final String[] statusValues = new String[]{
                Event.STATUS_UPCOMING,
                Event.STATUS_ACTIVE,
                Event.STATUS_LOCKED,
                Event.STATUS_COMPLETED,
                Event.STATUS_ARCHIVED
        };

        int selectedIndex = 0;
        String cur = currentEvent.getStatus();
        for (int i = 0; i < statusValues.length; i++) {
            if (statusValues[i].equalsIgnoreCase(cur)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Event Lifecycle State")
                .setSingleChoiceItems(statuses, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    String newStatus = statusValues[which];
                    confirmStatusChange(newStatus);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmStatusChange(String newStatus) {
        String warning = "";
        if (Event.STATUS_LOCKED.equalsIgnoreCase(newStatus)) {
            warning = "\n\nWarning: When locked, judges cannot submit any new evaluations.";
        } else if (Event.STATUS_COMPLETED.equalsIgnoreCase(newStatus)) {
            warning = "\n\nWarning: When completed, results become permanent historical records.";
        }

        UIUtils.showConfirmationDialog(this, "Update Lifecycle",
                "Set status of '" + currentEvent.getName() + "' to " + newStatus + "?" + warning,
                "Update", () -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Updating event status...");
                    currentEvent.setStatus(newStatus);
                    currentEvent.setLocked(Event.STATUS_LOCKED.equalsIgnoreCase(newStatus));

                    firebaseManager.updateEvent(currentEvent)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Event status set to " + newStatus);
                                renderEventUI();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to update status: " + e.getMessage());
                            });
                });
    }

    private void showEditEventDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        tvDialogTitle.setText("Configure Event");

        TextInputLayout tilEventName = view.findViewById(R.id.tilEventName);
        TextInputLayout tilEventDescription = view.findViewById(R.id.tilEventDescription);
        TextInputEditText etEventName = view.findViewById(R.id.etEventName);
        TextInputEditText etEventDescription = view.findViewById(R.id.etEventDescription);

        RadioGroup rgScoringType = view.findViewById(R.id.rgScoringType);
        RadioButton rbScoringWeighted = view.findViewById(R.id.rbScoringWeighted);
        RadioButton rbScoringRaw = view.findViewById(R.id.rbScoringRaw);
        MaterialCheckBox cbAnonymousJudging = view.findViewById(R.id.cbAnonymousJudging);

        etEventName.setText(currentEvent.getName());
        etEventDescription.setText(currentEvent.getDescription());

        if (Event.SCORING_RAW.equalsIgnoreCase(currentEvent.getScoringType())) {
            rbScoringRaw.setChecked(true);
        } else {
            rbScoringWeighted.setChecked(true);
        }
        cbAnonymousJudging.setChecked(currentEvent.isAnonymousJudging());

        builder.setView(view);
        builder.setPositiveButton("Save Changes", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etEventName.getText() != null ? etEventName.getText().toString().trim() : "";
                String desc = etEventDescription.getText() != null ? etEventDescription.getText().toString().trim() : "";

                if (TextUtils.isEmpty(name)) {
                    tilEventName.setError("Event name is required");
                    return;
                }

                dialog.dismiss();
                Dialog loadingDialog = UIUtils.showLoadingDialog(this, "Saving configurations...");

                currentEvent.setName(name);
                currentEvent.setDescription(desc);
                currentEvent.setScoringType(rbScoringRaw.isChecked() ? Event.SCORING_RAW : Event.SCORING_WEIGHTED);
                currentEvent.setAnonymousJudging(cbAnonymousJudging.isChecked());

                firebaseManager.updateEvent(currentEvent)
                        .addOnSuccessListener(aVoid -> {
                            loadingDialog.dismiss();
                            UIUtils.showToast(this, "Event settings updated!");
                            renderEventUI();
                        })
                        .addOnFailureListener(e -> {
                            loadingDialog.dismiss();
                            UIUtils.showToast(this, "Error saving settings: " + e.getMessage());
                        });
            });
        });
        dialog.show();
    }
}
