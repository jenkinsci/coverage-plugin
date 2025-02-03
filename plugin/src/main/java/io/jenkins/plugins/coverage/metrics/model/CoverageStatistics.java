package io.jenkins.plugins.coverage.metrics.model;

import java.util.List;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;

/**
 * Represents the different mappings of coverage metric and baseline to actual values.
 */
public class CoverageStatistics {
    private final List<Value> projectValueMapping;
    private final NavigableMap<Metric, Difference> projectDelta;
    private final List<Value> changeValueMapping;
    private final NavigableMap<Metric, Difference> changeDelta;
    private final List<Value> fileValueMapping;
    private final NavigableMap<Metric, Difference> fileDelta;

    /**
     * Creates a new instance of {@link CoverageStatistics}.
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
            final NavigableMap<Metric, Difference> projectDeltaMapping,
            final List<? extends Value> modifiedLinesValueMapping,
            final NavigableMap<Metric, Difference> modifiedLinesDeltaMapping,
            final List<? extends Value> modifiedFilesValueMapping,
            final NavigableMap<Metric, Difference> modifiedFilesDeltaMapping) {
        this.projectValueMapping = List.copyOf(projectValueMapping);
        this.changeValueMapping = List.copyOf(modifiedLinesValueMapping);
        this.fileValueMapping = List.copyOf(modifiedFilesValueMapping);
        this.projectDelta = new TreeMap<>(projectDeltaMapping);
        this.changeDelta = new TreeMap<>(modifiedLinesDeltaMapping);
        this.fileDelta = new TreeMap<>(modifiedFilesDeltaMapping);
    }

    /**
     * Returns the value for the specified baseline and metric.
     *
     * @param baseline
     *         the baseline of the value
     * @param metric
     *         the metric of the value
     *
     * @return the value, if available
     */
    public Optional<Value> getValue(final Baseline baseline, final Metric metric) {
        if (baseline == Baseline.PROJECT) {
            return Value.findValue(metric, projectValueMapping);
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return Value.findValue(metric, fileValueMapping);
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return Value.findValue(metric, changeValueMapping);
        }
        if (baseline == Baseline.PROJECT_DELTA) {
            return getValue(metric, projectDelta);
        }
        if (baseline == Baseline.MODIFIED_LINES_DELTA) {
            return getValue(metric, changeDelta);
        }
        if (baseline == Baseline.MODIFIED_FILES_DELTA) {
            return getValue(metric, fileDelta);
        }

        throw new NoSuchElementException("No such baseline: " + baseline);
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

    private Optional<Value> getValue(final Metric metric, final NavigableMap<Metric, Difference> mapping) {
        return Optional.ofNullable(mapping.get(metric));
    }

    /**
     * Returns whether a value for the specified metric and baseline is available.
     *
     * @param baseline
     *         the baseline of the value
     * @param metric
     *         the metric of the value
     *
     * @return {@code true}, if a value is available, {@code false} otherwise
     */
    public boolean containsValue(final Baseline baseline, final Metric metric) {
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
    // FIXME: reoder overloaded name
    public boolean containsValue(final Metric metric) {
        return containsValue(Baseline.PROJECT, metric);
    }
}
