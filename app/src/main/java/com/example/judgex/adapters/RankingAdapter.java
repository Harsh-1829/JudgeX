package com.example.judgex.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.TeamResult;

import java.util.List;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.RankingViewHolder> {

    public interface OnRankingClickListener {
        void onTeamResultClick(TeamResult result);
    }

    private final List<TeamResult> results;
    private boolean isEventCompletedOrLocked;
    private boolean isAnonymousJudging;
    private final OnRankingClickListener listener;

    public RankingAdapter(List<TeamResult> results, boolean isEventCompletedOrLocked, boolean isAnonymousJudging, OnRankingClickListener listener) {
        this.results = results;
        this.isEventCompletedOrLocked = isEventCompletedOrLocked;
        this.isAnonymousJudging = isAnonymousJudging;
        this.listener = listener;
    }

    public void setEventCompletedOrLocked(boolean completedOrLocked) {
        this.isEventCompletedOrLocked = completedOrLocked;
    }

    public void setAnonymousJudging(boolean anonymousJudging) {
        this.isAnonymousJudging = anonymousJudging;
    }

    public RankingAdapter(List<TeamResult> results, boolean isEventCompletedOrLocked, OnRankingClickListener listener) {
        this(results, isEventCompletedOrLocked, false, listener);
    }

    public RankingAdapter(List<TeamResult> results, OnRankingClickListener listener) {
        this(results, false, false, listener);
    }

    @NonNull
    @Override
    public RankingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ranking, parent, false);
        return new RankingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RankingViewHolder holder, int position) {
        TeamResult item = results.get(position);
        
        holder.tvRank.setText("#" + item.getRank());
        if (item.getTeam() != null) {
            holder.tvTeamNumber.setText(item.getTeam().getTeamNumber() != null ? item.getTeam().getTeamNumber() : "");
            String displayName = isAnonymousJudging 
                    ? "Team #" + item.getTeam().getTeamNumber() 
                    : (item.getTeam().getTeamName() != null ? item.getTeam().getTeamName() : "Team");
            holder.tvTeamName.setText(displayName);
            holder.tvProjectTitle.setText(item.getTeam().getProjectTitle() != null ? item.getTeam().getProjectTitle() : "");
        }

        int count = item.getEvaluationsCount();
        int totalJudges = item.getTotalAssignedJudges();
        if (totalJudges > 0) {
            holder.tvEvaluationsCount.setText("📊 " + count + "/" + totalJudges + " Judges Evaluated");
        } else {
            holder.tvEvaluationsCount.setText("📊 " + count + " Evaluations");
        }

        if (count > 0) {
            holder.tvAverageScore.setText(String.format(java.util.Locale.US, "%.1f", item.getAverageScore()));
            holder.tvScoreSubtext.setText(String.format(java.util.Locale.US, "/ %.0f pts", item.getMaxPossibleScore()));
        } else {
            holder.tvAverageScore.setText("—");
            holder.tvScoreSubtext.setText("No evals");
        }

        // Provisional vs Final Result Indicator
        if (count > 0 && (isEventCompletedOrLocked || (totalJudges > 0 && count >= totalJudges))) {
            holder.tvProvisionalBadge.setText("FINAL");
            holder.tvProvisionalBadge.setBackgroundResource(R.drawable.bg_badge_success);
            holder.tvProvisionalBadge.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.success));
        } else if (count > 0) {
            holder.tvProvisionalBadge.setText("PROVISIONAL");
            holder.tvProvisionalBadge.setBackgroundResource(R.drawable.bg_badge_pending);
            holder.tvProvisionalBadge.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.warning));
        } else {
            holder.tvProvisionalBadge.setText(isEventCompletedOrLocked ? "UNRANKED" : "PENDING");
            holder.tvProvisionalBadge.setBackgroundResource(R.drawable.bg_badge_locked);
            holder.tvProvisionalBadge.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
        }

        // Custom medal styling for top 3 ranks
        if (item.getRank() == 1 && count > 0) {
            setRankBadge(holder.tvRank, "#FEF08A", "#854D0E"); // Gold
        } else if (item.getRank() == 2 && count > 0) {
            setRankBadge(holder.tvRank, "#E2E8F0", "#334155"); // Silver
        } else if (item.getRank() == 3 && count > 0) {
            setRankBadge(holder.tvRank, "#FFEDD5", "#9A3412"); // Bronze
        } else {
            setRankBadge(holder.tvRank, "#F1F5F9", "#64748B"); // Default
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTeamResultClick(item);
        });
    }

    private void setRankBadge(TextView textView, String bgColorHex, String textColorHex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor(bgColorHex));
        textView.setBackground(drawable);
        textView.setTextColor(Color.parseColor(textColorHex));
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class RankingViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvTeamNumber, tvTeamName, tvProjectTitle, tvEvaluationsCount, tvAverageScore, tvScoreSubtext, tvProvisionalBadge;

        public RankingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvTeamNumber = itemView.findViewById(R.id.tvTeamNumber);
            tvTeamName = itemView.findViewById(R.id.tvTeamName);
            tvProjectTitle = itemView.findViewById(R.id.tvProjectTitle);
            tvEvaluationsCount = itemView.findViewById(R.id.tvEvaluationsCount);
            tvAverageScore = itemView.findViewById(R.id.tvAverageScore);
            tvScoreSubtext = itemView.findViewById(R.id.tvScoreSubtext);
            tvProvisionalBadge = itemView.findViewById(R.id.tvProvisionalBadge);
        }
    }
}
