package com.example.judgex.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.judgex.R;
import com.example.judgex.models.Event;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventSelected(Event event);
        void onEventItemClick(Event event);
        void onEventStatusClick(Event event);
        void onEventDeleteClick(Event event);
    }

    private final List<Event> eventList;
    private final String activeEventId;
    private final OnEventClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    public EventAdapter(List<Event> eventList, String activeEventId, OnEventClickListener listener) {
        this.eventList = eventList;
        this.activeEventId = activeEventId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = eventList.get(position);
        holder.tvEventName.setText(event.getName());
        holder.tvEventDescription.setText(event.getDescription());
        
        String status = event.getStatus() != null ? event.getStatus() : Event.STATUS_ACTIVE;
        holder.tvEventStatus.setText(status);
        if (Event.STATUS_COMPLETED.equalsIgnoreCase(status)) {
            holder.tvEventStatus.setBackgroundResource(R.drawable.bg_badge_admin);
            holder.tvEventStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.accent));
        } else if (Event.STATUS_ARCHIVED.equalsIgnoreCase(status)) {
            holder.tvEventStatus.setBackgroundResource(R.drawable.bg_badge_pending);
            holder.tvEventStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.text_secondary));
        } else if (Event.STATUS_UPCOMING.equalsIgnoreCase(status)) {
            holder.tvEventStatus.setBackgroundResource(R.drawable.bg_badge_pending);
            holder.tvEventStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.warning));
        } else {
            holder.tvEventStatus.setBackgroundResource(R.drawable.bg_badge_success);
            holder.tvEventStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.success));
        }

        if (event.isLocked()) {
            holder.tvEventLockedBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvEventLockedBadge.setVisibility(View.GONE);
        }

        // Format schedule and venue line
        StringBuilder scheduleVenue = new StringBuilder();
        if (event.getScheduledDate() > 0) {
            scheduleVenue.append("📅 ").append(dateFormat.format(new Date(event.getScheduledDate())));
        }
        if (event.getStartTime() != null && !event.getStartTime().isEmpty()) {
            scheduleVenue.append(" • ").append(event.getStartTime());
            if (event.getEndTime() != null && !event.getEndTime().isEmpty()) {
                scheduleVenue.append(" - ").append(event.getEndTime());
            }
        }
        if (event.getVenue() != null && !event.getVenue().isEmpty()) {
            scheduleVenue.append(" • 📍 ").append(event.getVenue());
        }

        if (scheduleVenue.length() > 0) {
            holder.tvEventScheduleVenue.setText(scheduleVenue.toString());
            holder.tvEventScheduleVenue.setVisibility(View.VISIBLE);
        } else {
            holder.tvEventScheduleVenue.setVisibility(View.GONE);
        }

        holder.btnSelectEvent.setOnClickListener(v -> {
            if (listener != null) listener.onEventSelected(event);
        });

        holder.btnStatusAction.setOnClickListener(v -> {
            if (listener != null) listener.onEventStatusClick(event);
        });

        holder.btnDeleteEvent.setOnClickListener(v -> {
            if (listener != null) listener.onEventDeleteClick(event);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEventItemClick(event);
        });
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventName, tvEventDescription, tvEventStatus, tvEventLockedBadge, tvEventScheduleVenue;
        MaterialButton btnSelectEvent, btnStatusAction, btnDeleteEvent;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            tvEventDescription = itemView.findViewById(R.id.tvEventDescription);
            tvEventStatus = itemView.findViewById(R.id.tvEventStatus);
            tvEventLockedBadge = itemView.findViewById(R.id.tvEventLockedBadge);
            tvEventScheduleVenue = itemView.findViewById(R.id.tvEventScheduleVenue);
            btnSelectEvent = itemView.findViewById(R.id.btnSelectEvent);
            btnStatusAction = itemView.findViewById(R.id.btnStatusAction);
            btnDeleteEvent = itemView.findViewById(R.id.btnDeleteEvent);
        }
    }
}
