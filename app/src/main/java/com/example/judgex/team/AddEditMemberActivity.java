package com.example.judgex.team;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamMember;
import com.example.judgex.utils.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.UUID;

public class AddEditMemberActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvFormTitle, tvErrorMessage;
    private TextInputLayout tilMemberName, tilEmail, tilRollNumber, tilDepartment, tilYear, tilPhone, tilRoleInTeam;
    private TextInputEditText etMemberName, etEmail, etRollNumber, etDepartment, etYear, etPhone, etRoleInTeam;
    private MaterialSwitch switchLeader;
    private MaterialButton btnSaveMember;

    private Team currentTeam;
    private TeamMember existingMember;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_member);

        currentTeam = (Team) getIntent().getSerializableExtra(Constants.EXTRA_TEAM);
        existingMember = (TeamMember) getIntent().getSerializableExtra(Constants.EXTRA_MEMBER);

        initViews();
        populateFields();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvFormTitle = findViewById(R.id.tvFormTitle);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        tilMemberName = findViewById(R.id.tilMemberName);
        tilEmail = findViewById(R.id.tilEmail);
        tilRollNumber = findViewById(R.id.tilRollNumber);
        tilDepartment = findViewById(R.id.tilDepartment);
        tilYear = findViewById(R.id.tilYear);
        tilPhone = findViewById(R.id.tilPhone);
        tilRoleInTeam = findViewById(R.id.tilRoleInTeam);

        etMemberName = findViewById(R.id.etMemberName);
        etEmail = findViewById(R.id.etEmail);
        etRollNumber = findViewById(R.id.etRollNumber);
        etDepartment = findViewById(R.id.etDepartment);
        etYear = findViewById(R.id.etYear);
        etPhone = findViewById(R.id.etPhone);
        etRoleInTeam = findViewById(R.id.etRoleInTeam);

        switchLeader = findViewById(R.id.switchLeader);
        btnSaveMember = findViewById(R.id.btnSaveMember);

        toolbar.setNavigationOnClickListener(v -> finish());
        btnSaveMember.setOnClickListener(v -> handleSave());
    }

    private void populateFields() {
        if (existingMember != null) {
            tvFormTitle.setText("Edit Member");
            toolbar.setTitle("Edit Member");
            etMemberName.setText(existingMember.getName());
            etEmail.setText(existingMember.getEmail());
            etRollNumber.setText(existingMember.getRollNumber());
            etDepartment.setText(existingMember.getDepartment());
            etYear.setText(existingMember.getYearOfStudy());
            etPhone.setText(existingMember.getPhone());
            etRoleInTeam.setText(existingMember.getRoleInTeam());
            switchLeader.setChecked(existingMember.isLeader());
        } else {
            tvFormTitle.setText("Add New Member");
            toolbar.setTitle("Add Member");
            etYear.setText("3rd Year");
            etRoleInTeam.setText("Team Member");
        }
    }

    private void handleSave() {
        String name = etMemberName.getText() != null ? etMemberName.getText().toString().trim() : "";
        String rawEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String rollNumber = etRollNumber.getText() != null ? etRollNumber.getText().toString().trim() : "";
        String department = etDepartment.getText() != null ? etDepartment.getText().toString().trim() : "";
        String year = etYear.getText() != null ? etYear.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String roleInTeam = etRoleInTeam.getText() != null ? etRoleInTeam.getText().toString().trim() : "";
        boolean isLeader = switchLeader.isChecked();

        tilMemberName.setError(null);
        tilEmail.setError(null);
        tilRollNumber.setError(null);
        tilDepartment.setError(null);
        tvErrorMessage.setVisibility(View.GONE);

        if (TextUtils.isEmpty(name) || name.length() < 2) {
            tilMemberName.setError("Full name is required (min 2 characters)");
            etMemberName.requestFocus();
            return;
        }

        String email = FirebaseManager.normalizeEmail(rawEmail);
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid institutional email");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(rollNumber)) {
            tilRollNumber.setError("Roll number / Student ID is required");
            etRollNumber.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(department)) {
            tilDepartment.setError("Department is required");
            etDepartment.requestFocus();
            return;
        }

        // Check for duplicates in current team
        if (currentTeam != null && currentTeam.getMembers() != null) {
            for (TeamMember m : currentTeam.getMembers()) {
                // If editing existing member, skip comparing against self
                if (existingMember != null && existingMember.getId() != null && existingMember.getId().equals(m.getId())) {
                    continue;
                }

                if (email.equalsIgnoreCase(m.getEmail())) {
                    showError("A member with email " + email + " already exists in this team.");
                    tilEmail.setError("Duplicate email in team");
                    etEmail.requestFocus();
                    return;
                }

                if (rollNumber.equalsIgnoreCase(m.getRollNumber())) {
                    showError("A member with Roll Number " + rollNumber + " already exists in this team.");
                    tilRollNumber.setError("Duplicate roll number in team");
                    etRollNumber.requestFocus();
                    return;
                }
            }
        }

        TeamMember resultMember = existingMember != null ? existingMember : new TeamMember();
        if (resultMember.getId() == null || resultMember.getId().isEmpty()) {
            resultMember.setId(UUID.randomUUID().toString());
        }
        resultMember.setName(name);
        resultMember.setEmail(email);
        resultMember.setRollNumber(rollNumber);
        resultMember.setDepartment(department);
        resultMember.setYearOfStudy(!year.isEmpty() ? year : "Undergraduate");
        resultMember.setPhone(phone);
        resultMember.setRoleInTeam(!roleInTeam.isEmpty() ? roleInTeam : (isLeader ? "Team Leader" : "Member"));
        resultMember.setLeader(isLeader);

        Intent data = new Intent();
        data.putExtra(Constants.EXTRA_MEMBER, resultMember);
        setResult(RESULT_OK, data);
        finish();
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
