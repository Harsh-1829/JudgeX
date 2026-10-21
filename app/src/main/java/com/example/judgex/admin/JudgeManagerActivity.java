package com.example.judgex.admin;

import android.app.Dialog;
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
import com.example.judgex.adapters.JudgeAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JudgeManagerActivity extends AppCompatActivity implements JudgeAdapter.OnJudgeClickListener {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewJudges;
    private TextView tvEmptyState;
    private ExtendedFloatingActionButton fabAddJudge;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<User> judgeList = new ArrayList<>();
    private final List<Panel> panelList = new ArrayList<>();
    private final Map<String, Panel> panelMap = new HashMap<>();
    private JudgeAdapter adapter;
    private Event currentEvent;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_judge_manager);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
        if (currentEvent != null && eventId == null) {
            eventId = currentEvent.getId();
        }

        initViews();
        loadPanelsAndJudges();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewJudges = findViewById(R.id.recyclerViewJudges);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        fabAddJudge = findViewById(R.id.fabAddJudge);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_judge_manager);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_random_assign_judges) {
                handleRandomJudgeAssignment();
                return true;
            }
            return false;
        });

        recyclerViewJudges.setLayoutManager(new LinearLayoutManager(this));
        adapter = new JudgeAdapter(judgeList, panelMap, eventId, this);
        recyclerViewJudges.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadPanelsAndJudges);
        fabAddJudge.setOnClickListener(v -> showAddJudgeDialog());
    }

    private void loadPanelsAndJudges() {
        swipeRefreshLayout.setRefreshing(true);
        if (eventId == null) {
            eventId = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE).getString(Constants.PREF_ACTIVE_EVENT_ID, null);
        }

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> panelTask =
                (eventId != null && !eventId.isEmpty())
                        ? firebaseManager.getPanelsForEvent(eventId)
                        : firebaseManager.getPanelsCollection().get();

        panelTask.addOnSuccessListener(querySnapshot -> {
            panelList.clear();
            panelMap.clear();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Panel p = doc.toObject(Panel.class);
                if (p != null) {
                    p.setId(doc.getId());
                    panelList.add(p);
                    panelMap.put(p.getId(), p);
                }
            }
            loadJudges();
        }).addOnFailureListener(e -> {
            firebaseManager.getPanelsCollection().get()
                    .addOnSuccessListener(qs -> {
                        panelList.clear();
                        panelMap.clear();
                        for (DocumentSnapshot doc : qs.getDocuments()) {
                            Panel p = doc.toObject(Panel.class);
                            if (p != null) {
                                p.setId(doc.getId());
                                panelList.add(p);
                                panelMap.put(p.getId(), p);
                            }
                        }
                        loadJudges();
                    })
                    .addOnFailureListener(err -> loadJudges());
        });
    }

    private void loadJudges() {
        firebaseManager.getAllJudges()
                .addOnSuccessListener(querySnapshot -> {
                    judgeList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            user.setUid(doc.getId());
                            // Show in judge list if role is JUDGE or not ADMIN
                            if (user.isJudge() || (user.getRole() != null && !user.isAdmin())) {
                                judgeList.add(user);
                            }
                        }
                    }

                    if (judgeList.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerViewJudges.setVisibility(View.GONE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                        recyclerViewJudges.setVisibility(View.VISIBLE);
                    }

                    adapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load judges: " + e.getMessage());
                });
    }

    private void showAddJudgeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_judge, null);

        TextInputLayout tilJudgeName = view.findViewById(R.id.tilJudgeName);
        TextInputLayout tilJudgeEmail = view.findViewById(R.id.tilJudgeEmail);
        TextInputLayout tilJudgePassword = view.findViewById(R.id.tilJudgePassword);
        TextInputEditText etJudgeName = view.findViewById(R.id.etJudgeName);
        TextInputEditText etJudgeEmail = view.findViewById(R.id.etJudgeEmail);
        TextInputEditText etJudgePassword = view.findViewById(R.id.etJudgePassword);
        Spinner spinnerPanels = view.findViewById(R.id.spinnerPanels);

        List<String> panelNames = new ArrayList<>();
        panelNames.add("None / Unassigned");
        for (Panel p : panelList) {
            panelNames.add(p.getName());
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, panelNames);
        spinnerPanels.setAdapter(spinnerAdapter);

        builder.setView(view);
        builder.setPositiveButton("Register Judge", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etJudgeName.getText() != null ? etJudgeName.getText().toString().trim() : "";
                String email = etJudgeEmail.getText() != null ? etJudgeEmail.getText().toString().trim() : "";
                String password = etJudgePassword.getText() != null ? etJudgePassword.getText().toString().trim() : "";

                if (TextUtils.isEmpty(name)) {
                    tilJudgeName.setError("Name is required");
                    return;
                }
                if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tilJudgeEmail.setError("Valid email is required");
                    return;
                }
                if (TextUtils.isEmpty(password) || password.length() < 6) {
                    tilJudgePassword.setError("Password must be at least 6 characters");
                    return;
                }

                String selectedPanelId = null;
                int selectedPosition = spinnerPanels.getSelectedItemPosition();
                if (selectedPosition > 0 && selectedPosition - 1 < panelList.size()) {
                    selectedPanelId = panelList.get(selectedPosition - 1).getId();
                }

                dialog.dismiss();
                registerJudge(name, email, password, selectedPanelId);
            });
        });
        dialog.show();
    }

    private void registerJudge(String name, String email, String password, String panelId) {
        Dialog loadingDialog = UIUtils.showLoadingDialog(this, "Registering judge account...");

        firebaseManager.createJudgeAccount(this, email, password, name, panelId)
                .addOnSuccessListener(uid -> {
                    loadingDialog.dismiss();
                    UIUtils.showToast(this, "Judge account created for: " + name);
                    loadPanelsAndJudges();
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    UIUtils.showToast(this, "Failed to register judge: " + e.getMessage());
                });
    }

    @Override
    public void onJudgeClick(User judge) {
        // Show dialog to reassign panel
        showReassignDialog(judge);
    }

    private void handleRandomJudgeAssignment() {
        if (panelList.isEmpty()) {
            UIUtils.showToast(this, "Please create panels for this event first!");
            return;
        }

        if (judgeList.isEmpty()) {
            UIUtils.showToast(this, "No judges available to assign!");
            return;
        }

        // Random distribution algorithm
        List<User> shuffledJudges = new ArrayList<>(judgeList);
        java.util.Collections.shuffle(shuffledJudges);

        Map<String, String> judgeToPanelMap = new HashMap<>();
        Map<String, List<String>> panelToJudgeNames = new HashMap<>();
        for (Panel p : panelList) {
            panelToJudgeNames.put(p.getId(), new ArrayList<>());
        }

        for (int i = 0; i < shuffledJudges.size(); i++) {
            User judge = shuffledJudges.get(i);
            int panelIndex = i % panelList.size();
            Panel assignedPanel = panelList.get(panelIndex);

            judgeToPanelMap.put(judge.getUid(), assignedPanel.getId());
            panelToJudgeNames.get(assignedPanel.getId()).add(judge.getName());
        }

        // Build summary for Admin verification
        StringBuilder summary = new StringBuilder();
        summary.append("Random assignment preview across ").append(panelList.size()).append(" panels:\n\n");
        for (Panel p : panelList) {
            List<String> names = panelToJudgeNames.get(p.getId());
            int count = names != null ? names.size() : 0;
            summary.append("• ").append(p.getName()).append(": ").append(count).append(" judges\n");
            if (names != null) {
                for (String name : names) {
                    summary.append("    - ").append(name).append("\n");
                }
            }
        }
        summary.append("\nProceed with updating judge panel assignments?");

        new AlertDialog.Builder(this)
                .setTitle("Confirm Random Judge Assignment")
                .setMessage(summary.toString())
                .setPositiveButton("Apply Assignments", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Applying random assignments...");
                    firebaseManager.batchAssignJudges(judgeToPanelMap, panelList, eventId)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Judges successfully distributed across panels!");
                                loadPanelsAndJudges();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to apply assignments: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showReassignDialog(User judge) {
        List<String> panelNames = new ArrayList<>();
        panelNames.add("None / Unassigned");
        int currentSelection = 0;

        String currentPanelId = judge.getPanelForEvent(eventId);

        for (int i = 0; i < panelList.size(); i++) {
            Panel p = panelList.get(i);
            panelNames.add(p.getName());
            if (p.getId().equals(currentPanelId)) {
                currentSelection = i + 1;
            }
        }

        int finalSelection = currentSelection;
        new AlertDialog.Builder(this)
                .setTitle("Reassign Panel: " + judge.getName())
                .setSingleChoiceItems(panelNames.toArray(new String[0]), finalSelection, (dialog, which) -> {
                    String newPanelId = which > 0 ? panelList.get(which - 1).getId() : "";
                    dialog.dismiss();
                    
                    Dialog loading = UIUtils.showLoadingDialog(this, "Updating panel assignment...");
                    firebaseManager.assignJudgeToPanel(judge.getUid(), eventId, newPanelId)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Panel reassigned successfully");
                                loadPanelsAndJudges();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to assign panel: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
