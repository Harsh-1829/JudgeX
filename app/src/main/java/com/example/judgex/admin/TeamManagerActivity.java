package com.example.judgex.admin;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.TeamAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.models.Team;
import com.example.judgex.qr.QRViewActivity;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TeamManagerActivity extends AppCompatActivity implements TeamAdapter.OnTeamClickListener {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewTeams;
    private TextView tvEmptyState;
    private ExtendedFloatingActionButton fabAddTeam;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Team> teamList = new ArrayList<>();
    private final List<Panel> panelList = new ArrayList<>();
    private final Set<String> evaluatedTeamIds = new HashSet<>();
    private TeamAdapter adapter;
    private Event currentEvent;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_manager);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
        if (currentEvent != null && eventId == null) {
            eventId = currentEvent.getId();
        }

        initViews();
        loadPanelsAndTeams();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewTeams = findViewById(R.id.recyclerViewTeams);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        fabAddTeam = findViewById(R.id.fabAddTeam);

        if (currentEvent != null) {
            toolbar.setSubtitle(currentEvent.getName());
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewTeams.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TeamAdapter(teamList, evaluatedTeamIds, this);
        recyclerViewTeams.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadPanelsAndTeams);
        fabAddTeam.setOnClickListener(v -> showAddEditTeamDialog(null));
    }

    private void loadPanelsAndTeams() {
        if (eventId == null) {
            UIUtils.showToast(this, "No active event specified.");
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        firebaseManager.getPanelsForEvent(eventId)
                .addOnSuccessListener(querySnapshot -> {
                    panelList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Panel p = doc.toObject(Panel.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            panelList.add(p);
                        }
                    }
                    loadEvaluationsAndTeams();
                })
                .addOnFailureListener(e -> loadEvaluationsAndTeams());
    }

    private void loadEvaluationsAndTeams() {
        firebaseManager.getEvaluationsForEvent(eventId)
                .addOnSuccessListener(evalSnapshots -> {
                    evaluatedTeamIds.clear();
                    for (DocumentSnapshot doc : evalSnapshots.getDocuments()) {
                        String tId = doc.getString("teamId");
                        if (tId != null) {
                            evaluatedTeamIds.add(tId);
                        }
                    }
                    loadTeams();
                })
                .addOnFailureListener(e -> loadTeams());
    }

    private void loadTeams() {
        firebaseManager.getTeamsForEvent(eventId)
                .addOnSuccessListener(querySnapshot -> {
                    teamList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Team team = doc.toObject(Team.class);
                        if (team != null) {
                            team.setId(doc.getId());
                            teamList.add(team);
                        }
                    }

                    if (teamList.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerViewTeams.setVisibility(View.GONE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                        recyclerViewTeams.setVisibility(View.VISIBLE);
                    }

                    adapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load teams: " + e.getMessage());
                });
    }

    private void showAddEditTeamDialog(Team existingTeam) {
        if (panelList.isEmpty()) {
            UIUtils.showToast(this, "Please create at least one Panel before adding teams!");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_team, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        TextInputLayout tilTeamNumber = view.findViewById(R.id.tilTeamNumber);
        TextInputLayout tilTeamName = view.findViewById(R.id.tilTeamName);
        TextInputLayout tilProjectTitle = view.findViewById(R.id.tilProjectTitle);
        TextInputLayout tilCategory = view.findViewById(R.id.tilCategory);
        TextInputLayout tilLeaderName = view.findViewById(R.id.tilLeaderName);
        TextInputLayout tilLeaderEmail = view.findViewById(R.id.tilLeaderEmail);
        TextInputLayout tilMemberCount = view.findViewById(R.id.tilMemberCount);

        TextInputEditText etTeamNumber = view.findViewById(R.id.etTeamNumber);
        TextInputEditText etTeamName = view.findViewById(R.id.etTeamName);
        TextInputEditText etProjectTitle = view.findViewById(R.id.etProjectTitle);
        TextInputEditText etCategory = view.findViewById(R.id.etCategory);
        TextInputEditText etLeaderName = view.findViewById(R.id.etLeaderName);
        TextInputEditText etLeaderEmail = view.findViewById(R.id.etLeaderEmail);
        TextInputEditText etMemberCount = view.findViewById(R.id.etMemberCount);
        Spinner spinnerPanels = view.findViewById(R.id.spinnerPanels);

        List<String> panelNames = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < panelList.size(); i++) {
            Panel p = panelList.get(i);
            panelNames.add(p.getName());
            if (existingTeam != null && p.getId().equals(existingTeam.getPanelId())) {
                selectedIndex = i;
            }
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, panelNames);
        spinnerPanels.setAdapter(spinnerAdapter);
        spinnerPanels.setSelection(selectedIndex);

        if (existingTeam != null) {
            tvDialogTitle.setText("Edit Team Details");
            etTeamNumber.setText(existingTeam.getTeamNumber());
            etTeamName.setText(existingTeam.getTeamName());
            etProjectTitle.setText(existingTeam.getProjectTitle());
            etCategory.setText(existingTeam.getCategory());
            etLeaderName.setText(existingTeam.getLeaderName());
            etLeaderEmail.setText(existingTeam.getLeaderEmail());
            etMemberCount.setText(String.valueOf(existingTeam.getMemberCount()));
        }

        builder.setView(view);
        builder.setPositiveButton(existingTeam != null ? "Update" : "Register Team", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String number = etTeamNumber.getText() != null ? etTeamNumber.getText().toString().trim() : "";
                String name = etTeamName.getText() != null ? etTeamName.getText().toString().trim() : "";
                String title = etProjectTitle.getText() != null ? etProjectTitle.getText().toString().trim() : "";
                String category = etCategory.getText() != null ? etCategory.getText().toString().trim() : "";
                String leader = etLeaderName.getText() != null ? etLeaderName.getText().toString().trim() : "";
                String email = etLeaderEmail.getText() != null ? etLeaderEmail.getText().toString().trim() : "";
                String countStr = etMemberCount.getText() != null ? etMemberCount.getText().toString().trim() : "4";

                if (TextUtils.isEmpty(number)) {
                    tilTeamNumber.setError("Team number is required");
                    return;
                }
                if (TextUtils.isEmpty(name)) {
                    tilTeamName.setError("Team name is required");
                    return;
                }
                if (TextUtils.isEmpty(title)) {
                    tilProjectTitle.setError("Project title is required");
                    return;
                }

                int memberCount = 4;
                try {
                    memberCount = Integer.parseInt(countStr);
                } catch (Exception ignored) {}

                String panelId = panelList.get(spinnerPanels.getSelectedItemPosition()).getId();

                dialog.dismiss();
                saveTeam(existingTeam, number, name, title, category, leader, email, memberCount, panelId);
            });
        });
        dialog.show();
    }

    private void saveTeam(Team existingTeam, String number, String name, String title, 
                          String category, String leader, String email, int memberCount, String panelId) {
        Dialog loadingDialog = UIUtils.showLoadingDialog(this, existingTeam != null ? "Updating team..." : "Registering team...");

        if (existingTeam != null) {
            existingTeam.setTeamNumber(number);
            existingTeam.setTeamName(name);
            existingTeam.setProjectTitle(title);
            existingTeam.setCategory(category);
            existingTeam.setLeaderName(leader);
            existingTeam.setLeaderEmail(email);
            existingTeam.setMemberCount(memberCount);
            existingTeam.setPanelId(panelId);
            existingTeam.setQrCodeData(generateQRDataPayload(existingTeam.getId(), number, name, eventId, panelId));

            firebaseManager.updateTeam(existingTeam)
                    .addOnSuccessListener(aVoid -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Team updated successfully");
                        loadPanelsAndTeams();
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Failed to update team: " + e.getMessage());
                    });
        } else {
            Team newTeam = new Team(null, eventId, panelId, number, name, title, category, leader, email, memberCount, "", System.currentTimeMillis());
            firebaseManager.createTeam(newTeam)
                    .addOnSuccessListener(docRef -> {
                        newTeam.setId(docRef.getId());
                        newTeam.setQrCodeData(generateQRDataPayload(docRef.getId(), number, name, eventId, panelId));
                        firebaseManager.updateTeam(newTeam);

                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Team registered: " + name);
                        loadPanelsAndTeams();
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Failed to create team: " + e.getMessage());
                    });
        }
    }

    private String generateQRDataPayload(String teamId, String teamNumber, String teamName, String eventId, String panelId) {
        try {
            JSONObject json = new JSONObject();
            json.put("teamId", teamId);
            json.put("teamNumber", teamNumber);
            json.put("teamName", teamName);
            json.put("eventId", eventId);
            json.put("panelId", panelId);
            return json.toString();
        } catch (Exception e) {
            return teamId;
        }
    }

    @Override
    public void onTeamClick(Team team) {
        showAddEditTeamDialog(team);
    }

    @Override
    public void onQRClick(Team team) {
        Intent intent = new Intent(this, QRViewActivity.class);
        intent.putExtra(Constants.EXTRA_TEAM, team);
        startActivity(intent);
    }
}
