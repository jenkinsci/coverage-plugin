package io.jenkins.plugins.coverage.metrics.steps;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.core.JacksonException;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.line.LinesChartModel;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.jenkins.plugins.coverage.metrics.charts.CoverageTrendChart;
import io.jenkins.plugins.coverage.metrics.charts.MetricsTrendChart;
import io.jenkins.plugins.coverage.metrics.charts.TrendChart;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.echarts.GenericBuildActionIterator.BuildActionIterable;

/**
 * Creates trend charts for coverage results.
 *
 * @author Ullrich Hafner
 */
class TrendChartFactory {
    private static final JacksonFacade JACKSON = new JacksonFacade();
    static final Set<Metric> DEFAULT_TREND_METRICS = Set.of(
            Metric.LINE, Metric.BRANCH,
            Metric.MUTATION, Metric.TEST_STRENGTH,
            Metric.NCSS, Metric.LOC, Metric.CYCLOMATIC_COMPLEXITY, Metric.COGNITIVE_COMPLEXITY);
    static final Set<Metric> IGNORED_TREND_METRICS = Set.of(
                    Metric.ACCESS_TO_FOREIGN_DATA, Metric.WEIGHED_METHOD_COUNT,
                    Metric.NUMBER_OF_ACCESSORS,
                    Metric.WEIGHT_OF_CLASS, Metric.COHESION, Metric.CONTAINER,
                    Metric.FAN_OUT, Metric.MODULE);

    LinesChartModel createMetricsModel(final String configuration, final CoverageBuildAction latestAction) {
        return getLinesChartModel(configuration, latestAction, true);
    }

    LinesChartModel createChartModel(final String configuration, final CoverageBuildAction latestAction) {
        return getLinesChartModel(configuration, latestAction, false);
    }

    private LinesChartModel getLinesChartModel(final String configuration, final CoverageBuildAction latestAction,
            final boolean isMetric) {
        var buildActions = new BuildActionIterable<>(CoverageBuildAction.class, Optional.of(latestAction),
                action -> latestAction.getUrlName().equals(action.getUrlName()),
                CoverageBuildAction::getStatistics);

        Set<Metric> actualValues = latestAction.getAllValues(Baseline.PROJECT).stream()
                .map(Value::getMetric)
                .collect(Collectors.toSet());
        actualValues.retainAll(getVisibleMetrics(configuration));

        return getTrendChartType(latestAction, actualValues, useLines(configuration), isMetric)
                .create(buildActions, ChartModelConfiguration.fromJson(configuration));
    }

    private boolean useLines(final String configuration) {
        return JACKSON.getBoolean(configuration, "useLines", false);
    }

    Set<Metric> getVisibleMetrics(final String configuration) {
        try {
            var objectMapper = new ObjectMapper();
            var jsonNodes = objectMapper.readValue(configuration, ObjectNode.class);
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
        catch (JacksonException | ClassCastException | IllegalArgumentException ignored) {
            // ignore and return default values
        }

        return DEFAULT_TREND_METRICS;
    }

    private TrendChart getTrendChartType(final CoverageBuildAction latestAction,
            final Set<Metric> visibleMetrics, final boolean useLines, final boolean isMetric) {
        var hasCoverage = latestAction.getAllValues(Baseline.PROJECT).stream()
                .map(Value::getMetric).anyMatch(Metric::isCoverage);
        if (isMetric || !hasCoverage) {
            return new MetricsTrendChart(visibleMetrics, useLines);
        }
        return new CoverageTrendChart(visibleMetrics, useLines);
    }
}
