package com.example.judgex.judge;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.adapters.EvaluationCriteriaAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EvaluationFormActivity extends AppCompatActivity implements EvaluationCriteriaAdapter.OnScoreChangedListener {

    private MaterialToolbar toolbar;
    private TextView tvTeamNumber, tvCategory, tvTeamName, tvProjectTitle, tvLeaderDetails, tvDraftBadge;
    private TextView tvCriteriaProgress, tvRunningTotalScore;
    private ProgressBar progressBarCriteria;
    private RecyclerView recyclerViewCriteria;
    private TextInputEditText etRemarks;
    private MaterialButton btnSaveDraft, btnSubmitEvaluation;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Criterion> criteriaList = new ArrayList<>();
    private EvaluationCriteriaAdapter adapter;

    private Team currentTeam;
    private double currentTotalWeightedScore = 0.0;
    private boolean isAllCriteriaScored = false;
    private Evaluation loadedDraft = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evaluation_form);

        currentTeam = (Team) getIntent().getSerializableExtra(Constants.EXTRA_TEAM);
        if (currentTeam == null) {
            UIUtils.showToast(this, "Team information missing");
            finish();
            return;
        }

        initViews();
        displayTeamDetails();
        loadCriteriaAndDraft();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTeamNumber = findViewById(R.id.tvTeamNumber);
        tvCategory = findViewById(R.id.tvCategory);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvProjectTitle = findViewById(R.id.tvProjectTitle);
        tvLeaderDetails = findViewById(R.id.tvLeaderDetails);
        tvDraftBadge = findViewById(R.id.tvDraftBadge);
        tvCriteriaProgress = findViewById(R.id.tvCriteriaProgress);
        tvRunningTotalScore = findViewById(R.id.tvRunningTotalScore);
        progressBarCriteria = findViewById(R.id.progressBarCriteria);
        recyclerViewCriteria = findViewById(R.id.recyclerViewCriteria);
        etRemarks = findViewById(R.id.etRemarks);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnSubmitEvaluation = findViewById(R.id.btnSubmitEvaluation);

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewCriteria.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EvaluationCriteriaAdapter(criteriaList, this);
        recyclerViewCriteria.setAdapter(adapter);

        btnSaveDraft.setOnClickListener(v -> handleSaveDraft());
        btnSubmitEvaluation.setOnClickListener(v -> handleSubmitEvaluation());
    }

    private void displayTeamDetails() {
        tvTeamNumber.setText(currentTeam.getTeamNumber() != null ? currentTeam.getTeamNumber() : "TM");
        tvCategory.setText(currentTeam.getCategory() != null ? currentTeam.getCategory() : "General");
        tvTeamName.setText(currentTeam.getTeamName() != null ? currentTeam.getTeamName() : "Team");
        tvProjectTitle.setText(currentTeam.getProjectTitle() != null ? currentTeam.getProjectTitle() : "");

        String leader = currentTeam.getLeaderName() != null ? currentTeam.getLeaderName() : "Leader";
        int members = currentTeam.getMemberCount() > 0 ? currentTeam.getMemberCount() : 1;
        tvLeaderDetails.setText("👤 Leader: " + leader + " (" + members + " members)");
    }

    private void loadCriteriaAndDraft() {
        progressBarCriteria.setVisibility(View.VISIBLE);
        recyclerViewCriteria.setVisibility(View.GONE);

        // Fetch criteria for event
        firebaseManager.getCriteriaForEvent(currentTeam.getEventId())
                .addOnSuccessListener(querySnapshot -> {
                    criteriaList.clear();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Criterion criterion = doc.toObject(Criterion.class);
                        if (criterion != null) {
                            criterion.setId(doc.getId());
                            criteriaList.add(criterion);
                        }
                    }

                    // Fallback default criteria if none created yet
                    if (criteriaList.isEmpty()) {
                        criteriaList.add(new Criterion("1", currentTeam.getEventId(), "Innovation & Novelty", "Uniqueness of idea", 10.0, 25.0, 0));
                        criteriaList.add(new Criterion("2", currentTeam.getEventId(), "Technical Feasibility", "Architecture & Code quality", 10.0, 25.0, 1));
                        criteriaList.add(new Criterion("3", currentTeam.getEventId(), "Implementation & Demo", "Live prototype execution", 10.0, 25.0, 2));
                        criteriaList.add(new Criterion("4", currentTeam.getEventId(), "Presentation & Q&A", "Clarity of delivery and answers", 10.0, 25.0, 3));
                    }

                    adapter = new EvaluationCriteriaAdapter(criteriaList, this);
                    recyclerViewCriteria.setAdapter(adapter);

                    // Check for existing evaluation or draft
                    checkForExistingDraftOrEvaluation();
                })
                .addOnFailureListener(e -> {
                    progressBarCriteria.setVisibility(View.GONE);
                    UIUtils.showToast(this, "Failed to load criteria: " + e.getMessage());
                });
    }

    private void checkForExistingDraftOrEvaluation() {
        User currentJudge = firebaseManager.getCurrentUserProfile();
        String judgeId = currentJudge != null ? currentJudge.getUid() :
                (firebaseManager.getCurrentFirebaseUser() != null ? firebaseManager.getCurrentFirebaseUser().getUid() : "");

        firebaseManager.checkExistingEvaluation(currentTeam.getEventId(), currentTeam.getId(), judgeId)
                .addOnSuccessListener(doc -> {
                    progressBarCriteria.setVisibility(View.GONE);
                    recyclerViewCriteria.setVisibility(View.VISIBLE);

                    if (doc.exists()) {
                        Evaluation eval = doc.toObject(Evaluation.class);
                        if (eval != null) {
                            if (eval.isSubmitted()) {
                                // Already submitted - redirect to receipt
                                Intent intent = new Intent(EvaluationFormActivity.this, SubmissionConfirmationActivity.class);
                                intent.putExtra(Constants.EXTRA_TEAM, currentTeam);
                                intent.putExtra(Constants.EXTRA_EVALUATION, eval);
                                startActivity(intent);
                                finish();
                                return;
                            } else if (eval.isDraft()) {
                                // Resume draft
                                loadedDraft = eval;
                                tvDraftBadge.setVisibility(View.VISIBLE);
                                if (eval.getRemarks() != null) {
                                    etRemarks.setText(eval.getRemarks());
                                }
                                adapter.setExistingData(eval.getScores(), eval.getCriterionComments());
                                UIUtils.showToast(this, "Resumed saved draft for " + currentTeam.getTeamName());
                                return;
                            }
                        }
                    }
                    tvCriteriaProgress.setText("0 of " + criteriaList.size() + " criteria scored");
                    tvRunningTotalScore.setText("0.0 / 100");
                })
                .addOnFailureListener(e -> {
                    progressBarCriteria.setVisibility(View.GONE);
                    recyclerViewCriteria.setVisibility(View.VISIBLE);
                    tvCriteriaProgress.setText("0 of " + criteriaList.size() + " criteria scored");
                    tvRunningTotalScore.setText("0.0 / 100");
                });
    }

    @Override
    public void onScoreUpdated(Map<String, Double> currentScores, Map<String, String> comments, double totalWeightedScore, boolean allValid) {
        this.isAllCriteriaScored = allValid;
        this.currentTotalWeightedScore = totalWeightedScore;

        tvCriteriaProgress.setText(currentScores.size() + " of " + criteriaList.size() + " criteria scored");
        tvRunningTotalScore.setText(String.format("%.1f / 100", currentTotalWeightedScore));
    }

    private void handleSaveDraft() {
        User currentJudge = firebaseManager.getCurrentUserProfile();
        String judgeId = currentJudge != null ? currentJudge.getUid() :
                (firebaseManager.getCurrentFirebaseUser() != null ? firebaseManager.getCurrentFirebaseUser().getUid() : "");
        String judgeName = currentJudge != null && currentJudge.getName() != null ? currentJudge.getName() : "Judge";

        String remarks = etRemarks.getText() != null ? etRemarks.getText().toString().trim() : "";

        Dialog loading = UIUtils.showLoadingDialog(this, "Saving draft...");

        Evaluation draft = new Evaluation(
                Evaluation.generateEvaluationId(currentTeam.getEventId(), currentTeam.getId(), judgeId),
                currentTeam.getEventId(),
                currentTeam.getId(),
                currentTeam.getPanelId(),
                judgeId,
                judgeName,
                adapter.getScoresMap(),
                adapter.getCommentsMap(),
                currentTotalWeightedScore,
                100.0,
                remarks,
                System.currentTimeMillis(),
                Evaluation.STATUS_DRAFT
        );

        firebaseManager.getEvaluationsCollection().document(draft.getId()).set(draft)
                .addOnSuccessListener(aVoid -> {
                    loading.dismiss();
                    tvDraftBadge.setVisibility(View.VISIBLE);
                    UIUtils.showToast(this, "Draft saved successfully");
                })
                .addOnFailureListener(e -> {
                    loading.dismiss();
                    UIUtils.showToast(this, "Failed to save draft: " + e.getMessage());
                });
    }

    private void handleSubmitEvaluation() {
        if (!isAllCriteriaScored || adapter == null || !adapter.isAllScoresValid()) {
            UIUtils.showToast(this, "Please grade all criteria within allowed mark ranges before submitting.");
            return;
        }

        User currentJudge = firebaseManager.getCurrentUserProfile();
        String judgeId = currentJudge != null ? currentJudge.getUid() :
                (firebaseManager.getCurrentFirebaseUser() != null ? firebaseManager.getCurrentFirebaseUser().getUid() : "");
        String judgeName = currentJudge != null && currentJudge.getName() != null ? currentJudge.getName() : "Judge";

        String remarks = etRemarks.getText() != null ? etRemarks.getText().toString().trim() : "";

        UIUtils.showConfirmationDialog(this, "Confirm Submission",
                "Submit evaluation with final score of " + String.format("%.1f", currentTotalWeightedScore) + " / 100 for " + currentTeam.getTeamName() + "?\n\nSubmissions are permanent and cannot be modified.",
                "Submit", () -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Submitting evaluation securely...");

                    Evaluation evaluation = new Evaluation(
                            Evaluation.generateEvaluationId(currentTeam.getEventId(), currentTeam.getId(), judgeId),
                            currentTeam.getEventId(),
                            currentTeam.getId(),
                            currentTeam.getPanelId(),
                            judgeId,
                            judgeName,
                            adapter.getScoresMap(),
                            adapter.getCommentsMap(),
                            currentTotalWeightedScore,
                            100.0,
                            remarks,
                            System.currentTimeMillis(),
                            Evaluation.STATUS_SUBMITTED
                    );

                    firebaseManager.submitEvaluation(evaluation)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                Intent intent = new Intent(EvaluationFormActivity.this, SubmissionConfirmationActivity.class);
                                intent.putExtra(Constants.EXTRA_TEAM, currentTeam);
                                intent.putExtra(Constants.EXTRA_EVALUATION, evaluation);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Submission Error: " + e.getMessage());
                            });
                });
    }
}
