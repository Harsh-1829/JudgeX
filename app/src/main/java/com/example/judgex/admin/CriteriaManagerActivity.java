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
import com.example.judgex.adapters.CriteriaAdapter;
import com.example.judgex.data.FirebaseManager;
import com.example.judgex.models.Criterion;
import com.example.judgex.models.Event;
import com.example.judgex.utils.Constants;
import com.example.judgex.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class CriteriaManagerActivity extends AppCompatActivity implements CriteriaAdapter.OnCriterionClickListener {

    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewCriteria;
    private TextView tvEmptyState, tvTotalMaxMarks;
    private ExtendedFloatingActionButton fabAddCriterion;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private final List<Criterion> criteriaList = new ArrayList<>();
    private CriteriaAdapter adapter;
    private Event currentEvent;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_criteria_manager);

        currentEvent = (Event) getIntent().getSerializableExtra(Constants.EXTRA_EVENT);
        eventId = getIntent().getStringExtra(Constants.EXTRA_EVENT_ID);
        if (currentEvent != null && eventId == null) {
            eventId = currentEvent.getId();
        }

        initViews();
        loadCriteria();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewCriteria = findViewById(R.id.recyclerViewCriteria);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvTotalMaxMarks = findViewById(R.id.tvTotalMaxMarks);
        fabAddCriterion = findViewById(R.id.fabAddCriterion);

        if (currentEvent != null) {
            toolbar.setSubtitle(currentEvent.getName());
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewCriteria.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CriteriaAdapter(criteriaList, this);
        recyclerViewCriteria.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadCriteria);
        fabAddCriterion.setOnClickListener(v -> showAddEditCriterionDialog(null));
    }

    private void loadCriteria() {
        if (eventId == null) {
            UIUtils.showToast(this, "No active event specified.");
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        firebaseManager.getCriteriaForEvent(eventId)
                .addOnSuccessListener(querySnapshot -> {
                    criteriaList.clear();
                    double totalWeight = 0.0;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Criterion criterion = doc.toObject(Criterion.class);
                        if (criterion != null) {
                            criterion.setId(doc.getId());
                            criteriaList.add(criterion);
                            totalWeight += criterion.getWeight();
                        }
                    }

                    tvTotalMaxMarks.setText(String.format("Total Weight: %.0f%% (%d criteria)", totalWeight, criteriaList.size()));

                    if (criteriaList.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerViewCriteria.setVisibility(View.GONE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                        recyclerViewCriteria.setVisibility(View.VISIBLE);
                    }

                    adapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    UIUtils.showToast(this, "Failed to load criteria: " + e.getMessage());
                });
    }

    private void showAddEditCriterionDialog(Criterion existingCriterion) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_criterion, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        TextInputLayout tilCriterionName = view.findViewById(R.id.tilCriterionName);
        TextInputLayout tilCriterionDescription = view.findViewById(R.id.tilCriterionDescription);
        TextInputLayout tilMaxMarks = view.findViewById(R.id.tilMaxMarks);
        TextInputLayout tilWeight = view.findViewById(R.id.tilWeight);
        TextInputEditText etCriterionName = view.findViewById(R.id.etCriterionName);
        TextInputEditText etCriterionDescription = view.findViewById(R.id.etCriterionDescription);
        TextInputEditText etMaxMarks = view.findViewById(R.id.etMaxMarks);
        TextInputEditText etWeight = view.findViewById(R.id.etWeight);

        if (existingCriterion != null) {
            tvDialogTitle.setText("Edit Criterion");
            etCriterionName.setText(existingCriterion.getName());
            etCriterionDescription.setText(existingCriterion.getDescription());
            etMaxMarks.setText(String.format("%.0f", existingCriterion.getMaxMarks()));
            etWeight.setText(String.format("%.0f", existingCriterion.getWeight()));
        }

        builder.setView(view);
        builder.setPositiveButton(existingCriterion != null ? "Update" : "Save", null);
        if (existingCriterion != null) {
            builder.setNeutralButton("Delete", (dialog, which) -> confirmDeleteCriterion(existingCriterion));
        }
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etCriterionName.getText() != null ? etCriterionName.getText().toString().trim() : "";
                String desc = etCriterionDescription.getText() != null ? etCriterionDescription.getText().toString().trim() : "";
                String marksStr = etMaxMarks.getText() != null ? etMaxMarks.getText().toString().trim() : "10";
                String weightStr = etWeight.getText() != null ? etWeight.getText().toString().trim() : "25";

                if (TextUtils.isEmpty(name)) {
                    tilCriterionName.setError("Criterion name is required");
                    return;
                }

                double maxMarks = 10.0;
                double weight = 25.0;
                try {
                    maxMarks = Double.parseDouble(marksStr);
                    if (maxMarks <= 0) {
                        tilMaxMarks.setError("Max marks must be greater than 0");
                        return;
                    }
                } catch (NumberFormatException e) {
                    tilMaxMarks.setError("Invalid number");
                    return;
                }

                try {
                    weight = Double.parseDouble(weightStr);
                    if (weight <= 0) {
                        tilWeight.setError("Weight must be greater than 0");
                        return;
                    }
                } catch (NumberFormatException e) {
                    tilWeight.setError("Invalid number");
                    return;
                }

                dialog.dismiss();
                saveCriterion(existingCriterion, name, desc, maxMarks, weight);
            });
        });
        dialog.show();
    }

    private void saveCriterion(Criterion existingCriterion, String name, String description, double maxMarks, double weight) {
        Dialog loadingDialog = UIUtils.showLoadingDialog(this, existingCriterion != null ? "Updating criterion..." : "Adding criterion...");

        if (existingCriterion != null) {
            existingCriterion.setName(name);
            existingCriterion.setDescription(description);
            existingCriterion.setMaxMarks(maxMarks);
            existingCriterion.setWeight(weight);

            firebaseManager.updateCriterion(existingCriterion)
                    .addOnSuccessListener(aVoid -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Criterion updated successfully");
                        loadCriteria();
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Failed to update criterion: " + e.getMessage());
                    });
        } else {
            Criterion newCriterion = new Criterion(null, eventId, name, description, maxMarks, weight, criteriaList.size());
            firebaseManager.createCriterion(newCriterion)
                    .addOnSuccessListener(docRef -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Criterion added: " + name);
                        loadCriteria();
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        UIUtils.showToast(this, "Failed to add criterion: " + e.getMessage());
                    });
        }
    }

    private void confirmDeleteCriterion(Criterion criterion) {
        UIUtils.showConfirmationDialog(this, "Delete Criterion",
                "Are you sure you want to delete '" + criterion.getName() + "'?", "Delete", () -> {
                    Dialog loading = UIUtils.showLoadingDialog(this, "Deleting criterion...");
                    firebaseManager.deleteCriterion(criterion.getId())
                            .addOnSuccessListener(aVoid -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Criterion deleted");
                                loadCriteria();
                            })
                            .addOnFailureListener(e -> {
                                loading.dismiss();
                                UIUtils.showToast(this, "Failed to delete: " + e.getMessage());
                            });
                });
    }

    @Override
    public void onCriterionClick(Criterion criterion) {
        showAddEditCriterionDialog(criterion);
    }
}
