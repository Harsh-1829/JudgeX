package com.example.judgex.adapters;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.Criterion;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluationCriteriaAdapter extends RecyclerView.Adapter<EvaluationCriteriaAdapter.EvalViewHolder> {

    public interface OnScoreChangedListener {
        void onScoreUpdated(Map<String, Double> currentScores, Map<String, String> comments, double totalWeightedScore, boolean allValid);
    }

    private final List<Criterion> criteriaList;
    private final Map<String, Double> scoresMap = new HashMap<>();
    private final Map<String, String> commentsMap = new HashMap<>();
    private final Map<String, Boolean> validityMap = new HashMap<>();
    private final OnScoreChangedListener scoreListener;

    public EvaluationCriteriaAdapter(List<Criterion> criteriaList, OnScoreChangedListener scoreListener) {
        this.criteriaList = criteriaList;
        this.scoreListener = scoreListener;
        for (Criterion c : criteriaList) {
            validityMap.put(c.getId(), false);
        }
    }

    public void setExistingData(Map<String, ?> existingScores, Map<String, String> existingComments) {
        if (existingScores != null) {
            for (Map.Entry<String, ?> entry : existingScores.entrySet()) {
                if (entry.getKey() != null && entry.getValue() instanceof Number) {
                    scoresMap.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                }
            }
            for (Criterion c : criteriaList) {
                if (scoresMap.containsKey(c.getId())) {
                    Double s = scoresMap.get(c.getId());
                    if (s != null && s >= 0 && s <= c.getMaxMarks()) {
                        validityMap.put(c.getId(), true);
                    }
                }
            }
        }
        if (existingComments != null) {
            commentsMap.putAll(existingComments);
        }
        notifyDataSetChanged();
        notifyScoreChange();
    }

    @NonNull
    @Override
    public EvalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_eval_criterion, parent, false);
        return new EvalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EvalViewHolder holder, int position) {
        Criterion criterion = criteriaList.get(position);
        holder.tvCriterionName.setText(criterion.getName());
        holder.tvCriterionDescription.setText(
                criterion.getDescription() != null && !criterion.getDescription().isEmpty()
                        ? criterion.getDescription()
                        : "Grade this parameter based on presentation quality."
        );
        holder.tvMaxMarksBadge.setText(String.format("Max: %.0f marks", criterion.getMaxMarks()));
        holder.tvWeightBadge.setText(String.format("Weight: %.0f%%", criterion.getWeight()));

        holder.tilScore.setHint(String.format("Award Score (0 - %.0f)", criterion.getMaxMarks()));
        holder.tilScore.setHelperText(String.format("Enter mark between 0.0 and %.0f", criterion.getMaxMarks()));
        holder.etScore.setHint(null);

        // Remove old watchers if recycling
        if (holder.scoreWatcher != null) {
            holder.etScore.removeTextChangedListener(holder.scoreWatcher);
        }
        if (holder.commentWatcher != null) {
            holder.etCriterionComment.removeTextChangedListener(holder.commentWatcher);
        }

        Double existingScore = scoresMap.get(criterion.getId());
        if (existingScore != null) {
            holder.etScore.setText(String.valueOf(existingScore));
        } else {
            holder.etScore.setText("");
        }

        String existingComment = commentsMap.get(criterion.getId());
        if (existingComment != null) {
            holder.etCriterionComment.setText(existingComment);
        } else {
            holder.etCriterionComment.setText("");
        }

        holder.scoreWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String input = s != null ? s.toString().trim() : "";
                if (input.isEmpty()) {
                    scoresMap.remove(criterion.getId());
                    validityMap.put(criterion.getId(), false);
                    holder.tilScore.setError("Score is required");
                } else {
                    try {
                        input = input.replace(',', '.');
                        double val = Double.parseDouble(input);
                        if (val < 0) {
                            validityMap.put(criterion.getId(), false);
                            holder.tilScore.setError("Score cannot be negative");
                        } else if (val > criterion.getMaxMarks()) {
                            validityMap.put(criterion.getId(), false);
                            holder.tilScore.setError(String.format("Cannot exceed max mark of %.0f", criterion.getMaxMarks()));
                        } else {
                            holder.tilScore.setError(null);
                            scoresMap.put(criterion.getId(), val);
                            validityMap.put(criterion.getId(), true);
                        }
                    } catch (NumberFormatException e) {
                        validityMap.put(criterion.getId(), false);
                        holder.tilScore.setError("Invalid numeric format");
                    }
                }

                notifyScoreChange();
            }
        };

        holder.commentWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String comment = s != null ? s.toString().trim() : "";
                if (!comment.isEmpty()) {
                    commentsMap.put(criterion.getId(), comment);
                } else {
                    commentsMap.remove(criterion.getId());
                }
                notifyScoreChange();
            }
        };

        holder.etScore.addTextChangedListener(holder.scoreWatcher);
        holder.etCriterionComment.addTextChangedListener(holder.commentWatcher);
    }

    private void notifyScoreChange() {
        if (scoreListener != null) {
            boolean allValid = isAllScoresValid();
            double totalWeighted = calculateTotalWeightedScore();
            scoreListener.onScoreUpdated(scoresMap, commentsMap, totalWeighted, allValid);
        }
    }

    public double calculateTotalWeightedScore() {
        double sum = 0.0;
        for (Criterion c : criteriaList) {
            Double score = scoresMap.get(c.getId());
            if (score != null && c.getMaxMarks() > 0) {
                // Task requirement formula: Criterion contribution = (score / maximum score) * criterion weight
                double contribution = (score / c.getMaxMarks()) * c.getWeight();
                sum += contribution;
            }
        }
        return sum;
    }

    public double calculateRawTotalScore() {
        double sum = 0.0;
        for (Criterion c : criteriaList) {
            Double score = scoresMap.get(c.getId());
            if (score != null) {
                sum += score;
            }
        }
        return sum;
    }

    public double calculateTotalPossibleMarks() {
        double sum = 0.0;
        for (Criterion c : criteriaList) {
            sum += c.getMaxMarks();
        }
        return sum > 0 ? sum : 100.0;
    }

    public static class CriteriaValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final int invalidPosition;
        private final String invalidCriterionName;

        public CriteriaValidationResult(boolean valid, String errorMessage, int invalidPosition, String invalidCriterionName) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.invalidPosition = invalidPosition;
            this.invalidCriterionName = invalidCriterionName;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getInvalidPosition() {
            return invalidPosition;
        }

        public String getInvalidCriterionName() {
            return invalidCriterionName;
        }
    }

    public CriteriaValidationResult validateCriteria() {
        if (criteriaList == null || criteriaList.isEmpty()) {
            return new CriteriaValidationResult(false, "No evaluation criteria defined for this event.", -1, null);
        }

        for (int i = 0; i < criteriaList.size(); i++) {
            Criterion c = criteriaList.get(i);
            String cid = c.getId();
            if (cid == null) {
                return new CriteriaValidationResult(false, "Criterion has an invalid configuration (missing ID).", i, c.getName());
            }

            if (!scoresMap.containsKey(cid)) {
                return new CriteriaValidationResult(false,
                        "Please award marks for criterion '" + c.getName() + "' (Allowed: 0.0 to " + String.format(java.util.Locale.US, "%.0f", c.getMaxMarks()) + " marks).",
                        i, c.getName());
            }

            Double score = scoresMap.get(cid);
            if (score == null) {
                return new CriteriaValidationResult(false,
                        "Please enter a valid numeric score for '" + c.getName() + "'.",
                        i, c.getName());
            }

            if (score < 0.0) {
                return new CriteriaValidationResult(false,
                        "Score for '" + c.getName() + "' cannot be negative.",
                        i, c.getName());
            }

            if (score > c.getMaxMarks()) {
                return new CriteriaValidationResult(false,
                        "Score of " + score + " for '" + c.getName() + "' exceeds maximum allowed marks of " + String.format(java.util.Locale.US, "%.0f", c.getMaxMarks()) + ".",
                        i, c.getName());
            }
        }

        return new CriteriaValidationResult(true, null, -1, null);
    }

    public void commitAllVisibleInputs(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder rawHolder = recyclerView.getChildViewHolder(child);
            if (rawHolder instanceof EvalViewHolder) {
                EvalViewHolder holder = (EvalViewHolder) rawHolder;
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0 && pos < criteriaList.size()) {
                    Criterion criterion = criteriaList.get(pos);
                    String input = holder.etScore.getText() != null ? holder.etScore.getText().toString().trim() : "";
                    if (!input.isEmpty()) {
                        try {
                            input = input.replace(',', '.');
                            double val = Double.parseDouble(input);
                            if (val >= 0 && val <= criterion.getMaxMarks()) {
                                scoresMap.put(criterion.getId(), val);
                                validityMap.put(criterion.getId(), true);
                                holder.tilScore.setError(null);
                            }
                        } catch (Exception ignored) {}
                    }
                    String comment = holder.etCriterionComment.getText() != null ? holder.etCriterionComment.getText().toString().trim() : "";
                    if (!comment.isEmpty()) {
                        commentsMap.put(criterion.getId(), comment);
                    }
                }
            }
        }
        notifyScoreChange();
    }

    public boolean isAllScoresValid() {
        return validateCriteria().isValid();
    }

    public Map<String, Double> getScoresMap() {
        return scoresMap;
    }

    public Map<String, String> getCommentsMap() {
        return commentsMap;
    }

    @Override
    public int getItemCount() {
        return criteriaList.size();
    }

    static class EvalViewHolder extends RecyclerView.ViewHolder {
        TextView tvCriterionName, tvCriterionDescription, tvMaxMarksBadge, tvWeightBadge;
        TextInputLayout tilScore, tilCriterionComment;
        TextInputEditText etScore, etCriterionComment;
        TextWatcher scoreWatcher, commentWatcher;

        public EvalViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCriterionName = itemView.findViewById(R.id.tvCriterionName);
            tvCriterionDescription = itemView.findViewById(R.id.tvCriterionDescription);
            tvMaxMarksBadge = itemView.findViewById(R.id.tvMaxMarksBadge);
            tvWeightBadge = itemView.findViewById(R.id.tvWeightBadge);
            tilScore = itemView.findViewById(R.id.tilScore);
            tilCriterionComment = itemView.findViewById(R.id.tilCriterionComment);
            etScore = itemView.findViewById(R.id.etScore);
            etCriterionComment = itemView.findViewById(R.id.etCriterionComment);
        }
    }
}
