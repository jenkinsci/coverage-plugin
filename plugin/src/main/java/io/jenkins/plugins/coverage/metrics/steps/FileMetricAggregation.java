package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Metric.MetricValueType;
import edu.hm.hafner.coverage.MetricAggregation;

/**
 * Determines how the values of a metric are aggregated when the value of a single file is shown. Coverage and size
 * metrics (e.g., line coverage, lines of code, or the number of tests) are summed up for a file. Metrics that are
 * measured for individual methods (e.g., the cyclomatic complexity) are reduced to the maximum value of all methods
 * of the file: the sum of the complexities of all methods mainly reflects the size of a file, while the maximum
 * points to the method that should be improved first.
 *
 * @author Ullrich Hafner
 */
final class FileMetricAggregation {
    /**
     * Returns the aggregation that should be used to compute the value of the specified metric for a single file.
     *
     * @param metric
     *         the metric to get the aggregation for
     *
     * @return the aggregation to use
     */
    static MetricAggregation of(final Metric metric) {
        if (metric.getType() == MetricValueType.METHOD_METRIC) {
            return MetricAggregation.MAXIMUM;
        }
        return MetricAggregation.OVERALL;
    }

    private FileMetricAggregation() {
        // prevents instantiation
    }
}
