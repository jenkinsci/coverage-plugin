package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * Coverage summary of a single metric of a build that is shown in the build history of the job page. Contains the
 * already formatted value of the metric, its delta to the reference build, and the number of missed elements.
 *
 * @author Ullrich Hafner
 */
public final class CoverageHistorySummary {
    private static final ElementFormatter FORMATTER = new ElementFormatter();
    private static final CoverageHistorySummary NULL_OBJECT =
            new CoverageHistorySummary("", "", "", "", Trend.UNCHANGED, "", "");

    private final String urlName;
    private final String icon;
    private final String value;
    private final String delta;
    private final Trend trend;
    private final String tooltip;
    private final String details;

    /**
     * Returns an empty summary, i.e., a summary without a value.
     *
     * @return an empty summary
     */
    static CoverageHistorySummary empty() {
        return NULL_OBJECT;
    }

    /**
     * Creates a summary for the specified metric of the given action. If the metric is not available in the action,
     * an empty summary is returned.
     *
     * @param action
     *         the action with the coverage results
     * @param metric
     *         the metric to show
     * @param missedDescription
     *         the function that describes the number of missed elements of the metric
     * @param locale
     *         the locale to use to format the values
     *
     * @return the summary
     */
    static CoverageHistorySummary create(
            final CoverageBuildAction action,
            final Metric metric,
            final IntFunction<String> missedDescription,
            final Locale locale) {
        return action.getValueForMetric(Baseline.PROJECT, metric)
                .map(value -> create(action, metric, value, missedDescription, locale))
                .orElse(NULL_OBJECT);
    }

    private static CoverageHistorySummary create(
            final CoverageBuildAction action,
            final Metric metric,
            final Value value,
            final IntFunction<String> missedDescription,
            final Locale locale) {
        var formattedValue = FORMATTER.format(value, locale);
        var details = value instanceof Coverage coverage ? missedDescription.apply(coverage.getMissed()) : "";
        var delta = action.getDelta(Baseline.PROJECT, metric);
        if (delta.isPresent()) {
            var formattedDelta = FORMATTER.formatDelta(delta.get(), locale);
            return new CoverageHistorySummary(
                    action.getUrlName(),
                    FORMATTER.getIcon(metric),
                    formattedValue,
                    formattedDelta,
                    Trend.of(delta.get().asDouble()),
                    Messages.HistoryEntry_Delta_Tooltip(
                            FORMATTER.getDisplayName(metric), formattedValue, formattedDelta),
                    details);
        }
        return new CoverageHistorySummary(
                action.getUrlName(),
                FORMATTER.getIcon(metric),
                formattedValue,
                "",
                Trend.UNCHANGED,
                Messages.HistoryEntry_Value_Tooltip(FORMATTER.getDisplayName(metric), formattedValue),
                details);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private CoverageHistorySummary(
            final String urlName,
            final String icon,
            final String value,
            final String delta,
            final Trend trend,
            final String tooltip,
            final String details) {
        this.urlName = urlName;
        this.icon = icon;
        this.value = value;
        this.delta = delta;
        this.trend = trend;
        this.tooltip = tooltip;
        this.details = details;
    }

    /**
     * Returns whether this summary contains no value.
     *
     * @return {@code true} if there is no value to show, {@code false} otherwise
     */
    public boolean isEmpty() {
        return value.isEmpty();
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
     * Returns the icon of the metric.
     *
     * @return the icon
     */
    public String getIcon() {
        return icon;
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

    /**
     * Returns whether details about the missed elements are available.
     *
     * @return {@code true} if details are available, {@code false} otherwise
     */
    public boolean hasDetails() {
        return !details.isEmpty();
    }

    /**
     * Returns a description of the missed elements of the metric, e.g. "12 missed lines".
     *
     * @return the details, or an empty string if there are no details
     */
    public String getDetails() {
        return details;
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
         * Returns the name of the CSS modifier that represents this trend.
         *
         * @return the CSS modifier
         */
        public String getCssModifier() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }
}
