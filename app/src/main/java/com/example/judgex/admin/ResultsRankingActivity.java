package com.example.judgex.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
import java.util.List;
import java.util.Map;

public class ResultsRankingActivity extends AppCompatActivity implements RankingAdapter.OnRankingClickListener {

    private MaterialToolbar toolbar;
    private Spinner spinnerFilterPanel;
    private MaterialButton btnShareResults;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvEmptyState;
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
        loadPanelsAndRankings();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        spinnerFilterPanel = findViewById(R.id.spinnerFilterPanel);
        btnShareResults = findViewById(R.id.btnShareResults);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        recyclerViewRankings = findViewById(R.id.recyclerViewRankings);

        boolean isFinal = currentEvent != null && (currentEvent.isLocked() || currentEvent.isCompleted());
        if (currentEvent != null) {
            toolbar.setSubtitle(currentEvent.getName() + (isFinal ? " (Official Results)" : " (Provisional)"));
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewRankings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RankingAdapter(filteredResults, isFinal, this);
        recyclerViewRankings.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadPanelsAndRankings);
        btnShareResults.setOnClickListener(v -> shareRankings());

        spinnerFilterPanel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilter(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadPanelsAndRankings() {
        if (eventId == null) {
            UIUtils.showToast(this, "No active event specified.");
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        // Load Criteria for detailed breakdown dialogs
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
                });

        // Load Panels
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

                    ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, filterOptions);
                    spinnerFilterPanel.setAdapter(spinnerAdapter);

                    loadTeamsAndEvaluations();
                })
                .addOnFailureListener(e -> loadTeamsAndEvaluations());
    }

    private void loadTeamsAndEvaluations() {
        firebaseManager.getTeamsForEvent(eventId)
                .addOnSuccessListener(teamSnapshots -> {
                    List<Team> teams = new ArrayList<>();
                    for (DocumentSnapshot doc : teamSnapshots.getDocuments()) {
                        Team team = doc.toObject(Team.class);
                        if (team != null) {
                            team.setId(doc.getId());
                            teams.add(team);
                        }
                    }

                    firebaseManager.getEvaluationsForEvent(eventId)
                            .addOnSuccessListener(evalSnapshots -> {
                                Map<String, List<Evaluation>> teamEvalMap = new HashMap<>();
                                for (DocumentSnapshot doc : evalSnapshots.getDocuments()) {
                                    Evaluation eval = doc.toObject(Evaluation.class);
                                    if (eval != null && eval.getTeamId() != null && eval.isSubmitted()) {
                                        if (!teamEvalMap.containsKey(eval.getTeamId())) {
                                            teamEvalMap.put(eval.getTeamId(), new ArrayList<>());
                                        }
                                        teamEvalMap.get(eval.getTeamId()).add(eval);
                                    }
                                }

                                calculateAndRank(teams, teamEvalMap);
                                swipeRefreshLayout.setRefreshing(false);
                            })
                            .addOnFailureListener(e -> {
                                swipeRefreshLayout.setRefreshing(false);
                                UIUtils.showToast(this, "Failed to load evaluations: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load teams: " + e.getMessage());
                });
    }

    private void calculateAndRank(List<Team> teams, Map<String, List<Evaluation>> teamEvalMap) {
        allResults.clear();

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

            // Calculate score using configured rubric
            result.calculateScores(judgesInPanel);
            allResults.add(result);
        }

        // Sort descending by average score
        Collections.sort(allResults);

        // Assign standard competition ranks with consistent tie handling
        assignRanks(allResults);

        applyFilter(spinnerFilterPanel.getSelectedItemPosition());
    }

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
        } else {
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
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewRankings.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private void shareRankings() {
        if (filteredResults.isEmpty()) {
            UIUtils.showToast(this, "No ranking data to share.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🏆 JudgeX Standings & Leaderboard\n");
        if (currentEvent != null) {
            sb.append("Event: ").append(currentEvent.getName()).append("\n");
            sb.append("Status: ").append(currentEvent.isLocked() ? "FINAL (Official)" : "PROVISIONAL").append("\n");
        }
        sb.append("==============================\n\n");

        for (TeamResult r : filteredResults) {
            sb.append("#").append(r.getRank()).append(" | ")
                    .append(r.getTeam().getTeamNumber()).append(" - ").append(r.getTeam().getTeamName())
                    .append("\nProject: ").append(r.getTeam().getProjectTitle())
                    .append("\nAverage Score: ").append(String.format("%.1f / 100", r.getAverageScore()))
                    .append(" (").append(r.getEvaluationsCount()).append(" evaluations)\n\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "JudgeX Leaderboard");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Standings"));
    }

    @Override
    public void onTeamResultClick(TeamResult result) {
        if (result.getEvaluations().isEmpty()) {
            UIUtils.showToast(this, "No submitted evaluations for this team yet.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(result.getTeam().getProjectTitle()).append("\n");
        sb.append("Overall Average Score: ").append(String.format("%.1f / 100", result.getAverageScore())).append("\n\n");
        sb.append("Evaluations Breakdown (").append(result.getEvaluationsCount()).append("):\n");
        sb.append("------------------------------------\n");

        for (int i = 0; i < result.getEvaluations().size(); i++) {
            Evaluation eval = result.getEvaluations().get(i);
            sb.append(i + 1).append(". ").append(eval.getJudgeName() != null ? eval.getJudgeName() : "Judge")
                    .append(" → Total: ").append(String.format("%.1f", eval.getTotalScore())).append(" pts\n");

            if (eval.getScores() != null && !eval.getScores().isEmpty()) {
                for (Criterion c : criteriaList) {
                    if (eval.getScores().containsKey(c.getId())) {
                        Double score = eval.getScores().get(c.getId());
                        sb.append("   • ").append(c.getName()).append(": ").append(String.format("%.1f", score))
                                .append("/").append(String.format("%.0f", c.getMaxMarks()))
                                .append(" (Weight: ").append(String.format("%.0f%%", c.getWeight())).append(")\n");
                    }
                }
            }

            if (eval.getRemarks() != null && !eval.getRemarks().isEmpty()) {
                sb.append("   Remarks: \"").append(eval.getRemarks()).append("\"\n");
            }
            sb.append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(result.getTeam().getTeamNumber() + " - " + result.getTeam().getTeamName())
                .setMessage(sb.toString())
                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
