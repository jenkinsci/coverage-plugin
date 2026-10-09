package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.line.LinesChartModel;
import io.jenkins.plugins.coverage.metrics.charts.CoverageTrendChart;
import io.jenkins.plugins.coverage.metrics.charts.MetricsTrendChart;
import io.jenkins.plugins.coverage.metrics.charts.TrendChart;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.echarts.GenericBuildActionIterator.BuildActionIterable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Creates trend charts for coverage results.
 *
 * @author Ullrich Hafner
 */
class TrendChartFactory {
    /**
     * Default metrics for the trend charts of the legacy single-page view. Intentionally excludes
     * {@link Metric#INSTRUCTION}, keeping the historical default so that switching the new-build-page
     * experimental flag on or off cannot change what a user of the legacy view sees by default.
     */
    static final Set<Metric> LEGACY_DEFAULT_TREND_METRICS = Set.of(
            Metric.LINE,
            Metric.BRANCH,
            Metric.MUTATION,
            Metric.TEST_STRENGTH,
            Metric.NCSS,
            Metric.LOC,
            Metric.CYCLOMATIC_COMPLEXITY,
            Metric.COGNITIVE_COMPLEXITY);

    static final Set<Metric> IGNORED_TREND_METRICS = Set.of(
            Metric.ACCESS_TO_FOREIGN_DATA,
            Metric.WEIGHED_METHOD_COUNT,
            Metric.NUMBER_OF_ACCESSORS,
            Metric.WEIGHT_OF_CLASS,
            Metric.COHESION,
            Metric.CONTAINER,
            Metric.FAN_OUT,
            Metric.MODULE);

    /**
     * All metrics that are shown in the coverage trend chart of the new (tabbed) run UI. These metrics are
     * percentages (i.e., they are rendered with a trailing '%'), so they share a common scale in the interval
     * [0, 100]. Also used as the fallback when a configuration does not specify an explicit metric selection.
     */
    static final Set<Metric> COVERAGE_TREND_METRICS = Arrays.stream(Metric.values())
            .filter(TrendChartFactory::isTrendMetric)
            .filter(TrendChartFactory::isPercentage)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * All metrics that are shown in the software metrics trend chart of the new (tabbed) run UI. These metrics are
     * absolute values (lines of code, complexity, ...), so they must not be mixed with the percentages of
     * {@link #COVERAGE_TREND_METRICS} in one chart.
     */
    static final Set<Metric> SOFTWARE_TREND_METRICS = Arrays.stream(Metric.values())
            .filter(TrendChartFactory::isTrendMetric)
            .filter(metric -> !isPercentage(metric))
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Returns whether the specified metric can be shown in a trend chart at all. Container metrics (modules, packages,
     * files, ...) are never shown. The metrics that are ignored on purpose are only ignored if they are absolute
     * values: percentages (e.g., class cohesion) are always shown.
     *
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if the metric can be shown in a trend chart
     */
    private static boolean isTrendMetric(final Metric metric) {
        return !metric.isContainer() && (!IGNORED_TREND_METRICS.contains(metric) || isPercentage(metric));
    }

    /**
     * Returns whether the specified metric is shown as a percentage. This is decided by the way the value is
     * displayed, so that percentages that are not coverage metrics (e.g., the test strength) are treated like
     * coverage.
     *
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if the values of the metric are rendered as a percentage
     */
    private static boolean isPercentage(final Metric metric) {
        return metric.isCoverage() || metric.format(Locale.ENGLISH, 0.5).endsWith("%");
    }

    LinesChartModel createMetricsModel(
            final String configuration, final CoverageBuildAction latestAction, final Set<Metric> defaultMetrics) {
        return getLinesChartModel(configuration, latestAction, true, false, defaultMetrics);
    }

    /**
     * Creates the trend chart for absolute software metrics. In contrast to
     * {@link #createMetricsModel(String, CoverageBuildAction, Set)}, the Y-axis can be configured to start at zero,
     * and only the minimum of the Y-axis is fixed: the maximum adapts to the series that are selected in the legend.
     *
     * @param configuration
     *         JSON object to configure optional properties for the trend chart
     * @param latestAction
     *         the latest action that provides the results of the builds to show
     * @param defaultMetrics
     *         the metrics to show if the configuration does not select any metrics
     *
     * @return the chart model
     */
    LinesChartModel createSoftwareMetricsModel(
            final String configuration, final CoverageBuildAction latestAction, final Set<Metric> defaultMetrics) {
        var linesChartModel = getLinesChartModel(configuration, latestAction, true, true, defaultMetrics);
        linesChartModel.setZeroBasedYAxis(useZeroBasedAxis(configuration));
        return linesChartModel;
    }

    LinesChartModel createChartModel(
            final String configuration, final CoverageBuildAction latestAction, final Set<Metric> defaultMetrics) {
        var linesChartModel = getLinesChartModel(configuration, latestAction, false, false, defaultMetrics);
        linesChartModel.setZeroBasedYAxis(useZeroBasedAxis(configuration));
        return linesChartModel;
    }

    private LinesChartModel getLinesChartModel(
            final String configuration,
            final CoverageBuildAction latestAction,
            final boolean isMetric,
            final boolean adaptiveMaximum,
            final Set<Metric> defaultMetrics) {
        var buildActions = new BuildActionIterable<>(
                CoverageBuildAction.class,
                Optional.of(latestAction),
                action -> latestAction.getUrlName().equals(action.getUrlName()),
                CoverageBuildAction::getStatistics);

        Set<Metric> actualValues = latestAction.getAllValues(Baseline.PROJECT).stream()
                .map(Value::getMetric)
                .collect(Collectors.toSet());
        actualValues.retainAll(getVisibleMetrics(configuration, defaultMetrics));

        return getTrendChartType(latestAction, actualValues, useLines(configuration), isMetric, adaptiveMaximum)
                .create(buildActions, ChartModelConfiguration.fromJson(configuration));
    }

    private boolean useLines(final String configuration) {
        return getBoolean(configuration, "useLines", false);
    }

    private boolean useZeroBasedAxis(final String configuration) {
        return getBoolean(configuration, "zeroBasedYAxis", false);
    }

    boolean getBoolean(final String json, final String property, final boolean defaultValue) {
        try {
            var node = new ObjectMapper().readValue(json, ObjectNode.class);
            var typeNode = node.get(property);
            if (typeNode != null) {
                return typeNode.asBoolean(defaultValue);
            }
        } catch (JacksonException exception) {
            // ignore
        }

        return defaultValue;
    }

    Set<Metric> getVisibleMetrics(final String configuration, final Set<Metric> defaultMetrics) {
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
        } catch (JacksonException | ClassCastException | IllegalArgumentException ignored) {
            // ignore and return default values
        }

        return defaultMetrics;
    }

    private TrendChart getTrendChartType(
            final CoverageBuildAction latestAction,
            final Set<Metric> visibleMetrics,
            final boolean useLines,
            final boolean isMetric,
            final boolean adaptiveMaximum) {
        var hasCoverage = latestAction.getAllValues(Baseline.PROJECT).stream()
                .map(Value::getMetric)
                .anyMatch(Metric::isCoverage);
        if (isMetric || !hasCoverage) {
            return new MetricsTrendChart(visibleMetrics, useLines, adaptiveMaximum);
        }
        return new CoverageTrendChart(visibleMetrics, useLines);
    }
}
