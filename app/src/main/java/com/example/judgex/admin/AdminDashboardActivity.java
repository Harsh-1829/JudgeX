package com.example.judgex.admin;

import android.app.Dialog;
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

public class AdminDashboardActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvOrgName, tvAdminEmail;
    private TextView tvCountTotalEvents, tvCountActiveEvents, tvCountUpcomingEvents, tvCountCompletedEvents;
    private MaterialButton btnLogout, btnManageEvents;
    private MaterialCardView cardEventsHub, cardCreateEvent, cardOrgSettings;

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
        TextInputEditText etEventName = view.findViewById(R.id.etEventName);
        TextInputEditText etEventDescription = view.findViewById(R.id.etEventDescription);

        RadioGroup rgScoringType = view.findViewById(R.id.rgScoringType);
        RadioButton rbScoringWeighted = view.findViewById(R.id.rbScoringWeighted);
        RadioButton rbScoringRaw = view.findViewById(R.id.rbScoringRaw);
        MaterialCheckBox cbAnonymousJudging = view.findViewById(R.id.cbAnonymousJudging);

        rbScoringWeighted.setChecked(true);
        cbAnonymousJudging.setChecked(false);

        builder.setView(view);
        builder.setPositiveButton("Schedule Event", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etEventName.getText() != null ? etEventName.getText().toString().trim() : "";
                String desc = etEventDescription.getText() != null ? etEventDescription.getText().toString().trim() : "";

                if (TextUtils.isEmpty(name)) {
                    tilEventName.setError("Competition name is required");
                    return;
                }

                String scoringType = rbScoringRaw.isChecked() ? Event.SCORING_RAW : Event.SCORING_WEIGHTED;
                boolean isAnonymous = cbAnonymousJudging.isChecked();

                dialog.dismiss();
                Dialog loadingDialog = UIUtils.showLoadingDialog(this, "Creating event...");

                String orgId = currentAdmin != null ? currentAdmin.getOrganizationId() : null;
                Event newEvent = new Event(null, name, desc, Event.STATUS_ACTIVE, false, System.currentTimeMillis());
                newEvent.setScoringType(scoringType);
                newEvent.setAnonymousJudging(isAnonymous);
                if (orgId != null) {
                    newEvent.setOrganizationId(orgId);
                }

                firebaseManager.createEvent(newEvent)
                        .addOnSuccessListener(docRef -> {
                            loadingDialog.dismiss();
                            newEvent.setId(docRef.getId());
                            UIUtils.showToast(this, "Event created successfully!");

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

    private void showOrganizationDetailsDialog() {
        String orgName = currentAdmin != null && currentAdmin.getOrganizationName() != null ? currentAdmin.getOrganizationName() : "Institution";
        String orgId = currentAdmin != null && currentAdmin.getOrganizationId() != null ? currentAdmin.getOrganizationId() : "primary_tenant";
        String adminName = currentAdmin != null && currentAdmin.getName() != null ? currentAdmin.getName() : "Administrator";
        String email = currentAdmin != null && currentAdmin.getEmail() != null ? currentAdmin.getEmail() : "";

        new AlertDialog.Builder(this)
                .setTitle("Institution Details")
                .setMessage("College / Org: " + orgName + "\n\n" +
                        "Tenant ID: " + orgId + "\n\n" +
                        "Administrator: " + adminName + "\n\n" +
                        "Official Email: " + email + "\n\n" +
                        "Multi-tenant data isolation is active. All events, rubrics, panels, and scores are scoped to your institution.")
                .setPositiveButton("Close", null)
                .show();
    }
}
