package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.line.LineSeries.FilledMode;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.hm.hafner.echarts.line.LinesDataSet;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.coverage.metrics.model.Messages;
import io.jenkins.plugins.echarts.JenkinsPalette;

/**
 * Builds the Java side model for a trend chart showing the metrics of a project. The number of builds
 * to consider is controlled by a {@link ChartModelConfiguration} instance. The created model object can be serialized
 * to JSON (e.g., using the {@link JacksonFacade}) and can be used 1:1 as ECharts configuration object in the
 * corresponding JS file.
 *
 * @author Ullrich Hafner
 * @see JacksonFacade
 */
public class MetricsTrendChart extends TrendChart {
    @Override
    public LinesChartModel create(final Iterable<BuildResult<CoverageStatistics>> results,
            final ChartModelConfiguration configuration) {
        CoverageSeriesBuilder builder = new CoverageSeriesBuilder();
        LinesDataSet dataSet = builder.createDataSet(configuration, results);

        var filledMode = FilledMode.LINES;

        LinesChartModel model = new LinesChartModel(dataSet);
        if (dataSet.isNotEmpty()) {
            model.useContinuousRangeAxis();
            model.setRangeMax(dataSet.getMaximumValue());
            model.setRangeMin(dataSet.getMinimumValue());

            addSeries(dataSet, model, Messages.Metric_COMPLEXITY(), CoverageSeriesBuilder.CYCLOMATIC_COMPLEXITY,
                    JenkinsPalette.ORANGE.normal(), filledMode);
            addSeries(dataSet, model, Messages.Metric_COGNITIVE_COMPLEXITY(), CoverageSeriesBuilder.COGNITIVE_COMPLEXITY,
                    JenkinsPalette.ORANGE.normal(), filledMode);
            addSeries(dataSet, model, Messages.Metric_NPATH(), CoverageSeriesBuilder.NPATH_COMPLEXITY,
                    JenkinsPalette.ORANGE.normal(), filledMode);
            addSeries(dataSet, model, Messages.Metric_NCSS(), CoverageSeriesBuilder.NCSS,
                    JenkinsPalette.ORANGE.normal(), filledMode);
        }
        return model;
    }
}
