package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.Team;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Set;

public class TeamAdapter extends RecyclerView.Adapter<TeamAdapter.TeamViewHolder> {

    public interface OnTeamClickListener {
        void onTeamClick(Team team);
        void onQRClick(Team team);
    }

    private final List<Team> teamList;
    private final Set<String> evaluatedTeamIds; // teams evaluated and submitted
    private final Set<String> draftTeamIds; // teams with saved in-progress draft
    private final OnTeamClickListener listener;

    public TeamAdapter(List<Team> teamList, Set<String> evaluatedTeamIds, Set<String> draftTeamIds, OnTeamClickListener listener) {
        this.teamList = teamList;
        this.evaluatedTeamIds = evaluatedTeamIds;
        this.draftTeamIds = draftTeamIds;
        this.listener = listener;
    }

    public TeamAdapter(List<Team> teamList, Set<String> evaluatedTeamIds, OnTeamClickListener listener) {
        this(teamList, evaluatedTeamIds, null, listener);
    }

    @NonNull
    @Override
    public TeamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_team, parent, false);
        return new TeamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TeamViewHolder holder, int position) {
        Team team = teamList.get(position);
        holder.tvTeamNumber.setText(team.getTeamNumber() != null ? team.getTeamNumber() : "TM");
        holder.tvTeamName.setText(team.getTeamName() != null ? team.getTeamName() : "Team");
        holder.tvProjectTitle.setText(team.getProjectTitle() != null ? team.getProjectTitle() : "No Project Title");

        String leader = team.getLeaderName() != null ? team.getLeaderName() : "Leader";
        int members = team.getMemberCount() > 0 ? team.getMemberCount() : 1;
        holder.tvLeader.setText(leader + " (" + members + " members)");

        if (team.isUnregistered()) {
            holder.tvEvaluationStatus.setText("UNREGISTERED");
            holder.tvEvaluationStatus.setBackgroundResource(R.drawable.bg_badge_locked);
            holder.tvEvaluationStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.danger));
        } else if (evaluatedTeamIds != null && evaluatedTeamIds.contains(team.getId())) {
            holder.tvEvaluationStatus.setText("EVALUATED");
            holder.tvEvaluationStatus.setBackgroundResource(R.drawable.bg_badge_success);
            holder.tvEvaluationStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.success));
        } else if (draftTeamIds != null && draftTeamIds.contains(team.getId())) {
            holder.tvEvaluationStatus.setText("DRAFT");
            holder.tvEvaluationStatus.setBackgroundResource(R.drawable.bg_badge_pending);
            holder.tvEvaluationStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.warning));
        } else {
            holder.tvEvaluationStatus.setText("PENDING");
            holder.tvEvaluationStatus.setBackgroundResource(R.drawable.bg_badge_judge);
            holder.tvEvaluationStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.text_secondary));
        }

        holder.btnViewQR.setOnClickListener(v -> {
            if (listener != null) listener.onQRClick(team);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTeamClick(team);
        });
    }

    @Override
    public int getItemCount() {
        return teamList.size();
    }

    static class TeamViewHolder extends RecyclerView.ViewHolder {
        TextView tvTeamNumber, tvTeamName, tvEvaluationStatus, tvProjectTitle, tvLeader;
        MaterialButton btnViewQR;

        public TeamViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTeamNumber = itemView.findViewById(R.id.tvTeamNumber);
            tvTeamName = itemView.findViewById(R.id.tvTeamName);
            tvEvaluationStatus = itemView.findViewById(R.id.tvEvaluationStatus);
            tvProjectTitle = itemView.findViewById(R.id.tvProjectTitle);
            tvLeader = itemView.findViewById(R.id.tvLeader);
            btnViewQR = itemView.findViewById(R.id.btnViewQR);
        }
    }
}
