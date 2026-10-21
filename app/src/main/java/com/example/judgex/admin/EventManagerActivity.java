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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.EventAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventManagerActivity extends AppCompatActivity implements EventAdapter.OnEventClickListener {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewEvents;
    private TextView tvEmptyState;
    private ChipGroup chipGroupStatus;
    private ExtendedFloatingActionButton fabAddEvent;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> eventList = new ArrayList<>();
    private EventAdapter adapter;
    private SharedPreferences prefs;
    private String activeEventId;
    private String selectedFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_manager);

        prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        activeEventId = prefs.getString(Constants.PREF_ACTIVE_EVENT_ID, "");

        initViews();
        loadEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewEvents = findViewById(R.id.recyclerViewEvents);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        fabAddEvent = findViewById(R.id.fabAddEvent);

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter(eventList, activeEventId, this);
        recyclerViewEvents.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadEvents);
        fabAddEvent.setOnClickListener(v -> showAddEditEventDialog(null));

        if (chipGroupStatus != null) {
            chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.contains(R.id.chipActive)) {
                    selectedFilter = Event.STATUS_ACTIVE;
                } else if (checkedIds.contains(R.id.chipUpcoming)) {
                    selectedFilter = Event.STATUS_UPCOMING;
                } else if (checkedIds.contains(R.id.chipLocked)) {
                    selectedFilter = Event.STATUS_LOCKED;
                } else if (checkedIds.contains(R.id.chipCompleted)) {
                    selectedFilter = Event.STATUS_COMPLETED;
                } else if (checkedIds.contains(R.id.chipArchived)) {
                    selectedFilter = Event.STATUS_ARCHIVED;
                } else {
                    selectedFilter = "ALL";
                }
                applyFilter();
            });
        }
    }

    private void loadEvents() {
        swipeRefreshLayout.setRefreshing(true);

        User currentAdmin = firebaseManager.getCurrentUserProfile();
        String orgId = currentAdmin != null ? currentAdmin.getOrganizationId() : null;

        firebaseManager.getEventsForOrganization(orgId)
                .addOnSuccessListener(querySnapshot -> {
                    allEvents.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            event.setId(doc.getId());
                            allEvents.add(event);
                        }
                    }

                    applyFilter();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load events: " + e.getMessage());
                });
    }

    private void applyFilter() {
        eventList.clear();
        for (Event e : allEvents) {
            if (e.isDeleted()) {
                if (Event.STATUS_ARCHIVED.equalsIgnoreCase(selectedFilter)) {
                    eventList.add(e);
                }
                continue;
            }

            if ("ALL".equalsIgnoreCase(selectedFilter)) {
                eventList.add(e);
            } else if (selectedFilter.equalsIgnoreCase(e.getStatus())) {
                eventList.add(e);
            }
        }

        if (eventList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerViewEvents.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewEvents.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private void showAddEditEventDialog(Event existingEvent) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
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

        boolean isEdit = existingEvent != null;
        tvDialogTitle.setText(isEdit ? "Edit Event" : "Create New Event");

        final long[] selectedDateMillis = new long[]{
                (existingEvent != null && existingEvent.getScheduledDate() > 0)
                        ? existingEvent.getScheduledDate()
                        : System.currentTimeMillis()
        };
        final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        if (existingEvent != null && existingEvent.getScheduledDate() > 0) {
            etEventDate.setText(sdf.format(new Date(existingEvent.getScheduledDate())));
        } else {
            etEventDate.setText(sdf.format(new Date(selectedDateMillis[0])));
        }

        if (isEdit) {
            etEventName.setText(existingEvent.getName());
            etEventDescription.setText(existingEvent.getDescription());
            if (existingEvent.getStartTime() != null) etStartTime.setText(existingEvent.getStartTime());
            if (existingEvent.getEndTime() != null) etEndTime.setText(existingEvent.getEndTime());
            if (existingEvent.getVenue() != null) etEventVenue.setText(existingEvent.getVenue());

            if (Event.SCORING_RAW.equalsIgnoreCase(existingEvent.getScoringType())) {
                rbScoringRaw.setChecked(true);
            } else {
                rbScoringWeighted.setChecked(true);
            }
            cbAnonymousJudging.setChecked(existingEvent.isAnonymousJudging());
        } else {
            rbScoringWeighted.setChecked(true);
            cbAnonymousJudging.setChecked(false);
            etStartTime.setText("09:00 AM");
            etEndTime.setText("05:00 PM");
        }

        etEventDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selectedDateMillis[0]);
            DatePickerDialog dpd = new DatePickerDialog(this, (dp, year, month, dayOfMonth) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, dayOfMonth, 0, 0, 0);
                selectedDateMillis[0] = chosen.getTimeInMillis();
                etEventDate.setText(sdf.format(chosen.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });

        etStartTime.setOnClickListener(v -> {
            TimePickerDialog tpd = new TimePickerDialog(this, (tp, hourOfDay, minute) -> {
                String ampm = hourOfDay >= 12 ? "PM" : "AM";
                int h = hourOfDay % 12;
                if (h == 0) h = 12;
                etStartTime.setText(String.format(Locale.US, "%02d:%02d %s", h, minute, ampm));
            }, 9, 0, false);
            tpd.show();
        });

        etEndTime.setOnClickListener(v -> {
            TimePickerDialog tpd = new TimePickerDialog(this, (tp, hourOfDay, minute) -> {
                String ampm = hourOfDay >= 12 ? "PM" : "AM";
                int h = hourOfDay % 12;
                if (h == 0) h = 12;
                etEndTime.setText(String.format(Locale.US, "%02d:%02d %s", h, minute, ampm));
            }, 17, 0, false);
            tpd.show();
        });

        builder.setView(view);
        builder.setPositiveButton(isEdit ? "Update" : "Create", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etEventName.getText() != null ? etEventName.getText().toString().trim() : "";
                String description = etEventDescription.getText() != null ? etEventDescription.getText().toString().trim() : "";
                String startTime = etStartTime.getText() != null ? etStartTime.getText().toString().trim() : "";
                String endTime = etEndTime.getText() != null ? etEndTime.getText().toString().trim() : "";
                String venue = etEventVenue.getText() != null ? etEventVenue.getText().toString().trim() : "";

                if (TextUtils.isEmpty(name)) {
                    tilEventName.setError("Event name is required");
                    return;
                }

                String scoringType = rbScoringRaw.isChecked() ? Event.SCORING_RAW : Event.SCORING_WEIGHTED;
                boolean isAnonymous = cbAnonymousJudging.isChecked();

                // Compute lifecycle status based on scheduled date
                Calendar endOfToday = Calendar.getInstance();
                endOfToday.set(Calendar.HOUR_OF_DAY, 23);
                endOfToday.set(Calendar.MINUTE, 59);
                endOfToday.set(Calendar.SECOND, 59);

                boolean isFuture = selectedDateMillis[0] > endOfToday.getTimeInMillis();

                dialog.dismiss();
                Dialog loadingDialog = UIUtils.showLoadingDialog(this, isEdit ? "Updating event..." : "Creating event...");

                if (isEdit) {
                    existingEvent.setName(name);
                    existingEvent.setDescription(description);
                    existingEvent.setScoringType(scoringType);
                    existingEvent.setAnonymousJudging(isAnonymous);
                    existingEvent.setScheduledDate(selectedDateMillis[0]);
                    existingEvent.setStartTime(startTime);
                    existingEvent.setEndTime(endTime);
                    existingEvent.setVenue(venue);

                    firebaseManager.updateEvent(existingEvent)
                            .addOnSuccessListener(aVoid -> {
                                loadingDialog.dismiss();
                                UIUtils.showToast(this, "Event updated successfully");
                                loadEvents();
                            })
                            .addOnFailureListener(e -> {
                                loadingDialog.dismiss();
                                UIUtils.showToast(this, "Failed to update event: " + e.getMessage());
                            });
                } else {
                    User currentAdmin = firebaseManager.getCurrentUserProfile();
                    String orgId = currentAdmin != null ? currentAdmin.getOrganizationId() : null;

                    String initialStatus = isFuture ? Event.STATUS_UPCOMING : Event.STATUS_ACTIVE;
                    Event newEvent = new Event(null, name, description, initialStatus, false, System.currentTimeMillis());
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
                                openEventHub(newEvent);
                            })
                            .addOnFailureListener(e -> {
                                loadingDialog.dismiss();
                                UIUtils.showToast(this, "Failed to create event: " + e.getMessage());
                            });
                }
            });
        });
        dialog.show();
    }

    private void openEventHub(Event event) {
        activeEventId = event.getId();
        prefs.edit().putString(Constants.PREF_ACTIVE_EVENT_ID, activeEventId).apply();

        Intent intent = new Intent(this, EventDetailActivity.class);
        intent.putExtra(Constants.EXTRA_EVENT, event);
        intent.putExtra(Constants.EXTRA_EVENT_ID, event.getId());
        startActivity(intent);
    }

    @Override
    public void onEventSelected(Event event) {
        openEventHub(event);
    }

    @Override
    public void onEventItemClick(Event event) {
        openEventHub(event);
    }

    @Override
    public void onEventDeleteClick(Event event) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Event: " + event.getName())
                .setMessage("⚠️ Warning: Deleting this competition will remove it from the active event schedule.\n\nTo preserve historical competition scores, teams, and audit records, this event will be safely archived.\n\nAre you sure you want to proceed?")
                .setPositiveButton("Archive & Remove", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Archiving event safely...");
                    event.setDeleted(true);
                    event.setStatus(Event.STATUS_ARCHIVED);
                    firebaseManager.updateEvent(event)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Event archived and removed from active list.");
                                loadEvents();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to delete event: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public void onEventStatusClick(Event event) {
        String[] statuses = new String[]{
                Event.STATUS_UPCOMING,
                Event.STATUS_ACTIVE,
                Event.STATUS_LOCKED,
                Event.STATUS_COMPLETED,
                Event.STATUS_ARCHIVED
        };

        int currentSelection = 0;
        for (int i = 0; i < statuses.length; i++) {
            if (statuses[i].equalsIgnoreCase(event.getStatus())) {
                currentSelection = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Change Event Status: " + event.getName())
                .setSingleChoiceItems(statuses, currentSelection, (dialog, which) -> {
                    String newStatus = statuses[which];
                    dialog.dismiss();
                    Dialog loading = UIUtils.showLoadingDialog(this, "Updating status to " + newStatus + "...");
                    event.setStatus(newStatus);
                    event.setLocked(Event.STATUS_LOCKED.equalsIgnoreCase(newStatus));

                    firebaseManager.updateEvent(event)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Event status set to " + newStatus);
                                loadEvents();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to update status: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
