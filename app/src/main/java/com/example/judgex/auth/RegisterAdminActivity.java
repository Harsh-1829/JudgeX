package com.example.judgex.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.admin.AdminDashboardActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Organization;
import com.example.judgex.models.User;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

public class RegisterAdminActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextInputLayout tilOrgName, tilAdminName, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etOrgName, etAdminName, etEmail, etPassword, etConfirmPassword;
    private TextView tvErrorMessage, tvBackToLogin;
    private ProgressBar progressBar;
    private MaterialButton btnRegister;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_admin);

        initViews();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tilOrgName = findViewById(R.id.tilOrgName);
        tilAdminName = findViewById(R.id.tilAdminName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        etOrgName = findViewById(R.id.etOrgName);
        etAdminName = findViewById(R.id.etAdminName);
        etEmail = findViewById(R.id.etEmail);
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
        String orgName = etOrgName.getText() != null ? etOrgName.getText().toString().trim() : "";
        String adminName = etAdminName.getText() != null ? etAdminName.getText().toString().trim() : "";
        String rawEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        tilOrgName.setError(null);
        tilAdminName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
        tvErrorMessage.setVisibility(View.GONE);

        if (TextUtils.isEmpty(orgName) || orgName.length() < 3) {
            tilOrgName.setError("College / Organization name is required (min 3 chars)");
            etOrgName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(adminName) || adminName.length() < 2) {
            tilAdminName.setError("Administrator name is required (min 2 chars)");
            etAdminName.requestFocus();
            return;
        }

        String email = FirebaseManager.normalizeEmail(rawEmail);
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid official email address");
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

        firebaseManager.registerOrganizationAndAdmin(orgName, adminName, email, password)
                .addOnSuccessListener(adminUser -> {
                    setLoading(false);
                    UIUtils.showToast(this, "Organization established! Welcome, " + adminUser.getName());

                    Intent intent = new Intent(RegisterAdminActivity.this, AdminDashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        showError("An account with this email address already exists. Please sign in or reset your password.");
                    } else {
                        String msg = e.getMessage() != null ? e.getMessage() : "Registration failed.";
                        showError("Registration failed: " + msg);
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        btnRegister.setEnabled(!isLoading);
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        etOrgName.setEnabled(!isLoading);
        etAdminName.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
        etConfirmPassword.setEnabled(!isLoading);
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
