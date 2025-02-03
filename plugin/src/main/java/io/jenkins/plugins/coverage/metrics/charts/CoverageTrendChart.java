package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.line.LineSeries.FilledMode;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.hm.hafner.echarts.line.LinesDataSet;

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
    @Override
    public LinesChartModel create(final Iterable<BuildResult<CoverageStatistics>> results,
            final ChartModelConfiguration configuration) {
        var dataSet = new CoverageSeriesBuilder().createDataSet(configuration, results);

        LinesChartModel model = new LinesChartModel(dataSet);
        if (dataSet.isNotEmpty()) {
            model.useContinuousRangeAxis();
            model.setRangeMax(100);
            model.setRangeMin(dataSet.getMinimumValue());

            var filledMode = computeFilledMode(dataSet);
            addSeriesIfAvailable(dataSet, model, Metric.LINE, JenkinsPalette.GREEN.normal(), filledMode);
            addSeriesIfAvailable(dataSet, model, Metric.BRANCH, JenkinsPalette.GREEN.dark(), filledMode);
            addSeriesIfAvailable(dataSet, model, Metric.MUTATION, JenkinsPalette.GREEN.dark(), filledMode);
            addSeriesIfAvailable(dataSet, model, Metric.TEST_STRENGTH, JenkinsPalette.GREEN.light(), filledMode);

            addSeriesIfAvailable(dataSet, model, Metric.MCDC_PAIR, JenkinsPalette.RED.light(), filledMode);
            addSeriesIfAvailable(dataSet, model, Metric.METHOD, JenkinsPalette.RED.normal(), filledMode);
            addSeriesIfAvailable(dataSet, model, Metric.FUNCTION_CALL, JenkinsPalette.RED.dark(), filledMode);
        }
        return model;
    }

    /**
     * Returns the filled mode based on the contained coverage values. If the dataset contains MCDC or Function Call
     * coverage, then the filled mode is set to LINES, otherwise FILLED.
     *
     * @param dataSet
     *         the dataset to check
     *
     * @return the filled mode
     */
    private FilledMode computeFilledMode(final LinesDataSet dataSet) {
        if (dataSet.containsSeries(CoverageSeriesBuilder.MCDC_PAIR_COVERAGE)
                || dataSet.containsSeries(CoverageSeriesBuilder.FUNCTION_CALL_COVERAGE)) {
            return FilledMode.LINES;
        }
        return FilledMode.FILLED;
    }
}
