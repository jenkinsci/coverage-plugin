package io.jenkins.plugins.coverage.metrics.model;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Provides statistics for values and differences for all Base  the different mappings of coverage metric and baseline to actual values.
 */
public class CoverageStatistics {
    /**
     * Collects the values of all metrics of the specified tree of nodes, once for every available
     * {@link MetricAggregation aggregation}.
     *
     * @param node
     *         the root of the tree to aggregate
     *
     * @return the values of all metrics, grouped by aggregation
     */
    public static Map<MetricAggregation, List<Value>> aggregateValues(final Node node) {
        var aggregatedValues = new EnumMap<MetricAggregation, List<Value>>(MetricAggregation.class);
        for (MetricAggregation aggregation : MetricAggregation.values()) {
            aggregatedValues.put(aggregation, aggregateValues(node, aggregation));
        }
        return aggregatedValues;
    }

    private static List<Value> aggregateValues(final Node node, final MetricAggregation aggregation) {
        if (aggregation == MetricAggregation.getDefault()) {
            return node.aggregateValues();
        }
        return node.getMetrics().stream()
                .map(metric -> node.getValue(metric, aggregation))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Map<MetricAggregation, List<Value>> asTotal(final List<? extends Value> values) {
        return Map.of(MetricAggregation.getDefault(), List.copyOf(values));
    }

    private final Map<MetricAggregation, List<Value>> projectValueMapping;
    private final List<Difference> projectDelta;
    private final Map<MetricAggregation, List<Value>> changeValueMapping;
    private final List<Difference> changeDelta;
    private final Map<MetricAggregation, List<Value>> fileValueMapping;
    private final List<Difference> fileDelta;

    /**
     * Creates a new instance of {@link CoverageStatistics}. All values are registered for the aggregation
     * {@link MetricAggregation#TOTAL}.
     *
     * @param projectValueMapping
     *         mapping of metrics to values for {@link Baseline#PROJECT}
     * @param projectDeltaMapping
     *         mapping of metrics to delta values for {@link Baseline#PROJECT_DELTA}
     * @param modifiedLinesValueMapping
     *         mapping of metrics to values for {@link Baseline#MODIFIED_LINES}
     * @param modifiedLinesDeltaMapping
     *         mapping of metrics to delta values for {@link Baseline#MODIFIED_LINES_DELTA}
     * @param modifiedFilesValueMapping
     *         mapping of metrics to values for {@link Baseline#MODIFIED_FILES}
     * @param modifiedFilesDeltaMapping
     *         mapping of metrics to delta values for {@link Baseline#MODIFIED_FILES_DELTA}
     */
    public CoverageStatistics(
            final List<? extends Value> projectValueMapping,
            final List<? extends Difference> projectDeltaMapping,
            final List<? extends Value> modifiedLinesValueMapping,
            final List<? extends Difference> modifiedLinesDeltaMapping,
            final List<? extends Value> modifiedFilesValueMapping,
            final List<? extends Difference> modifiedFilesDeltaMapping) {
        this(asTotal(projectValueMapping), projectDeltaMapping,
                asTotal(modifiedLinesValueMapping), modifiedLinesDeltaMapping,
                asTotal(modifiedFilesValueMapping), modifiedFilesDeltaMapping);
    }

    /**
     * Creates a new instance of {@link CoverageStatistics}. The values of each baseline are grouped by the
     * {@link MetricAggregation aggregation} they have been computed with, see {@link #aggregateValues(Node)}.
     *
     * @param projectValueMapping
     *         mapping of aggregations and metrics to values for {@link Baseline#PROJECT}
     * @param projectDeltaMapping
     *         mapping of metrics to delta values for {@link Baseline#PROJECT_DELTA}
     * @param modifiedLinesValueMapping
     *         mapping of aggregations and metrics to values for {@link Baseline#MODIFIED_LINES}
     * @param modifiedLinesDeltaMapping
     *         mapping of metrics to delta values for {@link Baseline#MODIFIED_LINES_DELTA}
     * @param modifiedFilesValueMapping
     *         mapping of aggregations and metrics to values for {@link Baseline#MODIFIED_FILES}
     * @param modifiedFilesDeltaMapping
     *         mapping of metrics to delta values for {@link Baseline#MODIFIED_FILES_DELTA}
     */
    public CoverageStatistics(
            final Map<MetricAggregation, List<Value>> projectValueMapping,
            final List<? extends Difference> projectDeltaMapping,
            final Map<MetricAggregation, List<Value>> modifiedLinesValueMapping,
            final List<? extends Difference> modifiedLinesDeltaMapping,
            final Map<MetricAggregation, List<Value>> modifiedFilesValueMapping,
            final List<? extends Difference> modifiedFilesDeltaMapping) {
        this.projectValueMapping = Map.copyOf(projectValueMapping);
        this.changeValueMapping = Map.copyOf(modifiedLinesValueMapping);
        this.fileValueMapping = Map.copyOf(modifiedFilesValueMapping);
        this.projectDelta = List.copyOf(projectDeltaMapping);
        this.changeDelta = List.copyOf(modifiedLinesDeltaMapping);
        this.fileDelta = List.copyOf(modifiedFilesDeltaMapping);
    }

    /**
     * Returns the total value for the specified baseline and metric.
     *
     * @param baseline
     *         the baseline of the value
     * @param metric
     *         the metric of the value
     *
     * @return the value, if available
     */
    public Optional<Value> getValue(final Baseline baseline, final Metric metric) {
        return getValue(baseline, metric, MetricAggregation.getDefault());
    }

    /**
     * Returns the value for the specified baseline and metric that has been computed with the specified aggregation.
     * Delta baselines do not support aggregations, so the aggregation will be ignored for these baselines.
     *
     * @param baseline
     *         the baseline of the value
     * @param metric
     *         the metric of the value
     * @param aggregation
     *         the aggregation of the value
     *
     * @return the value, if available
     */
    public Optional<Value> getValue(final Baseline baseline, final Metric metric,
            final MetricAggregation aggregation) {
        if (baseline == Baseline.PROJECT) {
            return findValue(metric, aggregation, projectValueMapping);
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return findValue(metric, aggregation, fileValueMapping);
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return findValue(metric, aggregation, changeValueMapping);
        }
        if (baseline == Baseline.PROJECT_DELTA) {
            return Value.findValue(metric, projectDelta);
        }
        if (baseline == Baseline.MODIFIED_LINES_DELTA) {
            return Value.findValue(metric, changeDelta);
        }
        if (baseline == Baseline.MODIFIED_FILES_DELTA) {
            return Value.findValue(metric, fileDelta);
        }

        throw new NoSuchElementException("No such baseline: " + baseline);
    }

    private Optional<Value> findValue(final Metric metric, final MetricAggregation aggregation,
            final Map<MetricAggregation, List<Value>> valueMapping) {
        return Value.findValue(metric, valueMapping.getOrDefault(aggregation, List.of()));
    }

    /**
     * Returns the rounded value for the specified baseline and metric. If the value is not available, 0.0 is returned.
     *
     * @param baseline
     *         the baseline of the value
     * @param metric
     *         the metric of the value
     *
     * @return the value, if available
     */
    public double roundValue(final Baseline baseline, final Metric metric) {
        return getValue(baseline, metric).map(Value::asRounded).orElse(0.0);
    }

    /**
     * Returns the rounded value for metric in the project. If the value is not available, 0.0 is returned.
     *
     * @param metric
     *         the metric of the value
     *
     * @return the value, if available
     */
    public double roundValue(final Metric metric) {
        return roundValue(Baseline.PROJECT, metric);
    }

    /**
     * Returns whether a value for the specified metric and baseline is available.
     *
     * @param metric
     *         the metric of the value
     * @param baseline
     *         the baseline of the value
     *
     * @return {@code true}, if a value is available, {@code false} otherwise
     */
    public boolean containsValue(final Metric metric, final Baseline baseline) {
        return getValue(baseline, metric).isPresent();
    }

    /**
     * Returns whether a value for the specified metric and baseline is available in the project.
     *
     * @param metric
     *         the metric of the value
     *
     * @return {@code true}, if a value is available, {@code false} otherwise
     */
    public boolean containsValue(final Metric metric) {
        return containsValue(metric, Baseline.PROJECT);
    }
}
