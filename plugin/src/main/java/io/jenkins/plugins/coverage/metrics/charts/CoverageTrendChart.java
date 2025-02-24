package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.line.LineSeries;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.hm.hafner.util.VisibleForTesting;

import java.util.List;
import java.util.Set;

import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.echarts.JenkinsPalette;

/**
 * Builds the Java side model for a trend chart showing the line and branch coverage of a project. The number of builds
 * to consider is controlled by a {@link ChartModelConfiguration} instance. The created model object can be serialized
 * to JSON (e.g., using the {@link JacksonFacade}) and can be used 1:1 as ECharts configuration object in the
 * corresponding JS file.
 *
 * @author Ullrich Hafner
 * @see JacksonFacade
 */
public class CoverageTrendChart extends TrendChart {
    @VisibleForTesting
    CoverageTrendChart() {
        super(Set.of(), false);
    }

    /**
     * Creates a new {@link CoverageTrendChart}.
     *
     * @param visibleMetrics
     *         the metrics to render in the trend chart
     * @param useLines
     *         determines if the chart should use lines or filled areas
     */
    public CoverageTrendChart(final Set<Metric> visibleMetrics, final boolean useLines) {
        super(visibleMetrics, useLines);
    }

    @Override
    public LinesChartModel create(final Iterable<BuildResult<CoverageStatistics>> results,
            final ChartModelConfiguration configuration) {
        var dataSet = new CoverageSeriesBuilder().createDataSet(configuration, results);

        LinesChartModel model = new LinesChartModel(dataSet);
        if (dataSet.isNotEmpty()) {
            int colorIndex = 0;
            for (Metric metric : List.of(Metric.MODULE, Metric.PACKAGE, Metric.FILE, Metric.CLASS, Metric.METHOD)) {
                addSeriesIfAvailable(dataSet, model, metric, JenkinsPalette.chartColor(colorIndex++).normal());
            }

            addSeriesIfAvailable(dataSet, model, Metric.LINE, JenkinsPalette.GREEN.normal());
            addSeriesIfAvailable(dataSet, model, Metric.BRANCH, JenkinsPalette.GREEN.dark());
            addSeriesIfAvailable(dataSet, model, Metric.INSTRUCTION, JenkinsPalette.GREEN.light());

            addSeriesIfAvailable(dataSet, model, Metric.MUTATION, JenkinsPalette.GREEN.dark());
            addSeriesIfAvailable(dataSet, model, Metric.TEST_STRENGTH, JenkinsPalette.GREEN.light());

            addSeriesIfAvailable(dataSet, model, Metric.MCDC_PAIR, JenkinsPalette.RED.light());
            addSeriesIfAvailable(dataSet, model, Metric.FUNCTION_CALL, JenkinsPalette.RED.dark());

            model.useContinuousRangeAxis();
            model.setRangeMax(100); // Restrict the range to 100%
            model.setRangeMin(model.getSeries().stream().map(LineSeries::getData).flatMap(List::stream).mapToDouble(Number::doubleValue).min().orElse(0));
        }
        return model;
    }
}
