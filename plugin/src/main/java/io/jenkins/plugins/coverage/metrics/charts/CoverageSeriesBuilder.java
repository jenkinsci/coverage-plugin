package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.line.SeriesBuilder;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
                .collect(Collectors.toMap(Metric::toTagName, metric -> roundValue(statistics, metric)));
    }

    /**
     * Returns the rounded value of the metric. Percentages that are stored as fraction in the interval [0, 1] (e.g.,
     * class cohesion) are scaled to the interval [0, 100], so that they share the scale with all other percentages.
     *
     * @param statistics
     *         the statistics that contain the value
     * @param metric
     *         the metric of the value
     *
     * @return the rounded value (two decimals), or 0 if the value is not available
     */
    private double roundValue(final CoverageStatistics statistics, final Metric metric) {
        var value = statistics.getValue(Baseline.PROJECT, metric);
        if (value.isPresent() && TreeMapNodeConverter.isFraction(value.get())) {
            return BigDecimal.valueOf(TreeMapNodeConverter.asDisplayValue(value.get()))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        return statistics.roundValue(metric);
    }
}
