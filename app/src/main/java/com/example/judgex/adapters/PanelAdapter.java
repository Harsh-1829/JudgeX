package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.Panel;

import java.util.List;

public class PanelAdapter extends RecyclerView.Adapter<PanelAdapter.PanelViewHolder> {

    public interface OnPanelClickListener {
        void onPanelClick(Panel panel);
    }

    private final List<Panel> panelList;
    private final OnPanelClickListener listener;

    public PanelAdapter(List<Panel> panelList, OnPanelClickListener listener) {
        this.panelList = panelList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PanelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_panel, parent, false);
        return new PanelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PanelViewHolder holder, int position) {
        Panel panel = panelList.get(position);
        holder.tvPanelName.setText(panel.getName());
        holder.tvVenue.setText(panel.getVenue() != null && !panel.getVenue().isEmpty() ? panel.getVenue() : "Venue N/A");
        
        int judgeCount = panel.getJudgeIds() != null ? panel.getJudgeIds().size() : 0;
        int teamCount = panel.getTeamIds() != null ? panel.getTeamIds().size() : 0;

        holder.tvJudgeCount.setText("👥 " + judgeCount + " Judges");
        holder.tvTeamCount.setText("📁 " + teamCount + " Teams");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPanelClick(panel);
        });
    }

    @Override
    public int getItemCount() {
        return panelList.size();
    }

    static class PanelViewHolder extends RecyclerView.ViewHolder {
        TextView tvPanelName, tvVenue, tvJudgeCount, tvTeamCount;

        public PanelViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPanelName = itemView.findViewById(R.id.tvPanelName);
            tvVenue = itemView.findViewById(R.id.tvVenue);
            tvJudgeCount = itemView.findViewById(R.id.tvJudgeCount);
            tvTeamCount = itemView.findViewById(R.id.tvTeamCount);
        }
    }
}
