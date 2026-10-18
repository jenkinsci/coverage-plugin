package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.Value;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The values and differences of all baselines of a build that have been computed with an aggregation other than the
 * default aggregation (e.g., the maximum cyclomatic complexity of all methods). These values are persisted in the
 * {@link CoverageBuildAction} so that they are available without loading the coverage tree, e.g., in trend charts.
 *
 * @author Ullrich Hafner
 */
final class AggregatedValues implements Serializable {
    @Serial
    private static final long serialVersionUID = 6563154306519437318L;

    private final MetricAggregation aggregation;

    @SuppressWarnings("serial")
    private final List<Value> projectValues;

    @SuppressWarnings("serial")
    private final List<Difference> projectDifferences;

    @SuppressWarnings("serial")
    private final List<Value> modifiedLinesValues;

    @SuppressWarnings("serial")
    private final List<Difference> modifiedLinesDifferences;

    @SuppressWarnings("serial")
    private final List<Value> modifiedFilesValues;

    @SuppressWarnings("serial")
    private final List<Difference> modifiedFilesDifferences;

    /**
     * Creates the aggregated values for the specified aggregation from the specified statistics.
     *
     * @param aggregation
     *         the aggregation of the values
     * @param statistics
     *         the statistics that contain the values of all aggregations
     */
    AggregatedValues(final MetricAggregation aggregation, final CoverageStatistics statistics) {
        this.aggregation = aggregation;

        // do not use immutable collections to simplify serialization
        projectValues = new ArrayList<>(statistics.getAllValues(Baseline.PROJECT, aggregation));
        projectDifferences = new ArrayList<>(statistics.getAllDeltas(Baseline.PROJECT_DELTA, aggregation));
        modifiedLinesValues = new ArrayList<>(statistics.getAllValues(Baseline.MODIFIED_LINES, aggregation));
        modifiedLinesDifferences = new ArrayList<>(statistics.getAllDeltas(Baseline.MODIFIED_LINES_DELTA, aggregation));
        modifiedFilesValues = new ArrayList<>(statistics.getAllValues(Baseline.MODIFIED_FILES, aggregation));
        modifiedFilesDifferences = new ArrayList<>(statistics.getAllDeltas(Baseline.MODIFIED_FILES_DELTA, aggregation));
    }

    MetricAggregation getAggregation() {
        return aggregation;
    }

    List<Value> getProjectValues() {
        return projectValues;
    }

    List<Difference> getProjectDifferences() {
        return projectDifferences;
    }

    List<Value> getModifiedLinesValues() {
        return modifiedLinesValues;
    }

    List<Difference> getModifiedLinesDifferences() {
        return modifiedLinesDifferences;
    }

    List<Value> getModifiedFilesValues() {
        return modifiedFilesValues;
    }

    List<Difference> getModifiedFilesDifferences() {
        return modifiedFilesDifferences;
    }
}
