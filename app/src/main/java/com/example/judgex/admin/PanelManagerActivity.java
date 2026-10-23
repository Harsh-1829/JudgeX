package com.example.judgex.admin;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.judgex.R;
import com.example.judgex.adapters.PanelAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Event;
import com.example.judgex.models.Panel;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class PanelManagerActivity extends AppCompatActivity implements PanelAdapter.OnPanelClickListener {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewPanels;
    private TextView tvEmptyState;
    private ExtendedFloatingActionButton fabAddPanel;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Panel> panelList = new ArrayList<>();
    private PanelAdapter adapter;
    private Event currentEvent;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panel_manager);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
        if (currentEvent != null && eventId == null) {
            eventId = currentEvent.getId();
        }

        initViews();
        loadPanels();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewPanels = findViewById(R.id.recyclerViewPanels);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        fabAddPanel = findViewById(R.id.fabAddPanel);

        if (currentEvent != null) {
            toolbar.setSubtitle(currentEvent.getName());
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewPanels.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PanelAdapter(panelList, this);
        recyclerViewPanels.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadPanels);
        fabAddPanel.setOnClickListener(v -> showAddEditPanelDialog(null));
    }

    private void loadPanels() {
        if (eventId == null) {
            UIUtils.showToast(this, "No active event specified.");
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        firebaseManager.getPanelsForEvent(eventId)
                .addOnSuccessListener(querySnapshot -> {
                    panelList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Panel panel = doc.toObject(Panel.class);
                        if (panel != null) {
                            panel.setId(doc.getId());
                            panelList.add(panel);
                        }
                    }

                    if (panelList.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerViewPanels.setVisibility(View.GONE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                        recyclerViewPanels.setVisibility(View.VISIBLE);
                    }

                    adapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load panels: " + e.getMessage());
                });
    }

    private void showAddEditPanelDialog(Panel existingPanel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_panel, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        TextInputLayout tilPanelName = view.findViewById(R.id.tilPanelName);
        TextInputLayout tilVenue = view.findViewById(R.id.tilVenue);
        TextInputEditText etPanelName = view.findViewById(R.id.etPanelName);
        TextInputEditText etVenue = view.findViewById(R.id.etVenue);

        if (existingPanel != null) {
            tvDialogTitle.setText("Edit Panel Details");
            etPanelName.setText(existingPanel.getName());
            etVenue.setText(existingPanel.getVenue());
        }

        builder.setView(view);
        builder.setPositiveButton(existingPanel != null ? "Update" : "Save", null);
        if (existingPanel != null) {
            builder.setNeutralButton("Delete", (dialog, which) -> confirmDeletePanel(existingPanel));
        }
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etPanelName.getText() != null ? etPanelName.getText().toString().trim() : "";
                String venue = etVenue.getText() != null ? etVenue.getText().toString().trim() : "";

                if (TextUtils.isEmpty(name)) {
                    tilPanelName.setError("Panel name is required");
                    return;
                }

                dialog.dismiss();
                savePanel(existingPanel, name, venue);
            });
        });
        dialog.show();
    }

    private void confirmDeletePanel(Panel panel) {
        int judgeCount = panel.getJudgeIds() != null ? panel.getJudgeIds().size() : 0;
        int teamCount = panel.getTeamIds() != null ? panel.getTeamIds().size() : 0;
        boolean hasDependencies = judgeCount > 0 || teamCount > 0;

        String warning = hasDependencies
                ? "WARNING: Deleting panel '" + panel.getName() + "' will unassign " + judgeCount + 
                  " judge(s) and " + teamCount + " team(s) from this panel.\n\n" +
                  "Historical evaluations and team scores are safely preserved.\n\nProceed with panel deletion?"
                : "Are you sure you want to permanently delete panel '" + panel.getName() + "'?";

        UIUtils.showConfirmationDialog(this, "Delete Panel: " + panel.getName(), warning, "Delete Panel", () -> {
            Dialog loading = UIUtils.showLoadingDialog(this, "Deleting panel and unassigning records...");
            firebaseManager.deletePanel(panel)
                    .addOnSuccessListener(aVoid -> {
                        loading.dismiss();
                        UIUtils.showToast(this, "Panel deleted successfully.");
                        loadPanels();
                    })
                    .addOnFailureListener(e -> {
                        loading.dismiss();
                        new AlertDialog.Builder(this)
                                .setTitle("Deletion Failed")
                                .setMessage("Failed to delete panel: " + e.getMessage())
                                .setPositiveButton("OK", (d, w) -> d.dismiss())
                                .show();
                    });
        });
    }

    private void savePanel(Panel existingPanel, String name, String venue) {
        Dialog loadingDialog = UIUtils.showLoadingDialog(this, existingPanel != null ? "Updating panel..." : "Creating panel...");

        if (existingPanel != null) {
            existingPanel.setName(name);
            existingPanel.setVenue(venue);
            firebaseManager.updatePanel(existingPanel)
                    .addOnSuccessListener(aVoid -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Panel updated successfully");
                        loadPanels();
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Failed to update panel: " + e.getMessage());
                    });
        } else {
            Panel newPanel = new Panel(null, eventId, name, venue, new ArrayList<>(), new ArrayList<>(), System.currentTimeMillis());
            firebaseManager.createPanel(newPanel)
                    .addOnSuccessListener(docRef -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Panel created successfully");
                        loadPanels();
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Failed to create panel: " + e.getMessage());
                    });
        }
    }

    @Override
    public void onPanelClick(Panel panel) {
        showAddEditPanelDialog(panel);
    }
}

