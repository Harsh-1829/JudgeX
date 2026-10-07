package com.example.judgex.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.admin.AdminDashboardActivity;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.judge.JudgeDashboardActivity;
import com.example.judgex.models.User;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndRoute, 1200);
    }

    private void checkAuthAndRoute() {
        FirebaseUser currentUser = firebaseManager.getCurrentFirebaseUser();
        if (currentUser == null) {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
            return;
        }

        firebaseManager.fetchUserProfile(currentUser.getUid())
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            firebaseManager.setCurrentUserProfile(user);
                            if (user.isAdmin()) {
                                startActivity(new Intent(SplashActivity.this, AdminDashboardActivity.class));
                            } else {
                                startActivity(new Intent(SplashActivity.this, JudgeDashboardActivity.class));
                            }
                            finish();
                            return;
                        }
                    }
                    // Fallback to login if profile not found
                    firebaseManager.signOut();
                    startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                    finish();
                });
    }
}
