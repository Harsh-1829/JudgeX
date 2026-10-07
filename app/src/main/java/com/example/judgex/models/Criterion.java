package com.example.judgex.models;

import java.io.Serializable;

public class Criterion implements Serializable {
    private String id;
    private String eventId;
    private String name;
    private String description;
    private double maxMarks;
    private double weight; // Percentage weight (e.g. 25.0 for 25%)
    private int orderIndex;

    public Criterion() {
        // Required for Firestore
    }

    public Criterion(String id, String eventId, String name, String description, double maxMarks, double weight, int orderIndex) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.description = description;
        this.maxMarks = maxMarks;
        this.weight = weight > 0 ? weight : maxMarks;
        this.orderIndex = orderIndex;
    }

    public Criterion(String id, String eventId, String name, String description, double maxMarks, int orderIndex) {
        this(id, eventId, name, description, maxMarks, maxMarks, orderIndex);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(double maxMarks) {
        this.maxMarks = maxMarks;
    }

    public double getWeight() {
        return weight > 0 ? weight : maxMarks;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
