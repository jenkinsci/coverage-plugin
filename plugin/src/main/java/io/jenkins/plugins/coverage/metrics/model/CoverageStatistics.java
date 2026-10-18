package io.jenkins.plugins.coverage.metrics.model;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.Value;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Provides statistics for values and differences for all Base  the different mappings of coverage metric and baseline to actual values.
 */
public class CoverageStatistics {
    private static <T extends Value> Map<MetricAggregation, List<T>> asOverall(final List<? extends T> values) {
        return Map.of(MetricAggregation.getDefault(), List.copyOf(values));
    }

    private final Map<MetricAggregation, List<Value>> projectValueMapping;
    private final Map<MetricAggregation, List<Difference>> projectDelta;
    private final Map<MetricAggregation, List<Value>> changeValueMapping;
    private final Map<MetricAggregation, List<Difference>> changeDelta;
    private final Map<MetricAggregation, List<Value>> fileValueMapping;
    private final Map<MetricAggregation, List<Difference>> fileDelta;

    /**
     * Creates a new instance of {@link CoverageStatistics}. All values are registered for the aggregation
     * {@link MetricAggregation#OVERALL}.
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
        this(
                asOverall(projectValueMapping),
                asOverall(projectDeltaMapping),
                asOverall(modifiedLinesValueMapping),
                asOverall(modifiedLinesDeltaMapping),
                asOverall(modifiedFilesValueMapping),
                asOverall(modifiedFilesDeltaMapping));
    }

    /**
     * Creates a new instance of {@link CoverageStatistics}. The values and deltas of each baseline are grouped by the
     * {@link MetricAggregation aggregation} they have been computed with, see
     * {@link edu.hm.hafner.coverage.Node#aggregateValues(MetricAggregation)}.
     *
     * @param projectValueMapping
     *         mapping of aggregations and metrics to values for {@link Baseline#PROJECT}
     * @param projectDeltaMapping
     *         mapping of aggregations and metrics to delta values for {@link Baseline#PROJECT_DELTA}
     * @param modifiedLinesValueMapping
     *         mapping of aggregations and metrics to values for {@link Baseline#MODIFIED_LINES}
     * @param modifiedLinesDeltaMapping
     *         mapping of aggregations and metrics to delta values for {@link Baseline#MODIFIED_LINES_DELTA}
     * @param modifiedFilesValueMapping
     *         mapping of aggregations and metrics to values for {@link Baseline#MODIFIED_FILES}
     * @param modifiedFilesDeltaMapping
     *         mapping of aggregations and metrics to delta values for {@link Baseline#MODIFIED_FILES_DELTA}
     */
    public CoverageStatistics(
            final Map<MetricAggregation, List<Value>> projectValueMapping,
            final Map<MetricAggregation, List<Difference>> projectDeltaMapping,
            final Map<MetricAggregation, List<Value>> modifiedLinesValueMapping,
            final Map<MetricAggregation, List<Difference>> modifiedLinesDeltaMapping,
            final Map<MetricAggregation, List<Value>> modifiedFilesValueMapping,
            final Map<MetricAggregation, List<Difference>> modifiedFilesDeltaMapping) {
        this.projectValueMapping = Map.copyOf(projectValueMapping);
        this.changeValueMapping = Map.copyOf(modifiedLinesValueMapping);
        this.fileValueMapping = Map.copyOf(modifiedFilesValueMapping);
        this.projectDelta = Map.copyOf(projectDeltaMapping);
        this.changeDelta = Map.copyOf(modifiedLinesDeltaMapping);
        this.fileDelta = Map.copyOf(modifiedFilesDeltaMapping);
    }

    /**
     * Returns the overall value for the specified baseline and metric.
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
     * For delta baselines, the value is the difference of the aggregated values (e.g., the difference of the maximum
     * cyclomatic complexity).
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
    public Optional<Value> getValue(final Baseline baseline, final Metric metric, final MetricAggregation aggregation) {
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
            return findValue(metric, aggregation, projectDelta);
        }
        if (baseline == Baseline.MODIFIED_LINES_DELTA) {
            return findValue(metric, aggregation, changeDelta);
        }
        if (baseline == Baseline.MODIFIED_FILES_DELTA) {
            return findValue(metric, aggregation, fileDelta);
        }

        throw new NoSuchElementException("No such baseline: " + baseline);
    }

    private Optional<Value> findValue(
            final Metric metric,
            final MetricAggregation aggregation,
            final Map<MetricAggregation, ? extends List<? extends Value>> valueMapping) {
        return Optional.ofNullable(valueMapping.get(aggregation)).flatMap(values -> Value.findValue(metric, values));
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
