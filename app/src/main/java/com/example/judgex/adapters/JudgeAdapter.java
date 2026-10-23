package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.Panel;
import com.example.judgex.models.User;
import com.example.judgex.utils.Constants;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JudgeAdapter extends RecyclerView.Adapter<JudgeAdapter.JudgeViewHolder> {

    public interface OnJudgeClickListener {
        void onJudgeClick(User judge);
    }

    public interface OnJudgeSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private final List<User> judgeList;
    private final Map<String, Panel> panelMap; // panelId -> Panel
    private final String eventId;
    private final OnJudgeClickListener listener;
    private OnJudgeSelectionChangedListener selectionListener;
    private final Set<String> selectedJudgeUids = new HashSet<>();

    public JudgeAdapter(List<User> judgeList, Map<String, Panel> panelMap, String eventId, OnJudgeClickListener listener) {
        this.judgeList = judgeList;
        this.panelMap = panelMap;
        this.eventId = eventId;
        this.listener = listener;
    }

    public void setSelectionListener(OnJudgeSelectionChangedListener selectionListener) {
        this.selectionListener = selectionListener;
    }

    public Set<String> getSelectedJudgeUids() {
        return selectedJudgeUids;
    }

    public List<User> getSelectedJudges() {
        List<User> selected = new ArrayList<>();
        for (User u : judgeList) {
            if (selectedJudgeUids.contains(u.getUid())) {
                selected.add(u);
            }
        }
        return selected;
    }

    public void selectAll() {
        selectedJudgeUids.clear();
        for (User u : judgeList) {
            if (u.getUid() != null) {
                selectedJudgeUids.add(u.getUid());
            }
        }
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedJudgeUids.size());
    }

    public void clearSelection() {
        selectedJudgeUids.clear();
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(0);
    }

    public void toggleSelection(String uid) {
        if (uid == null) return;
        if (selectedJudgeUids.contains(uid)) {
            selectedJudgeUids.remove(uid);
        } else {
            selectedJudgeUids.add(uid);
        }
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedJudgeUids.size());
    }

    @NonNull
    @Override
    public JudgeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_judge, parent, false);
        return new JudgeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JudgeViewHolder holder, int position) {
        User judge = judgeList.get(position);
        holder.tvJudgeName.setText(judge.getName() != null ? judge.getName() : "Judge");
        if (judge.getUsername() != null && !judge.getUsername().isEmpty()) {
            holder.tvJudgeEmail.setText("Username: " + judge.getUsername());
        } else if (Constants.isSyntheticEmail(judge.getEmail())) {
            holder.tvJudgeEmail.setText("Username: " + Constants.extractUsernameFromSyntheticEmail(judge.getEmail()));
        } else {
            holder.tvJudgeEmail.setText(judge.getEmail() != null ? judge.getEmail() : "");
        }

        String initial = judge.getName() != null && !judge.getName().isEmpty()
                ? String.valueOf(judge.getName().charAt(0)).toUpperCase()
                : "J";
        holder.tvJudgeAvatar.setText(initial);

        String panelId = eventId != null ? judge.getPanelForEvent(eventId) : judge.getAssignedPanelId();
        if (panelId != null && !panelId.isEmpty() && panelMap != null && panelMap.containsKey(panelId)) {
            Panel panel = panelMap.get(panelId);
            holder.tvJudgePanelBadge.setText(panel != null ? panel.getName() : "Assigned");
            holder.tvJudgePanelBadge.setVisibility(View.VISIBLE);
        } else if (panelId != null && !panelId.isEmpty()) {
            holder.tvJudgePanelBadge.setText("Assigned");
            holder.tvJudgePanelBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvJudgePanelBadge.setText("Unassigned");
            holder.tvJudgePanelBadge.setVisibility(View.VISIBLE);
        }

        boolean isSelected = judge.getUid() != null && selectedJudgeUids.contains(judge.getUid());
        holder.cbSelectJudge.setOnCheckedChangeListener(null);
        holder.cbSelectJudge.setChecked(isSelected);

        holder.cbSelectJudge.setOnClickListener(v -> toggleSelection(judge.getUid()));

        holder.itemView.setOnClickListener(v -> {
            if (!selectedJudgeUids.isEmpty()) {
                toggleSelection(judge.getUid());
            } else if (listener != null) {
                listener.onJudgeClick(judge);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(judge.getUid());
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return judgeList.size();
    }

    static class JudgeViewHolder extends RecyclerView.ViewHolder {
        MaterialCheckBox cbSelectJudge;
        TextView tvJudgeAvatar, tvJudgeName, tvJudgeEmail, tvJudgePanelBadge;

        public JudgeViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelectJudge = itemView.findViewById(R.id.cbSelectJudge);
            tvJudgeAvatar = itemView.findViewById(R.id.tvJudgeAvatar);
            tvJudgeName = itemView.findViewById(R.id.tvJudgeName);
            tvJudgeEmail = itemView.findViewById(R.id.tvJudgeEmail);
            tvJudgePanelBadge = itemView.findViewById(R.id.tvJudgePanelBadge);
        }
    }
}
