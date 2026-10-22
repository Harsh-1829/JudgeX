package com.example.judgex.admin;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.example.judgex.auth.LoginActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminDashboardActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvOrgName, tvAdminEmail;
    private TextView tvCountTotalEvents, tvCountActiveEvents, tvCountUpcomingEvents, tvCountCompletedEvents;
    private MaterialButton btnLogout, btnManageEvents;
    private MaterialCardView cardEventsHub, cardCreateEvent, cardResultsRankings, cardOrgSettings;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private User currentAdmin;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);

        initViews();
        setupClickListeners();
        loadAdminProfileAndMetrics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAdminProfileAndMetrics();
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvOrgName = findViewById(R.id.tvOrgName);
        tvAdminEmail = findViewById(R.id.tvAdminEmail);
        tvCountTotalEvents = findViewById(R.id.tvCountTotalEvents);
        tvCountActiveEvents = findViewById(R.id.tvCountActiveEvents);
        tvCountUpcomingEvents = findViewById(R.id.tvCountUpcomingEvents);
        tvCountCompletedEvents = findViewById(R.id.tvCountCompletedEvents);

        btnLogout = findViewById(R.id.btnLogout);
        btnManageEvents = findViewById(R.id.btnManageEvents);
        cardEventsHub = findViewById(R.id.cardEventsHub);
        cardCreateEvent = findViewById(R.id.cardCreateEvent);
        cardResultsRankings = findViewById(R.id.cardResultsRankings);
        cardOrgSettings = findViewById(R.id.cardOrgSettings);

        swipeRefreshLayout.setOnRefreshListener(this::loadAdminProfileAndMetrics);
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

        View.OnClickListener openEvents = v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, EventManagerActivity.class);
            startActivity(intent);
        };
        cardEventsHub.setOnClickListener(openEvents);
        btnManageEvents.setOnClickListener(openEvents);

        cardCreateEvent.setOnClickListener(v -> showCreateEventDialog());
        cardResultsRankings.setOnClickListener(v -> showSelectEventForResultsDialog());
        cardOrgSettings.setOnClickListener(v -> showOrganizationDetailsDialog());
    }

    private void loadAdminProfileAndMetrics() {
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
                        currentAdmin = doc.toObject(User.class);
                        if (currentAdmin != null) {
                            currentAdmin.setUid(doc.getId());
                            firebaseManager.setCurrentUserProfile(currentAdmin);

                            String orgName = currentAdmin.getOrganizationName();
                            if (orgName == null || orgName.isEmpty()) {
                                orgName = "JudgeX Institution";
                            }
                            tvOrgName.setText(orgName);
                            tvAdminEmail.setText((currentAdmin.getName() != null ? currentAdmin.getName() + " • " : "") + currentAdmin.getEmail());

                            loadEventsMetrics(currentAdmin.getOrganizationId());
                            return;
                        }
                    }
                    loadEventsMetrics(null);
                })
                .addOnFailureListener(e -> {
                    loadEventsMetrics(null);
                });
    }

    private void loadEventsMetrics(String orgId) {
        firebaseManager.getEventsForOrganization(orgId)
                .addOnSuccessListener(querySnapshot -> {
                    int total = querySnapshot.size();
                    int active = 0;
                    int upcoming = 0;
                    int completed = 0;

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) {
                            String st = e.getStatus();
                            if (Event.STATUS_ACTIVE.equalsIgnoreCase(st)) {
                                active++;
                            } else if (Event.STATUS_UPCOMING.equalsIgnoreCase(st)) {
                                upcoming++;
                            } else if (Event.STATUS_COMPLETED.equalsIgnoreCase(st) || Event.STATUS_LOCKED.equalsIgnoreCase(st)) {
                                completed++;
                            }
                        }
                    }

                    tvCountTotalEvents.setText(String.valueOf(total));
                    tvCountActiveEvents.setText(String.valueOf(active));
                    tvCountUpcomingEvents.setText(String.valueOf(upcoming));
                    tvCountCompletedEvents.setText(String.valueOf(completed));

                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load metrics: " + e.getMessage());
                });
    }

    private void showCreateEventDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        tvDialogTitle.setText("Schedule New Competition");

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

        rbScoringWeighted.setChecked(true);
        cbAnonymousJudging.setChecked(false);

        final long[] selectedDateMillis = new long[]{System.currentTimeMillis()};
        final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        etEventDate.setText(sdf.format(new Date(selectedDateMillis[0])));
        etStartTime.setText("09:00 AM");
        etEndTime.setText("05:00 PM");

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

            Calendar startOfToday = Calendar.getInstance();
            startOfToday.set(Calendar.HOUR_OF_DAY, 0);
            startOfToday.set(Calendar.MINUTE, 0);
            startOfToday.set(Calendar.SECOND, 0);
            startOfToday.set(Calendar.MILLISECOND, 0);
            dpd.getDatePicker().setMinDate(startOfToday.getTimeInMillis());
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

        builder.setView(view);
        builder.setPositiveButton("Schedule Event", null);
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
                    tilEventName.setError("Competition name is required");
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

                String scoringType = rbScoringRaw.isChecked() ? Event.SCORING_RAW : Event.SCORING_WEIGHTED;
                boolean isAnonymous = cbAnonymousJudging.isChecked();

                dialog.dismiss();
                Dialog loadingDialog = UIUtils.showLoadingDialog(this, "Creating event...");

                String orgId = currentAdmin != null ? currentAdmin.getOrganizationId() : null;
                // Respect scheduling policy: newly created events start as UPCOMING until an admin activates them
                String initialStatus = Event.STATUS_UPCOMING;
                Event newEvent = new Event(null, name, desc, initialStatus, false, System.currentTimeMillis());
                newEvent.setScoringType(scoringType);
                newEvent.setAnonymousJudging(isAnonymous);
                newEvent.setScheduledDate(selectedDateMillis[0]);
                newEvent.setStartTime(startTime);
                newEvent.setEndTime(endTime);
                newEvent.setVenue(venue);
                if (orgId != null) {
                    newEvent.setOrganizationId(orgId);
                }

                firebaseManager.createEvent(newEvent)
                        .addOnSuccessListener(docRef -> {
                            loadingDialog.dismiss();
                            newEvent.setId(docRef.getId());
                            UIUtils.showToast(this, "Event created successfully as " + initialStatus);

                            Intent hubIntent = new Intent(AdminDashboardActivity.this, EventDetailActivity.class);
                            hubIntent.putExtra(Constants.EXTRA_EVENT, newEvent);
                            hubIntent.putExtra(Constants.EXTRA_EVENT_ID, newEvent.getId());
                            startActivity(hubIntent);
                        })
                        .addOnFailureListener(e -> {
                            loadingDialog.dismiss();
                            UIUtils.showToast(this, "Failed to create event: " + e.getMessage());
                        });
            });
        });
        dialog.show();
    }

    private void showSelectEventForResultsDialog() {
        Dialog loading = UIUtils.showLoadingDialog(this, "Loading competition events...");
        String orgId = currentAdmin != null ? currentAdmin.getOrganizationId() : null;

        firebaseManager.getEventsForOrganization(orgId)
                .addOnSuccessListener(querySnapshot -> {
                    loading.dismiss();
                    List<Event> events = new ArrayList<>();
                    List<String> eventLabels = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event ev = doc.toObject(Event.class);
                        if (ev != null) {
                            ev.setId(doc.getId());
                            events.add(ev);
                            String status = ev.getStatus() != null ? ev.getStatus() : "UPCOMING";
                            eventLabels.add(ev.getName() + " [" + status + "]");
                        }
                    }

                    if (events.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setTitle("No Events Found")
                                .setMessage("No competition events have been created for your organization yet.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Select Event for Standings & Rankings")
                            .setItems(eventLabels.toArray(new String[0]), (dialog, which) -> {
                                Event selected = events.get(which);
                                Intent intent = new Intent(AdminDashboardActivity.this, ResultsRankingActivity.class);
                                intent.putExtra(Constants.EXTRA_EVENT, selected);
                                intent.putExtra(Constants.EXTRA_EVENT_ID, selected.getId());
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    loading.dismiss();
                    UIUtils.showToast(this, "Failed to load events: " + e.getMessage());
                });
    }

    private void showOrganizationDetailsDialog() {
        if (currentAdmin == null) return;
        String orgName = currentAdmin.getOrganizationName() != null ? currentAdmin.getOrganizationName() : "Institution";
        String orgId = currentAdmin.getOrganizationId() != null ? currentAdmin.getOrganizationId() : "N/A";
        String adminName = currentAdmin.getName() != null ? currentAdmin.getName() : "Administrator";
        String adminEmail = currentAdmin.getEmail() != null ? currentAdmin.getEmail() : "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("🏛️ Institution / College: ").append(orgName).append("\n\n");
        sb.append("🆔 Organization ID: ").append(orgId).append("\n\n");
        sb.append("👤 Administrator: ").append(adminName).append("\n\n");
        sb.append("📧 Contact Email: ").append(adminEmail).append("\n\n");
        sb.append("🔒 Tenant Scoping: Independent institutional boundaries enforced.");

        new AlertDialog.Builder(this)
                .setTitle("Organization Settings & Details")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}
