package com.example.judgex.team;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.RegistrationAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MyRegistrationsActivity extends AppCompatActivity implements RegistrationAdapter.OnRegistrationClickListener {

    private MaterialToolbar toolbar;
    private ChipGroup chipGroupStatus;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewRegistrations;
    private View llEmptyState;
    private android.widget.TextView tvEmptyTitle;
    private android.widget.TextView tvEmptySubtitle;
    private MaterialButton btnExploreEvents;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<EventRegistration> allRegistrations = new ArrayList<>();
    private final List<EventRegistration> filteredRegistrations = new ArrayList<>();
    private RegistrationAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_registrations);

        initViews();
        loadRegistrations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRegistrations();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewRegistrations = findViewById(R.id.recyclerViewRegistrations);
        llEmptyState = findViewById(R.id.llEmptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        btnExploreEvents = findViewById(R.id.btnExploreEvents);

        toolbar.setNavigationOnClickListener(v -> finish());
        swipeRefreshLayout.setOnRefreshListener(this::loadRegistrations);

        recyclerViewRegistrations.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RegistrationAdapter(filteredRegistrations, this);
        recyclerViewRegistrations.setAdapter(adapter);

        chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());

        btnExploreEvents.setOnClickListener(v -> {
            startActivity(new Intent(this, BrowseEventsActivity.class));
            finish();
        });
    }

    private static final String TAG = "JudgeX_Registrations";

    private void loadRegistrations() {
        swipeRefreshLayout.setRefreshing(true);
        boolean isAuthenticated = firebaseManager.getCurrentFirebaseUser() != null;
        String uid = isAuthenticated ? firebaseManager.getCurrentFirebaseUser().getUid() : "";
        String safeUid = (uid.length() > 6) ? uid.substring(0, 6) + "..." : uid;

        android.util.Log.d(TAG, "Registration loading initiated. Authenticated: " + isAuthenticated + ", UID: " + safeUid);

        if (!isAuthenticated || uid.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            finish();
            return;
        }

        android.util.Log.d(TAG, "Executing query on collection '" + Constants.COLLECTION_REGISTRATIONS + "' with field 'teamLeaderId' == " + safeUid);

        firebaseManager.getTeamLeaderRegistrations(uid)
                .addOnSuccessListener(querySnapshot -> {
                    int count = querySnapshot != null ? querySnapshot.size() : 0;
                    android.util.Log.d(TAG, "Query succeeded. Records retrieved: " + count);

                    allRegistrations.clear();
                    java.util.Set<String> seenIds = new java.util.HashSet<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            EventRegistration reg = doc.toObject(EventRegistration.class);
                            if (reg != null) {
                                reg.setId(doc.getId());
                                allRegistrations.add(reg);
                                seenIds.add(doc.getId());
                            }
                        }
                    }

                    // Fallback to query by createdById if zero records returned to support legacy registrations
                    if (allRegistrations.isEmpty()) {
                        android.util.Log.d(TAG, "0 records by teamLeaderId. Checking legacy query by 'createdById' == " + safeUid);
                        firebaseManager.getRegistrationsByCreator(uid)
                                .addOnSuccessListener(creatorSnap -> {
                                    swipeRefreshLayout.setRefreshing(false);
                                    if (creatorSnap != null) {
                                        for (DocumentSnapshot doc : creatorSnap.getDocuments()) {
                                            if (!seenIds.contains(doc.getId())) {
                                                EventRegistration reg = doc.toObject(EventRegistration.class);
                                                if (reg != null) {
                                                    reg.setId(doc.getId());
                                                    allRegistrations.add(reg);
                                                    seenIds.add(doc.getId());
                                                }
                                            }
                                        }
                                    }
                                    android.util.Log.d(TAG, "Total records after legacy fallback check: " + allRegistrations.size());
                                    showSuccessfulResultsState();
                                })
                                .addOnFailureListener(e -> {
                                    swipeRefreshLayout.setRefreshing(false);
                                    android.util.Log.w(TAG, "Legacy query by createdById finished with notice: " + e.getMessage());
                                    showSuccessfulResultsState();
                                });
                    } else {
                        swipeRefreshLayout.setRefreshing(false);
                        showSuccessfulResultsState();
                    }
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    android.util.Log.e(TAG, "Query failed with error: " + (e != null ? e.getMessage() : "Unknown"), e);

                    allRegistrations.clear();
                    filteredRegistrations.clear();
                    adapter.notifyDataSetChanged();

                    String errorMsg = (e != null && e.getMessage() != null) ? e.getMessage() : "Unknown error";
                    if (tvEmptyTitle != null) tvEmptyTitle.setText("Failed to Load Registrations");
                    if (tvEmptySubtitle != null) {
                        if (errorMsg.contains("PERMISSION_DENIED")) {
                            tvEmptySubtitle.setText("Error: PERMISSION_DENIED: Missing or insufficient permissions.\n\n"
                                    + "Please ensure updated Firestore security rules are deployed to Firebase project 'judgex-app'.\n\n"
                                    + "Tap Retry below or swipe down to try again.");
                        } else {
                            tvEmptySubtitle.setText("Error: " + errorMsg + "\n\nSwipe down to retry or tap below.");
                        }
                    }
                    if (btnExploreEvents != null) {
                        btnExploreEvents.setText("Retry");
                        btnExploreEvents.setOnClickListener(v -> loadRegistrations());
                    }
                    llEmptyState.setVisibility(View.VISIBLE);
                    UIUtils.showToast(this, "Failed to load registrations: " + errorMsg);
                });
    }

    private void showSuccessfulResultsState() {
        if (tvEmptyTitle != null) tvEmptyTitle.setText("No Registrations Found");
        if (tvEmptySubtitle != null) tvEmptySubtitle.setText("You haven't submitted any competition registrations under this status.");
        if (btnExploreEvents != null) {
            btnExploreEvents.setText("Explore Events");
            btnExploreEvents.setOnClickListener(v -> {
                startActivity(new Intent(this, BrowseEventsActivity.class));
                finish();
            });
        }
        applyFilter();
    }

    private void applyFilter() {
        filteredRegistrations.clear();
        int checkedId = chipGroupStatus.getCheckedChipId();

        for (EventRegistration reg : allRegistrations) {
            if (checkedId == R.id.chipConfirmed) {
                if (!reg.isConfirmed()) continue;
            } else if (checkedId == R.id.chipPending) {
                if (!reg.isPending()) continue;
            } else if (checkedId == R.id.chipWaitlisted) {
                if (!reg.isWaitlisted()) continue;
            } else if (checkedId == R.id.chipCancelled) {
                if (!reg.isCancelled()) continue;
            }
            filteredRegistrations.add(reg);
        }

        adapter.notifyDataSetChanged();
        llEmptyState.setVisibility(filteredRegistrations.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onRegistrationClick(EventRegistration registration) {
        Intent intent = new Intent(this, RegistrationDetailsActivity.class);
        intent.putExtra(Constants.EXTRA_REGISTRATION, registration);
        intent.putExtra(Constants.EXTRA_REGISTRATION_ID, registration.getId());
        startActivity(intent);
    }
}
