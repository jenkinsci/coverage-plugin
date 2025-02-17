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
        LinesDataSet dataSet = new MetricSeriesBuilder().createDataSet(configuration, results);

        LinesChartModel model = new LinesChartModel(dataSet);
        if (dataSet.isNotEmpty()) {
            model.useContinuousRangeAxis();
            model.setRangeMax(dataSet.getMaximumValue());
            model.setRangeMin(dataSet.getMinimumValue());

            int colorIndex = 0;
            for (var tag : dataSet.getDataSetIds()) {
                Metric metric = Metric.fromTag(tag);
                addSeriesIfAvailable(dataSet, model, metric.getDisplayName(),
                        tag, JenkinsPalette.chartColor(colorIndex++).normal(),
                        FilledMode.LINES);
            }
        }
        return model;
    }
}
