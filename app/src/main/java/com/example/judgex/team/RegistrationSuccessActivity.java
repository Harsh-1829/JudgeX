package com.example.judgex.team;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.judgex.R;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.utils.Constants;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RegistrationSuccessActivity extends AppCompatActivity {

    private TextView tvSubtitle, tvStatusBadge, tvRegistrationId, tvEventName, tvTeamName, tvMemberCount, tvTimestamp;
    private MaterialButton btnViewRegistrations, btnBackToDashboard;

    private EventRegistration registration;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration_success);

        registration = (EventRegistration) getIntent().getSerializableExtra(Constants.EXTRA_REGISTRATION);

        initViews();
        bindData();
    }

    private void initViews() {
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvRegistrationId = findViewById(R.id.tvRegistrationId);
        tvEventName = findViewById(R.id.tvEventName);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvMemberCount = findViewById(R.id.tvMemberCount);
        tvTimestamp = findViewById(R.id.tvTimestamp);
        btnViewRegistrations = findViewById(R.id.btnViewRegistrations);
        btnBackToDashboard = findViewById(R.id.btnBackToDashboard);

        btnViewRegistrations.setOnClickListener(v -> {
            Intent intent = new Intent(this, MyRegistrationsActivity.class);
            startActivity(intent);
            finish();
        });

        btnBackToDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, TeamLeaderDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void bindData() {
        if (registration == null) return;

        tvRegistrationId.setText(registration.getId());
        tvEventName.setText(registration.getEventName());
        tvTeamName.setText(registration.getTeamName() + " (" + registration.getTeamNumber() + ")");
        tvMemberCount.setText(registration.getMemberCount() + " members");
        tvTimestamp.setText(dateFormat.format(new Date(registration.getCreatedAt())));

        if (registration.isConfirmed()) {
            tvStatusBadge.setText("CONFIRMED");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.white));
            tvSubtitle.setText("Your team registration is confirmed. You can present your QR pass during judging.");
        } else if (registration.isPending()) {
            tvStatusBadge.setText("PENDING APPROVAL");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending);
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.warning));
            tvSubtitle.setText("Your registration is submitted and awaiting review by the event coordinator.");
        } else {
            tvStatusBadge.setText(registration.getStatus());
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(this, TeamLeaderDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
