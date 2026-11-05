package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests the class {@link FileMetricAggregation}.
 *
 * @author Ullrich Hafner
 */
class FileMetricAggregationTest {
    /**
     * Verifies that method metrics are aggregated using the maximum.
     *
     * @param metric
     *         the metric under test
     */
    @ParameterizedTest(name = "{0} should use the maximum")
    @EnumSource(
            value = Metric.class,
            names = {"CYCLOMATIC_COMPLEXITY", "COGNITIVE_COMPLEXITY", "NPATH_COMPLEXITY"})
    void shouldUseMaximumForMethodMetrics(final Metric metric) {
        assertThat(FileMetricAggregation.of(metric)).isEqualTo(MetricAggregation.MAXIMUM);
    }

    /**
     * Verifies that coverage and size metrics are summed up.
     *
     * @param metric
     *         the metric under test
     */
    @ParameterizedTest(name = "{0} should use the overall value")
    @EnumSource(
            value = Metric.class,
            names = {"LINE", "BRANCH", "MUTATION", "LOC", "NCSS", "TESTS"})
    void shouldUseOverallValueForCoverageAndSizeMetrics(final Metric metric) {
        assertThat(FileMetricAggregation.of(metric)).isEqualTo(MetricAggregation.OVERALL);
    }
}
