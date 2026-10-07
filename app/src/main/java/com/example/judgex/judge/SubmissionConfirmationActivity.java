package com.example.judgex.judge;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.judgex.R;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Team;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SubmissionConfirmationActivity extends AppCompatActivity {

    private TextView tvReceiptTeamName, tvReceiptProjectTitle, tvReceiptScore;
    private TextView tvReceiptJudge, tvReceiptTimestamp, tvReceiptRemarks;
    private MaterialButton btnDone;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private Team currentTeam;
    private Evaluation currentEvaluation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submission_confirmation);

        currentTeam = (Team) getIntent().getSerializableExtra(Constants.EXTRA_TEAM);
        currentEvaluation = (Evaluation) getIntent().getSerializableExtra(Constants.EXTRA_EVALUATION);

        initViews();
        displayData();
    }

    private void initViews() {
        tvReceiptTeamName = findViewById(R.id.tvReceiptTeamName);
        tvReceiptProjectTitle = findViewById(R.id.tvReceiptProjectTitle);
        tvReceiptScore = findViewById(R.id.tvReceiptScore);
        tvReceiptJudge = findViewById(R.id.tvReceiptJudge);
        tvReceiptTimestamp = findViewById(R.id.tvReceiptTimestamp);
        tvReceiptRemarks = findViewById(R.id.tvReceiptRemarks);
        btnDone = findViewById(R.id.btnDone);

        btnDone.setOnClickListener(v -> {
            Intent intent = new Intent(SubmissionConfirmationActivity.this, JudgeDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void displayData() {
        if (currentTeam != null) {
            tvReceiptTeamName.setText(currentTeam.getTeamNumber() + " - " + currentTeam.getTeamName());
            tvReceiptProjectTitle.setText(currentTeam.getProjectTitle());
        }

        if (currentEvaluation != null) {
            renderEvaluationDetails(currentEvaluation);
        } else if (currentTeam != null) {
            // Fetch evaluation from Firestore if viewing existing receipt
            String judgeId = getIntent().getStringExtra(Constants.EXTRA_JUDGE_ID);
            String eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
            if (judgeId != null && eventId != null) {
                firebaseManager.checkExistingEvaluation(eventId, currentTeam.getId(), judgeId)
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                Evaluation eval = doc.toObject(Evaluation.class);
                                if (eval != null) {
                                    renderEvaluationDetails(eval);
                                }
                            }
                        })
                        .addOnFailureListener(e -> UIUtils.showToast(this, "Failed to load receipt"));
            }
        }
    }

    private void renderEvaluationDetails(Evaluation eval) {
        tvReceiptScore.setText(String.format(Locale.getDefault(), "%.1f / %.0f", eval.getTotalScore(), eval.getMaxPossibleScore()));
        tvReceiptJudge.setText("Judge: " + (eval.getJudgeName() != null ? eval.getJudgeName() : "Screening Judge"));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        String formattedDate = sdf.format(new Date(eval.getSubmittedAt() > 0 ? eval.getSubmittedAt() : System.currentTimeMillis()));
        tvReceiptTimestamp.setText("Submitted: " + formattedDate);

        if (eval.getRemarks() != null && !eval.getRemarks().isEmpty()) {
            tvReceiptRemarks.setText("Remarks: \"" + eval.getRemarks() + "\"");
            tvReceiptRemarks.setVisibility(View.VISIBLE);
        } else {
            tvReceiptRemarks.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(SubmissionConfirmationActivity.this, JudgeDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
