package com.example.judgex.team;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.auth.LoginActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.User;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class TeamLeaderProfileActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvLeaderName, tvLeaderEmail, tvRoleBadge;
    private TextView tvOrgName, tvDepartment, tvRollNumber, tvPhone;
    private MaterialButton btnSignOut;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_leader_profile);

        initViews();
        loadProfileData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvLeaderName = findViewById(R.id.tvLeaderName);
        tvLeaderEmail = findViewById(R.id.tvLeaderEmail);
        tvRoleBadge = findViewById(R.id.tvRoleBadge);
        tvOrgName = findViewById(R.id.tvOrgName);
        tvDepartment = findViewById(R.id.tvDepartment);
        tvRollNumber = findViewById(R.id.tvRollNumber);
        tvPhone = findViewById(R.id.tvPhone);
        btnSignOut = findViewById(R.id.btnSignOut);

        toolbar.setNavigationOnClickListener(v -> finish());

        btnSignOut.setOnClickListener(v -> {
            UIUtils.showConfirmationDialog(this, "Sign Out", "Are you sure you want to sign out?", "Sign Out", () -> {
                firebaseManager.signOut();
                Intent intent = new Intent(TeamLeaderProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });
    }

    private void loadProfileData() {
        User user = firebaseManager.getCurrentUserProfile();
        if (user != null) {
            bindUser(user);
        } else if (firebaseManager.getCurrentFirebaseUser() != null) {
            String uid = firebaseManager.getCurrentFirebaseUser().getUid();
            firebaseManager.fetchUserProfile(uid)
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            User loadedUser = doc.toObject(User.class);
                            if (loadedUser != null) {
                                loadedUser.setUid(doc.getId());
                                firebaseManager.setCurrentUserProfile(loadedUser);
                                bindUser(loadedUser);
                            }
                        }
                    });
        }
    }

    private void bindUser(User user) {
        tvLeaderName.setText(user.getName() != null ? user.getName() : "Team Leader");
        tvLeaderEmail.setText(user.getEmail() != null ? user.getEmail() : "");
        tvOrgName.setText(user.getOrganizationName() != null ? user.getOrganizationName() : "Institution");
        tvDepartment.setText(user.getDepartment() != null && !user.getDepartment().isEmpty() ? user.getDepartment() : "General Engineering");
        tvRollNumber.setText(user.getRollNumber() != null && !user.getRollNumber().isEmpty() ? user.getRollNumber() : "—");
        tvPhone.setText(user.getPhone() != null && !user.getPhone().isEmpty() ? user.getPhone() : "Not Provided");
    }
}
