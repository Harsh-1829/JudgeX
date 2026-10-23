package com.example.judgex.auth;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.admin.AdminDashboardActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.judge.JudgeDashboardActivity;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private TextView tvErrorMessage;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
    }

    private void initViews() {
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String identifier = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        tilEmail.setError(null);
        tilPassword.setError(null);
        tvErrorMessage.setVisibility(View.GONE);

        if (TextUtils.isEmpty(identifier)) {
            tilEmail.setError("Username or Email is required");
            etEmail.requestFocus();
            return;
        }

        final boolean isUsernameLogin = !identifier.contains("@");
        final String authEmail;

        if (isUsernameLogin) {
            authEmail = Constants.buildTeamSyntheticEmail(identifier);
        } else {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                tilEmail.setError("Enter a valid email address");
                etEmail.requestFocus();
                return;
            }
            authEmail = identifier;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        firebaseManager.signIn(authEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser != null) {
                        fetchProfileAndNavigate(firebaseUser);
                    } else {
                        setLoading(false);
                        showError("Authentication failed. Please try again.");
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        showError("Invalid credentials. Please verify your " + (isUsernameLogin ? "username" : "email") + " and password.");
                    } else if (e instanceof FirebaseAuthInvalidUserException) {
                        showError("No account found for " + (isUsernameLogin ? "username '" + identifier + "'." : "email '" + identifier + "'."));
                    } else {
                        showError(e.getMessage() != null ? e.getMessage() : "Login failed. Please check network connection.");
                    }
                });
    }

    private void fetchProfileAndNavigate(FirebaseUser firebaseUser) {
        firebaseManager.fetchUserProfile(firebaseUser.getUid())
                .addOnSuccessListener(documentSnapshot -> {
                    setLoading(false);
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setUid(documentSnapshot.getId());
                            
                            // Check active status
                            if (!user.isActive()) {
                                firebaseManager.signOut();
                                showError("Your account is currently disabled. Please contact the administrator.");
                                return;
                            }

                            firebaseManager.setCurrentUserProfile(user);
                            navigateBasedOnRole(user);
                            return;
                        }
                    }

                    // For the initial admin setup (if email contains admin and no profile in Firestore):
                    String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";
                    if (email.toLowerCase().contains("admin")) {
                        User adminUser = new User(firebaseUser.getUid(), email, "System Administrator", User.ROLE_ADMIN, null, System.currentTimeMillis());
                        firebaseManager.saveUserProfile(adminUser)
                                .addOnSuccessListener(aVoid -> {
                                    firebaseManager.setCurrentUserProfile(adminUser);
                                    navigateBasedOnRole(adminUser);
                                })
                                .addOnFailureListener(e -> {
                                    firebaseManager.signOut();
                                    showError("Failed to initialize admin profile: " + e.getMessage());
                                });
                    } else {
                        // Deny access if profile not provisioned by Admin
                        firebaseManager.signOut();
                        showError("Account profile is not provisioned in the system. Please contact the administrator.");
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Error retrieving user profile: " + e.getMessage());
                });
    }

    private void navigateBasedOnRole(User user) {
        if (user.isAdmin()) {
            UIUtils.showToast(this, "Logged in as Admin: " + user.getName());
            Intent intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else if (user.isJudge()) {
            UIUtils.showToast(this, "Logged in as Judge: " + user.getName());
            Intent intent = new Intent(LoginActivity.this, JudgeDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else if (user.isTeamLeader()) {
            UIUtils.showToast(this, "Logged in as Team Leader: " + user.getName());
            Intent intent = new Intent(LoginActivity.this, com.example.judgex.team.TeamLeaderDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            firebaseManager.signOut();
            showError("Unauthorized role (" + user.getRole() + "). Access denied.");
            return;
        }
        finish();
    }

    private void setLoading(boolean isLoading) {
        btnLogin.setEnabled(!isLoading);
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
