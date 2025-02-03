package io.jenkins.plugins.coverage.metrics.charts;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.line.SeriesBuilder;

import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;

/**
 * Builds one x-axis point for the series of a line chart showing the line and branch coverage of a project.
 *
 * @author Ullrich Hafner
 */
public class MetricSeriesBuilder extends SeriesBuilder<CoverageStatistics> {
    @Override
    protected Map<String, Double> computeSeries(final CoverageStatistics statistics) {
        return Arrays.stream(Metric.values())
                .filter(Predicate.not(Metric::isCoverage))
                .filter(statistics::containsValue)
                .collect(Collectors.toMap(Metric::toTagName, statistics::roundValue));
    }
}
