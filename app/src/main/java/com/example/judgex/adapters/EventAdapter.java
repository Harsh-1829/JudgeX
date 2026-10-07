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

import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventSelected(Event event);
        void onEventItemClick(Event event);
        void onEventStatusClick(Event event);
    }

    private final List<Event> eventList;
    private final String activeEventId;
    private final OnEventClickListener listener;

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
        
        boolean isActive = event.getId() != null && event.getId().equals(activeEventId);
        String status = event.getStatus() != null ? event.getStatus() : Event.STATUS_ACTIVE;
        
        holder.tvEventStatus.setText(status);
        if (Event.STATUS_COMPLETED.equalsIgnoreCase(status)) {
            holder.tvEventStatus.setBackgroundResource(R.drawable.bg_badge_admin);
            holder.tvEventStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.accent));
        } else if (Event.STATUS_ARCHIVED.equalsIgnoreCase(status)) {
            holder.tvEventStatus.setBackgroundResource(R.drawable.bg_badge_pending);
            holder.tvEventStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.text_secondary));
        } else {
            holder.tvEventStatus.setBackgroundResource(R.drawable.bg_badge_success);
            holder.tvEventStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.success));
        }

        if (event.isLocked()) {
            holder.tvEventLockedBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvEventLockedBadge.setVisibility(View.GONE);
        }

        if (isActive) {
            holder.btnSelectEvent.setText("Selected");
            holder.btnSelectEvent.setEnabled(false);
        } else {
            holder.btnSelectEvent.setText("Set Active");
            holder.btnSelectEvent.setEnabled(true);
        }

        holder.btnSelectEvent.setOnClickListener(v -> {
            if (listener != null) listener.onEventSelected(event);
        });

        holder.btnStatusAction.setOnClickListener(v -> {
            if (listener != null) listener.onEventStatusClick(event);
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
        TextView tvEventName, tvEventDescription, tvEventStatus, tvEventLockedBadge;
        MaterialButton btnSelectEvent, btnStatusAction;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            tvEventDescription = itemView.findViewById(R.id.tvEventDescription);
            tvEventStatus = itemView.findViewById(R.id.tvEventStatus);
            tvEventLockedBadge = itemView.findViewById(R.id.tvEventLockedBadge);
            btnSelectEvent = itemView.findViewById(R.id.btnSelectEvent);
            btnStatusAction = itemView.findViewById(R.id.btnStatusAction);
        }
    }
}
