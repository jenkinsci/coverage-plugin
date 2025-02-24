package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import hudson.model.Job;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.echarts.ActionSelector;
import io.jenkins.plugins.echarts.TrendChartJobAction;

/**
 * Project level action for the coverage results. A job action displays a link on the side panel of a job that refers to
 * the last build that contains coverage results (i.e. a {@link CoverageBuildAction} with a {@link Node} instance). This
 * action also is responsible to render the historical trend via its associated 'floatingBox.jelly' view.
 *
 * @author Ullrich Hafner
 */
public class CoverageJobAction extends TrendChartJobAction<CoverageBuildAction> {
    private static final LinesChartModel EMPTY_CHART = new LinesChartModel();

    private final String id;
    private final String name;
    private final String icon;

    CoverageJobAction(final Job<?, ?> owner, final String id, final String name, final String icon) {
        super(owner, CoverageBuildAction.class);

        this.id = id;
        this.name = name;
        this.icon = icon;
    }

    @Override
    public String getIconFileName() {
        return icon;
    }

    @Override
    public String getDisplayName() {
        return StringUtils.defaultIfBlank(name, Messages.Coverage_Link_Name());
    }

    /**
     * Returns a label for the trend chart.
     *
     * @return a label for the trend chart
     */
    public String getTrendName() {
        if (StringUtils.isBlank(name)) {
            return Messages.Coverage_Trend_Default_Name();
        }
        return Messages.Coverage_Trend_Name(name);
    }

    @Override @NonNull
    public String getUrlName() {
        return id;
    }

    public Job<?, ?> getProject() {
        return getOwner();
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    /**
     * Returns the metrics that are available for the trend chart.
     *
     * @return the available metrics
     */
    @SuppressWarnings("unused") // Used in trend chart configuration
    public List<Metric> getTrendMetrics() {
        var latestAction = getLatestAction();

        return latestAction.stream()
                .map(a -> a.getAllValues(Baseline.PROJECT))
                .flatMap(Collection::stream)
                .map(Value::getMetric)
                .filter(m -> !TrendChartFactory.IGNORED_TREND_METRICS.contains(m))
                .filter(m -> m.isCoverage() || latestAction.map(a -> !a.hasCoverage()).orElse(true))
                .toList();
    }

    @Override
    protected LinesChartModel createChartModel(final String configuration) {
        var latestAction = getLatestAction();

        return latestAction
                .map(a -> new TrendChartFactory().createChartModel(configuration, a))
                .orElse(EMPTY_CHART);
    }

    @Override
    public Optional<CoverageBuildAction> getLatestAction() {
        return new ActionSelector<>(CoverageBuildAction.class,
                action -> getUrlName().equals(action.getUrlName()))
                .findFirst(getOwner().getLastBuild());
    }
}
