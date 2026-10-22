package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.EventRegistration;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RegistrationAdapter extends RecyclerView.Adapter<RegistrationAdapter.RegistrationViewHolder> {

    public interface OnRegistrationClickListener {
        void onRegistrationClick(EventRegistration registration);
    }

    private final List<EventRegistration> registrations;
    private final OnRegistrationClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    public RegistrationAdapter(List<EventRegistration> registrations, OnRegistrationClickListener listener) {
        this.registrations = registrations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RegistrationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_registration, parent, false);
        return new RegistrationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RegistrationViewHolder holder, int position) {
        EventRegistration registration = registrations.get(position);
        holder.bind(registration);
    }

    @Override
    public int getItemCount() {
        return registrations != null ? registrations.size() : 0;
    }

    class RegistrationViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvRegistrationId, tvStatusBadge, tvEventName, tvTeamAndProject;
        private final TextView tvSourceBadge, tvDate;
        private final MaterialButton btnViewDetails;

        public RegistrationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRegistrationId = itemView.findViewById(R.id.tvRegistrationId);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            tvTeamAndProject = itemView.findViewById(R.id.tvTeamAndProject);
            tvSourceBadge = itemView.findViewById(R.id.tvSourceBadge);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
        }

        public void bind(EventRegistration reg) {
            tvRegistrationId.setText(reg.getId());
            tvEventName.setText(reg.getEventName());

            String proj = reg.getProjectTitle() != null && !reg.getProjectTitle().isEmpty() ? " • " + reg.getProjectTitle() : "";
            tvTeamAndProject.setText("Team: " + reg.getTeamName() + " (" + reg.getTeamNumber() + ")" + proj);

            String src = EventRegistration.SOURCE_ADMIN.equals(reg.getRegistrationSource())
                    ? "Source: College Admin" : "Source: Team Leader";
            tvSourceBadge.setText(src);

            tvDate.setText(dateFormat.format(new Date(reg.getCreatedAt())));

            // Status Badge
            String status = reg.getStatus();
            if (reg.isConfirmed()) {
                tvStatusBadge.setText("CONFIRMED");
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
            } else if (reg.isPending()) {
                tvStatusBadge.setText("PENDING");
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.warning));
            } else if (reg.isCancelled()) {
                tvStatusBadge.setText("CANCELLED");
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_locked);
                tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            } else if (reg.isRejected()) {
                tvStatusBadge.setText("REJECTED");
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_locked);
                tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.danger));
            } else {
                tvStatusBadge.setText(status != null ? status : "REGISTERED");
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.accent));
            }

            View.OnClickListener clickAction = v -> {
                if (listener != null) listener.onRegistrationClick(reg);
            };

            itemView.setOnClickListener(clickAction);
            btnViewDetails.setOnClickListener(clickAction);
        }
    }
}
