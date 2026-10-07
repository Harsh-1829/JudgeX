package com.example.judgex.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.ProgressAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Evaluation;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamResult;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluationProgressActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvOverallPercentage, tvOverallStats, tvEmptyState;
    private LinearProgressIndicator overallProgressBar;
    private RecyclerView recyclerViewProgress;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<TeamResult> progressList = new ArrayList<>();
    private final Map<String, String> panelNameMap = new HashMap<>(); // panelId -> name
    private final Map<String, Integer> panelJudgeCountMap = new HashMap<>(); // panelId -> judgeCount
    private ProgressAdapter adapter;

    private Event currentEvent;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evaluation_progress);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
        if (currentEvent != null && eventId == null) {
            eventId = currentEvent.getId();
        }

        initViews();
        loadProgressData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvOverallPercentage = findViewById(R.id.tvOverallPercentage);
        tvOverallStats = findViewById(R.id.tvOverallStats);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        overallProgressBar = findViewById(R.id.overallProgressBar);
        recyclerViewProgress = findViewById(R.id.recyclerViewProgress);

        if (currentEvent != null) {
            toolbar.setSubtitle(currentEvent.getName());
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewProgress.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProgressAdapter(progressList, panelNameMap);
        recyclerViewProgress.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadProgressData);
    }

    private void loadProgressData() {
        if (eventId == null) {
            UIUtils.showToast(this, "No event specified");
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        // 1. Fetch Panels
        firebaseManager.getPanelsForEvent(eventId)
                .addOnSuccessListener(panelSnapshots -> {
                    panelNameMap.clear();
                    panelJudgeCountMap.clear();

                    for (DocumentSnapshot doc : panelSnapshots.getDocuments()) {
                        Panel p = doc.toObject(Panel.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            panelNameMap.put(p.getId(), p.getName());
                            int count = p.getJudgeIds() != null ? p.getJudgeIds().size() : 0;
                            panelJudgeCountMap.put(p.getId(), count);
                        }
                    }

                    // 2. Fetch Teams
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

                                // 3. Fetch Evaluations
                                firebaseManager.getEvaluationsForEvent(eventId)
                                        .addOnSuccessListener(evalSnapshots -> {
                                            Map<String, List<Evaluation>> teamEvalMap = new HashMap<>();
                                            for (DocumentSnapshot doc : evalSnapshots.getDocuments()) {
                                                Evaluation eval = doc.toObject(Evaluation.class);
                                                if (eval != null && eval.getTeamId() != null) {
                                                    if (!teamEvalMap.containsKey(eval.getTeamId())) {
                                                        teamEvalMap.put(eval.getTeamId(), new ArrayList<>());
                                                    }
                                                    teamEvalMap.get(eval.getTeamId()).add(eval);
                                                }
                                            }

                                            computeProgress(teams, teamEvalMap);
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
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load panels: " + e.getMessage());
                });
    }

    private void computeProgress(List<Team> teams, Map<String, List<Evaluation>> teamEvalMap) {
        progressList.clear();

        int totalExpectedEvaluations = 0;
        int totalCompletedEvaluations = 0;

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

            result.calculateScores(judgesInPanel);
            progressList.add(result);

            totalExpectedEvaluations += judgesInPanel;
            totalCompletedEvaluations += result.getEvaluationsCount();
        }

        int overallPercent = totalExpectedEvaluations > 0 
                ? (int) Math.round(((double) totalCompletedEvaluations / totalExpectedEvaluations) * 100) 
                : (totalCompletedEvaluations > 0 ? 100 : 0);
        if (overallPercent > 100) overallPercent = 100;

        tvOverallPercentage.setText(overallPercent + "%");
        overallProgressBar.setProgress(overallPercent);
        tvOverallStats.setText(totalCompletedEvaluations + " of " + totalExpectedEvaluations + " total evaluations submitted");

        if (progressList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerViewProgress.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewProgress.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }
}
