package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.line.LineSeries;
import edu.hm.hafner.echarts.line.LineSeries.FilledMode;
import edu.hm.hafner.echarts.line.LineSeries.StackedMode;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.hm.hafner.echarts.line.LinesDataSet;

import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.coverage.metrics.model.Messages;
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
public class CoverageTrendChart {
    /* Line Mode used to indicate whether is should be a filled line chart or line chart */
    private static FilledMode lineMode;
    
    /**
     * Sets the line mode for the trend chart.
     *
     * @param dataSet
     *
     */
    private void setLineMode(final LinesDataSet dataSet) {
        // If the dataset contains MCDC or Function Call Coverage
        if (dataSet.containsSeries(CoverageSeriesBuilder.MCDC_PAIR_COVERAGE) 
                || dataSet.containsSeries(CoverageSeriesBuilder.FUNCTION_CALL_COVERAGE)) {        
            lineMode = FilledMode.LINES;
        } 
        else {
            lineMode = FilledMode.FILLED;
        }
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
    public LinesChartModel create(final Iterable<BuildResult<CoverageStatistics>> results,
            final ChartModelConfiguration configuration) {
        CoverageSeriesBuilder builder = new CoverageSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);
        
        setLineMode(dataSet);

        LinesChartModel model = new LinesChartModel(dataSet);
        if (dataSet.isNotEmpty()) {
            LineSeries lineSeries = new LineSeries(Messages.Metric_LINE(),
                    JenkinsPalette.GREEN.normal(), StackedMode.SEPARATE_LINES, lineMode,
                    dataSet.getSeries(CoverageSeriesBuilder.LINE_COVERAGE));
            model.addSeries(lineSeries);
            model.useContinuousRangeAxis();
            model.setRangeMax(100);
            model.setRangeMin(dataSet.getMinimumValue());

            addSeries(dataSet, model, Messages.Metric_BRANCH(), CoverageSeriesBuilder.BRANCH_COVERAGE,
                    JenkinsPalette.GREEN.dark());
            addSeries(dataSet, model, Messages.Metric_MUTATION(), CoverageSeriesBuilder.MUTATION_COVERAGE,
                    JenkinsPalette.GREEN.dark());
            addSeries(dataSet, model, Messages.Metric_TEST_STRENGTH(), CoverageSeriesBuilder.TEST_STRENGTH,
                    JenkinsPalette.GREEN.light());

            addSeries(dataSet, model, Messages.Metric_MCDC_PAIR(), CoverageSeriesBuilder.MCDC_PAIR_COVERAGE,
                    JenkinsPalette.RED.light());
            addSeries(dataSet, model, Messages.Metric_METHOD(), CoverageSeriesBuilder.METHOD_COVERAGE,
                    JenkinsPalette.RED.normal());
            addSeries(dataSet, model, Messages.Metric_FUNCTION_CALL(), CoverageSeriesBuilder.FUNCTION_CALL_COVERAGE,
                    JenkinsPalette.RED.dark());
        }
        return model;
    }

    private static void addSeries(final LinesDataSet dataSet, final LinesChartModel model,
            final String name, final String seriesId, final String color) {
        if (dataSet.containsSeries(seriesId)) {
            LineSeries branchSeries = new LineSeries(name,
                    color, StackedMode.SEPARATE_LINES, lineMode,
                    dataSet.getSeries(seriesId));

            model.addSeries(branchSeries);
        }
    }
}
