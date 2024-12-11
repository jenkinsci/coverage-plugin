package io.jenkins.plugins.coverage.metrics.charts;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.IntegerValue;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.line.SeriesBuilder;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;

/**
 * Builds one x-axis point for the series of a line chart showing the line and branch coverage of a project.
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
    static final String CYCLOMATIC_COMPLEXITY = Metric.COMPLEXITY.toTagName();
    static final String COGNITIVE_COMPLEXITY = Metric.COGNITIVE_COMPLEXITY.toTagName();
    static final String NPATH_COMPLEXITY = Metric.NPATH_COMPLEXITY.toTagName();
    static final String NCSS = Metric.NCSS.toTagName();

    @Override
    protected Map<String, Double> computeSeries(final CoverageStatistics statistics) {
        Map<String, Double> series = new HashMap<>();

        series.put(LINE_COVERAGE, getRoundedPercentage(statistics, Metric.LINE));
        add(statistics, Metric.BRANCH, BRANCH_COVERAGE, series);
        add(statistics, Metric.MUTATION, MUTATION_COVERAGE, series);
        add(statistics, Metric.TEST_STRENGTH, TEST_STRENGTH, series);
        add(statistics, Metric.MCDC_PAIR, MCDC_PAIR_COVERAGE, series);
        add(statistics, Metric.FUNCTION_CALL, FUNCTION_CALL_COVERAGE, series);

        if (statistics.containsValue(Baseline.PROJECT, Metric.MCDC_PAIR)
                || statistics.containsValue(Baseline.PROJECT, Metric.FUNCTION_CALL)) {
            // Method coverage is only relevant if MC/DC pair or function call coverage is available
            add(statistics, Metric.METHOD, METHOD_COVERAGE, series);
        }

        add(statistics, Metric.COMPLEXITY, CYCLOMATIC_COMPLEXITY, series);
        add(statistics, Metric.COGNITIVE_COMPLEXITY, COGNITIVE_COMPLEXITY, series);
        add(statistics, Metric.NPATH_COMPLEXITY, NPATH_COMPLEXITY, series);
        add(statistics, Metric.NCSS, NCSS, series);

        return series;
    }

    private void add(final CoverageStatistics statistics, final Metric metric, final String chartId,
            final Map<String, Double> series) {
        if (statistics.containsValue(Baseline.PROJECT, metric)) {
            series.put(chartId, getRoundedPercentage(statistics, metric));
        }
    }

    private double getRoundedPercentage(final CoverageStatistics statistics, final Metric metric) {
        Optional<Value> value = statistics.getValue(Baseline.PROJECT, metric);
        if (value.isEmpty()) {
            return 0;
        }
        if (value.get() instanceof IntegerValue) {
            return ((IntegerValue) value.get()).getValue();
        }
        else {
            Coverage coverage = (Coverage) value.get();
            return coverage.getCoveredPercentage().toDouble() / 100.0 * 100.0;
        }
    }
}
