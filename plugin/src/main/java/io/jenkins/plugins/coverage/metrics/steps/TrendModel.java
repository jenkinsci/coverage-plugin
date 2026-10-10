package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.line.LinesChartModel;
import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import java.util.Set;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * Server side model that provides the data for the "Trend" tab of the coverage details view. Shows a trend
 * chart that visualizes the selected coverage and size metrics across previous builds, so regressions and
 * improvements become visible over time. Whether an area or line style is used, which Y-axis scaling is
 * applied, and how many builds are considered can be switched inline on the client side, see the corresponding
 * {@code trend-model.js}. This model simply delegates the actual chart model computation to
 * {@link TrendChartFactory}. The layout of the associated view is defined in the corresponding jelly view
 * 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class TrendModel implements ModelObject {
    private final Run<?, ?> owner;
    private final CoverageBuildAction latestAction;

    TrendModel(final Run<?, ?> owner, final CoverageBuildAction latestAction) {
        this.owner = owner;
        this.latestAction = latestAction;
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverageTrendModel_displayName();
    }

    public String getId() {
        return latestAction.getUrlName();
    }

    public Run<?, ?> getObject() {
        return owner;
    }

    public RunTab getTab() {
        return new RunTab(owner);
    }

    /**
     * Returns whether the report contains percentage metrics (coverage), i.e., whether the coverage trend chart can
     * be shown.
     *
     * @return {@code true} if there is at least one coverage metric
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasCoverageMetrics() {
        return hasMetric(TrendChartFactory.COVERAGE_TREND_METRICS);
    }

    /**
     * Returns whether the report contains absolute software metrics (size, complexity, ...), i.e., whether the
     * software metrics trend chart can be shown.
     *
     * @return {@code true} if there is at least one software metric
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasSoftwareMetrics() {
        return hasMetric(TrendChartFactory.SOFTWARE_TREND_METRICS);
    }

    /**
     * Returns whether the latest results contain at least one of the specified metrics.
     *
     * @param metrics
     *         the metrics to look for
     *
     * @return {@code true} if at least one of the metrics is available
     */
    private boolean hasMetric(final Set<Metric> metrics) {
        return latestAction.getAllValues(Baseline.PROJECT).stream()
                .map(Value::getMetric)
                .anyMatch(metrics::contains);
    }

    /**
     * Returns the trend chart configuration. Coverage metrics (percentages) and software metrics (absolute values)
     * are shown in separate charts, since they cannot share the same Y-axis. Which of the two charts is created is
     * selected by the boolean property {@code softwareMetrics} of the configuration. If this property is missing,
     * the coverage chart is shown, or the software metrics chart if the report does not contain any coverage.
     *
     * @param configuration
     *         JSON object to configure optional properties for the trend chart
     *
     * @return the trend chart model (converted to a JSON string)
     */
    @JavaScriptMethod
    @SuppressWarnings("unused")
    public LinesChartModel getTrendChart(final String configuration) {
        var factory = new TrendChartFactory();
        if (factory.getBoolean(configuration, "softwareMetrics", !hasCoverageMetrics())) {
            return factory.createSoftwareMetricsModel(
                    configuration, latestAction, TrendChartFactory.SOFTWARE_TREND_METRICS);
        }
        return factory.createChartModel(configuration, latestAction, TrendChartFactory.COVERAGE_TREND_METRICS);
    }
}
