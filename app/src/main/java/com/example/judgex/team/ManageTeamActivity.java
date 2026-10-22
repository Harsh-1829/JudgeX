package com.example.judgex.team;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.MemberAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Team;
import com.example.judgex.models.TeamMember;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ManageTeamActivity extends AppCompatActivity implements MemberAdapter.OnMemberActionListener {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvTeamNumber, tvRosterCountBadge, tvTeamName, tvEmptyState;
    private RecyclerView recyclerViewMembers;
    private ExtendedFloatingActionButton fabAddMember;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private Team currentTeam;
    private final List<TeamMember> memberList = new ArrayList<>();
    private MemberAdapter adapter;

    private final ActivityResultLauncher<Intent> memberLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    TeamMember member = (TeamMember) result.getData().getSerializableExtra(Constants.EXTRA_MEMBER);
                    if (member != null && currentTeam != null) {
                        currentTeam.addMember(member);
                        if (member.isLeader()) {
                            // Ensure only this member has leader flag
                            for (TeamMember m : currentTeam.getMembers()) {
                                if (!m.getId().equals(member.getId())) {
                                    m.setLeader(false);
                                }
                            }
                            currentTeam.setLeaderName(member.getName());
                            currentTeam.setLeaderEmail(member.getEmail());
                        }
                        saveTeamToFirestore();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_team);

        initViews();
        loadTeamRoster();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvTeamNumber = findViewById(R.id.tvTeamNumber);
        tvRosterCountBadge = findViewById(R.id.tvRosterCountBadge);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        recyclerViewMembers = findViewById(R.id.recyclerViewMembers);
        fabAddMember = findViewById(R.id.fabAddMember);

        toolbar.setNavigationOnClickListener(v -> finish());
        swipeRefreshLayout.setOnRefreshListener(this::loadTeamRoster);

        recyclerViewMembers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemberAdapter(memberList, this);
        recyclerViewMembers.setAdapter(adapter);

        fabAddMember.setOnClickListener(v -> {
            if (currentTeam == null) {
                UIUtils.showToast(this, "Team not loaded yet.");
                return;
            }
            Intent intent = new Intent(this, AddEditMemberActivity.class);
            intent.putExtra(Constants.EXTRA_TEAM, currentTeam);
            memberLauncher.launch(intent);
        });
    }

    private void loadTeamRoster() {
        swipeRefreshLayout.setRefreshing(true);
        if (firebaseManager.getCurrentFirebaseUser() == null) {
            finish();
            return;
        }

        String uid = firebaseManager.getCurrentFirebaseUser().getUid();
        User profile = firebaseManager.getCurrentUserProfile();
        String assignedTeamId = profile != null ? profile.getAssignedTeamId() : null;

        if (assignedTeamId != null && !assignedTeamId.isEmpty()) {
            firebaseManager.getTeam(assignedTeamId)
                    .addOnSuccessListener(doc -> onTeamDocLoaded(doc, uid))
                    .addOnFailureListener(e -> fallbackQueryTeam(uid));
        } else {
            fallbackQueryTeam(uid);
        }
    }

    private void fallbackQueryTeam(String uid) {
        firebaseManager.getTeamsCollection().whereEqualTo("leaderUid", uid).limit(1).get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        onTeamDocLoaded(qs.getDocuments().get(0), uid);
                    } else {
                        swipeRefreshLayout.setRefreshing(false);
                        UIUtils.showToast(this, "No team found for your account.");
                    }
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load team: " + e.getMessage());
                });
    }

    private void onTeamDocLoaded(DocumentSnapshot doc, String uid) {
        swipeRefreshLayout.setRefreshing(false);
        if (doc.exists()) {
            currentTeam = doc.toObject(Team.class);
            if (currentTeam != null) {
                currentTeam.setId(doc.getId());
                bindTeamData();
                return;
            }
        }
        UIUtils.showToast(this, "Could not load team data.");
    }

    private void bindTeamData() {
        if (currentTeam == null) return;

        tvTeamNumber.setText(currentTeam.getTeamNumber() != null ? currentTeam.getTeamNumber() : "TM");
        tvTeamName.setText(currentTeam.getTeamName() != null ? currentTeam.getTeamName() : "Team Roster");

        memberList.clear();
        if (currentTeam.getMembers() != null && !currentTeam.getMembers().isEmpty()) {
            memberList.addAll(currentTeam.getMembers());
        } else {
            // Seed leader as first member if roster was empty
            TeamMember leader = new TeamMember(
                    java.util.UUID.randomUUID().toString(),
                    currentTeam.getLeaderName(),
                    currentTeam.getLeaderEmail(),
                    "LEADER-01",
                    "Engineering",
                    "Undergraduate",
                    "",
                    true
            );
            currentTeam.addMember(leader);
            memberList.add(leader);
            saveTeamToFirestore();
        }

        int count = memberList.size();
        tvRosterCountBadge.setText(count + (count == 1 ? " Member" : " Members"));
        adapter.notifyDataSetChanged();
        tvEmptyState.setVisibility(memberList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void saveTeamToFirestore() {
        if (currentTeam == null || currentTeam.getId() == null) return;

        Dialog loading = UIUtils.showLoadingDialog(this, "Updating team roster...");
        currentTeam.setUpdatedAt(System.currentTimeMillis());
        currentTeam.setMemberCount(currentTeam.getMembers().size());

        firebaseManager.updateTeam(currentTeam)
                .addOnSuccessListener(aVoid -> {
                    loading.dismiss();
                    UIUtils.showToast(this, "Team roster updated successfully.");
                    bindTeamData();
                })
                .addOnFailureListener(e -> {
                    loading.dismiss();
                    UIUtils.showToast(this, "Failed to update roster: " + e.getMessage());
                });
    }

    @Override
    public void onEdit(TeamMember member, int position) {
        Intent intent = new Intent(this, AddEditMemberActivity.class);
        intent.putExtra(Constants.EXTRA_TEAM, currentTeam);
        intent.putExtra(Constants.EXTRA_MEMBER, member);
        memberLauncher.launch(intent);
    }

    @Override
    public void onDelete(TeamMember member, int position) {
        if (memberList.size() <= 1) {
            UIUtils.showToast(this, "Your team must have at least one member.");
            return;
        }

        if (member.isLeader()) {
            UIUtils.showToast(this, "Cannot remove the designated Team Leader. Please assign another member as Team Leader first.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Remove Member")
                .setMessage("Are you sure you want to remove " + member.getName() + " from the team roster?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    currentTeam.removeMember(member.getId());
                    saveTeamToFirestore();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onSetLeader(TeamMember member, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Change Team Leader")
                .setMessage("Make " + member.getName() + " the official Team Leader for this team?")
                .setPositiveButton("Confirm", (dialog, which) -> {
                    for (TeamMember m : currentTeam.getMembers()) {
                        m.setLeader(m.getId().equals(member.getId()));
                    }
                    currentTeam.setLeaderName(member.getName());
                    currentTeam.setLeaderEmail(member.getEmail());
                    saveTeamToFirestore();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
