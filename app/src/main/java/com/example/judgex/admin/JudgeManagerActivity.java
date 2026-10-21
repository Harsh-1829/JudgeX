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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
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
    private MaterialCheckBox cbSelectAll;
    private TextView tvSelectionCount;
    private MaterialButton btnAssignToPanel, btnAutoAssign, btnDeleteJudges;

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
        cbSelectAll = findViewById(R.id.cbSelectAll);
        tvSelectionCount = findViewById(R.id.tvSelectionCount);
        btnAssignToPanel = findViewById(R.id.btnAssignToPanel);
        btnAutoAssign = findViewById(R.id.btnAutoAssign);
        btnDeleteJudges = findViewById(R.id.btnDeleteJudges);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_judge_manager);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_random_assign_judges) {
                handleAutoAssignJudges();
                return true;
            }
            return false;
        });

        recyclerViewJudges.setLayoutManager(new LinearLayoutManager(this));
        adapter = new JudgeAdapter(judgeList, panelMap, eventId, this);
        adapter.setSelectionListener(count -> {
            tvSelectionCount.setText(count + " selected");
            cbSelectAll.setChecked(count > 0 && count == judgeList.size());
            if (count > 0) {
                btnDeleteJudges.setText("Delete (" + count + ")");
                btnDeleteJudges.setVisibility(View.VISIBLE);
                btnAutoAssign.setVisibility(View.GONE);
            } else {
                btnDeleteJudges.setVisibility(View.GONE);
                btnAutoAssign.setVisibility(View.VISIBLE);
            }
        });
        recyclerViewJudges.setAdapter(adapter);

        cbSelectAll.setOnClickListener(v -> {
            if (cbSelectAll.isChecked()) {
                adapter.selectAll();
            } else {
                adapter.clearSelection();
            }
        });

        btnAssignToPanel.setOnClickListener(v -> handleBatchAssignToPanel());
        btnAutoAssign.setOnClickListener(v -> handleAutoAssignJudges());
        btnDeleteJudges.setOnClickListener(v -> handleBatchDeleteJudges());

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
                            // Skip deleted judge accounts
                            if (user.isDeleted()) {
                                continue;
                            }
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

        firebaseManager.createJudgeAccount(this, email, password, name, panelId, eventId)
                .addOnSuccessListener(uid -> {
                    loadingDialog.dismiss();
                    UIUtils.showToast(this, "Judge account ready: " + name);
                    loadPanelsAndJudges();
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Registration Notice")
                            .setMessage(e.getMessage() != null ? e.getMessage() : "Failed to register judge.")
                            .setPositiveButton("OK", (d, w) -> d.dismiss())
                            .show();
                });
    }

    @Override
    public void onJudgeClick(User judge) {
        String statusLabel = judge.isActive() ? "Deactivate Judge Account" : "Reactivate Judge Account";
        String[] options = {
                "Assign / Reassign Panel",
                "Remove from This Event",
                statusLabel,
                "Delete Judge Account"
        };

        new AlertDialog.Builder(this)
                .setTitle((judge.getName() != null ? judge.getName() : "Judge") + " (" + judge.getEmail() + ")")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showReassignDialog(judge);
                            break;
                        case 1:
                            handleRemoveJudgeFromEvent(judge);
                            break;
                        case 2:
                            handleToggleJudgeActiveStatus(judge);
                            break;
                        case 3:
                            handleDeleteJudgeAccount(judge);
                            break;
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void handleRemoveJudgeFromEvent(User judge) {
        new AlertDialog.Builder(this)
                .setTitle("Remove from Event: " + judge.getName())
                .setMessage("This will remove this judge's panel assignment for this competition event.\n\n" +
                        "• The judge's account and assignments in other events will be preserved.\n" +
                        "• Any historical evaluations already submitted will remain intact.\n" +
                        "• The judge will no longer be able to submit new evaluations for this event.\n\n" +
                        "Proceed with removal?")
                .setPositiveButton("Remove from Event", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Removing assignment...");
                    String currentPanelId = judge.getPanelForEvent(eventId);

                    judge.setPanelForEvent(eventId, null);
                    if (eventId != null && eventId.equals(judge.getAssignedPanelId())) {
                        judge.setAssignedPanelId("");
                    }

                    firebaseManager.saveUserProfile(judge)
                            .addOnSuccessListener(aVoid -> {
                                if (currentPanelId != null && !currentPanelId.isEmpty()) {
                                    Map<String, Object> removeData = new HashMap<>();
                                    removeData.put("judgeIds", com.google.firebase.firestore.FieldValue.arrayRemove(judge.getUid()));
                                    firebaseManager.getPanelsCollection().document(currentPanelId)
                                            .set(removeData, com.google.firebase.firestore.SetOptions.merge())
                                            .addOnCompleteListener(t -> {
                                                loading.dismiss();
                                                UIUtils.showToast(this, "Judge assignment removed from event.");
                                                loadPanelsAndJudges();
                                            });
                                } else {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Judge assignment removed from event.");
                                    loadPanelsAndJudges();
                                }
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to remove assignment: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void handleToggleJudgeActiveStatus(User judge) {
        boolean currentlyActive = judge.isActive();
        if (currentlyActive) {
            new AlertDialog.Builder(this)
                    .setTitle("Deactivate Judge Account: " + judge.getName())
                    .setMessage("Deactivating this judge will prevent them from signing in to JudgeX.\n\n" +
                            "• Historical evaluations will remain safely intact for competition rankings.\n" +
                            "• The judge will not be able to evaluate teams in any event.\n" +
                            "• Note: Permanent deletion of Firebase Authentication login credentials requires trusted backend/Cloud Functions administration.\n\n" +
                            "Proceed to deactivate this account?")
                    .setPositiveButton("Deactivate Account", (dialog, which) -> {
                        Dialog loading = UIUtils.showLoadingDialog(this, "Deactivating judge account...");
                        judge.setActive(false);
                        firebaseManager.saveUserProfile(judge)
                                .addOnSuccessListener(aVoid -> {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Judge account deactivated.");
                                    loadPanelsAndJudges();
                                })
                                .addOnFailureListener(e -> {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Failed to deactivate account: " + e.getMessage());
                                });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Reactivate Judge Account: " + judge.getName())
                    .setMessage("Reactivating this judge will allow them to sign in to JudgeX again.\n\nProceed to reactivate?")
                    .setPositiveButton("Reactivate Account", (dialog, which) -> {
                        Dialog loading = UIUtils.showLoadingDialog(this, "Reactivating judge account...");
                        judge.setActive(true);
                        firebaseManager.saveUserProfile(judge)
                                .addOnSuccessListener(aVoid -> {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Judge account reactivated.");
                                    loadPanelsAndJudges();
                                })
                                .addOnFailureListener(e -> {
                                    loading.dismiss();
                                    UIUtils.showToast(this, "Failed to reactivate account: " + e.getMessage());
                                });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        }
    }

    private boolean isDeletableJudge(User judge, boolean showToast) {
        if (judge == null) {
            if (showToast) UIUtils.showToast(this, "Invalid judge record.");
            return false;
        }
        String currentAdminUid = firebaseManager.getCurrentUser() != null
                ? firebaseManager.getCurrentUser().getUid() : null;
        String currentAdminEmail = firebaseManager.getCurrentUser() != null
                ? firebaseManager.getCurrentUser().getEmail() : null;

        if (judge.getUid() != null && judge.getUid().equals(currentAdminUid)) {
            if (showToast) UIUtils.showToast(this, "Cannot delete the currently signed-in administrator account.");
            return false;
        }

        if (judge.getEmail() != null && currentAdminEmail != null
                && judge.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            if (showToast) UIUtils.showToast(this, "Cannot delete the currently signed-in administrator account.");
            return false;
        }

        if (judge.isAdmin() || (judge.getRole() != null && !judge.isJudge())) {
            if (showToast) UIUtils.showToast(this, "Only judge accounts can be deleted from this screen.");
            return false;
        }

        return true;
    }

    private void handleDeleteJudgeAccount(User judge) {
        if (!isDeletableJudge(judge, true)) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Judge Account: " + (judge.getName() != null ? judge.getName() : "Judge"))
                .setMessage("Are you sure you want to permanently delete this judge account?\n\n" +
                        "• Name: " + (judge.getName() != null ? judge.getName() : "Judge") + "\n" +
                        "• Email: " + judge.getEmail() + "\n\n" +
                        "WARNING: This action is permanent. The judge will be removed from all panel assignments and active judge lists.\n\n" +
                        "• If this judge submitted competition evaluations, those scoring records will be preserved for leaderboard auditability, but the account will be deleted and unassigned.\n" +
                        "• If this is a test account without evaluations, the profile will be completely deleted from the database.\n\n" +
                        "Proceed with permanent deletion?")
                .setPositiveButton("Delete Judge Account", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Deleting judge account...");
                    firebaseManager.deleteJudgeAccount(judge)
                            .addOnSuccessListener(result -> {
                                loading.dismiss();
                                adapter.clearSelection();
                                loadPanelsAndJudges();
                                showDeleteOutcomeDialog("Judge Account Deleted",
                                        result.message + "\n\n" +
                                        "Note: To permanently remove the login credential from Firebase Authentication without a deployed Cloud Function, delete the user in Firebase Console (Authentication > Users).");
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                new AlertDialog.Builder(this)
                                        .setTitle("Deletion Failed")
                                        .setMessage("Failed to delete judge account: " + e.getMessage())
                                        .setPositiveButton("OK", (d, w) -> d.dismiss())
                                        .show();
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void handleBatchDeleteJudges() {
        List<User> selectedJudges = adapter.getSelectedJudges();
        if (selectedJudges.isEmpty()) {
            UIUtils.showToast(this, "Please select at least one judge to delete.");
            return;
        }

        List<User> validJudges = new ArrayList<>();
        int excludedCount = 0;
        for (User j : selectedJudges) {
            if (isDeletableJudge(j, false)) {
                validJudges.add(j);
            } else {
                excludedCount++;
            }
        }

        if (validJudges.isEmpty()) {
            UIUtils.showToast(this, "No deletable judge accounts selected.");
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Are you sure you want to permanently delete ").append(validJudges.size()).append(" judge account(s)?\n\n");
        for (User j : validJudges) {
            summary.append("• ").append(j.getName() != null ? j.getName() : "Judge").append(" (").append(j.getEmail()).append(")\n");
        }
        if (excludedCount > 0) {
            summary.append("\nNote: ").append(excludedCount).append(" account(s) (administrator or non-judge) were excluded for safety.\n");
        }
        summary.append("\nWARNING: This action is permanent. The selected judges will be unassigned from all panels and deleted.\n\nProceed with permanent deletion?");

        new AlertDialog.Builder(this)
                .setTitle("Delete " + validJudges.size() + " Judge Accounts")
                .setMessage(summary.toString())
                .setPositiveButton("Delete " + validJudges.size() + " Accounts", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Deleting " + validJudges.size() + " judge accounts...");
                    firebaseManager.deleteJudgeAccounts(validJudges)
                            .addOnSuccessListener(results -> {
                                loading.dismiss();
                                adapter.clearSelection();
                                loadPanelsAndJudges();
                                showDeleteOutcomeDialog("Batch Deletion Complete",
                                        validJudges.size() + " judge account(s) successfully processed.\n" +
                                        "Profiles and panel assignments have been deleted / unassigned.\n\n" +
                                        "Note: To permanently remove login credentials from Firebase Authentication without a deployed Cloud Function, delete the users in Firebase Console (Authentication > Users).");
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                new AlertDialog.Builder(this)
                                        .setTitle("Batch Deletion Error")
                                        .setMessage("Failed during batch deletion: " + e.getMessage())
                                        .setPositiveButton("OK", (d, w) -> d.dismiss())
                                        .show();
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showDeleteOutcomeDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .show();
    }

    private void handleBatchAssignToPanel() {
        List<User> selectedJudges = adapter.getSelectedJudges();
        if (selectedJudges.isEmpty()) {
            UIUtils.showToast(this, "Please select at least one judge to assign.");
            return;
        }

        if (panelList.isEmpty()) {
            UIUtils.showToast(this, "No panels exist for this event. Please create panels first.");
            return;
        }

        List<String> panelNames = new ArrayList<>();
        for (Panel p : panelList) {
            panelNames.add(p.getName());
        }

        new AlertDialog.Builder(this)
                .setTitle("Assign " + selectedJudges.size() + " Judges to Panel")
                .setItems(panelNames.toArray(new String[0]), (dialog, which) -> {
                    Panel targetPanel = panelList.get(which);
                    Map<String, String> assignments = new HashMap<>();
                    for (User judge : selectedJudges) {
                        assignments.put(judge.getUid(), targetPanel.getId());
                    }

                    Dialog loading = UIUtils.showLoadingDialog(this, "Assigning judges to " + targetPanel.getName() + "...");
                    firebaseManager.batchAssignJudges(assignments, panelList, eventId)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                adapter.clearSelection();
                                UIUtils.showToast(this, selectedJudges.size() + " judges assigned to " + targetPanel.getName());
                                loadPanelsAndJudges();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Assignment failed: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void handleAutoAssignJudges() {
        if (panelList.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No Panels Available")
                    .setMessage("No evaluation panels have been created for this event yet. Please open Panel Management and configure panels before assigning judges.")
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }

        if (judgeList.isEmpty()) {
            UIUtils.showToast(this, "No registered judges available to assign.");
            return;
        }

        List<User> targetJudges = adapter.getSelectedJudges();
        if (targetJudges.isEmpty()) {
            // Explain what will happen and offer to auto-assign all or cancel to select
            new AlertDialog.Builder(this)
                    .setTitle("Auto-Assign Judges")
                    .setMessage("No specific judges were selected.\n\nWould you like to auto-assign ALL " + judgeList.size() + " available judges balanced evenly across all " + panelList.size() + " panels, or cancel to pick specific judges?")
                    .setPositiveButton("Auto-Assign All", (dialog, which) -> executeAutoAssign(judgeList))
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            executeAutoAssign(targetJudges);
        }
    }

    private void executeAutoAssign(List<User> judgesToAssign) {
        List<User> shuffledJudges = new ArrayList<>(judgesToAssign);
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
            panelToJudgeNames.get(assignedPanel.getId()).add(judge.getName() != null ? judge.getName() : "Judge");
        }

        // Build preview summary
        StringBuilder summary = new StringBuilder();
        summary.append("Balancing ").append(shuffledJudges.size()).append(" judges across ").append(panelList.size()).append(" panels:\n\n");
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
        summary.append("\nConfirm and apply these assignments?");

        new AlertDialog.Builder(this)
                .setTitle("Auto-Assignment Preview")
                .setMessage(summary.toString())
                .setPositiveButton("Apply Assignments", (dialog, which) -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Applying panel assignments...");
                    firebaseManager.batchAssignJudges(judgeToPanelMap, panelList, eventId)
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                adapter.clearSelection();
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
