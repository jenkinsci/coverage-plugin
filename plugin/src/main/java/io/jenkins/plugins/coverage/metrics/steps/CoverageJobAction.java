package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import hudson.model.Job;

import io.jenkins.plugins.coverage.metrics.charts.CoverageTrendChart;
import io.jenkins.plugins.coverage.metrics.charts.MetricsTrendChart;
import io.jenkins.plugins.coverage.metrics.charts.TrendChart;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.echarts.ActionSelector;
import io.jenkins.plugins.echarts.GenericBuildActionIterator.BuildActionIterable;
import io.jenkins.plugins.echarts.TrendChartJobAction;

/**
 * Project level action for the coverage results. A job action displays a link on the side panel of a job that refers to
 * the last build that contains coverage results (i.e. a {@link CoverageBuildAction} with a {@link Node} instance). This
 * action also is responsible to render the historical trend via its associated 'floatingBox.jelly' view.
 *
 * @author Ullrich Hafner
 */
public class CoverageJobAction extends TrendChartJobAction<CoverageBuildAction> {
    static final Set<Metric> DEFAULT_TREND_METRICS = Set.of(
            Metric.LINE, Metric.BRANCH,
            Metric.MUTATION, Metric.TEST_STRENGTH,
            Metric.NCSS, Metric.LOC, Metric.CYCLOMATIC_COMPLEXITY, Metric.COGNITIVE_COMPLEXITY);
    private static final Set<Metric> IGNORED_TREND_METRICS = Set.of(
            Metric.ACCESS_TO_FOREIGN_DATA, Metric.WEIGHED_METHOD_COUNT,
            Metric.NUMBER_OF_ACCESSORS,
            Metric.WEIGHT_OF_CLASS, Metric.COHESION, Metric.CONTAINER,
            Metric.FAN_OUT, Metric.MODULE);

    private static final JacksonFacade JACKSON = new JacksonFacade();

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
                .filter(m -> !IGNORED_TREND_METRICS.contains(m))
                .filter(m -> m.isCoverage() || latestAction.map(a -> !a.hasCoverage()).orElse(true))
                .toList();
    }

    @Override
    protected LinesChartModel createChartModel(final String configuration) {
        var latestAction = getLatestAction();
        var buildActions = new BuildActionIterable<>(CoverageBuildAction.class, latestAction,
                selectByUrl(), CoverageBuildAction::getStatistics);

        Set<Metric> actualValues = latestAction.map(a ->
                        a.getAllValues(Baseline.PROJECT).stream()
                                .map(Value::getMetric)
                                .collect(Collectors.toSet()))
                .orElseGet(() -> Arrays.stream(Metric.values()).collect(Collectors.toSet()));
        actualValues.retainAll(getVisibleMetrics(configuration));
        return getTrendChartType(latestAction, actualValues, useLines(configuration))
                .create(buildActions, ChartModelConfiguration.fromJson(configuration));
    }

    private boolean useLines(final String configuration) {
        return JACKSON.getBoolean(configuration, "useLines", false);
    }

    Set<Metric> getVisibleMetrics(final String configuration) {
        try {
            var objectMapper = new ObjectMapper();
            ObjectNode jsonNodes = objectMapper.readValue(configuration, ObjectNode.class);
            var metrics = jsonNodes.get("metrics");
            @SuppressWarnings("unchecked")
            Map<String, Boolean> metricMapping = objectMapper.convertValue(metrics, Map.class);
            if (metricMapping != null && !metricMapping.isEmpty()) {
                return metricMapping.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .map(Metric::valueOf)
                        .collect(Collectors.toSet());
            }
        }
        catch (JsonProcessingException | ClassCastException | IllegalArgumentException ignored) {
            // ignore and return default values
        }

        return DEFAULT_TREND_METRICS;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private TrendChart getTrendChartType(final Optional<CoverageBuildAction> latestAction,
            final Set<Metric> visibleMetrics, final boolean useLines) {
        if (latestAction.isPresent()) {
            var hasCoverage = latestAction.get().getAllValues(Baseline.PROJECT).stream()
                    .map(Value::getMetric).anyMatch(Metric::isCoverage);
            if (!hasCoverage) {
                return new MetricsTrendChart(visibleMetrics, useLines);
            }
        }
        return new CoverageTrendChart(visibleMetrics, useLines);
    }

    @Override
    public Optional<CoverageBuildAction> getLatestAction() {
        return new ActionSelector<>(CoverageBuildAction.class, selectByUrl()).findFirst(getOwner().getLastBuild());
    }

    private Predicate<CoverageBuildAction> selectByUrl() {
        return action -> getUrlName().equals(action.getUrlName());
    }
}
