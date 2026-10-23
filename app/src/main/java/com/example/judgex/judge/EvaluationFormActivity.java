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
import com.example.judgex.models.Event;
import com.example.judgex.models.Team;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluationFormActivity extends AppCompatActivity implements EvaluationCriteriaAdapter.OnScoreChangedListener {

    private MaterialToolbar toolbar;
    private TextView tvTeamNumber, tvCategory, tvTeamName, tvProjectTitle, tvLeaderDetails, tvDraftBadge;
    private TextView tvCriteriaProgress, tvRunningTotalScore;
    private ProgressBar progressBarCriteria;
    private RecyclerView recyclerViewCriteria;
    private TextInputEditText etRemarks;
    private MaterialButton btnSubmitEvaluation;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Criterion> criteriaList = new ArrayList<>();
    private EvaluationCriteriaAdapter adapter;

    private Team currentTeam;
    private Event currentEvent;
    private double currentCalculatedScore = 0.0;
    private double currentMaxScore = 100.0;
    private boolean isAllCriteriaScored = false;
    private Evaluation loadedDraft = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evaluation_form);

        currentTeam = (Team) getIntent().getSerializableExtra(Constants.EXTRA_TEAM);
        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);

        String extraTeamId = getIntent().getStringExtra(Constants.EXTRA_TEAM_ID);
        if (currentTeam == null && extraTeamId != null && !extraTeamId.isEmpty()) {
            Dialog loading = UIUtils.showLoadingDialog(this, "Loading team details...");
            firebaseManager.getTeam(extraTeamId)
                    .addOnSuccessListener(doc -> {
                        loading.dismiss();
                        if (doc.exists()) {
                            currentTeam = doc.toObject(Team.class);
                            if (currentTeam != null) {
                                currentTeam.setId(doc.getId());
                                initViews();
                                loadEventAndSetupUI();
                                return;
                            }
                        }
                        UIUtils.showToast(this, "Could not load team with ID: " + extraTeamId);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        loading.dismiss();
                        UIUtils.showToast(this, "Network error loading team: " + e.getMessage());
                        finish();
                    });
            return;
        }

        if (currentTeam == null) {
            UIUtils.showToast(this, "Team information missing");
            finish();
            return;
        }

        initViews();
        loadEventAndSetupUI();
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
        btnSubmitEvaluation = findViewById(R.id.btnSubmitEvaluation);

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewCriteria.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EvaluationCriteriaAdapter(criteriaList, this);
        recyclerViewCriteria.setAdapter(adapter);

        btnSubmitEvaluation.setOnClickListener(v -> handleSubmitEvaluation());
    }

    private void loadEventAndSetupUI() {
        if (currentEvent != null) {
            displayTeamDetails();
            loadCriteriaAndDraft();
        } else if (currentTeam.getEventId() != null) {
            firebaseManager.getEvent(currentTeam.getEventId())
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            currentEvent = doc.toObject(Event.class);
                            if (currentEvent != null) {
                                currentEvent.setId(doc.getId());
                            }
                        }
                        displayTeamDetails();
                        loadCriteriaAndDraft();
                    })
                    .addOnFailureListener(e -> {
                        displayTeamDetails();
                        loadCriteriaAndDraft();
                    });
        } else {
            displayTeamDetails();
            loadCriteriaAndDraft();
        }
    }

    private void displayTeamDetails() {
        tvTeamNumber.setText(currentTeam.getTeamNumber() != null ? currentTeam.getTeamNumber() : "TM");
        tvCategory.setText(currentTeam.getCategory() != null ? currentTeam.getCategory() : "General");
        tvProjectTitle.setText(currentTeam.getProjectTitle() != null ? currentTeam.getProjectTitle() : "");

        boolean isAnonymous = currentEvent != null && currentEvent.isAnonymousJudging();
        if (isAnonymous) {
            tvTeamName.setText("Team #" + currentTeam.getTeamNumber());
            tvLeaderDetails.setText("Anonymous Presentation (" + currentTeam.getMemberCount() + " members)");
        } else {
            tvTeamName.setText(currentTeam.getTeamName() != null ? currentTeam.getTeamName() : "Team");
            String leader = currentTeam.getLeaderName() != null ? currentTeam.getLeaderName() : "Leader";
            int members = currentTeam.getMemberCount() > 0 ? currentTeam.getMemberCount() : 1;
            tvLeaderDetails.setText("Leader: " + leader + " (" + members + " members)");
        }

        if (currentEvent != null && currentEvent.isLocked()) {
            btnSubmitEvaluation.setEnabled(false);
            UIUtils.showToast(this, "Notice: Event is locked. New submissions are disabled.");
        }
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

                    // Strict requirement: Never automatically insert fallback criteria
                    if (criteriaList.isEmpty()) {
                        progressBarCriteria.setVisibility(View.GONE);
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Evaluation Criteria Missing")
                                .setMessage("No evaluation criteria have been defined for this event yet. An evaluation form cannot be created until criteria are added by the Admin.")
                                .setPositiveButton("Close", (d, w) -> finish())
                                .setCancelable(false)
                                .show();
                        return;
                    }

                    // Enforce display ordering by orderIndex
                    java.util.Collections.sort(criteriaList, (c1, c2) -> Integer.compare(c1.getOrderIndex(), c2.getOrderIndex()));

                    adapter = new EvaluationCriteriaAdapter(criteriaList, this);
                    recyclerViewCriteria.setAdapter(adapter);

                    if (com.example.judgex.utils.ScoringValidationUtils.isWeightedScoring(currentEvent)
                            && !com.example.judgex.utils.ScoringValidationUtils.isTotalWeightValid(criteriaList)) {
                        double totalWeight = com.example.judgex.utils.ScoringValidationUtils.calculateTotalWeight(criteriaList);
                        UIUtils.showToast(this, "Notice: Criteria weights total " 
                                + com.example.judgex.utils.ScoringValidationUtils.formatWeight(totalWeight) 
                                + " (must be 100%). Final submission is disabled until weights total 100%.");
                    }

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

        boolean isRaw = currentEvent != null && Event.SCORING_RAW.equalsIgnoreCase(currentEvent.getScoringType());
        if (isRaw) {
            this.currentCalculatedScore = adapter != null ? adapter.calculateRawTotalScore() : 0.0;
            this.currentMaxScore = adapter != null ? adapter.calculateTotalPossibleMarks() : 100.0;
            tvRunningTotalScore.setText(String.format("%.1f / %.0f pts", currentCalculatedScore, currentMaxScore));
        } else {
            this.currentCalculatedScore = totalWeightedScore;
            this.currentMaxScore = 100.0;
            tvRunningTotalScore.setText(String.format("%.1f / 100", currentCalculatedScore));
        }

        tvCriteriaProgress.setText(currentScores.size() + " of " + criteriaList.size() + " criteria scored");
    }

    private void handleSubmitEvaluation() {
        if (currentEvent != null && currentEvent.isLocked()) {
            UIUtils.showToast(this, "Event is locked. Submissions are rejected.");
            return;
        }

        if (adapter == null) {
            UIUtils.showToast(this, "Evaluation criteria form is not ready yet.");
            return;
        }

        // Commit any visible EditText inputs that haven't triggered focus loss yet
        adapter.commitAllVisibleInputs(recyclerViewCriteria);

        // Perform strict criteria validation
        EvaluationCriteriaAdapter.CriteriaValidationResult validation = adapter.validateCriteria();
        if (!validation.isValid()) {
            UIUtils.showToast(this, validation.getErrorMessage());
            if (validation.getInvalidPosition() >= 0) {
                recyclerViewCriteria.smoothScrollToPosition(validation.getInvalidPosition());
            }
            return;
        }

        // Prevent final submission when weighted scoring criteria do not total exactly 100%
        if (com.example.judgex.utils.ScoringValidationUtils.isWeightedScoring(currentEvent)
                && !com.example.judgex.utils.ScoringValidationUtils.isTotalWeightValid(criteriaList)) {
            double totalWeight = com.example.judgex.utils.ScoringValidationUtils.calculateTotalWeight(criteriaList);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Submission Blocked: Weights Not 100%")
                    .setMessage("Cannot submit evaluation: This event's evaluation criteria weights total " 
                            + com.example.judgex.utils.ScoringValidationUtils.formatWeight(totalWeight) 
                            + ", but must total exactly 100% before evaluations can be submitted.\n\nPlease notify the Event Administrator to balance the criteria weights to 100%.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        User currentJudge = firebaseManager.getCurrentUserProfile();
        String judgeId = currentJudge != null ? currentJudge.getUid() :
                (firebaseManager.getCurrentFirebaseUser() != null ? firebaseManager.getCurrentFirebaseUser().getUid() : "");
        String judgeName = currentJudge != null && currentJudge.getName() != null ? currentJudge.getName() : "Judge";

        String remarks = etRemarks.getText() != null ? etRemarks.getText().toString().trim() : "";

        String teamDisplayName = (currentEvent != null && currentEvent.isAnonymousJudging()) 
                ? "Team #" + currentTeam.getTeamNumber() 
                : currentTeam.getTeamName();

        String scoreDisplay = String.format(java.util.Locale.US, "%.1f", currentCalculatedScore) + " / " + String.format(java.util.Locale.US, "%.0f", currentMaxScore);

        UIUtils.showConfirmationDialog(this, "Confirm Submission",
                "Submit evaluation with final score of " + scoreDisplay + " for " + teamDisplayName + "?\n\nSubmissions are permanent and cannot be modified.",
                "Submit", () -> {
                    btnSubmitEvaluation.setEnabled(false);
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
                            currentCalculatedScore,
                            currentMaxScore,
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
                                btnSubmitEvaluation.setEnabled(true);
                                String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                                UIUtils.showToast(this, "Submission Error: " + msg);
                            });
                });
    }
}
