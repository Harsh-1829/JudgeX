package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.Event;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LeaderEventAdapter extends RecyclerView.Adapter<LeaderEventAdapter.EventViewHolder> {

    public interface OnEventListener {
        void onEventClick(Event event);
    }

    private final List<Event> events;
    private final Set<String> registeredEventIds;
    private final OnEventListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    public LeaderEventAdapter(List<Event> events, Set<String> registeredEventIds, OnEventListener listener) {
        this.events = events;
        this.registeredEventIds = registeredEventIds;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leader_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return events != null ? events.size() : 0;
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCategoryBadge, tvRegStatusBadge, tvEventName, tvOrganizingDept;
        private final TextView tvDescription, tvDateTimeVenue, tvTeamSizeInfo, tvDeadline;
        private final MaterialButton btnViewEvent;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryBadge = itemView.findViewById(R.id.tvCategoryBadge);
            tvRegStatusBadge = itemView.findViewById(R.id.tvRegStatusBadge);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            tvOrganizingDept = itemView.findViewById(R.id.tvOrganizingDept);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvDateTimeVenue = itemView.findViewById(R.id.tvDateTimeVenue);
            tvTeamSizeInfo = itemView.findViewById(R.id.tvTeamSizeInfo);
            tvDeadline = itemView.findViewById(R.id.tvDeadline);
            btnViewEvent = itemView.findViewById(R.id.btnViewEvent);
        }

        public void bind(Event event) {
            tvEventName.setText(event.getName() != null ? event.getName() : "Event");
            tvCategoryBadge.setText(event.getCategory() != null ? event.getCategory().toUpperCase(Locale.US) : "COMPETITION");
            tvOrganizingDept.setText(event.getDepartment() != null ? event.getDepartment() : "General Department");
            tvDescription.setText(event.getDescription() != null ? event.getDescription() : "No detailed description provided.");

            // Date, Time & Venue
            String dateStr = event.getScheduledDate() > 0 ? dateFormat.format(new Date(event.getScheduledDate())) : "TBD";
            String venueStr = event.getVenue() != null && !event.getVenue().isEmpty() ? event.getVenue() : "Campus Venue";
            tvDateTimeVenue.setText(dateStr + " • " + venueStr);

            // Participation
            if ("INDIVIDUAL".equalsIgnoreCase(event.getParticipationType())) {
                tvTeamSizeInfo.setText("👤 Individual");
            } else {
                tvTeamSizeInfo.setText("👥 Team (" + event.getMinTeamSize() + "-" + event.getMaxTeamSize() + " members)");
            }

            // Deadline
            if (event.getRegistrationDeadline() > 0) {
                tvDeadline.setText("Deadline: " + dateFormat.format(new Date(event.getRegistrationDeadline())));
            } else {
                tvDeadline.setText("Registration: Open till event");
            }

            // Registration Status Badge
            boolean isRegistered = registeredEventIds != null && registeredEventIds.contains(event.getId());
            boolean isOpen = event.isRegistrationOpen();

            if (isRegistered) {
                tvRegStatusBadge.setText("✓ REGISTERED");
                tvRegStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                tvRegStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.accent));
                btnViewEvent.setText("View Registration");
            } else if (isOpen) {
                tvRegStatusBadge.setText("REGISTRATION OPEN");
                tvRegStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                tvRegStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
                btnViewEvent.setText("View & Register");
            } else if (event.isLocked() || event.isCompleted()) {
                tvRegStatusBadge.setText("CONCLUDED");
                tvRegStatusBadge.setBackgroundResource(R.drawable.bg_badge_locked);
                tvRegStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
                btnViewEvent.setText("View Details");
            } else {
                tvRegStatusBadge.setText("CLOSED");
                tvRegStatusBadge.setBackgroundResource(R.drawable.bg_badge_locked);
                tvRegStatusBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.danger));
                btnViewEvent.setText("View Details");
            }

            View.OnClickListener clickAction = v -> {
                if (listener != null) {
                    listener.onEventClick(event);
                }
            };

            itemView.setOnClickListener(clickAction);
            btnViewEvent.setOnClickListener(clickAction);
        }
    }
}
