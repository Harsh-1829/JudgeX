package com.example.judgex.admin;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EventDetailActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvEventName, tvEventDescription, tvEventSchedule, tvEventVenue, tvEventScheduleVenue;
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
        tvEventSchedule = findViewById(R.id.tvEventSchedule);
        tvEventVenue = findViewById(R.id.tvEventVenue);
        tvEventScheduleVenue = findViewById(R.id.tvEventScheduleVenue);
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

        // Schedule formatting: Date & Time in clear primary row
        StringBuilder scheduleStr = new StringBuilder();
        if (currentEvent.getScheduledDate() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.US);
            scheduleStr.append("📅 ").append(sdf.format(new Date(currentEvent.getScheduledDate())));
        }
        if (currentEvent.getStartTime() != null && !currentEvent.getStartTime().isEmpty()) {
            if (scheduleStr.length() > 0) scheduleStr.append("   ");
            scheduleStr.append("⏰ ").append(currentEvent.getStartTime());
            if (currentEvent.getEndTime() != null && !currentEvent.getEndTime().isEmpty()) {
                scheduleStr.append(" – ").append(currentEvent.getEndTime());
            }
        }

        if (scheduleStr.length() > 0) {
            tvEventSchedule.setText(scheduleStr.toString());
            tvEventSchedule.setVisibility(View.VISIBLE);
        } else {
            tvEventSchedule.setVisibility(View.GONE);
        }

        // Venue formatting: Independent dedicated line with location pin
        if (currentEvent.getVenue() != null && !currentEvent.getVenue().trim().isEmpty()) {
            tvEventVenue.setText("📍 " + currentEvent.getVenue().trim());
            tvEventVenue.setVisibility(View.VISIBLE);
        } else {
            tvEventVenue.setVisibility(View.GONE);
        }

        // Status Badge with high-contrast readable text
        String status = currentEvent.getStatus() != null ? currentEvent.getStatus() : Event.STATUS_UPCOMING;
        tvStatusBadge.setText(status);
        if (Event.STATUS_ACTIVE.equalsIgnoreCase(status)) {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
            tvStatusBadge.setTextColor(Color.parseColor("#166534")); // Deep readable green
        } else if (Event.STATUS_LOCKED.equalsIgnoreCase(status)) {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_locked);
            tvStatusBadge.setTextColor(Color.parseColor("#991B1B")); // Deep readable red
        } else if (Event.STATUS_COMPLETED.equalsIgnoreCase(status)) {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_admin);
            tvStatusBadge.setTextColor(Color.parseColor("#581C87")); // Deep readable purple
        } else {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending);
            tvStatusBadge.setTextColor(Color.parseColor("#92400E")); // Deep readable amber
        }

        // Scoring Badge with high contrast
        boolean isWeighted = Event.SCORING_WEIGHTED.equalsIgnoreCase(currentEvent.getScoringType());
        tvScoringBadge.setText(isWeighted ? "WEIGHTED (%)" : "RAW SUM");
        tvScoringBadge.setBackgroundResource(R.drawable.bg_badge_admin);
        tvScoringBadge.setTextColor(Color.parseColor("#581C87"));

        // Anonymous Judging Badge with high contrast
        if (currentEvent.isAnonymousJudging()) {
            tvAnonymousBadge.setText("BLIND JUDGING");
            tvAnonymousBadge.setBackgroundResource(R.drawable.bg_badge_judge);
            tvAnonymousBadge.setTextColor(Color.parseColor("#0369A1"));
            tvAnonymousBadge.setVisibility(View.VISIBLE);
        } else {
            tvAnonymousBadge.setText("STANDARD");
            tvAnonymousBadge.setBackgroundResource(R.drawable.bg_badge_judge);
            tvAnonymousBadge.setTextColor(Color.parseColor("#0369A1"));
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
        TextInputLayout tilEventDate = view.findViewById(R.id.tilEventDate);
        TextInputLayout tilStartTime = view.findViewById(R.id.tilStartTime);
        TextInputLayout tilEndTime = view.findViewById(R.id.tilEndTime);
        TextInputLayout tilEventVenue = view.findViewById(R.id.tilEventVenue);

        TextInputEditText etEventName = view.findViewById(R.id.etEventName);
        TextInputEditText etEventDescription = view.findViewById(R.id.etEventDescription);
        TextInputEditText etEventDate = view.findViewById(R.id.etEventDate);
        TextInputEditText etStartTime = view.findViewById(R.id.etStartTime);
        TextInputEditText etEndTime = view.findViewById(R.id.etEndTime);
        TextInputEditText etEventVenue = view.findViewById(R.id.etEventVenue);

        RadioGroup rgScoringType = view.findViewById(R.id.rgScoringType);
        RadioButton rbScoringWeighted = view.findViewById(R.id.rbScoringWeighted);
        RadioButton rbScoringRaw = view.findViewById(R.id.rbScoringRaw);
        MaterialCheckBox cbAnonymousJudging = view.findViewById(R.id.cbAnonymousJudging);

        etEventName.setText(currentEvent.getName());
        etEventDescription.setText(currentEvent.getDescription());

        final long[] selectedDateMillis = new long[]{
                currentEvent.getScheduledDate() > 0 ? currentEvent.getScheduledDate() : System.currentTimeMillis()
        };
        final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        if (currentEvent.getScheduledDate() > 0) {
            etEventDate.setText(sdf.format(new Date(currentEvent.getScheduledDate())));
        } else {
            etEventDate.setText("");
            selectedDateMillis[0] = 0;
        }

        if (currentEvent.getStartTime() != null) etStartTime.setText(currentEvent.getStartTime());
        if (currentEvent.getEndTime() != null) etEndTime.setText(currentEvent.getEndTime());
        if (currentEvent.getVenue() != null) etEventVenue.setText(currentEvent.getVenue());

        etEventDate.setFocusable(false);
        etEventDate.setClickable(true);
        etStartTime.setFocusable(false);
        etStartTime.setClickable(true);
        etEndTime.setFocusable(false);
        etEndTime.setClickable(true);

        View.OnClickListener datePickerListener = v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedDateMillis[0] > 0) {
                cal.setTimeInMillis(selectedDateMillis[0]);
            }
            DatePickerDialog dpd = new DatePickerDialog(this, (dp, year, month, dayOfMonth) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, dayOfMonth, 0, 0, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                selectedDateMillis[0] = chosen.getTimeInMillis();
                etEventDate.setText(sdf.format(chosen.getTime()));
                tilEventDate.setError(null);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        };
        etEventDate.setOnClickListener(datePickerListener);
        tilEventDate.setOnClickListener(datePickerListener);

        View.OnClickListener startTimeListener = v -> {
            int initialHour = 9;
            int initialMinute = 0;
            String currentVal = etStartTime.getText() != null ? etStartTime.getText().toString().trim() : "";
            if (!currentVal.isEmpty()) {
                try {
                    SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.US);
                    Date d = tf.parse(currentVal);
                    if (d != null) {
                        Calendar c = Calendar.getInstance();
                        c.setTime(d);
                        initialHour = c.get(Calendar.HOUR_OF_DAY);
                        initialMinute = c.get(Calendar.MINUTE);
                    }
                } catch (Exception ignored) {}
            }
            TimePickerDialog tpd = new TimePickerDialog(this, (tp, hourOfDay, minute) -> {
                String ampm = hourOfDay >= 12 ? "PM" : "AM";
                int h = hourOfDay % 12;
                if (h == 0) h = 12;
                etStartTime.setText(String.format(Locale.US, "%02d:%02d %s", h, minute, ampm));
                tilStartTime.setError(null);
                tilEndTime.setError(null);
            }, initialHour, initialMinute, false);
            tpd.show();
        };
        etStartTime.setOnClickListener(startTimeListener);
        tilStartTime.setOnClickListener(startTimeListener);

        View.OnClickListener endTimeListener = v -> {
            int initialHour = 17;
            int initialMinute = 0;
            String currentVal = etEndTime.getText() != null ? etEndTime.getText().toString().trim() : "";
            if (!currentVal.isEmpty()) {
                try {
                    SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.US);
                    Date d = tf.parse(currentVal);
                    if (d != null) {
                        Calendar c = Calendar.getInstance();
                        c.setTime(d);
                        initialHour = c.get(Calendar.HOUR_OF_DAY);
                        initialMinute = c.get(Calendar.MINUTE);
                    }
                } catch (Exception ignored) {}
            }
            TimePickerDialog tpd = new TimePickerDialog(this, (tp, hourOfDay, minute) -> {
                String ampm = hourOfDay >= 12 ? "PM" : "AM";
                int h = hourOfDay % 12;
                if (h == 0) h = 12;
                etEndTime.setText(String.format(Locale.US, "%02d:%02d %s", h, minute, ampm));
                tilEndTime.setError(null);
            }, initialHour, initialMinute, false);
            tpd.show();
        };
        etEndTime.setOnClickListener(endTimeListener);
        tilEndTime.setOnClickListener(endTimeListener);

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
                String startTime = etStartTime.getText() != null ? etStartTime.getText().toString().trim() : "";
                String endTime = etEndTime.getText() != null ? etEndTime.getText().toString().trim() : "";
                String venue = etEventVenue.getText() != null ? etEventVenue.getText().toString().trim() : "";

                tilEventName.setError(null);
                tilEventDate.setError(null);
                tilStartTime.setError(null);
                tilEndTime.setError(null);

                if (TextUtils.isEmpty(name)) {
                    tilEventName.setError("Event name is required");
                    return;
                }

                if (!TextUtils.isEmpty(startTime) && !TextUtils.isEmpty(endTime)) {
                    try {
                        SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.US);
                        Date start = tf.parse(startTime);
                        Date end = tf.parse(endTime);
                        if (start != null && end != null && !end.after(start)) {
                            tilEndTime.setError("End time must be later than start time");
                            return;
                        }
                    } catch (ParseException ignored) {}
                }

                dialog.dismiss();
                Dialog loadingDialog = UIUtils.showLoadingDialog(this, "Saving configurations...");

                currentEvent.setName(name);
                currentEvent.setDescription(desc);
                currentEvent.setScoringType(rbScoringRaw.isChecked() ? Event.SCORING_RAW : Event.SCORING_WEIGHTED);
                currentEvent.setAnonymousJudging(cbAnonymousJudging.isChecked());
                currentEvent.setScheduledDate(selectedDateMillis[0]);
                currentEvent.setStartTime(startTime);
                currentEvent.setEndTime(endTime);
                currentEvent.setVenue(venue);

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
