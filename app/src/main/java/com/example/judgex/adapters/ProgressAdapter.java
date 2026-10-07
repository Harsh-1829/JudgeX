package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.TeamResult;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Map;

public class ProgressAdapter extends RecyclerView.Adapter<ProgressAdapter.ProgressViewHolder> {

    private final List<TeamResult> teamResults;
    private final Map<String, String> panelNameMap; // panelId -> panelName

    public ProgressAdapter(List<TeamResult> teamResults, Map<String, String> panelNameMap) {
        this.teamResults = teamResults;
        this.panelNameMap = panelNameMap;
    }

    @NonNull
    @Override
    public ProgressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_progress, parent, false);
        return new ProgressViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProgressViewHolder holder, int position) {
        TeamResult result = teamResults.get(position);
        
        if (result.getTeam() != null) {
            holder.tvTeamNumber.setText(result.getTeam().getTeamNumber() != null ? result.getTeam().getTeamNumber() : "");
            holder.tvTeamName.setText(result.getTeam().getTeamName() != null ? result.getTeam().getTeamName() : "Team");
            
            String panelId = result.getTeam().getPanelId();
            if (panelId != null && panelNameMap != null && panelNameMap.containsKey(panelId)) {
                holder.tvPanelName.setText(panelNameMap.get(panelId));
            } else {
                holder.tvPanelName.setText("Unassigned Panel");
            }
        }

        int evaluated = result.getEvaluationsCount();
        int total = result.getTotalAssignedJudges();
        
        int percent = (total > 0) ? (int) Math.round(((double) evaluated / total) * 100) : (evaluated > 0 ? 100 : 0);
        if (percent > 100) percent = 100;

        holder.tvPercentage.setText(percent + "%");
        holder.progressBar.setProgress(percent);
        
        if (percent == 100 && total > 0) {
            holder.progressBar.setIndicatorColor(holder.itemView.getContext().getResources().getColor(R.color.success));
        } else {
            holder.progressBar.setIndicatorColor(holder.itemView.getContext().getResources().getColor(R.color.accent));
        }

        if (total > 0) {
            holder.tvJudgesProgress.setText(evaluated + " of " + total + " judges submitted");
        } else {
            holder.tvJudgesProgress.setText(evaluated + " submissions recorded (No judges assigned)");
        }
    }

    @Override
    public int getItemCount() {
        return teamResults.size();
    }

    static class ProgressViewHolder extends RecyclerView.ViewHolder {
        TextView tvTeamNumber, tvTeamName, tvPercentage, tvJudgesProgress, tvPanelName;
        LinearProgressIndicator progressBar;

        public ProgressViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTeamNumber = itemView.findViewById(R.id.tvTeamNumber);
            tvTeamName = itemView.findViewById(R.id.tvTeamName);
            tvPercentage = itemView.findViewById(R.id.tvPercentage);
            tvJudgesProgress = itemView.findViewById(R.id.tvJudgesProgress);
            tvPanelName = itemView.findViewById(R.id.tvPanelName);
            progressBar = itemView.findViewById(R.id.progressBar);
        }
    }
}
