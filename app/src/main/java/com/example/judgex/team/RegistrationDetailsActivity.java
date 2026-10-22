package com.example.judgex.team;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.TeamMember;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RegistrationDetailsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvStatusBadge, tvRegistrationId, tvEventName, tvTeamName, tvSource;
    private TextView tvProjectAndCategory, tvSubmittedAt, tvRemarks;
    private LinearLayout llMembersContainer;
    private MaterialButton btnCancelRegistration;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private EventRegistration registration;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration_details);

        registration = (EventRegistration) getIntent().getSerializableExtra(Constants.EXTRA_REGISTRATION);
        String regId = getIntent().getStringExtra(Constants.EXTRA_REGISTRATION_ID);

        initViews();

        if (registration != null) {
            bindData();
        } else if (regId != null && !regId.isEmpty()) {
            loadRegistrationById(regId);
        } else {
            UIUtils.showToast(this, "Registration details not found.");
            finish();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvRegistrationId = findViewById(R.id.tvRegistrationId);
        tvEventName = findViewById(R.id.tvEventName);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvSource = findViewById(R.id.tvSource);
        tvProjectAndCategory = findViewById(R.id.tvProjectAndCategory);
        tvSubmittedAt = findViewById(R.id.tvSubmittedAt);
        tvRemarks = findViewById(R.id.tvRemarks);
        llMembersContainer = findViewById(R.id.llMembersContainer);
        btnCancelRegistration = findViewById(R.id.btnCancelRegistration);

        toolbar.setNavigationOnClickListener(v -> finish());
        btnCancelRegistration.setOnClickListener(v -> confirmCancellation());
    }

    private void loadRegistrationById(String regId) {
        Dialog loading = UIUtils.showLoadingDialog(this, "Loading registration details...");
        firebaseManager.getRegistration(regId)
                .addOnSuccessListener(doc -> {
                    loading.dismiss();
                    if (doc.exists()) {
                        registration = doc.toObject(EventRegistration.class);
                        if (registration != null) {
                            registration.setId(doc.getId());
                            bindData();
                            return;
                        }
                    }
                    UIUtils.showToast(this, "Registration record not found.");
                    finish();
                })
                .addOnFailureListener(e -> {
                    loading.dismiss();
                    UIUtils.showToast(this, "Error loading registration: " + e.getMessage());
                    finish();
                });
    }

    private void bindData() {
        if (registration == null) return;

        tvRegistrationId.setText(registration.getId());
        tvEventName.setText(registration.getEventName());
        tvTeamName.setText(registration.getTeamName() + " (" + registration.getTeamNumber() + ")");

        String src = EventRegistration.SOURCE_ADMIN.equals(registration.getRegistrationSource())
                ? "Registered by College Admin" : "Submitted by Team Leader";
        tvSource.setText(src);

        String proj = registration.getProjectTitle() != null ? registration.getProjectTitle() : "General";
        String cat = registration.getCategory() != null ? " • " + registration.getCategory() : "";
        tvProjectAndCategory.setText(proj + cat);

        tvSubmittedAt.setText("Submitted: " + dateFormat.format(new Date(registration.getCreatedAt())));

        if (registration.getRemarks() != null && !registration.getRemarks().isEmpty()) {
            tvRemarks.setVisibility(View.VISIBLE);
            tvRemarks.setText("Notice: " + registration.getRemarks());
        } else {
            tvRemarks.setVisibility(View.GONE);
        }

        // Status
        if (registration.isConfirmed()) {
            tvStatusBadge.setText("CONFIRMED");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnCancelRegistration.setVisibility(View.VISIBLE);
            btnCancelRegistration.setEnabled(true);
        } else if (registration.isPending()) {
            tvStatusBadge.setText("PENDING APPROVAL");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending);
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.warning));
            btnCancelRegistration.setVisibility(View.VISIBLE);
            btnCancelRegistration.setEnabled(true);
        } else if (registration.isCancelled()) {
            tvStatusBadge.setText("CANCELLED");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_locked);
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            btnCancelRegistration.setVisibility(View.GONE);
        } else {
            tvStatusBadge.setText(registration.getStatus());
            btnCancelRegistration.setVisibility(View.GONE);
        }

        // Populate Members Snapshot
        llMembersContainer.removeAllViews();
        List<TeamMember> members = registration.getMembers();
        LayoutInflater inflater = LayoutInflater.from(this);
        if (members != null && !members.isEmpty()) {
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

                btnSetLeader.setVisibility(View.GONE);
                btnEdit.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);

                llMembersContainer.addView(itemView);
            }
        }
    }

    private void confirmCancellation() {
        new AlertDialog.Builder(this)
                .setTitle("Withdraw Registration")
                .setMessage("Are you sure you want to withdraw your team's registration for " 
                        + registration.getEventName() + "?\n\nThis will cancel your registration.")
                .setPositiveButton("Withdraw", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Withdrawing registration...");
                    firebaseManager.cancelRegistration(registration.getId(), "Withdrawn by Team Leader")
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Registration successfully withdrawn.");
                                registration.setStatus(EventRegistration.STATUS_CANCELLED);
                                bindData();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to withdraw: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
