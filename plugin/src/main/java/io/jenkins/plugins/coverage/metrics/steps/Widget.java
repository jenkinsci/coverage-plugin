package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.steps.OverviewModel.CoverageBar;
import io.jenkins.plugins.util.QualityGateStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Server side model of the Coverage widget on the build page. The widget is a compact version of the {@link
 * OverviewModel overview} of the Coverage tab and answers the question: "Did this build make the coverage worse, and
 * does that block anything?". It shows (from top to bottom):
 * <ul>
 *     <li>the overall status of the quality gates (or the status of the coverage deltas if there are no quality
 *     gates),</li>
 *     <li>the most important metrics of the whole project with their deltas, and the reference build they are
 *     compared to,</li>
 *     <li>the covered and missed items of the modified lines (or of the whole project),</li>
 *     <li>a pill for each result (only if there is more than one result), colored by the status of its quality gates,
 *     and</li>
 *     <li>a link to the log page if errors have been recorded.</li>
 * </ul>
 *
 * <p>
 * All values are delegated to the {@link OverviewModel overviews} of the individual results, so the widget and the
 * overviews always show the same numbers. The metrics and the bar are taken from the first result, i.e., the result
 * that is opened when the Coverage tab is selected. The layout is defined in the Jelly view
 * {@code RunTab/widget.jelly}.
 * </p>
 */
@SuppressWarnings("PMD.GodClass")
public class Widget {
    private static final String ERROR_STATUS = "error";
    private static final String WARNING_STATUS = "warning";
    private static final String NO_STATUS = "none";
    private static final String BETTER = "better";
    private static final String WORSE = "worse";
    private static final String NO_TREND = "none";

    private final List<OverviewModel> overviews;

    /**
     * Creates a new widget for the specified overviews.
     *
     * @param overviews
     *         the overviews of the coverage results that provide all values
     */
    Widget(final List<OverviewModel> overviews) {
        this.overviews = List.copyOf(overviews);
    }

    /**
     * Returns whether at least one of the results has an active quality gate.
     *
     * @return {@code true} if there are quality gates, {@code false} otherwise
     */
    public boolean hasQualityGates() {
        return overviews.stream().anyMatch(OverviewModel::hasQualityGates);
    }

    /**
     * Returns the overall status of the quality gates, i.e., the worst status of all results.
     *
     * @return the overall quality gate status
     */
    public QualityGateStatus getQualityGateStatus() {
        return overviews.stream()
                .map(OverviewModel::getQualityGateStatus)
                .max(Comparator.naturalOrder())
                .orElse(QualityGateStatus.INACTIVE);
    }

    /**
     * Returns the symbol that visualizes the overall status of the quality gates (see
     * {@link OverviewModel#getStatusIcon(QualityGateStatus)}).
     *
     * @return the symbol
     */
    public String getQualityGateIcon() {
        return OverviewModel.toStatusIcon(getQualityGateStatus());
    }

    /**
     * Returns a localized summary of the quality gates, e.g. "1 of 3 quality gates missed".
     *
     * @return the summary
     */
    public String getQualityGateSummary() {
        int missed = getMissedQualityGatesSize();
        int total =
                overviews.stream().mapToInt(OverviewModel::getQualityGatesSize).sum();
        if (missed == 0) {
            return Messages.Widget_qualityGatesPassed(total);
        }
        return Messages.Widget_qualityGatesMissed(missed, total);
    }

    /**
     * Returns the number of quality gates of all results that have been missed.
     *
     * @return the number of missed quality gates
     */
    public int getMissedQualityGatesSize() {
        return overviews.stream()
                .mapToInt(OverviewModel::getMissedQualityGatesSize)
                .sum();
    }

    /**
     * Returns the number of coverage metrics of all results whose value for the whole project decreased with respect
     * to the reference build (see {@link OverviewModel#getDecreasedMetrics()}).
     *
     * @return the number of decreased coverage metrics
     */
    public int getDecreasedMetricsSize() {
        return overviews.stream()
                .mapToInt(overview -> overview.getDecreasedMetrics().size())
                .sum();
    }

    /**
     * Returns the symbol that is shown in front of the summary lines if there are no quality gates. Without quality
     * gates, the status is derived from the coverage deltas: an unstable symbol if the coverage decreased, and a
     * success symbol otherwise. The symbols are the same build status symbols that are used for the quality gates.
     *
     * @return the symbol
     */
    public String getSymbol() {
        return OverviewModel.toStatusIcon(
                getDecreasedMetricsSize() > 0 ? QualityGateStatus.WARNING : QualityGateStatus.PASSED);
    }

    /**
     * Returns the summary lines that are shown if there are no quality gates. The first line states whether the
     * coverage decreased, the second line explains the status.
     *
     * @return the summary lines
     */
    public List<String> getLines() {
        int decreased = getDecreasedMetricsSize();
        if (decreased > 0) {
            return List.of(Messages.Widget_coverageDecreased(decreased), Messages.Widget_noQualityGates());
        }
        if (hasReferenceBuild()) {
            return List.of(Messages.Widget_noCoverageDecrease(), Messages.Widget_noQualityGates());
        }
        return List.of(Messages.Widget_coverageRecorded(), Messages.Widget_noQualityGates());
    }

    private Optional<OverviewModel> getPrimary() {
        return overviews.stream().findFirst();
    }

    /**
     * Returns the most important metrics of the first result (see {@link OverviewModel#getHeadlineMetrics()}),
     * formatted for the widget.
     *
     * @return the metrics
     */
    public List<MetricSummary> getMetrics() {
        return getPrimary().map(this::createMetrics).orElseGet(List::of);
    }

    private List<MetricSummary> createMetrics(final OverviewModel overview) {
        return overview.getHeadlineMetrics().stream()
                .map(metric -> createMetric(overview, metric))
                .toList();
    }

    private MetricSummary createMetric(final OverviewModel overview, final Metric metric) {
        var formatter = overview.getFormatter();
        var tooltip = createTooltip(overview, metric);
        if (overview.hasDelta(Baseline.PROJECT, metric)) {
            return new MetricSummary(
                    formatter.getLabel(metric),
                    overview.getShortValue(Baseline.PROJECT, metric),
                    overview.formatDelta(Baseline.PROJECT, metric),
                    toTrend(overview.getTrend(Baseline.PROJECT, metric)),
                    tooltip);
        }
        return new MetricSummary(
                formatter.getLabel(metric), overview.getShortValue(Baseline.PROJECT, metric), "", NO_TREND, tooltip);
    }

    private String createTooltip(final OverviewModel overview, final Metric metric) {
        var tooltip = "%s: %s"
                .formatted(
                        overview.getFormatter().getDisplayName(metric), overview.formatValue(Baseline.PROJECT, metric));
        if (overviews.size() > 1) {
            return "%s - %s".formatted(overview.getResultName(), tooltip);
        }
        return tooltip;
    }

    private static String toTrend(final double trend) {
        if (trend > 0) {
            return BETTER;
        }
        if (trend < 0) {
            return WORSE;
        }
        return NO_TREND;
    }

    /**
     * Returns the covered and missed items of the first result (see {@link OverviewModel#getCoverageBar()}).
     *
     * @return the covered and missed items, or an empty {@link Optional} if there are no coverage metrics
     */
    public Optional<CoverageBar> getCoverageBar() {
        return getPrimary().flatMap(OverviewModel::getCoverageBar);
    }

    private List<Run<?, ?>> getReferenceBuilds() {
        return overviews.stream()
                .map(OverviewModel::getReferenceBuild)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    /**
     * Returns whether at least one of the results has a reference build. Without a reference build, the deltas are
     * not available.
     *
     * @return {@code true} if there is a reference build, {@code false} otherwise
     */
    public boolean hasReferenceBuild() {
        return !getReferenceBuilds().isEmpty();
    }

    /**
     * Returns the caption below the metrics that names the reference build, e.g. "compared to #48". If the results
     * use different reference builds, the number of reference builds is shown.
     *
     * @return the caption
     */
    public String getComparisonCaption() {
        var builds = getReferenceBuilds();
        if (builds.size() > 1) {
            return Messages.OverviewModel_comparedTo(Messages.OverviewModel_referenceBuilds(builds.size()));
        }
        return overviews.stream()
                .filter(OverviewModel::hasReferenceBuild)
                .findFirst()
                .map(OverviewModel::getComparisonCaption)
                .orElse(Messages.OverviewModel_noReferenceBuild());
    }

    /**
     * Returns the URL of the reference build (relative to the Jenkins root), or an empty string if there is no single
     * reference build.
     *
     * @return the URL of the reference build
     */
    public String getReferenceBuildUrl() {
        var builds = getReferenceBuilds();
        if (builds.size() == 1) {
            return builds.get(0).getUrl();
        }
        return "";
    }

    /**
     * Returns a tooltip that lists the reference build of each result, e.g. "Unit Tests: #48, Mutations: #47".
     *
     * @return the tooltip
     */
    public String getReferenceBuildTooltip() {
        return overviews.stream()
                .map(overview -> overview.getResultName() + ": "
                        + (overview.hasReferenceBuild()
                                ? overview.getReferenceBuildName()
                                : Messages.OverviewModel_noReferenceBuild()))
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns the URL of the Coverage tab, relative to the run. The pill that summarizes the remaining results links
     * to this page, which opens the overview of the first result.
     *
     * @return the URL of the Coverage tab
     */
    public String getTabUrl() {
        return RunTab.URL;
    }

    /**
     * Returns whether the widget shows a pill for each result. If there is only one result, the pills are skipped,
     * since the metrics already show the details of this result.
     *
     * @return {@code true} if the pills should be shown, {@code false} otherwise
     */
    public boolean hasPills() {
        return overviews.size() > 1;
    }

    /**
     * Returns the results that get a pill. The results are sorted so that the most relevant results come first:
     * results with missed quality gates (worst status first), then results with the strongest coverage decrease.
     * Passed quality gates do not change the order.
     *
     * @return the results, sorted by relevance
     */
    public List<OverviewModel> getResults() {
        return overviews.stream()
                .sorted(Comparator.comparingInt(this::getStatusRank)
                        .reversed()
                        .thenComparingDouble(OverviewModel::getWorstTrend)
                        .thenComparing(OverviewModel::getResultName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Returns the value of the first headline metric of the specified result, e.g. "91.02%".
     *
     * @param overview
     *         the result
     *
     * @return the value, or an empty string if the result has no metrics
     */
    public String getValueOf(final OverviewModel overview) {
        return overview.getHeadlineMetrics().stream()
                .findFirst()
                .map(metric -> overview.getShortValue(Baseline.PROJECT, metric))
                .orElse("");
    }

    /**
     * Returns the tooltip of the pill of the specified result, e.g. "Line Coverage: 91.02% (3047/3176)".
     *
     * @param overview
     *         the result
     *
     * @return the tooltip, or an empty string if the result has no metrics
     */
    public String getTooltipOf(final OverviewModel overview) {
        return overview.getHeadlineMetrics().stream()
                .findFirst()
                .map(metric -> "%s: %s"
                        .formatted(
                                overview.getFormatter().getDisplayName(metric),
                                overview.formatValue(Baseline.PROJECT, metric)))
                .orElse("");
    }

    /**
     * Returns the delta of the first decreased coverage metric of the specified result, e.g. "-0.52%".
     *
     * @param overview
     *         the result
     *
     * @return the delta, or an empty string if no coverage metric decreased
     */
    public String getDecreaseOf(final OverviewModel overview) {
        return overview.getDecreasedMetrics().stream()
                .findFirst()
                .map(metric -> overview.formatDelta(Baseline.PROJECT, metric))
                .orElse("");
    }

    /**
     * Returns the CSS modifier that defines the color of the pill of the specified result: {@code error} if a
     * quality gate failed the step or the build, {@code warning} if a quality gate marked the step or the build as
     * unstable, and {@code none} otherwise.
     *
     * @param overview
     *         the result
     *
     * @return the CSS modifier
     */
    public String getStatusModifier(final OverviewModel overview) {
        return switch (overview.getQualityGateStatus()) {
            case ERROR, FAILED -> ERROR_STATUS;
            case NOTE, WARNING -> WARNING_STATUS;
            default -> NO_STATUS;
        };
    }

    private int getStatusRank(final OverviewModel overview) {
        return switch (getStatusModifier(overview)) {
            case ERROR_STATUS -> 2;
            case WARNING_STATUS -> 1;
            default -> 0;
        };
    }

    /**
     * Returns the number of error messages that have been recorded for all results.
     *
     * @return the number of error messages
     */
    public int getErrorMessagesSize() {
        return overviews.stream().mapToInt(OverviewModel::getErrorMessagesSize).sum();
    }

    /**
     * Returns the URL of the log page that shows the error messages, relative to the run. The log page of the first
     * result with errors is used.
     *
     * @return the URL of the log page, or the URL of the Coverage tab if there are no errors
     */
    public String getLogUrl() {
        return overviews.stream()
                .filter(overview -> overview.getErrorMessagesSize() > 0)
                .findFirst()
                .map(OverviewModel::getLogUrl)
                .orElse(RunTab.URL);
    }

    /**
     * A single metric of the widget, pre-formatted so that the view can render it without needing to call back into
     * the {@link OverviewModel}.
     */
    @SuppressWarnings("PMD.DataClass")
    public static final class MetricSummary {
        private final String label;
        private final String value;
        private final String delta;
        private final String trend;
        private final String tooltip;

        MetricSummary(
                final String label, final String value, final String delta, final String trend, final String tooltip) {
            this.label = label;
            this.value = value;
            this.delta = delta;
            this.trend = trend;
            this.tooltip = tooltip;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        /**
         * Returns the formatted delta with respect to the reference build, e.g. "+0.52%".
         *
         * @return the delta, or an empty string if there is no delta
         */
        public String getDelta() {
            return delta;
        }

        /**
         * Returns whether the delta is an improvement: {@code better} for an improvement, {@code worse} for a
         * deterioration, and {@code none} if there is no significant change or no delta.
         *
         * @return the trend
         */
        public String getTrend() {
            return trend;
        }

        public String getTooltip() {
            return tooltip;
        }
    }
}
