package com.example.judgex.admin;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.judgex.utils.CryptoUtils;

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
import com.google.android.material.button.MaterialButton;
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
import java.util.Locale;
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_team, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        TextView tvDialogSubtitle = view.findViewById(R.id.tvDialogSubtitle);
        TextInputLayout tilTeamNumber = view.findViewById(R.id.tilTeamNumber);
        TextInputLayout tilTeamName = view.findViewById(R.id.tilTeamName);
        TextInputLayout tilProjectTitle = view.findViewById(R.id.tilProjectTitle);
        TextInputLayout tilCategory = view.findViewById(R.id.tilCategory);
        TextInputLayout tilLeaderName = view.findViewById(R.id.tilLeaderName);
        View llEmailAndMembers = view.findViewById(R.id.llEmailAndMembers);
        TextInputLayout tilLeaderEmail = view.findViewById(R.id.tilLeaderEmail);
        TextInputLayout tilLeaderPassword = view.findViewById(R.id.tilLeaderPassword);
        TextInputLayout tilMemberCount = view.findViewById(R.id.tilMemberCount);
        TextView tvPanelLabel = view.findViewById(R.id.tvPanelLabel);
        Spinner spinnerPanels = view.findViewById(R.id.spinnerPanels);

        TextInputEditText etTeamNumber = view.findViewById(R.id.etTeamNumber);
        TextInputEditText etTeamName = view.findViewById(R.id.etTeamName);
        TextInputEditText etProjectTitle = view.findViewById(R.id.etProjectTitle);
        TextInputEditText etCategory = view.findViewById(R.id.etCategory);
        TextInputEditText etLeaderName = view.findViewById(R.id.etLeaderName);
        TextInputEditText etLeaderEmail = view.findViewById(R.id.etLeaderEmail);
        TextInputEditText etLeaderPassword = view.findViewById(R.id.etLeaderPassword);
        TextInputEditText etMemberCount = view.findViewById(R.id.etMemberCount);

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

        if (existingTeam == null) {
            // Simplified Admin Team Creation: Ask only for Team Name and Team Leader Name
            tvDialogTitle.setText("Register New Team");
            if (tvDialogSubtitle != null) {
                tvDialogSubtitle.setVisibility(View.VISIBLE);
            }
            tilTeamName.setVisibility(View.VISIBLE);
            tilLeaderName.setVisibility(View.VISIBLE);

            // Hide all other fields during creation
            tilTeamNumber.setVisibility(View.GONE);
            tilProjectTitle.setVisibility(View.GONE);
            tilCategory.setVisibility(View.GONE);
            if (llEmailAndMembers != null) {
                llEmailAndMembers.setVisibility(View.GONE);
            }
            tilLeaderPassword.setVisibility(View.GONE);
            if (tvPanelLabel != null) {
                tvPanelLabel.setVisibility(View.GONE);
            }
            spinnerPanels.setVisibility(View.GONE);
        } else {
            // Edit existing team details
            tvDialogTitle.setText("Edit Team Details");
            if (tvDialogSubtitle != null) {
                tvDialogSubtitle.setVisibility(View.GONE);
            }
            tilTeamNumber.setVisibility(View.VISIBLE);
            tilTeamName.setVisibility(View.VISIBLE);
            tilProjectTitle.setVisibility(View.VISIBLE);
            tilCategory.setVisibility(View.VISIBLE);
            tilLeaderName.setVisibility(View.VISIBLE);
            if (llEmailAndMembers != null) {
                llEmailAndMembers.setVisibility(View.VISIBLE);
            }
            tilLeaderEmail.setVisibility(View.GONE);
            tilLeaderPassword.setVisibility(View.GONE);
            tilMemberCount.setVisibility(View.VISIBLE);
            if (tvPanelLabel != null) {
                tvPanelLabel.setVisibility(View.VISIBLE);
            }
            spinnerPanels.setVisibility(View.VISIBLE);

            etTeamNumber.setText(existingTeam.getTeamNumber());
            etTeamName.setText(existingTeam.getTeamName());
            etProjectTitle.setText(existingTeam.getProjectTitle());
            etCategory.setText(existingTeam.getCategory());
            etLeaderName.setText(existingTeam.getLeaderName());
            etMemberCount.setText(String.valueOf(existingTeam.getMemberCount()));
        }

        builder.setView(view);
        builder.setPositiveButton(existingTeam != null ? "Update" : "Register Team", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etTeamName.getText() != null ? etTeamName.getText().toString().trim() : "";
                String leader = etLeaderName.getText() != null ? etLeaderName.getText().toString().trim() : "";
                tilTeamName.setError(null);
                tilLeaderName.setError(null);

                if (TextUtils.isEmpty(name)) {
                    tilTeamName.setError("Team name is required");
                    return;
                }
                if (TextUtils.isEmpty(leader)) {
                    tilLeaderName.setError("Leader name is required");
                    return;
                }

                if (existingTeam == null) {
                    if (isDuplicateTeam(teamList, name, leader)) {
                        tilTeamName.setError("A team with this name and team leader already exists in this event.");
                        UIUtils.showToast(TeamManagerActivity.this, "A team with this name and team leader already exists in this event.");
                        return;
                    }
                    dialog.dismiss();
                    registerNewTeamWithGeneratedCredentials(name, leader);
                } else {
                    String number = etTeamNumber.getText() != null ? etTeamNumber.getText().toString().trim() : existingTeam.getTeamNumber();
                    String title = etProjectTitle.getText() != null ? etProjectTitle.getText().toString().trim() : existingTeam.getProjectTitle();
                    String category = etCategory.getText() != null ? etCategory.getText().toString().trim() : existingTeam.getCategory();
                    String countStr = etMemberCount.getText() != null ? etMemberCount.getText().toString().trim() : "4";
                    int memberCount = 4;
                    try {
                        memberCount = Integer.parseInt(countStr);
                    } catch (Exception ignored) {}

                    String panelId = !panelList.isEmpty() && spinnerPanels.getSelectedItemPosition() >= 0
                            ? panelList.get(spinnerPanels.getSelectedItemPosition()).getId()
                            : existingTeam.getPanelId();

                    dialog.dismiss();
                    updateExistingTeamDetails(existingTeam, number, name, title, category, leader, memberCount, panelId);
                }
            });
        });
        dialog.show();
    }

    private void registerNewTeamWithGeneratedCredentials(String teamName, String leaderName) {
        Dialog loadingDialog = UIUtils.showLoadingDialog(this, "Generating credentials and registering team...");

        // Generate a unique username, e.g. codewarriors4821
        firebaseManager.generateUniqueTeamUsername(teamName)
                .addOnSuccessListener(username -> {
                    // Generate secure random temporary password (never stored plaintext in Firestore)
                    String tempPassword = FirebaseManager.generateSecureTemporaryPassword();
                    String encryptedPassword = CryptoUtils.encrypt(tempPassword);
                    String panelId = !panelList.isEmpty() ? panelList.get(0).getId() : "";
                    String teamNumber = generateUniqueTeamNumber(teamList);
                    String syntheticEmail = Constants.buildTeamSyntheticEmail(username);

                    Team newTeam = new Team(null, eventId, panelId, teamNumber, teamName,
                            teamName + " Project", "General", leaderName, syntheticEmail,
                            4, "", System.currentTimeMillis());
                    newTeam.setUsername(username);
                    newTeam.setEncryptedPassword(encryptedPassword);

                    firebaseManager.createTeam(newTeam)
                            .addOnSuccessListener(docRef -> {
                                String teamId = docRef.getId();
                                newTeam.setId(teamId);
                                newTeam.setQrCodeData(generateQRDataPayload(teamId, teamNumber, teamName, eventId, panelId));
                                firebaseManager.updateTeam(newTeam);

                                // Provision Team Leader auth account using the generated username and temporary password
                                firebaseManager.createTeamLeaderAccountWithUsername(this, username, tempPassword, encryptedPassword, leaderName, teamId)
                                        .addOnSuccessListener(uid -> {
                                            loadingDialog.dismiss();
                                            loadPanelsAndTeams();
                                            showTeamCredentialsDialog(teamName, leaderName, username, tempPassword);
                                        })
                                        .addOnFailureListener(e -> {
                                            loadingDialog.dismiss();
                                            loadPanelsAndTeams();
                                            new AlertDialog.Builder(this)
                                                    .setTitle("Leader Account Warning")
                                                    .setMessage("Team '" + teamName + "' was created, but failed to provision login account:\n\n" + e.getMessage())
                                                    .setPositiveButton("OK", (d, w) -> d.dismiss())
                                                    .show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                loadingDialog.dismiss();
                                UIUtils.showToast(this, "Failed to create team: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    UIUtils.showToast(this, "Failed to generate unique username: " + e.getMessage());
                });
    }

    private void showTeamCredentialsDialog(String teamName, String leaderName, String username, String tempPassword) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_team_credentials, null);

        TextView tvCredTeamName = view.findViewById(R.id.tvCredTeamName);
        TextView tvCredLeaderName = view.findViewById(R.id.tvCredLeaderName);
        TextView tvCredUsername = view.findViewById(R.id.tvCredUsername);
        TextView tvCredPassword = view.findViewById(R.id.tvCredPassword);
        MaterialButton btnCopyCredentials = view.findViewById(R.id.btnCopyCredentials);
        MaterialButton btnCloseCredentials = view.findViewById(R.id.btnCloseCredentials);

        tvCredTeamName.setText("Team: " + teamName);
        tvCredLeaderName.setText("Leader: " + leaderName);
        tvCredUsername.setText(username);
        tvCredPassword.setText(tempPassword);

        AlertDialog dialog = builder.setView(view).create();

        btnCopyCredentials.setOnClickListener(v -> {
            String credsToCopy = "JudgeX Team Credentials\n"
                    + "Team: " + teamName + "\n"
                    + "Leader: " + leaderName + "\n"
                    + "Username: " + username + "\n"
                    + "Temporary Password: " + tempPassword;
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText("JudgeX Credentials", credsToCopy);
                clipboard.setPrimaryClip(clip);
                UIUtils.showToast(this, "Credentials copied to clipboard!");
            }
        });

        btnCloseCredentials.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private String generateUniqueTeamNumber(List<Team> teams) {
        int maxNum = 100;
        if (teams != null) {
            for (Team t : teams) {
                if (t.getTeamNumber() != null && t.getTeamNumber().startsWith("TM-")) {
                    try {
                        int num = Integer.parseInt(t.getTeamNumber().substring(3).trim());
                        if (num > maxNum) {
                            maxNum = num;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return "TM-" + (maxNum + 1);
    }

    private void updateExistingTeamDetails(Team existingTeam, String number, String name, String title,
                                           String category, String leader, int memberCount, String panelId) {
        Dialog loadingDialog = UIUtils.showLoadingDialog(this, "Updating team...");
        existingTeam.setTeamNumber(number);
        existingTeam.setTeamName(name);
        existingTeam.setProjectTitle(title);
        existingTeam.setCategory(category);
        existingTeam.setLeaderName(leader);
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
                            showAddEditTeamDialog(team);
                            break;
                        case 2:
                            showReassignTeamPanelDialog(team);
                            break;
                        case 3:
                            onQRClick(team);
                            break;
                        case 4:
                            handleRemoveTeamFromEvent(team);
                            break;
                        case 5:
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Team & Leader Details");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_team_details, null);

        TextView tvTeamNumber = view.findViewById(R.id.tvDetailsTeamNumber);
        TextView tvTeamName = view.findViewById(R.id.tvDetailsTeamName);
        TextView tvUsername = view.findViewById(R.id.tvDetailsUsername);
        View llPasswordRow = view.findViewById(R.id.llDetailsPasswordRow);
        TextView tvPasswordValue = view.findViewById(R.id.tvDetailsPasswordValue);
        ImageButton btnTogglePassword = view.findViewById(R.id.btnDetailsTogglePassword);
        TextView tvProject = view.findViewById(R.id.tvDetailsProject);
        TextView tvCategory = view.findViewById(R.id.tvDetailsCategory);
        TextView tvLeaderName = view.findViewById(R.id.tvDetailsLeaderName);
        TextView tvLeaderEmail = view.findViewById(R.id.tvDetailsLeaderEmail);
        TextView tvPanel = view.findViewById(R.id.tvDetailsPanel);
        TextView tvMembers = view.findViewById(R.id.tvDetailsMembers);
        TextView tvStatus = view.findViewById(R.id.tvDetailsStatus);
        TextView tvQRPass = view.findViewById(R.id.tvDetailsQRPass);

        tvTeamNumber.setText("🏷️ Team Number: " + (team.getTeamNumber() != null ? team.getTeamNumber() : "N/A"));
        tvTeamName.setText("🚀 Team Name: " + (team.getTeamName() != null ? team.getTeamName() : "N/A"));

        if (team.getUsername() != null && !team.getUsername().isEmpty()) {
            tvUsername.setVisibility(View.VISIBLE);
            tvUsername.setText("🔑 Login Username: " + team.getUsername());
        } else {
            tvUsername.setVisibility(View.GONE);
        }

        // Decrypt the securely stored password generated during team creation
        String decryptedPassword = CryptoUtils.decrypt(team.getEncryptedPassword());
        if (!decryptedPassword.isEmpty()) {
            llPasswordRow.setVisibility(View.VISIBLE);
            tvPasswordValue.setText("••••••••");
            final boolean[] isPasswordVisible = {false};

            btnTogglePassword.setOnClickListener(v -> {
                if (isPasswordVisible[0]) {
                    // Conceal password
                    tvPasswordValue.setText("••••••••");
                    btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
                    isPasswordVisible[0] = false;
                } else {
                    // Reveal actual password generated during creation
                    tvPasswordValue.setText(decryptedPassword);
                    btnTogglePassword.setImageResource(R.drawable.ic_visibility);
                    isPasswordVisible[0] = true;
                }
            });

            // Allow long-click or click on password to copy to clipboard
            tvPasswordValue.setOnLongClickListener(v -> {
                android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cb != null) {
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Team Password", decryptedPassword);
                    cb.setPrimaryClip(clip);
                    UIUtils.showToast(this, "Password copied to clipboard!");
                }
                return true;
            });
        } else {
            llPasswordRow.setVisibility(View.GONE);
        }

        tvProject.setText("💡 Project: " + (team.getProjectTitle() != null ? team.getProjectTitle() : "N/A"));
        tvCategory.setText("📂 Category: " + (team.getCategory() != null ? team.getCategory() : "General"));
        tvLeaderName.setText("👤 Team Leader: " + (team.getLeaderName() != null ? team.getLeaderName() : "N/A"));

        if (team.getLeaderEmail() != null && !team.getLeaderEmail().isEmpty() && !Constants.isSyntheticEmail(team.getLeaderEmail())) {
            tvLeaderEmail.setVisibility(View.VISIBLE);
            tvLeaderEmail.setText("📧 Leader Email: " + team.getLeaderEmail());
        } else {
            tvLeaderEmail.setVisibility(View.GONE);
        }

        tvPanel.setText("⚖️ Panel Assignment: " + panelName);
        tvMembers.setText("👥 Team Members: " + team.getMemberCount());
        tvStatus.setText("🟢 Participation Status: " + (team.isUnregistered() ? "Unregistered / Removed" : "Active"));
        tvQRPass.setText("🎫 Event QR Pass: " + (team.getQrCodeData() != null && !team.getQrCodeData().isEmpty() ? "Generated (v2 Secured)" : "Not Generated"));

        builder.setView(view);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        builder.setNeutralButton("View QR Pass", (dialog, which) -> {
            dialog.dismiss();
            onQRClick(team);
        });
        builder.show();
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

    /**
     * Checks whether a team with the matching Team Name and Team Leader Name already exists
     * in the event, ignoring capitalization and extra spaces.
     */
    public static boolean isDuplicateTeam(List<Team> teams, String teamName, String leaderName) {
        if (teams == null || teamName == null || leaderName == null) {
            return false;
        }
        String normName = normalizeTeamString(teamName);
        String normLeader = normalizeTeamString(leaderName);
        for (Team t : teams) {
            if (t != null && !t.isDeleted()) {
                if (normName.equals(normalizeTeamString(t.getTeamName()))
                        && normLeader.equals(normalizeTeamString(t.getLeaderName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String normalizeTeamString(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
    }
}
