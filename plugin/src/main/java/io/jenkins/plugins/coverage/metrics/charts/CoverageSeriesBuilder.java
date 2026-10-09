package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.line.SeriesBuilder;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds one x-axis point for the series of a line chart showing the coverage metrics of a project.
 *
 * @author Ullrich Hafner
 */
public class CoverageSeriesBuilder extends SeriesBuilder<CoverageStatistics> {
    @Override
    protected Map<String, Double> computeSeries(final CoverageStatistics statistics) {
        return Arrays.stream(Metric.values())
                .filter(statistics::containsValue)
                .collect(Collectors.toMap(Metric::toTagName, statistics::roundValue));
    }
}
