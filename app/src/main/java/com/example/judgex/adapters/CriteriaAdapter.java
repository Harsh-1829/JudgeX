package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.Criterion;

import java.util.List;

public class CriteriaAdapter extends RecyclerView.Adapter<CriteriaAdapter.CriteriaViewHolder> {

    public interface OnCriterionClickListener {
        void onCriterionClick(Criterion criterion);
    }

    private final List<Criterion> criteriaList;
    private final OnCriterionClickListener listener;

    public CriteriaAdapter(List<Criterion> criteriaList, OnCriterionClickListener listener) {
        this.criteriaList = criteriaList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CriteriaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_criterion, parent, false);
        return new CriteriaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CriteriaViewHolder holder, int position) {
        Criterion criterion = criteriaList.get(position);
        holder.tvCriterionName.setText(criterion.getName());
        holder.tvCriterionDescription.setText(
                criterion.getDescription() != null && !criterion.getDescription().isEmpty() 
                        ? criterion.getDescription() 
                        : "No description provided."
        );
        holder.tvMaxMarks.setText(String.format("Max: %.0f pts", criterion.getMaxMarks()));
        holder.tvWeightBadge.setText(String.format("Weight: %.0f%%", criterion.getWeight()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCriterionClick(criterion);
        });
    }

    @Override
    public int getItemCount() {
        return criteriaList.size();
    }

    static class CriteriaViewHolder extends RecyclerView.ViewHolder {
        TextView tvCriterionName, tvCriterionDescription, tvMaxMarks, tvWeightBadge;

        public CriteriaViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCriterionName = itemView.findViewById(R.id.tvCriterionName);
            tvCriterionDescription = itemView.findViewById(R.id.tvCriterionDescription);
            tvMaxMarks = itemView.findViewById(R.id.tvMaxMarks);
            tvWeightBadge = itemView.findViewById(R.id.tvWeightBadge);
        }
    }
}
