package com.example.judgex.team;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.LeaderEventAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BrowseEventsActivity extends AppCompatActivity implements LeaderEventAdapter.OnEventListener {

    private MaterialToolbar toolbar;
    private TextInputEditText etSearch;
    private ChipGroup chipGroupFilters;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewEvents;
    private TextView tvEmptyState;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Event> allEventsList = new ArrayList<>();
    private final List<Event> filteredEventsList = new ArrayList<>();
    private final Set<String> registeredEventIds = new HashSet<>();
    private LeaderEventAdapter adapter;
    private String organizationId = "";
    private String currentSearchText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse_events);

        User profile = firebaseManager.getCurrentUserProfile();
        if (profile != null) {
            organizationId = profile.getOrganizationId();
        }

        initViews();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        etSearch = findViewById(R.id.etSearch);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewEvents = findViewById(R.id.recyclerViewEvents);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        toolbar.setNavigationOnClickListener(v -> finish());
        swipeRefreshLayout.setOnRefreshListener(this::loadData);

        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LeaderEventAdapter(filteredEventsList, registeredEventIds, this);
        recyclerViewEvents.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchText = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
    }

    private static final String TAG = "JudgeX_BrowseEvents";

    private void loadData() {
        swipeRefreshLayout.setRefreshing(true);
        boolean isAuthenticated = firebaseManager.getCurrentFirebaseUser() != null;
        String uid = isAuthenticated ? firebaseManager.getCurrentFirebaseUser().getUid() : "";
        User profile = firebaseManager.getCurrentUserProfile();
        boolean profileLoaded = profile != null;
        String orgId = profile != null ? profile.getOrganizationId() : organizationId;

        android.util.Log.d(TAG, "Event loading initiated. Auth: " + isAuthenticated + ", Profile loaded: " + profileLoaded + ", OrgId: " + orgId);

        if (!isAuthenticated || uid.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (profile == null) {
            android.util.Log.d(TAG, "User profile is null. Fetching user profile for UID before executing event query...");
            firebaseManager.fetchUserProfile(uid)
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            User u = doc.toObject(User.class);
                            if (u != null) {
                                u.setUid(doc.getId());
                                firebaseManager.setCurrentUserProfile(u);
                                organizationId = u.getOrganizationId();
                                android.util.Log.d(TAG, "User profile fetched successfully. School: " + u.getSchool() + ", OrgId: " + organizationId);
                            }
                        }
                        loadPublishedEvents();
                        fetchRegisteredEventIds(uid);
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e(TAG, "Failed to fetch user profile: " + e.getMessage(), e);
                        loadPublishedEvents();
                        fetchRegisteredEventIds(uid);
                    });
        } else {
            organizationId = profile.getOrganizationId();
            loadPublishedEvents();
            fetchRegisteredEventIds(uid);
        }
    }

    private void fetchRegisteredEventIds(String uid) {
        registeredEventIds.clear();
        firebaseManager.getTeamLeaderRegistrations(uid)
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            EventRegistration reg = doc.toObject(EventRegistration.class);
                            if (reg != null && !reg.isCancelled() && reg.getEventId() != null) {
                                registeredEventIds.add(reg.getEventId());
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w(TAG, "fetchRegisteredEventIds background query completed with notice: " + e.getMessage());
                });
    }

    private void loadPublishedEvents() {
        android.util.Log.d(TAG, "Executing getPublishedEventsForOrganization with OrgId: " + organizationId);
        firebaseManager.getPublishedEventsForOrganization(organizationId)
                .addOnSuccessListener(events -> {
                    swipeRefreshLayout.setRefreshing(false);
                    int count = events != null ? events.size() : 0;
                    android.util.Log.d(TAG, "Firestore query successful. Documents retrieved: " + count);
                    allEventsList.clear();
                    if (events != null) {
                        allEventsList.addAll(events);
                    }
                    applyFilters();
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    android.util.Log.e(TAG, "Firestore query failed: " + (e != null ? e.getMessage() : "Unknown"), e);
                    allEventsList.clear();
                    filteredEventsList.clear();
                    adapter.notifyDataSetChanged();
                    tvEmptyState.setText("Failed to load events: " + (e != null && e.getMessage() != null ? e.getMessage() : "Unknown error") + "\n\nSwipe down to retry.");
                    tvEmptyState.setVisibility(View.VISIBLE);
                    UIUtils.showToast(this, "Failed to load events: " + (e != null ? e.getMessage() : "Error"));
                });
    }

    private void applyFilters() {
        filteredEventsList.clear();
        int checkedChipId = chipGroupFilters.getCheckedChipId();

        User currentLeader = firebaseManager.getCurrentUserProfile();
        String leaderSchool = currentLeader != null ? currentLeader.getSchool() : null;

        int totalCount = allEventsList.size();
        int excludedBySchool = 0;
        int excludedByFilter = 0;

        for (Event event : allEventsList) {
            // School Eligibility Filtering (STME, SOC, SPTM, SBM, SOL)
            if (leaderSchool != null && !leaderSchool.isEmpty() && !event.isSchoolEligible(leaderSchool)) {
                excludedBySchool++;
                continue; // STME only sees STME or All Schools, etc.
            }

            // Text Search filter
            if (!currentSearchText.isEmpty()) {
                String name = event.getName() != null ? event.getName().toLowerCase(Locale.US) : "";
                String desc = event.getDescription() != null ? event.getDescription().toLowerCase(Locale.US) : "";
                String cat = event.getCategory() != null ? event.getCategory().toLowerCase(Locale.US) : "";
                String dept = event.getDepartment() != null ? event.getDepartment().toLowerCase(Locale.US) : "";

                if (!name.contains(currentSearchText) && !desc.contains(currentSearchText) 
                        && !cat.contains(currentSearchText) && !dept.contains(currentSearchText)) {
                    excludedByFilter++;
                    continue;
                }
            }

            // Chip category filter
            if (checkedChipId == R.id.chipRegOpen) {
                if (!event.isRegistrationOpen()) {
                    excludedByFilter++;
                    continue;
                }
            } else if (checkedChipId == R.id.chipUpcoming) {
                if (!event.isUpcoming() && !event.isActive()) {
                    excludedByFilter++;
                    continue;
                }
            } else if (checkedChipId == R.id.chipMyRegistered) {
                if (!registeredEventIds.contains(event.getId())) {
                    excludedByFilter++;
                    continue;
                }
            } else if (checkedChipId == R.id.chipRegClosed) {
                if (event.isRegistrationOpen()) {
                    excludedByFilter++;
                    continue;
                }
            }

            filteredEventsList.add(event);
        }

        android.util.Log.d(TAG, "Filter summary: Total=" + totalCount 
                + ", ExcludedBySchool=" + excludedBySchool 
                + ", ExcludedByFilter=" + excludedByFilter 
                + ", Displayed=" + filteredEventsList.size());

        adapter.notifyDataSetChanged();
        if (filteredEventsList.isEmpty()) {
            if (allEventsList.isEmpty()) {
                tvEmptyState.setText("No published events available at this time.");
            } else {
                tvEmptyState.setText("No events found matching your filter criteria.");
            }
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onEventClick(Event event) {
        Intent intent = new Intent(this, EventDetailLeaderActivity.class);
        intent.putExtra(Constants.EXTRA_EVENT, event);
        intent.putExtra(Constants.EXTRA_EVENT_ID, event.getId());
        startActivity(intent);
    }
}
