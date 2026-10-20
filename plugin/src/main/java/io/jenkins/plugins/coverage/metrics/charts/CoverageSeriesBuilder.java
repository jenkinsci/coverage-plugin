package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.line.SeriesBuilder;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds one x-axis point for the series of a line chart showing the coverage metrics of a project. The values of the
 * metrics are computed with a configurable {@link MetricAggregation aggregation}, e.g., the maximum cyclomatic
 * complexity of all methods. Builds that do not provide values for this aggregation (e.g., builds that have been
 * recorded with an older version of the plugin) provide no values for the series.
 *
 * @author Ullrich Hafner
 */
public class CoverageSeriesBuilder extends SeriesBuilder<CoverageStatistics> {
    private final MetricAggregation aggregation;

    /**
     * Creates a new {@link CoverageSeriesBuilder} that uses the {@link MetricAggregation#OVERALL overall} values.
     */
    public CoverageSeriesBuilder() {
        this(MetricAggregation.getDefault());
    }

    /**
     * Creates a new {@link CoverageSeriesBuilder} that uses the values of the specified aggregation.
     *
     * @param aggregation
     *         the aggregation of the values
     */
    public CoverageSeriesBuilder(final MetricAggregation aggregation) {
        super();

        this.aggregation = aggregation;
    }

    @Override
    protected Map<String, Double> computeSeries(final CoverageStatistics statistics) {
        return Arrays.stream(Metric.values())
                .filter(metric -> statistics
                        .getValue(Baseline.PROJECT, metric, aggregation)
                        .isPresent())
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
        var value = statistics.getValue(Baseline.PROJECT, metric, aggregation);
        if (value.isPresent() && TreeMapNodeConverter.isFraction(value.get())) {
            return BigDecimal.valueOf(TreeMapNodeConverter.asDisplayValue(value.get()))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        return value.map(Value::asRounded).orElse(0.0);
    }
}
