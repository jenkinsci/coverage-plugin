package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.line.LineSeries;
import edu.hm.hafner.echarts.line.LineSeries.FilledMode;
import edu.hm.hafner.echarts.line.LineSeries.StackedMode;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.hm.hafner.echarts.line.LinesDataSet;

import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;

/**
 * Builds the Java side model for a trend chart. The number of builds to consider is controlled by a
 * {@link ChartModelConfiguration} instance. The created model object can be serialized to JSON (e.g., using the
 * {@link JacksonFacade}) and can be used 1:1 as ECharts configuration object in the corresponding JS file.
 *
 * @author Ullrich Hafner
 * @see JacksonFacade
 */
public abstract class TrendChart {
    /**
     * Create a Trend Chart Instance that is either for Coverage or Metrics.
     *
     * @param metrics if the instance should be the metrics
     *
     * @return the created Trend Chart Instance
     */
    public static TrendChart createTrendChart(final boolean metrics) {
        return metrics ? new MetricsTrendChart() : new CoverageTrendChart();
    }

    /**
     * Creates the chart for the specified results.
     *
     * @param results
     *         the forensics results to render - these results must be provided in descending order, i.e. the current *
     *         build is the head of the list, then the previous builds, and so on
     * @param configuration
     *         the chart configuration to be used
     *
     * @return the chart model, ready to be serialized to JSON
     */
    public abstract LinesChartModel create(Iterable<BuildResult<CoverageStatistics>> results,
            ChartModelConfiguration configuration);

    void addSeriesIfAvailable(final LinesDataSet dataSet, final LinesChartModel model,
                   final String name, final String seriesId, final String color, final FilledMode filledMode) {
        if (dataSet.containsSeries(seriesId)) {
            LineSeries branchSeries = new LineSeries(name,
                    color, StackedMode.SEPARATE_LINES, filledMode, dataSet.getSeries(seriesId));

            model.addSeries(branchSeries);
        }
    }

    void addSeriesIfAvailable(final LinesDataSet dataSet, final LinesChartModel model,
                   final Metric metric, final String color, final FilledMode filledMode) {
        var tagName = metric.toTagName();
        if (dataSet.containsSeries(tagName)) {
            LineSeries branchSeries = new LineSeries(metric.getDisplayName(),
                    color, StackedMode.SEPARATE_LINES, filledMode, dataSet.getSeries(tagName));

            model.addSeries(branchSeries);
        }
    }
}
