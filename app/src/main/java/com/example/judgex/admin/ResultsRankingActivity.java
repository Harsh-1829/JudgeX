package com.example.judgex.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.RankingAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Criterion;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamResult;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ResultsRankingActivity extends AppCompatActivity implements RankingAdapter.OnRankingClickListener {

    private MaterialToolbar toolbar;
    private Spinner spinnerFilterPanel;
    private MaterialButton btnShareResults;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvEmptyState;
    private LinearLayout llErrorState;
    private TextView tvErrorMessage;
    private MaterialButton btnRetry;
    private RecyclerView recyclerViewRankings;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<TeamResult> allResults = new ArrayList<>();
    private final List<TeamResult> filteredResults = new ArrayList<>();
    private final List<Panel> panelList = new ArrayList<>();
    private final List<Criterion> criteriaList = new ArrayList<>();
    private final Map<String, Integer> panelJudgeCountMap = new HashMap<>();
    private RankingAdapter adapter;

    private Event currentEvent;
    private String eventId;
    private boolean isInitialDataLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results_ranking);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
        if (currentEvent != null && eventId == null) {
            eventId = currentEvent.getId();
        }

        initViews();
        loadFreshEventAndRankings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isInitialDataLoaded) {
            loadFreshEventAndRankings();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        spinnerFilterPanel = findViewById(R.id.spinnerFilterPanel);
        btnShareResults = findViewById(R.id.btnShareResults);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        llErrorState = findViewById(R.id.llErrorState);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        btnRetry = findViewById(R.id.btnRetry);
        recyclerViewRankings = findViewById(R.id.recyclerViewRankings);

        updateHeaderUI();

        toolbar.setNavigationOnClickListener(v -> finish());

        boolean isFinal = currentEvent != null && (currentEvent.isLocked() || currentEvent.isCompleted());
        boolean isAnonymous = currentEvent != null && currentEvent.isAnonymousJudging();

        recyclerViewRankings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RankingAdapter(filteredResults, isFinal, isAnonymous, this);
        recyclerViewRankings.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadFreshEventAndRankings);
        btnShareResults.setOnClickListener(v -> shareRankings());
        btnRetry.setOnClickListener(v -> loadFreshEventAndRankings());

        spinnerFilterPanel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isInitialDataLoaded) {
                    applyFilter(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateHeaderUI() {
        if (currentEvent != null) {
            boolean isFinal = currentEvent.isLocked() || currentEvent.isCompleted();
            toolbar.setSubtitle(currentEvent.getName() + (isFinal ? " (Official Final Results)" : " (Provisional Standings)"));
            if (adapter != null) {
                adapter.setEventCompletedOrLocked(isFinal);
                adapter.setAnonymousJudging(currentEvent.isAnonymousJudging());
            }
        } else {
            toolbar.setSubtitle("Event Leaderboard");
        }
    }

    private void loadFreshEventAndRankings() {
        if (eventId == null || eventId.trim().isEmpty()) {
            showError("No active event specified.");
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        hideError();

        // 1. Fetch fresh event document from Firestore to guarantee accurate lifecycle and scoring model
        firebaseManager.getEvent(eventId)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Event loaded = doc.toObject(Event.class);
                        if (loaded != null) {
                            loaded.setId(doc.getId());
                            currentEvent = loaded;
                            updateHeaderUI();
                        }
                    }
                    loadCriteriaAndPanels();
                })
                .addOnFailureListener(e -> {
                    // Fall back to existing cached event if offline
                    loadCriteriaAndPanels();
                });
    }

    private void loadCriteriaAndPanels() {
        // 2. Load Criteria
        firebaseManager.getCriteriaForEvent(eventId)
                .addOnSuccessListener(querySnapshot -> {
                    criteriaList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Criterion c = doc.toObject(Criterion.class);
                        if (c != null) {
                            c.setId(doc.getId());
                            criteriaList.add(c);
                        }
                    }
                    loadPanels();
                })
                .addOnFailureListener(e -> loadPanels());
    }

    private void loadPanels() {
        // 3. Load Panels
        firebaseManager.getPanelsForEvent(eventId)
                .addOnSuccessListener(panelSnapshots -> {
                    panelList.clear();
                    panelJudgeCountMap.clear();

                    List<String> filterOptions = new ArrayList<>();
                    filterOptions.add("All Panels (Overall Standings)");

                    for (DocumentSnapshot doc : panelSnapshots.getDocuments()) {
                        Panel p = doc.toObject(Panel.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            panelList.add(p);
                            int count = p.getJudgeIds() != null ? p.getJudgeIds().size() : 0;
                            panelJudgeCountMap.put(p.getId(), count);
                            filterOptions.add(p.getName());
                        }
                    }

                    int selectedPos = spinnerFilterPanel.getSelectedItemPosition();
                    ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, filterOptions);
                    spinnerFilterPanel.setAdapter(spinnerAdapter);
                    if (selectedPos > 0 && selectedPos < filterOptions.size()) {
                        spinnerFilterPanel.setSelection(selectedPos);
                    }

                    loadTeamsAndEvaluations();
                })
                .addOnFailureListener(e -> loadTeamsAndEvaluations());
    }

    private void loadTeamsAndEvaluations() {
        // 4. Query Teams for this specific event
        firebaseManager.getTeamsForEvent(eventId)
                .addOnSuccessListener(teamSnapshots -> {
                    List<Team> teams = new ArrayList<>();
                    Map<String, Team> teamMap = new HashMap<>();

                    for (DocumentSnapshot doc : teamSnapshots.getDocuments()) {
                        Team team = doc.toObject(Team.class);
                        if (team != null) {
                            team.setId(doc.getId());
                            teams.add(team);
                            teamMap.put(team.getId(), team);
                        }
                    }

                    // 5. Query evaluations for this specific event
                    firebaseManager.getEvaluationsForEvent(eventId)
                            .addOnSuccessListener(evalSnapshots -> {
                                Map<String, List<Evaluation>> teamEvalMap = new HashMap<>();
                                Set<String> evaluatedTeamIds = new HashSet<>();

                                for (DocumentSnapshot doc : evalSnapshots.getDocuments()) {
                                    Evaluation eval = doc.toObject(Evaluation.class);
                                    // Task requirement: Include only valid, successfully submitted evaluations.
                                    // Drafts must NEVER count toward rankings.
                                    if (eval != null && eval.getTeamId() != null && eval.isSubmitted()) {
                                        String tId = eval.getTeamId();
                                        if (!teamEvalMap.containsKey(tId)) {
                                            teamEvalMap.put(tId, new ArrayList<>());
                                        }
                                        teamEvalMap.get(tId).add(eval);
                                        evaluatedTeamIds.add(tId);
                                    }
                                }

                                // Data resilience: Ensure any team that has evaluations is included,
                                // even if team document had panel mismatch or unlinked eventId
                                for (String tId : evaluatedTeamIds) {
                                    if (!teamMap.containsKey(tId)) {
                                        Team fallbackTeam = new Team();
                                        fallbackTeam.setId(tId);
                                        fallbackTeam.setTeamNumber("TM-" + tId.substring(Math.max(0, tId.length() - 4)));
                                        fallbackTeam.setTeamName("Team " + tId);
                                        fallbackTeam.setEventId(eventId);
                                        teams.add(fallbackTeam);
                                        teamMap.put(tId, fallbackTeam);
                                    }
                                }

                                calculateAndRank(teams, teamEvalMap);
                                isInitialDataLoaded = true;
                                swipeRefreshLayout.setRefreshing(false);
                            })
                            .addOnFailureListener(e -> {
                                swipeRefreshLayout.setRefreshing(false);
                                showError("Failed to load evaluations: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    showError("Failed to load teams: " + e.getMessage());
                });
    }

    private void calculateAndRank(List<Team> teams, Map<String, List<Evaluation>> teamEvalMap) {
        allResults.clear();

        String scoringType = currentEvent != null ? currentEvent.getScoringType() : Event.SCORING_WEIGHTED;

        for (Team team : teams) {
            TeamResult result = new TeamResult(team);
            List<Evaluation> evals = teamEvalMap.get(team.getId());
            if (evals != null) {
                result.setEvaluations(evals);
            }

            int judgesInPanel = 1;
            if (team.getPanelId() != null && panelJudgeCountMap.containsKey(team.getPanelId())) {
                Integer count = panelJudgeCountMap.get(team.getPanelId());
                judgesInPanel = count != null && count > 0 ? count : 1;
            }

            // Aggregate scores using selected event's scoring model (WEIGHTED vs RAW SUM)
            result.calculateScores(judgesInPanel, scoringType, criteriaList);
            allResults.add(result);
        }

        // Sort descending by average score
        Collections.sort(allResults);

        // Assign standard competition ranks with explicit, consistent tie-handling
        assignRanks(allResults);

        int selectedPosition = spinnerFilterPanel.getSelectedItemPosition();
        if (selectedPosition < 0) selectedPosition = 0;
        applyFilter(selectedPosition);
    }

    /**
     * Standard competition ranking (1224) with consistent tie handling:
     * Teams with identical average scores (within 0.001 margin) share the same rank.
     */
    private void assignRanks(List<TeamResult> list) {
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                if (Math.abs(list.get(i).getAverageScore() - list.get(i - 1).getAverageScore()) < 0.001) {
                    list.get(i).setRank(list.get(i - 1).getRank());
                } else {
                    list.get(i).setRank(i + 1);
                }
            } else {
                list.get(i).setRank(1);
            }
        }
    }

    private void applyFilter(int position) {
        filteredResults.clear();
        if (position <= 0) {
            filteredResults.addAll(allResults);
        } else if (position - 1 < panelList.size()) {
            String selectedPanelId = panelList.get(position - 1).getId();
            for (TeamResult res : allResults) {
                if (res.getTeam() != null && selectedPanelId.equals(res.getTeam().getPanelId())) {
                    filteredResults.add(res);
                }
            }
            assignRanks(filteredResults);
        }

        if (filteredResults.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerViewRankings.setVisibility(View.GONE);
            if (allResults.isEmpty()) {
                tvEmptyState.setText("No evaluations have been submitted yet for this event.");
            } else {
                tvEmptyState.setText("No teams found for the selected panel.");
            }
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewRankings.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private void showError(String message) {
        if (llErrorState != null) {
            llErrorState.setVisibility(View.VISIBLE);
            tvErrorMessage.setText(message != null ? message : "Failed to load rankings.");
            recyclerViewRankings.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.GONE);
        } else {
            UIUtils.showToast(this, message);
        }
    }

    private void hideError() {
        if (llErrorState != null) {
            llErrorState.setVisibility(View.GONE);
        }
    }

    private void shareRankings() {
        if (filteredResults.isEmpty()) {
            UIUtils.showToast(this, "No ranking data to share.");
            return;
        }

        boolean isFinal = currentEvent != null && (currentEvent.isLocked() || currentEvent.isCompleted());
        boolean isAnonymous = currentEvent != null && currentEvent.isAnonymousJudging();
        String scoringModel = currentEvent != null && Event.SCORING_RAW.equalsIgnoreCase(currentEvent.getScoringType())
                ? "Raw Score Total" : "Weighted Formula (%)";

        StringBuilder sb = new StringBuilder();
        sb.append("🏆 JudgeX Standings & Leaderboard\n");
        if (currentEvent != null) {
            sb.append("Event: ").append(currentEvent.getName()).append("\n");
            sb.append("Status: ").append(isFinal ? "FINAL (Official)" : "PROVISIONAL").append("\n");
            sb.append("Scoring Model: ").append(scoringModel).append("\n");
        }
        sb.append("====================================\n\n");

        for (TeamResult r : filteredResults) {
            String teamLabel = isAnonymous
                    ? "Team #" + (r.getTeam() != null ? r.getTeam().getTeamNumber() : "TM")
                    : (r.getTeam() != null ? r.getTeam().getTeamNumber() + " - " + r.getTeam().getTeamName() : "Team");

            sb.append("#").append(r.getRank()).append(" | ").append(teamLabel);
            if (r.getTeam() != null && r.getTeam().getProjectTitle() != null && !r.getTeam().getProjectTitle().isEmpty()) {
                sb.append("\nProject: ").append(r.getTeam().getProjectTitle());
            }
            sb.append(String.format(Locale.US, "\nAverage Score: %.1f / %.0f pts", r.getAverageScore(), r.getMaxPossibleScore()))
                    .append(" (").append(r.getEvaluationsCount()).append(" evaluations)\n\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, (currentEvent != null ? currentEvent.getName() : "JudgeX") + " Standings");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Standings"));
    }

    @Override
    public void onTeamResultClick(TeamResult result) {
        if (result.getEvaluations().isEmpty()) {
            UIUtils.showToast(this, "No submitted evaluations for this team yet.");
            return;
        }

        boolean isAnonymous = currentEvent != null && currentEvent.isAnonymousJudging();
        String teamHeader = isAnonymous
                ? "Team #" + result.getTeam().getTeamNumber()
                : result.getTeam().getTeamNumber() + " - " + result.getTeam().getTeamName();

        StringBuilder sb = new StringBuilder();
        if (result.getTeam().getProjectTitle() != null) {
            sb.append("Project: ").append(result.getTeam().getProjectTitle()).append("\n");
        }
        sb.append(String.format(Locale.US, "Overall Average Score: %.1f / %.0f pts\n\n", result.getAverageScore(), result.getMaxPossibleScore()));
        sb.append("Submitted Evaluations (").append(result.getEvaluationsCount()).append("):\n");
        sb.append("------------------------------------\n");

        for (int i = 0; i < result.getEvaluations().size(); i++) {
            Evaluation eval = result.getEvaluations().get(i);
            String judgeLabel = eval.getJudgeName() != null ? eval.getJudgeName() : "Judge " + (i + 1);
            sb.append(i + 1).append(". ").append(judgeLabel)
                    .append(String.format(Locale.US, " → Total: %.1f pts\n", eval.getTotalScore()));

            if (eval.getScores() != null && !eval.getScores().isEmpty()) {
                for (Criterion c : criteriaList) {
                    if (eval.getScores().containsKey(c.getId())) {
                        Double score = eval.getScores().get(c.getId());
                        sb.append(String.format(Locale.US, "   • %s: %.1f/%.0f (Weight: %.0f%%)\n",
                                c.getName(), score != null ? score : 0.0, c.getMaxMarks(), c.getWeight()));
                    }
                }
            }

            if (eval.getRemarks() != null && !eval.getRemarks().isEmpty()) {
                sb.append("   Remarks: \"").append(eval.getRemarks()).append("\"\n");
            }
            sb.append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(teamHeader)
                .setMessage(sb.toString())
                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
