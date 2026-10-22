package com.example.judgex.team;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamMember;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventRegistrationReviewActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvEventName, tvEventSchedule, tvTeamIdentifier;
    private TextInputLayout tilProjectTitle, tilCategory;
    private TextInputEditText etProjectTitle, etCategory;
    private TextView tvEligibilityCheckBadge, tvSizeRequirementHint;
    private LinearLayout llMembersContainer;
    private MaterialCheckBox cbConfirmSubmission;
    private TextView tvErrorMessage;
    private MaterialButton btnSubmitRegistration;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private Event currentEvent;
    private Team currentTeam;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_registration_review);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        currentTeam = (Team) getIntent().getSerializableExtra(Constants.EXTRA_TEAM);

        if (currentEvent == null || currentTeam == null) {
            UIUtils.showToast(this, "Missing event or team information.");
            finish();
            return;
        }

        initViews();
        bindData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvEventName = findViewById(R.id.tvEventName);
        tvEventSchedule = findViewById(R.id.tvEventSchedule);
        tvTeamIdentifier = findViewById(R.id.tvTeamIdentifier);
        tilProjectTitle = findViewById(R.id.tilProjectTitle);
        tilCategory = findViewById(R.id.tilCategory);
        etProjectTitle = findViewById(R.id.etProjectTitle);
        etCategory = findViewById(R.id.etCategory);
        tvEligibilityCheckBadge = findViewById(R.id.tvEligibilityCheckBadge);
        tvSizeRequirementHint = findViewById(R.id.tvSizeRequirementHint);
        llMembersContainer = findViewById(R.id.llMembersContainer);
        cbConfirmSubmission = findViewById(R.id.cbConfirmSubmission);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        btnSubmitRegistration = findViewById(R.id.btnSubmitRegistration);

        toolbar.setNavigationOnClickListener(v -> finish());
        btnSubmitRegistration.setOnClickListener(v -> handleSubmit());
    }

    private void bindData() {
        tvEventName.setText(currentEvent.getName());
        String dateStr = currentEvent.getScheduledDate() > 0 ? dateFormat.format(new Date(currentEvent.getScheduledDate())) : "Scheduled Date TBD";
        String venueStr = currentEvent.getVenue() != null && !currentEvent.getVenue().isEmpty() ? currentEvent.getVenue() : "Campus Venue";
        tvEventSchedule.setText("Schedule: " + dateStr + " • " + venueStr);

        tvTeamIdentifier.setText("Team: " + currentTeam.getTeamName() + " (" + currentTeam.getTeamNumber() + ")");
        etProjectTitle.setText(currentTeam.getProjectTitle() != null ? currentTeam.getProjectTitle() : "");
        etCategory.setText(currentTeam.getCategory() != null ? currentTeam.getCategory() : currentEvent.getCategory());

        List<TeamMember> members = currentTeam.getMembers() != null ? currentTeam.getMembers() : new ArrayList<>();
        int count = members.size();

        tvSizeRequirementHint.setText("Competition requires between " + currentEvent.getMinTeamSize() 
                + " and " + currentEvent.getMaxTeamSize() + " members. Current roster: " + count + " member(s).");

        boolean eligible = count >= currentEvent.getMinTeamSize() && count <= currentEvent.getMaxTeamSize();
        if (eligible) {
            tvEligibilityCheckBadge.setText("ELIGIBLE (" + count + " MEMBERS)");
            tvEligibilityCheckBadge.setBackgroundResource(R.drawable.bg_badge_success);
            tvEligibilityCheckBadge.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            tvEligibilityCheckBadge.setText("INELIGIBLE (" + count + " MEMBERS)");
            tvEligibilityCheckBadge.setBackgroundResource(R.drawable.bg_badge_locked);
            tvEligibilityCheckBadge.setTextColor(ContextCompat.getColor(this, R.color.danger));
        }

        // Populate members summary list
        llMembersContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (TeamMember m : members) {
            View itemView = inflater.inflate(R.layout.item_member, llMembersContainer, false);
            TextView tvName = itemView.findViewById(R.id.tvMemberName);
            TextView tvLeaderBadge = itemView.findViewById(R.id.tvLeaderBadge);
            TextView tvDept = itemView.findViewById(R.id.tvDeptAndYear);
            TextView tvRoll = itemView.findViewById(R.id.tvRollNo);
            TextView tvEmail = itemView.findViewById(R.id.tvEmail);
            View btnSetLeader = itemView.findViewById(R.id.btnSetLeader);
            View btnEdit = itemView.findViewById(R.id.btnEditMember);
            View btnDelete = itemView.findViewById(R.id.btnDeleteMember);

            tvName.setText(m.getName());
            tvLeaderBadge.setVisibility(m.isLeader() ? View.VISIBLE : View.GONE);
            tvDept.setText(m.getDepartment() + " • " + m.getYearOfStudy());
            tvRoll.setText("Roll: " + m.getRollNumber());
            tvEmail.setText(m.getEmail());

            // Hide editing buttons on review screen
            btnSetLeader.setVisibility(View.GONE);
            btnEdit.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);

            llMembersContainer.addView(itemView);
        }
    }

    private void handleSubmit() {
        tvErrorMessage.setVisibility(View.GONE);

        String title = etProjectTitle.getText() != null ? etProjectTitle.getText().toString().trim() : "";
        String category = etCategory.getText() != null ? etCategory.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            tilProjectTitle.setError("Project title is required");
            etProjectTitle.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(category)) {
            tilCategory.setError("Project category is required");
            etCategory.requestFocus();
            return;
        }

        if (!cbConfirmSubmission.isChecked()) {
            showError("Please check the confirmation box to confirm your team details.");
            return;
        }

        int count = currentTeam.getMembers() != null ? currentTeam.getMembers().size() : currentTeam.getMemberCount();
        if (count < currentEvent.getMinTeamSize()) {
            showError("Team has fewer members (" + count + ") than required by event (" + currentEvent.getMinTeamSize() + ").");
            return;
        }
        if (count > currentEvent.getMaxTeamSize()) {
            showError("Team exceeds maximum allowed size (" + currentEvent.getMaxTeamSize() + "). Current: " + count + ".");
            return;
        }

        if (firebaseManager.getCurrentFirebaseUser() == null) {
            showError("User session expired. Please sign in again.");
            return;
        }

        String uid = firebaseManager.getCurrentFirebaseUser().getUid();

        // Prepare registration record
        EventRegistration registration = new EventRegistration();
        registration.setId(EventRegistration.generateRegistrationId(currentEvent.getId(), currentTeam.getId()));
        registration.setEventId(currentEvent.getId());
        registration.setEventName(currentEvent.getName());
        registration.setTeamId(currentTeam.getId());
        registration.setTeamName(currentTeam.getTeamName());
        registration.setTeamNumber(currentTeam.getTeamNumber());
        registration.setOrganizationId(currentEvent.getOrganizationId() != null ? currentEvent.getOrganizationId() : currentTeam.getOrganizationId());
        registration.setTeamLeaderId(uid);
        registration.setTeamLeaderName(currentTeam.getLeaderName());
        registration.setTeamLeaderEmail(currentTeam.getLeaderEmail());
        registration.setProjectTitle(title);
        registration.setCategory(category);
        registration.setRegistrationSource(EventRegistration.SOURCE_TEAM_LEADER);
        registration.setCreatedById(uid);
        registration.setCreatedAt(System.currentTimeMillis());
        registration.setUpdatedAt(System.currentTimeMillis());

        // Snapshot roster
        List<TeamMember> snapshotList = new ArrayList<>();
        if (currentTeam.getMembers() != null) {
            snapshotList.addAll(currentTeam.getMembers());
        }
        registration.setMembers(snapshotList);

        // Status determination
        if (currentEvent.isRegistrationApprovalRequired()) {
            registration.setStatus(EventRegistration.STATUS_PENDING);
        } else {
            registration.setStatus(EventRegistration.STATUS_CONFIRMED);
        }

        Dialog loading = UIUtils.showLoadingDialog(this, "Submitting team registration...");

        firebaseManager.registerTeamForEvent(registration, currentTeam)
                .addOnSuccessListener(aVoid -> {
                    loading.dismiss();
                    Intent successIntent = new Intent(EventRegistrationReviewActivity.this, RegistrationSuccessActivity.class);
                    successIntent.putExtra(Constants.EXTRA_REGISTRATION, registration);
                    startActivity(successIntent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    loading.dismiss();
                    String msg = e.getMessage() != null ? e.getMessage() : "Registration submission failed.";
                    showError(msg);
                });
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
