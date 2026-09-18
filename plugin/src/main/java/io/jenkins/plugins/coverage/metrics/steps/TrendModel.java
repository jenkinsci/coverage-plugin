package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.echarts.line.LinesChartModel;

import org.kohsuke.stapler.bind.JavaScriptMethod;
import hudson.model.ModelObject;
import hudson.model.Run;

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
     * Returns the trend chart configuration.
     *
     * @param configuration
     *         JSON object to configure optional properties for the trend chart
     *
     * @return the trend chart model (converted to a JSON string)
     */
    @JavaScriptMethod
    @SuppressWarnings("unused")
    public LinesChartModel getTrendChart(final String configuration) {
        return createCoverageModel(configuration);
    }

    private LinesChartModel createCoverageModel(final String configuration) {
        return new TrendChartFactory().createChartModel(configuration, latestAction);
    }
}
