package io.jenkins.plugins.coverage.metrics.charts;

import java.util.HashMap;
import java.util.Map;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.line.SeriesBuilder;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;

/**
 * Builds one x-axis point for the series of a line chart showing the line and branch coverage of a project.
 *
 * @author Ullrich Hafner
 */
public class CoverageSeriesBuilder extends SeriesBuilder<CoverageStatistics> {
    static final String LINE_COVERAGE = "line";
    static final String BRANCH_COVERAGE = "branch";
    static final String MUTATION_COVERAGE = "mutation";
    static final String TEST_STRENGTH = "test-strength";
    static final String MCDC_PAIR_COVERAGE = "mcdc-pair";
    static final String FUNCTION_CALL_COVERAGE = "function-call";
    static final String METHOD_COVERAGE = "method";

    @Override
    protected Map<String, Double> computeSeries(final CoverageStatistics statistics) {
        Map<String, Double> series = new HashMap<>();

        series.put(LINE_COVERAGE, getRoundedPercentage(statistics, Metric.LINE));
        add(statistics, Metric.BRANCH, BRANCH_COVERAGE, series);
        add(statistics, Metric.MUTATION, MUTATION_COVERAGE, series);
        add(statistics, Metric.TEST_STRENGTH, TEST_STRENGTH, series);
        add(statistics, Metric.MCDC_PAIR, MCDC_PAIR_COVERAGE, series);    
        add(statistics, Metric.FUNCTION_CALL, FUNCTION_CALL_COVERAGE, series);                
        add(statistics, Metric.METHOD, METHOD_COVERAGE, series);
        return series;
    }

    private void add(final CoverageStatistics statistics, final Metric metric, final String chartId,
            final Map<String, Double> series) {
        if (statistics.containsValue(Baseline.PROJECT, metric)) {
            series.put(chartId, getRoundedPercentage(statistics, metric));
        }
    }

    private double getRoundedPercentage(final CoverageStatistics statistics, final Metric metric) {
        Coverage coverage = (Coverage) statistics.getValue(Baseline.PROJECT, metric)
                .orElse(Coverage.nullObject(metric));
        return (coverage.getCoveredPercentage().toDouble() / 100.0) * 100.0;
    }
}
