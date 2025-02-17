package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.line.SeriesBuilder;

import java.util.HashMap;
import java.util.Map;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
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
        Map<String, Double> series = new HashMap<>();

        add(statistics, Metric.LINE, LINE_COVERAGE, series);
        add(statistics, Metric.BRANCH, BRANCH_COVERAGE, series);
        add(statistics, Metric.MUTATION, MUTATION_COVERAGE, series);
        add(statistics, Metric.TEST_STRENGTH, TEST_STRENGTH, series);
        add(statistics, Metric.MCDC_PAIR, MCDC_PAIR_COVERAGE, series);
        add(statistics, Metric.FUNCTION_CALL, FUNCTION_CALL_COVERAGE, series);

        if (statistics.containsValue(Metric.MCDC_PAIR, Baseline.PROJECT)
                || statistics.containsValue(Metric.FUNCTION_CALL, Baseline.PROJECT)) {
            // Method coverage is only relevant if MC/DC pair or function call coverage is available
            add(statistics, Metric.METHOD, METHOD_COVERAGE, series);
        }

        return series;
    }

    private void add(final CoverageStatistics statistics, final Metric metric, final String chartId,
            final Map<String, Double> series) {
        if (statistics.containsValue(metric, Baseline.PROJECT)) {
            series.put(chartId, statistics.roundValue(Baseline.PROJECT, metric));
        }
    }
}
