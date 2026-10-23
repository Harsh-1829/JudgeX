package com.example.judgex.admin;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
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
import com.example.judgex.utils.CryptoUtils;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
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
                btnAssignToPanel.setVisibility(View.VISIBLE);
                btnAutoAssign.setVisibility(View.VISIBLE);
                btnDeleteJudges.setText("Delete (" + count + ")");
                btnDeleteJudges.setVisibility(View.VISIBLE);
            } else {
                btnAssignToPanel.setVisibility(View.GONE);
                btnAutoAssign.setVisibility(View.GONE);
                btnDeleteJudges.setVisibility(View.GONE);
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
                            // Show in judge list if role is JUDGE (including legacy formats) and not ADMIN or TEAM_LEADER
                            if (user.isJudge() && !user.isAdmin() && !user.isTeamLeader()) {
                                if (isJudgeInEvent(user, eventId)) {
                                    judgeList.add(user);
                                }
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

    public static boolean isJudgeInEvent(User user, String eventId) {
        if (user == null) return false;
        if (eventId == null || eventId.trim().isEmpty()) return true;
        if (eventId.equals(user.getEventId())) return true;
        if (user.getEventPanelAssignments() != null && user.getEventPanelAssignments().containsKey(eventId)) return true;
        // Legacy fallback: accounts without eventId or eventPanelAssignments
        if ((user.getEventId() == null || user.getEventId().isEmpty())
                && (user.getEventPanelAssignments() == null || user.getEventPanelAssignments().isEmpty())) {
            return true;
        }
        return false;
    }

    private void showAddJudgeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_judge, null);

        TextInputLayout tilJudgeName = view.findViewById(R.id.tilJudgeName);
        TextInputLayout tilJudgeEmail = view.findViewById(R.id.tilJudgeEmail);
        TextInputLayout tilJudgePassword = view.findViewById(R.id.tilJudgePassword);
        TextInputEditText etJudgeName = view.findViewById(R.id.etJudgeName);
        View tvAssignPanelLabel = view.findViewById(R.id.tvAssignPanelLabel);
        Spinner spinnerPanels = view.findViewById(R.id.spinnerPanels);

        // Ask only for Judge Name: hide email, password, and panel dropdown during creation
        if (tilJudgeEmail != null) tilJudgeEmail.setVisibility(View.GONE);
        if (tilJudgePassword != null) tilJudgePassword.setVisibility(View.GONE);
        if (tvAssignPanelLabel != null) tvAssignPanelLabel.setVisibility(View.GONE);
        if (spinnerPanels != null) spinnerPanels.setVisibility(View.GONE);

        builder.setView(view);
        builder.setPositiveButton("Register Judge", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etJudgeName.getText() != null ? etJudgeName.getText().toString().trim() : "";

                if (TextUtils.isEmpty(name)) {
                    tilJudgeName.setError("Judge name is required");
                    return;
                }

                dialog.dismiss();
                registerNewJudgeWithGeneratedCredentials(name);
            });
        });
        dialog.show();
    }

    private void registerNewJudgeWithGeneratedCredentials(String name) {
        Dialog loadingDialog = UIUtils.showLoadingDialog(this, "Generating credentials and registering judge...");

        firebaseManager.generateUniqueJudgeUsername(name)
                .addOnSuccessListener(username -> {
                    String tempPassword = FirebaseManager.generateSecureTemporaryPassword();
                    String encryptedPassword = CryptoUtils.encrypt(tempPassword);

                    firebaseManager.createJudgeAccountWithUsername(this, username, tempPassword, encryptedPassword, name, "", eventId)
                            .addOnSuccessListener(uid -> {
                                loadingDialog.dismiss();
                                UIUtils.showToast(this, "Judge account ready: " + name);
                                loadPanelsAndJudges();
                                showJudgeCreatedCredentialsDialog(name, username, tempPassword);
                            })
                            .addOnFailureListener(e -> {
                                loadingDialog.dismiss();
                                String errorMsg = e.getMessage() != null ? e.getMessage() : "Failed to register judge.";
                                new AlertDialog.Builder(this)
                                        .setTitle("Registration Notice")
                                        .setMessage(errorMsg)
                                        .setPositiveButton("OK", (d, w) -> d.dismiss())
                                        .show();
                            });
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    UIUtils.showToast(this, "Failed to generate unique username: " + e.getMessage());
                });
    }

    private void showJudgeCreatedCredentialsDialog(String name, String username, String password) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_judge_credentials, null);

        TextView tvCredName = view.findViewById(R.id.tvCredName);
        TextView tvCredEmail = view.findViewById(R.id.tvCredEmail);
        TextView tvCredPassword = view.findViewById(R.id.tvCredPassword);
        MaterialButton btnTogglePassword = view.findViewById(R.id.btnTogglePassword);
        MaterialButton btnCopyCredentials = view.findViewById(R.id.btnCopyCredentials);
        MaterialButton btnDoneCredentials = view.findViewById(R.id.btnDoneCredentials);

        tvCredName.setText(name);
        tvCredEmail.setText("Login Username: " + username);

        final String maskedPassword = "••••••••";
        final boolean[] isPasswordVisible = {false};
        tvCredPassword.setText(maskedPassword);

        btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible[0] = !isPasswordVisible[0];
            if (isPasswordVisible[0]) {
                tvCredPassword.setText(password);
                btnTogglePassword.setText("Hide");
            } else {
                tvCredPassword.setText(maskedPassword);
                btnTogglePassword.setText("Show");
            }
        });

        btnCopyCredentials.setOnClickListener(v -> {
            String clipText = "JudgeX Credentials:\nName: " + name + "\nUsername: " + username + "\nPassword: " + password;
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText("Judge Credentials", clipText);
                clipboard.setPrimaryClip(clip);
                UIUtils.showToast(this, "Credentials copied to clipboard.");
            }
        });

        builder.setView(view);
        AlertDialog dialog = builder.create();
        btnDoneCredentials.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            // Clear temporary password display when dismissed
            tvCredPassword.setText("");
        });
        dialog.show();
    }

    @Override
    public void onJudgeClick(User judge) {
        String statusLabel = judge.isActive() ? "Deactivate Judge Account" : "Reactivate Judge Account";
        String[] options = {
                "View Profile Details",
                "Edit Judge Name",
                "Reset Password",
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
                            showJudgeDetailsDialog(judge);
                            break;
                        case 1:
                            showEditJudgeNameDialog(judge);
                            break;
                        case 2:
                            showResetPasswordDialog(judge);
                            break;
                        case 3:
                            showReassignDialog(judge);
                            break;
                        case 4:
                            handleRemoveJudgeFromEvent(judge);
                            break;
                        case 5:
                            handleToggleJudgeActiveStatus(judge);
                            break;
                        case 6:
                            handleDeleteJudgeAccount(judge);
                            break;
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showJudgeDetailsDialog(User judge) {
        String currentPanelId = judge.getPanelForEvent(eventId);
        String panelName = "Unassigned";
        if (currentPanelId != null && panelMap.containsKey(currentPanelId)) {
            Panel p = panelMap.get(currentPanelId);
            panelName = (p != null) ? p.getName() : currentPanelId;
        }

        int totalAssignments = judge.getEventPanelAssignments() != null ? judge.getEventPanelAssignments().size() : 0;
        String orgInfo = judge.getOrganizationName() != null ? judge.getOrganizationName() : (judge.getOrganizationId() != null ? judge.getOrganizationId() : "Current Organization");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Judge Profile & Account");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_judge_details, null);

        TextView tvJudgeName = view.findViewById(R.id.tvDetailsJudgeName);
        TextView tvUsername = view.findViewById(R.id.tvDetailsUsername);
        View llPasswordRow = view.findViewById(R.id.llDetailsPasswordRow);
        TextView tvPasswordValue = view.findViewById(R.id.tvDetailsPasswordValue);
        ImageButton btnTogglePassword = view.findViewById(R.id.btnDetailsTogglePassword);
        TextView tvEmail = view.findViewById(R.id.tvDetailsEmail);
        TextView tvOrganization = view.findViewById(R.id.tvDetailsOrganization);
        TextView tvPanel = view.findViewById(R.id.tvDetailsPanel);
        TextView tvAssignments = view.findViewById(R.id.tvDetailsAssignments);
        TextView tvStatus = view.findViewById(R.id.tvDetailsStatus);
        TextView tvAuthNote = view.findViewById(R.id.tvDetailsAuthNote);

        tvJudgeName.setText("Full Name: " + (judge.getName() != null ? judge.getName() : "N/A"));

        String username = null;
        if (judge.getUsername() != null && !judge.getUsername().isEmpty()) {
            username = judge.getUsername();
        } else if (Constants.isSyntheticEmail(judge.getEmail())) {
            username = Constants.extractUsernameFromSyntheticEmail(judge.getEmail());
        }

        if (username != null && !username.isEmpty()) {
            tvUsername.setVisibility(View.VISIBLE);
            tvUsername.setText("Login Username: " + username);
        } else {
            tvUsername.setVisibility(View.GONE);
        }

        // Decrypt the securely stored password generated during judge creation / reset
        String decryptedPassword = "";
        if (judge.getEncryptedSecret() != null && !judge.getEncryptedSecret().isEmpty()) {
            decryptedPassword = CryptoUtils.decrypt(judge.getEncryptedSecret());
        }

        if (!decryptedPassword.isEmpty()) {
            final String finalDecryptedPassword = decryptedPassword;
            llPasswordRow.setVisibility(View.VISIBLE);
            tvPasswordValue.setText("••••••••");
            btnTogglePassword.setVisibility(View.VISIBLE);
            final boolean[] isPasswordVisible = {false};

            btnTogglePassword.setOnClickListener(v -> {
                if (isPasswordVisible[0]) {
                    // Conceal password
                    tvPasswordValue.setText("••••••••");
                    btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
                    isPasswordVisible[0] = false;
                } else {
                    // Reveal actual password generated during creation/reset
                    tvPasswordValue.setText(finalDecryptedPassword);
                    btnTogglePassword.setImageResource(R.drawable.ic_visibility);
                    isPasswordVisible[0] = true;
                }
            });

            // Allow click or long-click on password to copy to clipboard
            tvPasswordValue.setOnLongClickListener(v -> {
                android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cb != null) {
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Judge Password", finalDecryptedPassword);
                    cb.setPrimaryClip(clip);
                    UIUtils.showToast(this, "Password copied to clipboard!");
                }
                return true;
            });
        } else {
            // Password not stored or legacy account
            llPasswordRow.setVisibility(View.VISIBLE);
            tvPasswordValue.setText("Not available (Tap Reset)");
            btnTogglePassword.setVisibility(View.GONE);
            tvPasswordValue.setOnClickListener(v -> showResetPasswordDialog(judge));
        }

        if (judge.getEmail() != null && !judge.getEmail().isEmpty() && !Constants.isSyntheticEmail(judge.getEmail())) {
            tvEmail.setVisibility(View.VISIBLE);
            tvEmail.setText("Email: " + judge.getEmail());
        } else {
            tvEmail.setVisibility(View.GONE);
        }

        tvOrganization.setText("Organization: " + orgInfo);
        tvPanel.setText("Panel (This Event): " + panelName);
        tvAssignments.setText("Total Event Assignments: " + totalAssignments);
        tvStatus.setText("Status: " + (judge.isActive() ? "Active" : "Deactivated"));
        tvAuthNote.setText("Authentication: Managed via Firebase Auth (passwords are encrypted and never stored in plain text).");

        builder.setView(view);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        builder.setNeutralButton("Reset Password", (dialog, which) -> {
            dialog.dismiss();
            showResetPasswordDialog(judge);
        });
        builder.show();
    }

    private void showEditJudgeNameDialog(User judge) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_judge_name, null);

        TextInputLayout tilEditJudgeName = view.findViewById(R.id.tilEditJudgeName);
        TextInputEditText etEditJudgeName = view.findViewById(R.id.etEditJudgeName);

        String currentName = judge.getName() != null ? judge.getName() : "";
        etEditJudgeName.setText(currentName);
        etEditJudgeName.setSelection(currentName.length());

        builder.setTitle("Edit Judge Name");
        builder.setView(view);
        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newName = etEditJudgeName.getText() != null ? etEditJudgeName.getText().toString().trim() : "";
                if (TextUtils.isEmpty(newName) || newName.length() < 2) {
                    tilEditJudgeName.setError("Name must be at least 2 characters.");
                    return;
                }

                dialog.dismiss();
                judge.setName(newName);
                Dialog loading = UIUtils.showLoadingDialog(JudgeManagerActivity.this, "Updating judge profile...");
                firebaseManager.saveUserProfile(judge)
                        .addOnSuccessListener(aVoid -> {
                            loading.dismiss();
                            UIUtils.showToast(JudgeManagerActivity.this, "Judge profile updated successfully.");
                            loadPanelsAndJudges();
                        })
                        .addOnFailureListener(e -> {
                            loading.dismiss();
                            UIUtils.showToast(JudgeManagerActivity.this, "Failed to update profile: " + e.getMessage());
                        });
            });
        });
        dialog.show();
    }

    private void showResetPasswordDialog(User judge) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_judge_password, null);

        TextInputLayout tilNewPassword = view.findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirmPassword = view.findViewById(R.id.tilConfirmPassword);
        TextInputEditText etNewPassword = view.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        MaterialButton btnGenerateRandomPassword = view.findViewById(R.id.btnGenerateRandomPassword);

        if (btnGenerateRandomPassword != null) {
            btnGenerateRandomPassword.setOnClickListener(v -> {
                String gen = FirebaseManager.generateSecureTemporaryPassword();
                etNewPassword.setText(gen);
                etConfirmPassword.setText(gen);
                tilNewPassword.setError(null);
                tilConfirmPassword.setError(null);
                UIUtils.showToast(this, "Generated secure temporary password");
            });
        }

        builder.setTitle("Reset Password: " + (judge.getName() != null ? judge.getName() : "Judge"));
        builder.setView(view);
        builder.setPositiveButton("Reset Password", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newPass = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";
                String confirmPass = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

                if (TextUtils.isEmpty(newPass)) {
                    tilNewPassword.setError("New password is required.");
                    return;
                }
                if (newPass.length() < 6) {
                    tilNewPassword.setError("Password must be at least 6 characters (Firebase requirement).");
                    return;
                }
                tilNewPassword.setError(null);

                if (!newPass.equals(confirmPass)) {
                    tilConfirmPassword.setError("Passwords do not match.");
                    return;
                }
                tilConfirmPassword.setError(null);

                dialog.dismiss();
                Dialog loading = UIUtils.showLoadingDialog(this, "Resetting judge password in Firebase...");
                firebaseManager.resetJudgePassword(judge.getUid(), newPass)
                        .addOnSuccessListener(resultMsg -> {
                            // Securely encrypt with AES-GCM and store in judge profile
                            String encryptedSecret = CryptoUtils.encrypt(newPass);
                            judge.setEncryptedSecret(encryptedSecret);
                            firebaseManager.saveUserProfile(judge);

                            loading.dismiss();
                            UIUtils.showToast(JudgeManagerActivity.this, "Password successfully reset for " + judge.getName());
                            String loginIdentifier = judge.getUsername() != null ? judge.getUsername() : judge.getEmail();
                            new AlertDialog.Builder(JudgeManagerActivity.this)
                                    .setTitle("Password Reset Successful")
                                    .setMessage("The password for " + judge.getName() + " (" + loginIdentifier + ") has been updated in Firebase Authentication.\n\n"
                                            + "Login Username: " + loginIdentifier + "\n"
                                            + "Login Password: " + newPass)
                                    .setPositiveButton("OK", (dialogInterface, which) -> dialogInterface.dismiss())
                                    .setNeutralButton("Copy Password", (dialogInterface, which) -> {
                                        android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                        if (cb != null) {
                                            android.content.ClipData clip = android.content.ClipData.newPlainText("Judge Password", newPass);
                                            cb.setPrimaryClip(clip);
                                            UIUtils.showToast(JudgeManagerActivity.this, "Password copied to clipboard!");
                                        }
                                    })
                                    .show();
                        })
                        .addOnFailureListener(e -> {
                            loading.dismiss();
                            String errMessage = e.getMessage() != null ? e.getMessage() : "Failed to reset password.";
                            new AlertDialog.Builder(JudgeManagerActivity.this)
                                    .setTitle("Password Reset Notice")
                                    .setMessage(errMessage + "\n\nNote: If not yet deployed, deploy the 'resetJudgePassword' Cloud Function using Firebase CLI (firebase deploy --only functions).")
                                    .setPositiveButton("OK", (dialogInterface, which) -> dialogInterface.dismiss())
                                    .show();
                        });
            });
        });
        dialog.show();
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
        if (judge == null || judge.getUid() == null || judge.getUid().trim().isEmpty()) {
            if (showToast) UIUtils.showToast(this, "Cannot delete account: Missing or invalid user ID.");
            return false;
        }

        // 1. Never delete the currently signed-in administrator account
        String currentAdminUid = firebaseManager.getCurrentUser() != null
                ? firebaseManager.getCurrentUser().getUid() : null;
        String currentAdminEmail = firebaseManager.getCurrentUser() != null
                ? firebaseManager.getCurrentUser().getEmail() : null;

        if (judge.getUid().equals(currentAdminUid)) {
            if (showToast) UIUtils.showToast(this, "Cannot delete the currently signed-in administrator account.");
            return false;
        }

        if (judge.getEmail() != null && currentAdminEmail != null
                && judge.getEmail().trim().equalsIgnoreCase(currentAdminEmail.trim())) {
            if (showToast) UIUtils.showToast(this, "Cannot delete the currently signed-in administrator account.");
            return false;
        }

        // 2. Reject Administrator accounts
        if (judge.isAdmin()) {
            if (showToast) UIUtils.showToast(this, "Administrator accounts cannot be deleted from this screen.");
            return false;
        }

        // 3. Reject Team Leader accounts
        if (judge.isTeamLeader()) {
            if (showToast) UIUtils.showToast(this, "Team Leader accounts cannot be deleted from this screen.");
            return false;
        }

        // 4. Must be a valid judge account (supports current and legacy role strings)
        if (!judge.isJudge()) {
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
                                showDeleteOutcomeDialog("Judge Account Deleted", result.message);
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
                                int successCount = results != null ? results.size() : 0;
                                int authDeletedCount = 0;
                                if (results != null) {
                                    for (FirebaseManager.DeleteJudgeResult r : results) {
                                        if (r.authDeleted) authDeletedCount++;
                                    }
                                }
                                String msg = successCount + " judge account(s) successfully processed.\n" +
                                        "Profiles and panel assignments have been deleted / unassigned.\n";
                                if (authDeletedCount == successCount && successCount > 0) {
                                    msg += "\nFirebase Authentication accounts were also successfully deleted.";
                                } else {
                                    msg += "\nNote: To permanently remove login credentials from Firebase Authentication without a deployed Cloud Function, delete the users in Firebase Console (Authentication > Users).";
                                }
                                showDeleteOutcomeDialog("Batch Deletion Complete", msg);
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
                    firebaseManager.batchAssignSelectedJudges(assignments, panelList, eventId)
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
        if (panelList == null || panelList.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No Panels Available")
                    .setMessage("No evaluation panels have been created for this event yet. Please open Panel Management and configure panels before assigning judges.")
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }

        List<User> selectedJudges = adapter.getSelectedJudges();
        if (selectedJudges.isEmpty()) {
            UIUtils.showToast(this, "Please select at least one judge to auto-assign.");
            return;
        }

        executeAutoAssign(selectedJudges);
    }

    private void executeAutoAssign(List<User> selectedJudges) {
        List<User> unassignedJudges = new ArrayList<>();
        List<User> alreadyAssignedJudges = new ArrayList<>();

        for (User j : selectedJudges) {
            if (!j.isActive() || j.isDeleted()) {
                continue;
            }
            String currentPanelId = j.getPanelForEvent(eventId);
            if (currentPanelId != null && !currentPanelId.isEmpty() && panelMap.containsKey(currentPanelId)) {
                alreadyAssignedJudges.add(j);
            } else {
                unassignedJudges.add(j);
            }
        }

        if (unassignedJudges.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("All selected judges (").append(alreadyAssignedJudges.size()).append(") are already assigned to panels for this event:\n\n");
            for (User j : alreadyAssignedJudges) {
                String pId = j.getPanelForEvent(eventId);
                String pName = (pId != null && panelMap.containsKey(pId)) ? panelMap.get(pId).getName() : "Assigned";
                msg.append("• ").append(j.getName()).append(" -> ").append(pName).append("\n");
            }
            msg.append("\nTo reassign an already-assigned judge, use 'Assign Panel' or edit their individual profile.");

            new AlertDialog.Builder(this)
                    .setTitle("Judges Already Assigned")
                    .setMessage(msg.toString())
                    .setPositiveButton("OK", (d, w) -> d.dismiss())
                    .show();
            return;
        }

        List<User> shuffledJudges = new ArrayList<>(unassignedJudges);
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

        StringBuilder summary = new StringBuilder();
        summary.append("Auto-assigning ").append(unassignedJudges.size()).append(" unassigned judges across ").append(panelList.size()).append(" panels:\n\n");
        for (Panel p : panelList) {
            List<String> names = panelToJudgeNames.get(p.getId());
            int count = names != null ? names.size() : 0;
            summary.append("• ").append(p.getName()).append(": +").append(count).append(" judges\n");
            if (names != null) {
                for (String name : names) {
                    summary.append("    - ").append(name).append("\n");
                }
            }
        }

        if (!alreadyAssignedJudges.isEmpty()) {
            summary.append("\nNote: ").append(alreadyAssignedJudges.size()).append(" selected judges are already assigned and will remain on their current panels.\n");
        }
        summary.append("\nConfirm and apply these assignments?");

        new AlertDialog.Builder(this)
                .setTitle("Auto-Assign " + unassignedJudges.size() + " Judges")
                .setMessage(summary.toString())
                .setPositiveButton("Apply Assignments", (dialog, which) -> {
                    btnAutoAssign.setEnabled(false);
                    Dialog loading = UIUtils.showLoadingDialog(this, "Applying panel assignments...");
                    firebaseManager.batchAssignSelectedJudges(judgeToPanelMap, panelList, eventId)
                            .addOnSuccessListener(aVoid -> {
                                btnAutoAssign.setEnabled(true);
                                loading.dismiss();
                                adapter.clearSelection();
                                UIUtils.showToast(this, unassignedJudges.size() + " judges successfully auto-assigned across panels!");
                                loadPanelsAndJudges();
                            })
                            .addOnFailureListener(e -> {
                                btnAutoAssign.setEnabled(true);
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
