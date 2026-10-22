package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.TeamMember;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    public interface OnMemberActionListener {
        void onEdit(TeamMember member, int position);
        void onDelete(TeamMember member, int position);
        void onSetLeader(TeamMember member, int position);
    }

    private final List<TeamMember> members;
    private final OnMemberActionListener listener;

    public MemberAdapter(List<TeamMember> members, OnMemberActionListener listener) {
        this.members = members;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        TeamMember member = members.get(position);
        holder.bind(member, position);
    }

    @Override
    public int getItemCount() {
        return members != null ? members.size() : 0;
    }

    class MemberViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMemberName, tvLeaderBadge, tvDeptAndYear, tvRollNo, tvEmail, tvPhone;
        private final MaterialButton btnSetLeader, btnEditMember, btnDeleteMember;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMemberName = itemView.findViewById(R.id.tvMemberName);
            tvLeaderBadge = itemView.findViewById(R.id.tvLeaderBadge);
            tvDeptAndYear = itemView.findViewById(R.id.tvDeptAndYear);
            tvRollNo = itemView.findViewById(R.id.tvRollNo);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            btnSetLeader = itemView.findViewById(R.id.btnSetLeader);
            btnEditMember = itemView.findViewById(R.id.btnEditMember);
            btnDeleteMember = itemView.findViewById(R.id.btnDeleteMember);
        }

        public void bind(TeamMember member, int position) {
            tvMemberName.setText(member.getName() != null ? member.getName() : "Member");

            if (member.isLeader()) {
                tvLeaderBadge.setVisibility(View.VISIBLE);
                btnSetLeader.setVisibility(View.GONE);
            } else {
                tvLeaderBadge.setVisibility(View.GONE);
                btnSetLeader.setVisibility(View.VISIBLE);
            }

            String dept = member.getDepartment() != null && !member.getDepartment().isEmpty() ? member.getDepartment() : "General";
            String year = member.getYearOfStudy() != null && !member.getYearOfStudy().isEmpty() ? member.getYearOfStudy() : "Undergraduate";
            tvDeptAndYear.setText(dept + " • " + year);

            tvRollNo.setText("Roll: " + (member.getRollNumber() != null ? member.getRollNumber() : "—"));
            tvEmail.setText(member.getEmail() != null ? member.getEmail() : "—");

            if (member.getPhone() != null && !member.getPhone().isEmpty()) {
                tvPhone.setVisibility(View.VISIBLE);
                tvPhone.setText("📞 " + member.getPhone());
            } else {
                tvPhone.setVisibility(View.GONE);
            }

            btnEditMember.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(member, position);
            });

            btnDeleteMember.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(member, position);
            });

            btnSetLeader.setOnClickListener(v -> {
                if (listener != null) listener.onSetLeader(member, position);
            });
        }
    }
}
