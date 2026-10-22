package com.example.judgex.team;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EventDetailLeaderActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvCategory, tvRegBadge, tvEventName, tvOrganizingDept;
    private TextView tvEventDate, tvVenue, tvDeadline;
    private TextView tvParticipationType, tvTeamSizeLimits, tvEligibility, tvFullDescription;
    private TextView tvStatusNotice;
    private MaterialButton btnRegisterTeam;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private Event currentEvent;
    private Team currentTeam;
    private EventRegistration existingRegistration;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail_leader);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        String eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
        if (currentEvent == null && eventId != null) {
            loadEventById(eventId);
        } else if (currentEvent != null) {
            initViews();
            bindEventDetails();
            loadTeamAndRegistration();
        } else {
            UIUtils.showToast(this, "Event not found.");
            finish();
        }
    }

    private void loadEventById(String eventId) {
        firebaseManager.getEvent(eventId)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentEvent = doc.toObject(Event.class);
                        if (currentEvent != null) {
                            currentEvent.setId(doc.getId());
                            initViews();
                            bindEventDetails();
                            loadTeamAndRegistration();
                            return;
                        }
                    }
                    UIUtils.showToast(this, "Could not load event.");
                    finish();
                })
                .addOnFailureListener(e -> {
                    UIUtils.showToast(this, "Error loading event: " + e.getMessage());
                    finish();
                });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvCategory = findViewById(R.id.tvCategory);
        tvRegBadge = findViewById(R.id.tvRegBadge);
        tvEventName = findViewById(R.id.tvEventName);
        tvOrganizingDept = findViewById(R.id.tvOrganizingDept);
        tvEventDate = findViewById(R.id.tvEventDate);
        tvVenue = findViewById(R.id.tvVenue);
        tvDeadline = findViewById(R.id.tvDeadline);
        tvParticipationType = findViewById(R.id.tvParticipationType);
        tvTeamSizeLimits = findViewById(R.id.tvTeamSizeLimits);
        tvEligibility = findViewById(R.id.tvEligibility);
        tvFullDescription = findViewById(R.id.tvFullDescription);
        tvStatusNotice = findViewById(R.id.tvStatusNotice);
        btnRegisterTeam = findViewById(R.id.btnRegisterTeam);

        toolbar.setNavigationOnClickListener(v -> finish());
        btnRegisterTeam.setOnClickListener(v -> handleRegistrationAction());
    }

    private void bindEventDetails() {
        if (currentEvent == null) return;

        tvEventName.setText(currentEvent.getName() != null ? currentEvent.getName() : "Event");
        tvCategory.setText(currentEvent.getCategory() != null ? currentEvent.getCategory().toUpperCase(Locale.US) : "COMPETITION");
        tvOrganizingDept.setText(currentEvent.getDepartment() != null ? currentEvent.getDepartment() : "General Department");
        tvFullDescription.setText(currentEvent.getDescription() != null ? currentEvent.getDescription() : "No detailed description provided.");

        String dateStr = currentEvent.getScheduledDate() > 0 ? dateFormat.format(new Date(currentEvent.getScheduledDate())) : "TBD";
        tvEventDate.setText(dateStr);
        tvVenue.setText(currentEvent.getVenue() != null && !currentEvent.getVenue().isEmpty() ? currentEvent.getVenue() : "Campus Venue");

        if (currentEvent.getRegistrationDeadline() > 0) {
            tvDeadline.setText("Registration Closes: " + dateFormat.format(new Date(currentEvent.getRegistrationDeadline())));
        } else {
            tvDeadline.setText("Registration: Open till event start");
        }

        if ("INDIVIDUAL".equalsIgnoreCase(currentEvent.getParticipationType())) {
            tvParticipationType.setText("Individual Participation");
            tvTeamSizeLimits.setText("Single Participant");
        } else {
            tvParticipationType.setText("Team Participation");
            tvTeamSizeLimits.setText("Min " + currentEvent.getMinTeamSize() + " • Max " + currentEvent.getMaxTeamSize() + " members");
        }

        tvEligibility.setText(currentEvent.getEligibility() != null ? currentEvent.getEligibility() : "Open to all verified college students.");

        boolean isOpen = currentEvent.isRegistrationOpen();
        if (isOpen) {
            tvRegBadge.setText("REGISTRATION OPEN");
            tvRegBadge.setBackgroundResource(R.drawable.bg_badge_success);
            tvRegBadge.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnRegisterTeam.setEnabled(true);
            btnRegisterTeam.setText("Register Team for Event");
        } else {
            tvRegBadge.setText("REGISTRATION CLOSED");
            tvRegBadge.setBackgroundResource(R.drawable.bg_badge_locked);
            tvRegBadge.setTextColor(ContextCompat.getColor(this, R.color.danger));
            btnRegisterTeam.setEnabled(false);
            btnRegisterTeam.setText("Registration Closed");
            tvStatusNotice.setVisibility(View.VISIBLE);
            tvStatusNotice.setText("Registration is closed or the deadline has passed for this competition.");
            tvStatusNotice.setTextColor(ContextCompat.getColor(this, R.color.danger));
        }
    }

    private void loadTeamAndRegistration() {
        if (firebaseManager.getCurrentFirebaseUser() == null || currentEvent == null) return;
        String uid = firebaseManager.getCurrentFirebaseUser().getUid();

        // Check if registration exists for this event
        firebaseManager.getTeamLeaderRegistrations(uid)
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        EventRegistration reg = doc.toObject(EventRegistration.class);
                        if (reg != null && currentEvent.getId().equals(reg.getEventId()) && !reg.isCancelled()) {
                            reg.setId(doc.getId());
                            existingRegistration = reg;
                            break;
                        }
                    }

                    if (existingRegistration != null) {
                        tvRegBadge.setText("✓ REGISTERED");
                        tvRegBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                        tvRegBadge.setTextColor(ContextCompat.getColor(this, R.color.accent));

                        tvStatusNotice.setVisibility(View.VISIBLE);
                        tvStatusNotice.setText("Your team is registered! Status: " + existingRegistration.getStatus() 
                                + "\nRegistration ID: " + existingRegistration.getId());
                        tvStatusNotice.setTextColor(ContextCompat.getColor(this, R.color.accent));

                        btnRegisterTeam.setEnabled(true);
                        btnRegisterTeam.setText("View Registration Details");
                    }

                    loadTeamData(uid);
                })
                .addOnFailureListener(e -> loadTeamData(uid));
    }

    private void loadTeamData(String uid) {
        User profile = firebaseManager.getCurrentUserProfile();
        String assignedTeamId = profile != null ? profile.getAssignedTeamId() : null;

        if (assignedTeamId != null && !assignedTeamId.isEmpty()) {
            firebaseManager.getTeam(assignedTeamId)
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            currentTeam = doc.toObject(Team.class);
                            if (currentTeam != null) currentTeam.setId(doc.getId());
                        }
                    });
        } else {
            firebaseManager.getTeamForLeader(uid)
                    .addOnSuccessListener(qs -> {
                        if (!qs.isEmpty()) {
                            DocumentSnapshot doc = qs.getDocuments().get(0);
                            currentTeam = doc.toObject(Team.class);
                            if (currentTeam != null) currentTeam.setId(doc.getId());
                        }
                    });
        }
    }

    private void handleRegistrationAction() {
        if (existingRegistration != null) {
            Intent intent = new Intent(this, RegistrationDetailsActivity.class);
            intent.putExtra(Constants.EXTRA_REGISTRATION, existingRegistration);
            intent.putExtra(Constants.EXTRA_REGISTRATION_ID, existingRegistration.getId());
            startActivity(intent);
            return;
        }

        if (currentEvent == null || !currentEvent.isRegistrationOpen()) {
            UIUtils.showToast(this, "Registration is currently closed for this event.");
            return;
        }

        if (currentTeam == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Team Setup Needed")
                    .setMessage("You need to set up your team roster before registering for this event.")
                    .setPositiveButton("Manage Team", (dialog, which) -> {
                        startActivity(new Intent(this, ManageTeamActivity.class));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Validate Team Size boundaries against event rules
        int memberCount = currentTeam.getMembers() != null && !currentTeam.getMembers().isEmpty()
                ? currentTeam.getMembers().size() : currentTeam.getMemberCount();

        if (memberCount < currentEvent.getMinTeamSize()) {
            new AlertDialog.Builder(this)
                    .setTitle("Team Size Requirement")
                    .setMessage("This competition requires a minimum of " + currentEvent.getMinTeamSize()
                            + " members. Your team currently has " + memberCount + " member(s).\n\nPlease add more members to your team roster.")
                    .setPositiveButton("Manage Team", (dialog, which) -> {
                        startActivity(new Intent(this, ManageTeamActivity.class));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (memberCount > currentEvent.getMaxTeamSize()) {
            new AlertDialog.Builder(this)
                    .setTitle("Team Size Exceeded")
                    .setMessage("This competition allows a maximum of " + currentEvent.getMaxTeamSize()
                            + " members. Your team currently has " + memberCount + " members.\n\nPlease adjust your team roster before proceeding.")
                    .setPositiveButton("Manage Team", (dialog, which) -> {
                        startActivity(new Intent(this, ManageTeamActivity.class));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Proceed to Review Screen
        Intent reviewIntent = new Intent(this, EventRegistrationReviewActivity.class);
        reviewIntent.putExtra(Constants.EXTRA_EVENT, currentEvent);
        reviewIntent.putExtra(Constants.EXTRA_TEAM, currentTeam);
        startActivity(reviewIntent);
    }
}
