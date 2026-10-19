package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.hm.hafner.echarts.line.LinesDataSet;
import edu.hm.hafner.util.VisibleForTesting;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.echarts.JenkinsPalette;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
    /** Metrics that are rendered with a predefined color. */
    private static final Set<Metric> PREDEFINED_METRICS = Set.of(
            Metric.LINE,
            Metric.BRANCH,
            Metric.INSTRUCTION,
            Metric.MUTATION,
            Metric.TEST_STRENGTH,
            Metric.MCDC_PAIR,
            Metric.FUNCTION_CALL,
            Metric.STMT_DC,
            Metric.STMT_CC,
            Metric.CONDITION);

    private final boolean showAllPercentages;

    @VisibleForTesting
    CoverageTrendChart() {
        this(Set.of(), false);
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
        this(visibleMetrics, useLines, false);
    }

    /**
     * Creates a new {@link CoverageTrendChart}.
     *
     * @param visibleMetrics
     *         the metrics to render in the trend chart
     * @param useLines
     *         determines if the chart should use lines or filled areas
     * @param showAllPercentages
     *         determines if all visible metrics that are shown as a percentage should be rendered (e.g., the class
     *         cohesion), or only the coverage metrics with a predefined color
     */
    public CoverageTrendChart(
            final Set<Metric> visibleMetrics, final boolean useLines, final boolean showAllPercentages) {
        this(visibleMetrics, useLines, showAllPercentages, MetricAggregation.getDefault());
    }

    /**
     * Creates a new {@link CoverageTrendChart}.
     *
     * @param visibleMetrics
     *         the metrics to render in the trend chart
     * @param useLines
     *         determines if the chart should use lines or filled areas
     * @param showAllPercentages
     *         determines if all visible metrics that are shown as a percentage should be rendered (e.g., the class
     *         cohesion), or only the coverage metrics with a predefined color
     * @param aggregation
     *         the aggregation of the values to show, e.g., the minimum line coverage of all files
     */
    public CoverageTrendChart(
            final Set<Metric> visibleMetrics,
            final boolean useLines,
            final boolean showAllPercentages,
            final MetricAggregation aggregation) {
        super(visibleMetrics, useLines, aggregation);

        this.showAllPercentages = showAllPercentages;
    }

    @Override
    public LinesChartModel create(
            final Iterable<BuildResult<CoverageStatistics>> results, final ChartModelConfiguration configuration) {
        var dataSet = createSeriesBuilder().createDataSet(configuration, results);

        var model = new LinesChartModel(dataSet);
        if (dataSet.isNotEmpty()) {
            int colorIndex = 0;
            for (Metric metric : List.of(Metric.MODULE, Metric.PACKAGE, Metric.FILE, Metric.CLASS, Metric.METHOD)) {
                addSeriesIfAvailable(
                        dataSet,
                        model,
                        metric,
                        JenkinsPalette.chartColor(colorIndex).normal());
                colorIndex++;
            }

            addSeriesIfAvailable(dataSet, model, Metric.LINE, JenkinsPalette.GREEN.normal());
            addSeriesIfAvailable(dataSet, model, Metric.BRANCH, JenkinsPalette.GREEN.dark());
            addSeriesIfAvailable(dataSet, model, Metric.INSTRUCTION, JenkinsPalette.GREEN.light());

            addSeriesIfAvailable(dataSet, model, Metric.MUTATION, JenkinsPalette.GREEN.dark());
            addSeriesIfAvailable(dataSet, model, Metric.TEST_STRENGTH, JenkinsPalette.GREEN.light());

            addSeriesIfAvailable(dataSet, model, Metric.MCDC_PAIR, JenkinsPalette.RED.light());
            addSeriesIfAvailable(dataSet, model, Metric.FUNCTION_CALL, JenkinsPalette.RED.dark());

            addSeriesIfAvailable(dataSet, model, Metric.STMT_DC, JenkinsPalette.RED.light());
            addSeriesIfAvailable(dataSet, model, Metric.STMT_CC, JenkinsPalette.RED.light());
            addSeriesIfAvailable(dataSet, model, Metric.CONDITION, JenkinsPalette.RED.normal());

            if (showAllPercentages) {
                addAdditionalPercentages(dataSet, model, colorIndex);
            }

            model.useContinuousRangeAxis();
            model.computeVisibleRange();
            model.setRangeMax(100); // Restrict the range to 100%
        }
        return model;
    }

    private void addAdditionalPercentages(
            final LinesDataSet dataSet, final LinesChartModel model, final int firstColorIndex) {
        int colorIndex = firstColorIndex;
        for (Metric metric : getAdditionalPercentages()) {
            addSeriesIfAvailable(
                    dataSet,
                    model,
                    metric,
                    JenkinsPalette.chartColor(colorIndex).normal());
            colorIndex++;
        }
    }

    private List<Metric> getAdditionalPercentages() {
        return Arrays.stream(Metric.values())
                .filter(this::isAdditionalPercentage)
                .toList();
    }

    private boolean isAdditionalPercentage(final Metric metric) {
        return !metric.isContainer() && metric.getUnit().isPercentage() && !PREDEFINED_METRICS.contains(metric);
    }
}
