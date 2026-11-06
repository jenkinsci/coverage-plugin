package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Coverage summary of a single build that is shown in the build history of the job page. Contains the already
 * formatted values of the selected metrics and their deltas to the reference build.
 *
 * @author Ullrich Hafner
 */
public final class CoverageHistorySummary {
    private static final ElementFormatter FORMATTER = new ElementFormatter();
    private static final CoverageHistorySummary NULL_OBJECT = new CoverageHistorySummary("", "", List.of());

    private final String urlName;
    private final String icon;
    private final List<MetricSummary> metrics;

    /**
     * Returns an empty summary, i.e., a summary without any metrics.
     *
     * @return an empty summary
     */
    static CoverageHistorySummary empty() {
        return NULL_OBJECT;
    }

    /**
     * Creates a summary for the specified metrics of the given action. Metrics that are not available in the action
     * are skipped.
     *
     * @param action
     *         the action with the coverage results
     * @param metrics
     *         the metrics to show
     * @param locale
     *         the locale to use to format the values
     *
     * @return the summary
     */
    static CoverageHistorySummary create(
            final CoverageBuildAction action, final List<Metric> metrics, final Locale locale) {
        var summaries = metrics.stream()
                .map(metric -> createMetricSummary(action, metric, locale))
                .flatMap(Optional::stream)
                .toList();
        if (summaries.isEmpty()) {
            return NULL_OBJECT;
        }
        return new CoverageHistorySummary(action.getUrlName(), action.getIcon(), summaries);
    }

    private static Optional<MetricSummary> createMetricSummary(
            final CoverageBuildAction action, final Metric metric, final Locale locale) {
        return action.getValueForMetric(Baseline.PROJECT, metric)
                .map(value -> createMetricSummary(action, metric, value, locale));
    }

    private static MetricSummary createMetricSummary(
            final CoverageBuildAction action, final Metric metric, final Value value, final Locale locale) {
        var formattedValue = FORMATTER.format(value, locale);
        var delta = action.getDelta(Baseline.PROJECT, metric);
        if (delta.isPresent()) {
            var formattedDelta = FORMATTER.formatDelta(delta.get(), locale);
            return new MetricSummary(
                    FORMATTER.getLabel(metric),
                    formattedValue,
                    formattedDelta,
                    Trend.of(delta.get().asDouble()),
                    Messages.HistoryEntry_Delta_Tooltip(
                            FORMATTER.getDisplayName(metric), formattedValue, formattedDelta));
        }
        return new MetricSummary(
                FORMATTER.getLabel(metric),
                formattedValue,
                "",
                Trend.UNCHANGED,
                Messages.HistoryEntry_Value_Tooltip(FORMATTER.getDisplayName(metric), formattedValue));
    }

    private CoverageHistorySummary(final String urlName, final String icon, final List<MetricSummary> metrics) {
        this.urlName = urlName;
        this.icon = icon;
        this.metrics = metrics;
    }

    /**
     * Returns whether this summary contains no metrics.
     *
     * @return {@code true} if there are no metrics to show, {@code false} otherwise
     */
    public boolean isEmpty() {
        return metrics.isEmpty();
    }

    /**
     * Returns the URL of the coverage results, relative to the build.
     *
     * @return the URL name of the coverage action
     */
    public String getUrlName() {
        return urlName;
    }

    /**
     * Returns the icon of the coverage results.
     *
     * @return the icon
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Returns the summaries of the individual metrics.
     *
     * @return the metric summaries
     */
    public List<MetricSummary> getMetrics() {
        return metrics;
    }

    /**
     * Returns the overall trend of all metrics compared to the reference build. The trend is
     * {@link Trend#INCREASED} if at least one metric increased and no metric decreased, and
     * {@link Trend#DECREASED} if at least one metric decreased and no metric increased. In all other cases (mixed
     * trends, no changes, or no reference build) the trend is {@link Trend#UNCHANGED}.
     *
     * @return the overall trend
     */
    public Trend getTrend() {
        return Trend.combine(metrics.stream().map(MetricSummary::getTrend).toList());
    }

    /**
     * Direction of the change of a metric compared to the reference build.
     */
    public enum Trend {
        INCREASED,
        DECREASED,
        UNCHANGED;

        /** Deltas are shown with two decimals, so smaller changes are rendered as unchanged (±0%). */
        private static final double EPSILON = 0.005;

        /**
         * Returns the trend for the specified delta.
         *
         * @param delta
         *         the delta to the reference build
         *
         * @return the trend
         */
        @SuppressWarnings("PMD.ShortMethodName")
        static Trend of(final double delta) {
            if (delta > EPSILON) {
                return INCREASED;
            }
            if (delta < -EPSILON) {
                return DECREASED;
            }
            return UNCHANGED;
        }

        /**
         * Combines the specified trends to an overall trend.
         *
         * @param trends
         *         the trends to combine
         *
         * @return {@link #INCREASED} or {@link #DECREASED} if all changed trends point in the same direction,
         *         {@link #UNCHANGED} otherwise
         */
        static Trend combine(final List<Trend> trends) {
            var hasIncreased = trends.contains(INCREASED);
            var hasDecreased = trends.contains(DECREASED);
            if (hasIncreased && !hasDecreased) {
                return INCREASED;
            }
            if (hasDecreased && !hasIncreased) {
                return DECREASED;
            }
            return UNCHANGED;
        }

        /**
         * Returns the name of the CSS modifier that represents this trend.
         *
         * @return the CSS modifier
         */
        public String getCssModifier() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }

    /**
     * Formatted value and delta of a single metric.
     */
    @SuppressWarnings("PMD.DataClass")
    public static final class MetricSummary {
        private final String label;
        private final String value;
        private final String delta;
        private final Trend trend;
        private final String tooltip;

        MetricSummary(
                final String label, final String value, final String delta, final Trend trend, final String tooltip) {
            this.label = label;
            this.value = value;
            this.delta = delta;
            this.trend = trend;
            this.tooltip = tooltip;
        }

        /**
         * Returns the short label of the metric.
         *
         * @return the label
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns the formatted value of the metric.
         *
         * @return the value
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns whether a delta to the reference build is available.
         *
         * @return {@code true} if a delta is available, {@code false} otherwise
         */
        public boolean hasDelta() {
            return !delta.isEmpty();
        }

        /**
         * Returns the formatted delta to the reference build.
         *
         * @return the delta, or an empty string if there is no delta
         */
        public String getDelta() {
            return delta;
        }

        /**
         * Returns the trend compared to the reference build.
         *
         * @return the trend
         */
        public Trend getTrend() {
            return trend;
        }

        /**
         * Returns the tooltip that describes the metric.
         *
         * @return the tooltip
         */
        public String getTooltip() {
            return tooltip;
        }
    }
}
