package com.example.judgex.qr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.models.EventRegistration;
import com.example.judgex.models.Team;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

public class QRViewActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvTeamNumber, tvTeamName, tvProjectTitle;
    private ImageView ivQRCode;
    private MaterialButton btnShare;

    private Team currentTeam;
    private EventRegistration currentRegistration;
    private Bitmap qrBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_view);

        currentRegistration = (EventRegistration) getIntent().getSerializableExtra(Constants.EXTRA_REGISTRATION);
        currentTeam = (Team) getIntent().getSerializableExtra(Constants.EXTRA_TEAM);

        if (currentRegistration == null && currentTeam == null) {
            UIUtils.showToast(this, "No team or registration details provided");
            finish();
            return;
        }

        initViews();
        displayAndGenerateQR();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTeamNumber = findViewById(R.id.tvTeamNumber);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvProjectTitle = findViewById(R.id.tvProjectTitle);
        ivQRCode = findViewById(R.id.ivQRCode);
        btnShare = findViewById(R.id.btnShare);

        toolbar.setNavigationOnClickListener(v -> finish());
        btnShare.setOnClickListener(v -> sharePassDetails());
    }

    private void displayAndGenerateQR() {
        String qrPayload;

        if (currentRegistration != null) {
            String teamNum = currentRegistration.getTeamNumber() != null ? currentRegistration.getTeamNumber() : "TM";
            String teamName = currentRegistration.getTeamName() != null ? currentRegistration.getTeamName() : "Team";
            String eventName = currentRegistration.getEventName() != null ? currentRegistration.getEventName() : "Competition";

            tvTeamNumber.setText(teamNum);
            tvTeamName.setText(teamName + " • " + eventName);
            String proj = currentRegistration.getProjectTitle() != null ? currentRegistration.getProjectTitle() : "";
            tvProjectTitle.setText(proj.isEmpty() ? ("Pass ID: " + currentRegistration.getId()) : (proj + "\nPass: " + currentRegistration.getId()));

            qrPayload = currentRegistration.buildRegistrationQrPayload();
        } else {
            tvTeamNumber.setText(currentTeam.getTeamNumber() != null ? currentTeam.getTeamNumber() : "TM");
            tvTeamName.setText(currentTeam.getTeamName() != null ? currentTeam.getTeamName() : "Team");
            tvProjectTitle.setText(currentTeam.getProjectTitle() != null ? currentTeam.getProjectTitle() : "");

            qrPayload = currentTeam.getQrCodeData();
            if (qrPayload == null || qrPayload.isEmpty() || !qrPayload.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject();
                    json.put("v", 2);
                    json.put("teamId", currentTeam.getId());
                    json.put("teamNumber", currentTeam.getTeamNumber());
                    json.put("teamName", currentTeam.getTeamName());
                    json.put("eventId", currentTeam.getEventId());
                    json.put("panelId", currentTeam.getPanelId());
                    json.put("issuedAt", System.currentTimeMillis());
                    json.put("token", java.util.UUID.randomUUID().toString());
                    qrPayload = json.toString();
                } catch (Exception e) {
                    qrPayload = currentTeam.getId();
                }
            }
        }

        try {
            qrBitmap = QRCodeGenerator.generateQRCodeBitmap(qrPayload, 600, 600);
            ivQRCode.setImageBitmap(qrBitmap);
        } catch (Exception e) {
            UIUtils.showToast(this, "Failed to render QR Code: " + e.getMessage());
        }
    }

    private void sharePassDetails() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");

        String shareBody;
        String subject;

        if (currentRegistration != null) {
            subject = "Event Pass - " + currentRegistration.getTeamNumber() + " (" + currentRegistration.getEventName() + ")";
            shareBody = "JudgeX Official Event Evaluation Pass\n"
                    + "Event: " + currentRegistration.getEventName() + "\n"
                    + "Registration ID: " + currentRegistration.getId() + "\n"
                    + "Team ID: " + currentRegistration.getTeamNumber() + "\n"
                    + "Team Name: " + currentRegistration.getTeamName() + "\n"
                    + "Project: " + (currentRegistration.getProjectTitle() != null ? currentRegistration.getProjectTitle() : "General") + "\n"
                    + "Leader: " + (currentRegistration.getTeamLeaderName() != null ? currentRegistration.getTeamLeaderName() : "Leader") + "\n"
                    + "Scan with JudgeX App for scoring.";
        } else {
            subject = "Team " + currentTeam.getTeamNumber() + " - JudgeX Badge";
            shareBody = "JudgeX Team Evaluation Pass\n"
                    + "Team ID: " + currentTeam.getTeamNumber() + "\n"
                    + "Team Name: " + currentTeam.getTeamName() + "\n"
                    + "Project: " + currentTeam.getProjectTitle() + "\n"
                    + "Leader: " + currentTeam.getLeaderName() + "\n"
                    + "Scan with JudgeX App for evaluation.";
        }

        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(shareIntent, "Share Evaluation Pass"));
    }
}
