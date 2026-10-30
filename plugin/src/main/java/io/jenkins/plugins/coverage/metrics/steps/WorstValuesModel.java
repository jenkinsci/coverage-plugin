package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Metric.MetricTendency;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.Value;
import hudson.Functions;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import java.util.Optional;

/**
 * Provides the worst values of the software metrics for the "Further metrics" table of the {@link OverviewModel}. The
 * worst value of a metric is the maximum (for metrics where smaller values are better, e.g., the cyclomatic
 * complexity) or the minimum (for metrics where larger values are better, e.g., the class cohesion) of all elements
 * that provide the metric, e.g., the most complex method.
 *
 * @author Ullrich Hafner
 */
public class WorstValuesModel {
    private static final double EPSILON = 0.001;

    private final CoverageStatistics statistics;
    private final ElementFormatter formatter;

    WorstValuesModel(final CoverageStatistics statistics, final ElementFormatter formatter) {
        this.statistics = statistics;
        this.formatter = formatter;
    }

    /**
     * Returns whether the worst value of the specified metric is available for the specified baseline. The worst value
     * is the maximum (for metrics where smaller values are better, e.g., the cyclomatic complexity) or the minimum (for
     * metrics where larger values are better, e.g., the class cohesion) of all elements that provide the metric, e.g.,
     * the most complex method. Container metrics provide no worst value. Builds that have been recorded with an older
     * version of the plugin provide no worst values as well.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if the worst value is available, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasValue(final Baseline baseline, final Metric metric) {
        return getValue(baseline, metric).isPresent();
    }

    private Optional<Value> getValue(final Baseline baseline, final Metric metric) {
        if (metric.isContainer() || baseline == Baseline.INDIRECT) {
            return Optional.empty();
        }
        return statistics.getValue(baseline, metric, getWorstAggregation(metric));
    }

    /**
     * Returns the aggregation that selects the worst value of the specified metric: the maximum for metrics where
     * smaller values are better, the minimum otherwise.
     *
     * @param metric
     *         the metric to get the aggregation for
     *
     * @return the aggregation that selects the worst value
     */
    static MetricAggregation getWorstAggregation(final Metric metric) {
        return metric.getTendency() == MetricTendency.SMALLER_IS_BETTER
                ? MetricAggregation.MAXIMUM
                : MetricAggregation.MINIMUM;
    }

    /**
     * Returns a formatted and localized String representation of the worst value of the specified metric (with
     * respect to the given baseline), see {@link #hasValue(Baseline, Metric)}. The value is prefixed with the
     * name of the aggregation, e.g. {@code "Maximum: 27"}.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the worst value for
     *
     * @return the formatted worst value
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String formatValue(final Baseline baseline, final Metric metric) {
        return getValue(baseline, metric)
                .map(value -> "%s: %s"
                        .formatted(formatter.getDisplayName(getWorstAggregation(metric)), formatter.formatValue(value)))
                .orElse(Messages.Coverage_Not_Available());
    }

    /**
     * Returns a tooltip that describes how the worst value of the specified metric has been computed, e.g.
     * {@code "Maximum of all methods"}.
     *
     * @param metric
     *         the metric to get the tooltip for
     *
     * @return the tooltip
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getTooltip(final Metric metric) {
        var aggregation = formatter.getDisplayName(getWorstAggregation(metric));
        return switch (metric.getTargetNodeMetric()) {
            case METHOD -> Messages.OverviewModel_worstValueOfMethods(aggregation);
            case FILE -> Messages.OverviewModel_worstValueOfFiles(aggregation);
            default -> Messages.OverviewModel_worstValueOfClasses(aggregation);
        };
    }

    /**
     * Returns whether a delta of the worst value of the specified metric is available for the specified baseline.
     * Like the deltas of the overall values in the table, these deltas are shown for the whole project only.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a delta of the worst value is available, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasDelta(final Baseline baseline, final Metric metric) {
        return getDelta(baseline, metric).isPresent();
    }

    private Optional<Value> getDelta(final Baseline baseline, final Metric metric) {
        if (baseline != Baseline.PROJECT || getValue(baseline, metric).isEmpty()) {
            return Optional.empty();
        }
        return statistics.getValue(Baseline.PROJECT_DELTA, metric, getWorstAggregation(metric));
    }

    /**
     * Returns a formatted and localized String representation of the delta of the worst value of the specified
     * metric (with respect to the given baseline).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the delta for
     *
     * @return the formatted delta
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String formatDelta(final Baseline baseline, final Metric metric) {
        return getDelta(baseline, metric)
                .map(delta -> formatter.formatDelta(metric, delta, Functions.getCurrentLocale()))
                .orElse(Messages.Coverage_Not_Available());
    }

    /**
     * Returns whether the delta of the worst value of the specified metric is an improvement or a deterioration. Like
     * in {@link OverviewModel#getTrend(Baseline, Metric)}, the tendency of the metric is considered: an increased
     * maximum cyclomatic complexity is a deterioration.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return a positive value for an improvement, a negative value for a deterioration, or {@code 0} if there is no
     *         significant change
     */
    @SuppressWarnings("unused") // Called by jelly view
    public double getTrend(final Baseline baseline, final Metric metric) {
        return computeTrend(metric, getDelta(baseline, metric));
    }

    /**
     * Returns whether the specified delta of a metric is an improvement or a deterioration. The tendency of the metric
     * is considered: an increased coverage is an improvement, while an increased cyclomatic complexity is a
     * deterioration.
     *
     * @param metric
     *         the metric of the delta
     * @param delta
     *         the delta, if available
     *
     * @return a positive value for an improvement, a negative value for a deterioration, or {@code 0} if there is no
     *         significant change
     */
    static double computeTrend(final Metric metric, final Optional<Value> delta) {
        var deltaValue = delta.map(Value::asDouble).orElse(0.0);
        if (Math.abs(deltaValue) < EPSILON) {
            return 0;
        }
        return metric.getTendency() == MetricTendency.SMALLER_IS_BETTER ? -deltaValue : deltaValue;
    }
}
