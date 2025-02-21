package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.line.SeriesBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;

/**
 * Builds one x-axis point for the series of a line chart showing the coverage metrics of a project.
 *
 * @author Ullrich Hafner
 */
public class CoverageSeriesBuilder extends SeriesBuilder<CoverageStatistics> {
    static final String LINE_COVERAGE = Metric.LINE.toTagName();
    static final String BRANCH_COVERAGE = Metric.BRANCH.toTagName();
    static final String MUTATION_COVERAGE = Metric.MUTATION.toTagName();
    static final String TEST_STRENGTH = Metric.TEST_STRENGTH.toTagName();
    static final String MCDC_PAIR_COVERAGE = Metric.MCDC_PAIR.toTagName();
    static final String FUNCTION_CALL_COVERAGE = Metric.FUNCTION_CALL.toTagName();
    static final String METHOD_COVERAGE = Metric.METHOD.toTagName();

    @Override
    protected Map<String, Double> computeSeries(final CoverageStatistics statistics) {
        return Arrays.stream(Metric.values())
                .filter(Metric::isCoverage)
                .filter(statistics::containsValue)
                .collect(Collectors.toMap(Metric::toTagName, statistics::roundValue));
    }
}
