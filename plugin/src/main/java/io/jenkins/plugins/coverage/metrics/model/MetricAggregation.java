package io.jenkins.plugins.coverage.metrics.model;

import edu.hm.hafner.coverage.Metric;

/**
 * Defines the aggregation mode for software metrics that can be aggregated in different ways (e.g., cyclomatic
 * complexity can be reported as total, maximum, or average). For coverage metrics, this aggregation is not applicable
 * and will be ignored.
 *
 * @author Akash Manna
 */
public enum MetricAggregation {
    /** The total value of the metric (sum of all values). */
    TOTAL,
    /** The maximum value of the metric. */
    MAXIMUM,
    /** The average value of the metric. */
    AVERAGE;

    /**
     * Returns whether the specified metric supports aggregation modes.
     *
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if the metric supports aggregation modes, {@code false} otherwise
     */
    public static boolean isSupported(final Metric metric) {
        return !metric.isCoverage();
    }

    /**
     * Returns the default aggregation mode for the specified metric.
     *
     * @param metric
     *         the metric to get the default aggregation for
     *
     * @return the default aggregation mode
     */
    public static MetricAggregation getDefault(final Metric metric) {
        return TOTAL;
    }
}
