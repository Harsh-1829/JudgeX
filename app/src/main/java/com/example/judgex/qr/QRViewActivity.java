package com.example.judgex.qr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
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
    private Bitmap qrBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_view);

        currentTeam = (Team) getIntent().getSerializableExtra(Constants.EXTRA_TEAM);
        if (currentTeam == null) {
            UIUtils.showToast(this, "No team details provided");
            finish();
            return;
        }

        initViews();
        displayTeamAndGenerateQR();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTeamNumber = findViewById(R.id.tvTeamNumber);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvProjectTitle = findViewById(R.id.tvProjectTitle);
        ivQRCode = findViewById(R.id.ivQRCode);
        btnShare = findViewById(R.id.btnShare);

        toolbar.setNavigationOnClickListener(v -> finish());
        btnShare.setOnClickListener(v -> shareTeamDetails());
    }

    private void displayTeamAndGenerateQR() {
        tvTeamNumber.setText(currentTeam.getTeamNumber() != null ? currentTeam.getTeamNumber() : "TM");
        tvTeamName.setText(currentTeam.getTeamName() != null ? currentTeam.getTeamName() : "Team");
        tvProjectTitle.setText(currentTeam.getProjectTitle() != null ? currentTeam.getProjectTitle() : "");

        String qrPayload = currentTeam.getQrCodeData();
        if (qrPayload == null || qrPayload.isEmpty()) {
            try {
                JSONObject json = new JSONObject();
                json.put("teamId", currentTeam.getId());
                json.put("teamNumber", currentTeam.getTeamNumber());
                json.put("teamName", currentTeam.getTeamName());
                json.put("eventId", currentTeam.getEventId());
                json.put("panelId", currentTeam.getPanelId());
                qrPayload = json.toString();
            } catch (Exception e) {
                qrPayload = currentTeam.getId();
            }
        }

        try {
            qrBitmap = QRCodeGenerator.generateQRCodeBitmap(qrPayload, 600, 600);
            ivQRCode.setImageBitmap(qrBitmap);
        } catch (Exception e) {
            UIUtils.showToast(this, "Failed to render QR Code: " + e.getMessage());
        }
    }

    private void shareTeamDetails() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareBody = "JudgeX Team Evaluation Pass\n"
                + "Team ID: " + currentTeam.getTeamNumber() + "\n"
                + "Team Name: " + currentTeam.getTeamName() + "\n"
                + "Project: " + currentTeam.getProjectTitle() + "\n"
                + "Leader: " + currentTeam.getLeaderName() + "\n"
                + "Scan with JudgeX App for evaluation.";
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Team " + currentTeam.getTeamNumber() + " - JudgeX Badge");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(shareIntent, "Share Team Info"));
    }
}
