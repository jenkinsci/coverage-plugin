package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.util.QualityGateResult;

/**
 * Model for the coverage widget.
 */
public class Widget {
    /** Alpha value (0-100) used for the badge background color, matching the coverage tables elsewhere. */
    private static final int BADGE_COLOR_ALPHA = 80;

    private final String message;
    private final List<CoverageBuildAction> results;

    /**
     * Creates a new widget.
     *
     * @param results
     *         the list of results to display in the widget
     */
    public Widget(final List<CoverageBuildAction> results) {
        this.results = new ArrayList<>(results);

        var failed = getFailedQualityGates();
        if (failed == 1) {
            message = Messages.Widget_failedQualityGate();
        }
        else if (failed > 1) {
            message = Messages.Widget_failedQualityGates(failed);
        }
        else {
            var active = results.stream()
                    .map(CoverageBuildAction::getQualityGateResult)
                    .filter(Predicate.not(QualityGateResult::isInactive))
                    .count();

            if (active > 0) {
                message = Messages.Widget_passedQualityGates();
            }
            else {
                message = Messages.Widget_noQualityGates();
            }
        }
    }

    private long getFailedQualityGates() {
        return results.stream()
                .map(CoverageBuildAction::getQualityGateResult)
                .filter(Predicate.not(QualityGateResult::isSuccessful))
                .count();
    }

    public String getSymbol() {
        return "symbol-footsteps-outline plugin-ionicons-api";
    }

    /**
     * Returns the CSS class to show the coverage logo in. The color depends on the quality gates results, successful is
     * returned only when all gates are passed.
     *
     * @return the CSS class to show the coverage logo in
     */
    public String getIconColor() {
        if (getFailedQualityGates() == 0) {
            return "jenkins-!-success-color";
        }
        return "jenkins-!-warning-color";
    }

    public String getMessage() {
        return message;
    }

    public List<CoverageBuildAction> getResults() {
        return results;
    }

    /**
     * Returns the single available result, if there is exactly one coverage result for this build. Showing a
     * generic pill per result (see {@link #getResults()}) is useful only to distinguish between several results;
     * when there is just one, the widget shows its card metrics (see {@link OverviewModel#isCardMetric}) as
     * colored badges instead, which is more informative at a glance.
     *
     * @return the single result, or an empty {@link Optional} if there is not exactly one result
     */
    public Optional<CoverageBuildAction> getSingleResult() {
        return results.size() == 1 ? Optional.of(results.getFirst()) : Optional.empty();
    }

    /**
     * Returns the card metrics (see {@link OverviewModel#isCardMetric}) of the project baseline for the single
     * available result, pre-formatted for rendering as colored badges (same coloring as the plugin's own coverage
     * tables, e.g. the file coverage table). Empty unless {@link #getSingleResult()} is present.
     *
     * @return the card metric badges of the single result, sorted by metric; an empty list if there is not exactly
     *         one result
     */
    public List<MetricBadge> getSingleResultMetrics() {
        return getSingleResult().map(single -> {
            var formatter = single.getFormatter();
            var reportId = single.getUrlName();
            return single.getAllValues(Baseline.PROJECT).stream()
                    .filter(value -> OverviewModel.isCardMetric(value.getMetric()))
                    .sorted(Comparator.comparing(Value::getMetric))
                    .map(value -> createBadge(formatter, value, reportId))
                    .collect(Collectors.<MetricBadge>toList());
        }).orElseGet(List::of);
    }

    private static MetricBadge createBadge(final ElementFormatter formatter, final Value value,
            final String reportId) {
        var colors = formatter.getDisplayColors(Baseline.PROJECT, value);
        var fillPercentage = formatter.getBackgroundColorFillPercentage(value);
        var style = "background-image: linear-gradient(90deg, %s %s, transparent %s);".formatted(
                colors.getFillColorAsRGBAHex(BADGE_COLOR_ALPHA), fillPercentage, fillPercentage);
        var metric = value.getMetric();
        var tooltip = "%s: %s".formatted(formatter.getDisplayName(metric),
                formatter.formatAdditionalInformation(value));

        return new MetricBadge(formatter.getLabel(metric), formatter.format(value), tooltip, style,
                toPercentage(value), fillPercentage, reportId);
    }

    /**
     * Returns the percentage of the given value on the same 0-100 scale used everywhere else in the plugin (e.g.
     * the Overview tab's ring gauges), regardless of whether it is backed by a {@link Coverage} instance (the
     * common case) or a plain {@link Value} such as a raw count.
     *
     * @param value
     *         the value to convert
     *
     * @return the percentage, from 0 to 100
     */
    private static double toPercentage(final Value value) {
        if (value instanceof Coverage coverage) {
            return coverage.getCoveredPercentage().toDouble();
        }
        return value.asDouble();
    }

    /**
     * A single card metric of {@link #getSingleResultMetrics()}, pre-formatted so the view can render it as a
     * colored badge without needing to call back into the {@link ElementFormatter}. Also carries the raw
     * percentage, the fill-percentage text, and the report ID (see {@link #getPercentage()}, {@link
     * #getFillPercentage()}, {@link #getReportId()}) so that client-side script can recolor the badge using the
     * green/red thresholds the user has configured for this report on the Overview tab -- those are only ever
     * persisted to the browser's local storage, so they are not available while rendering this badge on the
     * server, which instead uses the plugin's default thresholds.
     */
    @SuppressWarnings("PMD.DataClass")
    public static final class MetricBadge {
        private final String label;
        private final String value;
        private final String tooltip;
        private final String style;
        private final double percentage;
        private final String fillPercentage;
        private final String reportId;

        private MetricBadge(final String label, final String value, final String tooltip, final String style,
                final double percentage, final String fillPercentage, final String reportId) {
            this.label = label;
            this.value = value;
            this.tooltip = tooltip;
            this.style = style;
            this.percentage = percentage;
            this.fillPercentage = fillPercentage;
            this.reportId = reportId;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        public String getTooltip() {
            return tooltip;
        }

        public String getStyle() {
            return style;
        }

        public double getPercentage() {
            return percentage;
        }

        public String getFillPercentage() {
            return fillPercentage;
        }

        public String getReportId() {
            return reportId;
        }
    }
}
