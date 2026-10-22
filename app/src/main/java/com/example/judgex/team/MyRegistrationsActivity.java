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

    private void loadRegistrations() {
        swipeRefreshLayout.setRefreshing(true);
        if (firebaseManager.getCurrentFirebaseUser() == null) {
            finish();
            return;
        }

        String uid = firebaseManager.getCurrentFirebaseUser().getUid();

        firebaseManager.getTeamLeaderRegistrations(uid)
                .addOnSuccessListener(querySnapshot -> {
                    swipeRefreshLayout.setRefreshing(false);
                    allRegistrations.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        EventRegistration reg = doc.toObject(EventRegistration.class);
                        if (reg != null) {
                            reg.setId(doc.getId());
                            allRegistrations.add(reg);
                        }
                    }
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load registrations: " + e.getMessage());
                });
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
