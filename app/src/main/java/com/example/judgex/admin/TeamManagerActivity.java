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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        toolbar.inflateMenu(R.menu.menu_team_manager);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_random_assign_teams) {
                handleRandomTeamAssignment();
                return true;
            }
            return false;
        });

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
                        if (team != null && !team.isDeleted()) {
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
        TextInputLayout tilLeaderPassword = view.findViewById(R.id.tilLeaderPassword);
        TextInputLayout tilMemberCount = view.findViewById(R.id.tilMemberCount);

        TextInputEditText etTeamNumber = view.findViewById(R.id.etTeamNumber);
        TextInputEditText etTeamName = view.findViewById(R.id.etTeamName);
        TextInputEditText etProjectTitle = view.findViewById(R.id.etProjectTitle);
        TextInputEditText etCategory = view.findViewById(R.id.etCategory);
        TextInputEditText etLeaderName = view.findViewById(R.id.etLeaderName);
        TextInputEditText etLeaderEmail = view.findViewById(R.id.etLeaderEmail);
        TextInputEditText etLeaderPassword = view.findViewById(R.id.etLeaderPassword);
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
                String password = etLeaderPassword.getText() != null ? etLeaderPassword.getText().toString().trim() : "";
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
                if (!password.isEmpty() && password.length() < 6) {
                    tilLeaderPassword.setError("Password must be at least 6 characters");
                    return;
                }

                int memberCount = 4;
                try {
                    memberCount = Integer.parseInt(countStr);
                } catch (Exception ignored) {}

                String panelId = panelList.get(spinnerPanels.getSelectedItemPosition()).getId();

                dialog.dismiss();
                saveTeam(existingTeam, number, name, title, category, leader, email, password, memberCount, panelId);
            });
        });
        dialog.show();
    }

    private void saveTeam(Team existingTeam, String number, String name, String title, 
                          String category, String leader, String email, String password, int memberCount, String panelId) {
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
                        if (!password.isEmpty() && !email.isEmpty()) {
                            firebaseManager.createTeamLeaderAccount(this, email, password, leader, existingTeam.getId())
                                    .addOnSuccessListener(uid -> {
                                        loadingDialog.dismiss();
                                        UIUtils.showToast(this, "Team updated and leader login provisioned!");
                                        loadPanelsAndTeams();
                                    })
                                    .addOnFailureListener(e -> {
                                        loadingDialog.dismiss();
                                        new AlertDialog.Builder(this)
                                                .setTitle("Team Leader Account Notice")
                                                .setMessage("Team updated, but leader login could not be provisioned:\n\n" + e.getMessage())
                                                .setPositiveButton("OK", (d, w) -> d.dismiss())
                                                .show();
                                        loadPanelsAndTeams();
                                    });
                        } else {
                            loadingDialog.dismiss();
                            UIUtils.showToast(this, "Team updated successfully");
                            loadPanelsAndTeams();
                        }
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

                        if (!password.isEmpty() && !email.isEmpty()) {
                            firebaseManager.createTeamLeaderAccount(this, email, password, leader, docRef.getId())
                                    .addOnSuccessListener(uid -> {
                                        loadingDialog.dismiss();
                                        UIUtils.showToast(this, "Team registered and leader login provisioned: " + name);
                                        loadPanelsAndTeams();
                                    })
                                    .addOnFailureListener(e -> {
                                        loadingDialog.dismiss();
                                        new AlertDialog.Builder(this)
                                                .setTitle("Team Leader Account Notice")
                                                .setMessage("Team registered, but leader login could not be provisioned:\n\n" + e.getMessage())
                                                .setPositiveButton("OK", (d, w) -> d.dismiss())
                                                .show();
                                        loadPanelsAndTeams();
                                    });
                        } else {
                            loadingDialog.dismiss();
                            UIUtils.showToast(this, "Team registered: " + name);
                            loadPanelsAndTeams();
                        }
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Failed to create team: " + e.getMessage());
                    });
        }
    }

    private void handleRandomTeamAssignment() {
        if (panelList.isEmpty()) {
            UIUtils.showToast(this, "Please create panels for this event first!");
            return;
        }

        if (teamList.isEmpty()) {
            UIUtils.showToast(this, "No teams available to assign!");
            return;
        }

        // Random distribution algorithm across panels
        List<Team> shuffledTeams = new ArrayList<>(teamList);
        java.util.Collections.shuffle(shuffledTeams);

        Map<String, String> teamToPanelMap = new HashMap<>();
        Map<String, List<String>> panelToTeamNumbers = new HashMap<>();
        for (Panel p : panelList) {
            panelToTeamNumbers.put(p.getId(), new ArrayList<>());
        }

        for (int i = 0; i < shuffledTeams.size(); i++) {
            Team team = shuffledTeams.get(i);
            int panelIndex = i % panelList.size();
            Panel assignedPanel = panelList.get(panelIndex);

            teamToPanelMap.put(team.getId(), assignedPanel.getId());
            panelToTeamNumbers.get(assignedPanel.getId()).add(team.getTeamNumber());
        }

        // Build preview summary
        StringBuilder summary = new StringBuilder();
        summary.append("Random distribution preview of ").append(teamList.size())
                .append(" teams across ").append(panelList.size()).append(" panels:\n\n");

        for (Panel p : panelList) {
            List<String> nums = panelToTeamNumbers.get(p.getId());
            int count = nums != null ? nums.size() : 0;
            summary.append("• ").append(p.getName()).append(": ").append(count).append(" teams\n");
        }
        summary.append("\nProceed with updating panel assignments for all teams?");

        new AlertDialog.Builder(this)
                .setTitle("Confirm Random Team Distribution")
                .setMessage(summary.toString())
                .setPositiveButton("Distribute Teams", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Distributing teams across panels...");
                    firebaseManager.batchAssignTeams(teamToPanelMap, panelList)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Teams successfully distributed across panels!");
                                loadPanelsAndTeams();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to distribute teams: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String generateQRDataPayload(String teamId, String teamNumber, String teamName, String eventId, String panelId) {
        try {
            JSONObject json = new JSONObject();
            json.put("v", 2);
            json.put("teamId", teamId);
            json.put("teamNumber", teamNumber);
            json.put("teamName", teamName);
            json.put("eventId", eventId);
            json.put("panelId", panelId);
            json.put("issuedAt", System.currentTimeMillis());
            json.put("token", java.util.UUID.randomUUID().toString());
            return json.toString();
        } catch (Exception e) {
            return teamId;
        }
    }

    @Override
    public void onTeamClick(Team team) {
        String unregisterLabel = team.isUnregistered() 
                ? "Re-register Team in Event" 
                : "Remove from Event / Unregister Team";

        String[] options = {
                "View Team & Leader Details",
                "Send Password Reset to Leader",
                "Edit Team Details",
                "Reassign Panel",
                "View / Share QR Pass",
                unregisterLabel,
                "Delete Team Record"
        };

        new AlertDialog.Builder(this)
                .setTitle(team.getTeamNumber() + " - " + team.getTeamName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showTeamDetailsDialog(team);
                            break;
                        case 1:
                            sendPasswordResetForLeader(team);
                            break;
                        case 2:
                            showAddEditTeamDialog(team);
                            break;
                        case 3:
                            showReassignTeamPanelDialog(team);
                            break;
                        case 4:
                            onQRClick(team);
                            break;
                        case 5:
                            handleRemoveTeamFromEvent(team);
                            break;
                        case 6:
                            handleDeleteTeamRecord(team);
                            break;
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showTeamDetailsDialog(Team team) {
        String panelName = "Unassigned";
        if (team.getPanelId() != null) {
            for (Panel p : panelList) {
                if (p.getId().equals(team.getPanelId())) {
                    panelName = p.getName();
                    break;
                }
            }
        }

        StringBuilder details = new StringBuilder();
        details.append("🏷️ Team Number: ").append(team.getTeamNumber()).append("\n\n");
        details.append("🚀 Team Name: ").append(team.getTeamName()).append("\n\n");
        details.append("💡 Project: ").append(team.getProjectTitle() != null ? team.getProjectTitle() : "N/A").append("\n\n");
        details.append("📂 Category: ").append(team.getCategory() != null ? team.getCategory() : "General").append("\n\n");
        details.append("👤 Team Leader: ").append(team.getLeaderName() != null ? team.getLeaderName() : "N/A").append("\n\n");
        details.append("📧 Leader Email: ").append(team.getLeaderEmail() != null ? team.getLeaderEmail() : "N/A").append("\n\n");
        details.append("⚖️ Panel Assignment: ").append(panelName).append("\n\n");
        details.append("👥 Team Members: ").append(team.getMemberCount()).append("\n\n");
        details.append("🟢 Participation Status: ").append(team.isUnregistered() ? "Unregistered / Removed" : "Active").append("\n\n");
        details.append("🎫 Event QR Pass: ").append(team.getQrCodeData() != null && !team.getQrCodeData().isEmpty() ? "Generated (v2 Secured)" : "Not Generated");

        new AlertDialog.Builder(this)
                .setTitle("Team & Leader Details")
                .setMessage(details.toString())
                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                .setNeutralButton("View QR Pass", (dialog, which) -> {
                    dialog.dismiss();
                    onQRClick(team);
                })
                .show();
    }

    private void sendPasswordResetForLeader(Team team) {
        String email = team.getLeaderEmail();
        if (TextUtils.isEmpty(email)) {
            UIUtils.showToast(this, "No team leader email registered for this team.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Send Password Reset Email")
                .setMessage("Send an official Firebase Authentication password-reset email to the Team Leader at:\n\n" + email +
                        "\n\nThe Team Leader will receive a secure email link allowing them to choose a new password. Plaintext passwords are never stored.")
                .setPositiveButton("Send Reset Email", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Sending reset email...");
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim())
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Password reset email sent to " + email);
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                new AlertDialog.Builder(this)
                                        .setTitle("Password Reset Failed")
                                        .setMessage("Could not send password reset email:\n\n" + e.getMessage())
                                        .setPositiveButton("OK", (d, w) -> d.dismiss())
                                        .show();
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void handleRemoveTeamFromEvent(Team team) {
        boolean isUnregistered = team.isUnregistered();
        if (!isUnregistered) {
            new AlertDialog.Builder(this)
                    .setTitle("Unregister Team: " + team.getTeamNumber())
                    .setMessage("Unregistering '" + team.getTeamName() + "' will remove this team from active judging.\n\n" +
                            "• Judges will NOT be able to scan or evaluate this team in this event.\n" +
                            "• Any historical evaluations already submitted will remain intact.\n\n" +
                            "Proceed to unregister team?")
                    .setPositiveButton("Unregister Team", (dialog, which) -> {
                        Dialog loading = UIUtils.showLoadingDialog(this, "Unregistering team...");
                        team.setStatus(Team.STATUS_REMOVED);
                        firebaseManager.updateTeam(team)
                                .addOnSuccessListener(aVoid -> {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Team unregistered from event.");
                                    loadPanelsAndTeams();
                                })
                                .addOnFailureListener(e -> {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Failed to unregister team: " + e.getMessage());
                                });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Re-register Team: " + team.getTeamNumber())
                    .setMessage("Re-registering '" + team.getTeamName() + "' will allow judges to scan and evaluate this team again.\n\nProceed to re-register?")
                    .setPositiveButton("Re-register", (dialog, which) -> {
                        Dialog loading = UIUtils.showLoadingDialog(this, "Re-registering team...");
                        team.setStatus(Team.STATUS_ACTIVE);
                        firebaseManager.updateTeam(team)
                                .addOnSuccessListener(aVoid -> {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Team re-registered as active.");
                                    loadPanelsAndTeams();
                                })
                                .addOnFailureListener(e -> {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Failed to re-register team: " + e.getMessage());
                                });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        }
    }

    private void handleDeleteTeamRecord(Team team) {
        Dialog checkingDialog = UIUtils.showLoadingDialog(this, "Checking team evaluations...");

        firebaseManager.getEvaluationsCollection().whereEqualTo("teamId", team.getId()).get()
                .addOnSuccessListener(evalSnapshots -> {
                    checkingDialog.dismiss();
                    int evalCount = evalSnapshots != null ? evalSnapshots.size() : 0;

                    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                            .setTitle("Delete Team: " + team.getTeamNumber() + " (" + team.getTeamName() + ")");

                    if (evalCount > 0) {
                        builder.setMessage("⚠️ Warning: Team '" + team.getTeamName() + "' has " + evalCount + 
                                " existing evaluation record(s).\n\n" +
                                "To maintain competition history, ranking integrity, and audit referential integrity without destructive data loss, this team will be marked as DELETED and hidden from active participants.\n\n" +
                                "Proceed with soft-deletion?")
                                .setPositiveButton("Soft-Delete Record", (dialog, which) -> {
                                    Dialog loading = UIUtils.showLoadingDialog(this, "Soft-deleting team...");
                                    team.setDeleted(true);
                                    team.setStatus(Team.STATUS_DELETED);
                                    firebaseManager.updateTeam(team)
                                            .addOnSuccessListener(aVoid -> {
                                                loading.dismiss();
                                                UIUtils.showToast(this, "Team soft-deleted.");
                                                loadPanelsAndTeams();
                                            })
                                            .addOnFailureListener(e -> {
                                                loading.dismiss();
                                                UIUtils.showToast(this, "Failed to delete team: " + e.getMessage());
                                            });
                                });
                    } else {
                        builder.setMessage("This team has no submitted evaluations.\n\n" +
                                "It will be permanently removed from this competition event.\n\n" +
                                "Are you sure you want to permanently delete '" + team.getTeamName() + "'?")
                                .setPositiveButton("Permanently Delete", (dialog, which) -> {
                                    Dialog loading = UIUtils.showLoadingDialog(this, "Deleting team permanently...");
                                    firebaseManager.deleteTeam(team.getId())
                                            .addOnSuccessListener(aVoid -> {
                                                loading.dismiss();
                                                UIUtils.showToast(this, "Team permanently deleted.");
                                                loadPanelsAndTeams();
                                            })
                                            .addOnFailureListener(e -> {
                                                loading.dismiss();
                                                UIUtils.showToast(this, "Failed to delete team: " + e.getMessage());
                                            });
                                });
                    }

                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                            .show();
                })
                .addOnFailureListener(e -> {
                    checkingDialog.dismiss();
                    UIUtils.showToast(this, "Failed to check team evaluations: " + e.getMessage());
                });
    }

    private void showReassignTeamPanelDialog(Team team) {
        List<String> panelNames = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < panelList.size(); i++) {
            Panel p = panelList.get(i);
            panelNames.add(p.getName());
            if (p.getId().equals(team.getPanelId())) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Reassign Panel for " + team.getTeamNumber())
                .setSingleChoiceItems(panelNames.toArray(new String[0]), selectedIndex, (dialog, which) -> {
                    String newPanelId = panelList.get(which).getId();
                    dialog.dismiss();

                    Dialog loading = UIUtils.showLoadingDialog(this, "Updating panel assignment...");
                    team.setPanelId(newPanelId);
                    team.setQrCodeData(generateQRDataPayload(team.getId(), team.getTeamNumber(), team.getTeamName(), eventId, newPanelId));

                    firebaseManager.updateTeam(team)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Team reassigned to " + panelList.get(which).getName());
                                loadPanelsAndTeams();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to reassign team: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public void onQRClick(Team team) {
        Intent intent = new Intent(this, QRViewActivity.class);
        intent.putExtra(Constants.EXTRA_TEAM, team);
        startActivity(intent);
    }
}
