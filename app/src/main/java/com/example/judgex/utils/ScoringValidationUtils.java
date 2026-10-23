package com.example.judgex.utils;

import com.example.judgex.models.Criterion;
import com.example.judgex.models.Event;

import java.util.List;
import java.util.Locale;

/**
 * Utility for validating and formatting event evaluation criteria weights.
 *
 * Guarantees that for events using weighted scoring, criteria weights must total exactly 100%
 * (accounting safely for standard floating-point precision, e.g. 33.33% + 33.33% + 33.34% = 100%).
 */
public final class ScoringValidationUtils {

    public static final double TARGET_TOTAL_WEIGHT = 100.0;
    public static final double WEIGHT_TOLERANCE = 0.05;

    private ScoringValidationUtils() {
        // Utility class
    }

    /**
     * Calculates the sum of all criterion weights for the provided criteria list.
     */
    public static double calculateTotalWeight(List<Criterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Criterion c : criteria) {
            if (c != null) {
                total += c.getWeight();
            }
        }
        return total;
    }

    /**
     * Validates whether criteria total weight satisfies the 100% requirement
     * using a safe floating-point tolerance (accepts 33.33 + 33.33 + 33.34 = 100.0).
     */
    public static boolean isTotalWeightValid(List<Criterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return false;
        }
        double total = calculateTotalWeight(criteria);
        return isWeightValid(total);
    }

    /**
     * Validates if a raw total weight is within tolerance of 100.0%.
     */
    public static boolean isWeightValid(double totalWeight) {
        return Math.abs(totalWeight - TARGET_TOTAL_WEIGHT) <= WEIGHT_TOLERANCE;
    }

    /**
     * Determines whether an event uses weighted scoring (default).
     */
    public static boolean isWeightedScoring(Event event) {
        if (event == null) {
            return true;
        }
        return !Event.SCORING_RAW.equalsIgnoreCase(event.getScoringType());
    }

    /**
     * Formats weight cleanly with decimals only when necessary.
     */
    public static String formatWeight(double weight) {
        if (Math.abs(weight - Math.round(weight)) < 0.001) {
            return String.format(Locale.US, "%.0f%%", weight);
        } else {
            return String.format(Locale.US, "%.2f%%", weight);
        }
    }

    public static String getInvalidWeightMessage(double totalWeight) {
        return "Evaluation criteria weights must total exactly 100%. Current total is " + formatWeight(totalWeight) + ".";
    }
}
