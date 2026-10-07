package com.example.judgex.admin;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
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
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class EventManagerActivity extends AppCompatActivity implements EventAdapter.OnEventClickListener {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewEvents;
    private TextView tvEmptyState;
    private ExtendedFloatingActionButton fabAddEvent;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Event> eventList = new ArrayList<>();
    private EventAdapter adapter;
    private SharedPreferences prefs;
    private String activeEventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_manager);

        prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        activeEventId = prefs.getString(Constants.PREF_ACTIVE_EVENT_ID, "");

        initViews();
        loadEvents();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewEvents = findViewById(R.id.recyclerViewEvents);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        fabAddEvent = findViewById(R.id.fabAddEvent);

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter(eventList, activeEventId, this);
        recyclerViewEvents.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadEvents);
        fabAddEvent.setOnClickListener(v -> showAddEditEventDialog(null));
    }

    private void loadEvents() {
        swipeRefreshLayout.setRefreshing(true);
        firebaseManager.getEventsCollection()
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    eventList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            event.setId(doc.getId());
                            eventList.add(event);
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
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load events: " + e.getMessage());
                });
    }

    private void showAddEditEventDialog(Event existingEvent) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, null);
        
        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        TextInputLayout tilEventName = view.findViewById(R.id.tilEventName);
        TextInputLayout tilEventDescription = view.findViewById(R.id.tilEventDescription);
        TextInputEditText etEventName = view.findViewById(R.id.etEventName);
        TextInputEditText etEventDescription = view.findViewById(R.id.etEventDescription);

        if (existingEvent != null) {
            tvDialogTitle.setText("Edit Screening Event");
            etEventName.setText(existingEvent.getName());
            etEventDescription.setText(existingEvent.getDescription());
        }

        builder.setView(view);
        builder.setPositiveButton(existingEvent != null ? "Update" : "Create", null);
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
                saveEvent(existingEvent, name, desc);
            });
        });
        dialog.show();
    }

    private void saveEvent(Event existingEvent, String name, String description) {
        Dialog loadingDialog = UIUtils.showLoadingDialog(this, existingEvent != null ? "Updating event..." : "Creating event...");

        if (existingEvent != null) {
            existingEvent.setName(name);
            existingEvent.setDescription(description);
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
            Event newEvent = new Event(null, name, description, Event.STATUS_ACTIVE, false, System.currentTimeMillis());
            firebaseManager.createEvent(newEvent)
                    .addOnSuccessListener(docRef -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Event created successfully");
                        if (TextUtils.isEmpty(activeEventId)) {
                            setActiveEvent(newEvent);
                        }
                        loadEvents();
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Failed to create event: " + e.getMessage());
                    });
        }
    }

    private void setActiveEvent(Event event) {
        activeEventId = event.getId();
        prefs.edit().putString(Constants.PREF_ACTIVE_EVENT_ID, activeEventId).apply();
        UIUtils.showToast(this, "Set '" + event.getName() + "' as active event");
        loadEvents();
    }

    @Override
    public void onEventSelected(Event event) {
        setActiveEvent(event);
    }

    @Override
    public void onEventItemClick(Event event) {
        showAddEditEventDialog(event);
    }

    @Override
    public void onEventStatusClick(Event event) {
        String[] statuses = new String[]{"ACTIVE", "COMPLETED", "ARCHIVED"};
        int currentSelection = 0;
        if (Event.STATUS_COMPLETED.equalsIgnoreCase(event.getStatus())) currentSelection = 1;
        if (Event.STATUS_ARCHIVED.equalsIgnoreCase(event.getStatus())) currentSelection = 2;

        new AlertDialog.Builder(this)
                .setTitle("Change Event Status: " + event.getName())
                .setSingleChoiceItems(statuses, currentSelection, (dialog, which) -> {
                    String newStatus = statuses[which];
                    dialog.dismiss();
                    Dialog loading = UIUtils.showLoadingDialog(this, "Updating status to " + newStatus + "...");
                    event.setStatus(newStatus);
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
