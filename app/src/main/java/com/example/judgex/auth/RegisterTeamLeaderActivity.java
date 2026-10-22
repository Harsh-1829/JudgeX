package com.example.judgex.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.team.TeamLeaderDashboardActivity;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

import java.util.Arrays;
import java.util.List;

public class RegisterTeamLeaderActivity extends AppCompatActivity {

    public static final String[] SCHOOL_OPTIONS = new String[]{
            Event.SCHOOL_STME,
            Event.SCHOOL_SOC,
            Event.SCHOOL_SPTM,
            Event.SCHOOL_SBM,
            Event.SCHOOL_SOL
    };

    public static final String[] YEAR_OPTIONS = new String[]{
            "1st Year",
            "2nd Year",
            "3rd Year",
            "4th Year",
            "5th Year / PG"
    };

    private MaterialToolbar toolbar;
    private TextInputLayout tilLeaderName, tilYearOfStudy, tilSchool, tilEmail, tilPassword, tilConfirmPassword, tilTeamName;
    private TextInputEditText etLeaderName, etEmail, etPassword, etConfirmPassword, etTeamName;
    private AutoCompleteTextView actvYearOfStudy, actvSchool;
    private TextView tvErrorMessage, tvBackToLogin;
    private ProgressBar progressBar;
    private MaterialButton btnRegister;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_team_leader);

        initViews();
        setupDropdowns();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tilLeaderName = findViewById(R.id.tilLeaderName);
        tilYearOfStudy = findViewById(R.id.tilYearOfStudy);
        tilSchool = findViewById(R.id.tilSchool);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        tilTeamName = findViewById(R.id.tilTeamName);

        etLeaderName = findViewById(R.id.etLeaderName);
        actvYearOfStudy = findViewById(R.id.actvYearOfStudy);
        actvSchool = findViewById(R.id.actvSchool);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etTeamName = findViewById(R.id.etTeamName);

        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        progressBar = findViewById(R.id.progressBar);
        btnRegister = findViewById(R.id.btnRegister);

        toolbar.setNavigationOnClickListener(v -> finish());
        tvBackToLogin.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> handleRegistration());
    }

    private void setupDropdowns() {
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                YEAR_OPTIONS
        );
        actvYearOfStudy.setAdapter(yearAdapter);

        ArrayAdapter<String> schoolAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                SCHOOL_OPTIONS
        );
        actvSchool.setAdapter(schoolAdapter);
    }

    private void handleRegistration() {
        String name = etLeaderName.getText() != null ? etLeaderName.getText().toString().trim() : "";
        String yearOfStudy = actvYearOfStudy.getText() != null ? actvYearOfStudy.getText().toString().trim() : "";
        String school = actvSchool.getText() != null ? actvSchool.getText().toString().trim() : "";
        String rawEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";
        String teamName = etTeamName.getText() != null ? etTeamName.getText().toString().trim() : "";

        tilLeaderName.setError(null);
        tilYearOfStudy.setError(null);
        tilSchool.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
        tilTeamName.setError(null);
        tvErrorMessage.setVisibility(View.GONE);

        if (TextUtils.isEmpty(name) || name.length() < 2) {
            tilLeaderName.setError("Full name is required (min 2 characters)");
            etLeaderName.requestFocus();
            return;
        }

        List<String> validYears = Arrays.asList(YEAR_OPTIONS);
        if (TextUtils.isEmpty(yearOfStudy) || !validYears.contains(yearOfStudy)) {
            tilYearOfStudy.setError("Please select a valid study year from the list");
            actvYearOfStudy.requestFocus();
            return;
        }

        List<String> validSchools = Arrays.asList(SCHOOL_OPTIONS);
        if (TextUtils.isEmpty(school) || !validSchools.contains(school.toUpperCase())) {
            tilSchool.setError("Please select a valid school (STME, SOC, SPTM, SBM, SOL)");
            actvSchool.requestFocus();
            return;
        }

        String email = FirebaseManager.normalizeEmail(rawEmail);
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid institutional email address");
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

        if (TextUtils.isEmpty(teamName) || teamName.length() < 2) {
            tilTeamName.setError("Team name is required (min 2 characters)");
            etTeamName.requestFocus();
            return;
        }

        setLoading(true);

        firebaseManager.registerTeamLeaderAccount(name, email, password, school.toUpperCase(), yearOfStudy, teamName)
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
        actvYearOfStudy.setEnabled(!isLoading);
        actvSchool.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
        etConfirmPassword.setEnabled(!isLoading);
        etTeamName.setEnabled(!isLoading);
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
