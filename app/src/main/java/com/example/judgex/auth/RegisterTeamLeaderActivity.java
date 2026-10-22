package com.example.judgex.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.team.TeamLeaderDashboardActivity;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

public class RegisterTeamLeaderActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextInputLayout tilLeaderName, tilOrgName, tilEmail, tilDepartment, tilRollNumber, tilPhone, tilTeamName, tilPassword, tilConfirmPassword;
    private TextInputEditText etLeaderName, etOrgName, etEmail, etDepartment, etRollNumber, etPhone, etTeamName, etPassword, etConfirmPassword;
    private TextView tvErrorMessage, tvBackToLogin;
    private ProgressBar progressBar;
    private MaterialButton btnRegister;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_team_leader);

        initViews();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tilLeaderName = findViewById(R.id.tilLeaderName);
        tilOrgName = findViewById(R.id.tilOrgName);
        tilEmail = findViewById(R.id.tilEmail);
        tilDepartment = findViewById(R.id.tilDepartment);
        tilRollNumber = findViewById(R.id.tilRollNumber);
        tilPhone = findViewById(R.id.tilPhone);
        tilTeamName = findViewById(R.id.tilTeamName);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        etLeaderName = findViewById(R.id.etLeaderName);
        etOrgName = findViewById(R.id.etOrgName);
        etEmail = findViewById(R.id.etEmail);
        etDepartment = findViewById(R.id.etDepartment);
        etRollNumber = findViewById(R.id.etRollNumber);
        etPhone = findViewById(R.id.etPhone);
        etTeamName = findViewById(R.id.etTeamName);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        progressBar = findViewById(R.id.progressBar);
        btnRegister = findViewById(R.id.btnRegister);

        toolbar.setNavigationOnClickListener(v -> finish());
        tvBackToLogin.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> handleRegistration());
    }

    private void handleRegistration() {
        String name = etLeaderName.getText() != null ? etLeaderName.getText().toString().trim() : "";
        String orgName = etOrgName.getText() != null ? etOrgName.getText().toString().trim() : "";
        String rawEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String dept = etDepartment.getText() != null ? etDepartment.getText().toString().trim() : "";
        String rollNo = etRollNumber.getText() != null ? etRollNumber.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String teamName = etTeamName.getText() != null ? etTeamName.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        tilLeaderName.setError(null);
        tilOrgName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
        tvErrorMessage.setVisibility(View.GONE);

        if (TextUtils.isEmpty(name) || name.length() < 2) {
            tilLeaderName.setError("Full name is required (min 2 characters)");
            etLeaderName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(orgName) || orgName.length() < 2) {
            tilOrgName.setError("College / Organization name is required");
            etOrgName.requestFocus();
            return;
        }

        String email = FirebaseManager.normalizeEmail(rawEmail);
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        setLoading(true);

        String orgId = "org_" + orgName.toLowerCase().replaceAll("[^a-z0-9]", "_");

        firebaseManager.registerTeamLeaderAccount(name, email, password, orgId, orgName, dept, rollNo, phone, teamName)
                .addOnSuccessListener(leaderUser -> {
                    setLoading(false);
                    UIUtils.showToast(this, "Welcome, " + leaderUser.getName() + "! Team Leader account ready.");

                    Intent intent = new Intent(RegisterTeamLeaderActivity.this, TeamLeaderDashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        showError("An account with this email address already exists. Please sign in.");
                    } else {
                        String msg = e.getMessage() != null ? e.getMessage() : "Registration failed.";
                        showError(msg);
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        btnRegister.setEnabled(!isLoading);
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        etLeaderName.setEnabled(!isLoading);
        etOrgName.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etDepartment.setEnabled(!isLoading);
        etRollNumber.setEnabled(!isLoading);
        etPhone.setEnabled(!isLoading);
        etTeamName.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
        etConfirmPassword.setEnabled(!isLoading);
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
