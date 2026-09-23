package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import hudson.model.ModelObject;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.util.QualityGateResult;

/**
 * Server side model that provides the data for the "Overview" tab of the coverage details view. Shows the same
 * baseline summaries, reference build information, and quality gate results that are also shown in the build summary
 * (see {@link CoverageBuildAction} and its corresponding {@code summary.jelly}). The layout of the associated view is
 * defined in the corresponding jelly view 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class OverviewModel implements ModelObject {
    private final Run<?, ?> owner;
    private final CoverageBuildAction action;

    OverviewModel(final Run<?, ?> owner, final CoverageBuildAction action) {
        this.owner = owner;
        this.action = action;
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverageOverviewModel_displayName();
    }

    @NonNull
    public String getId() {
        return action.getUrlName();
    }

    @NonNull
    public String getUrlName() {
        return action.getUrlName();
    }

    public Run<?, ?> getOwner() {
        return owner;
    }

    public Run<?, ?> getObject() {
        return owner;
    }

    public RunTab getTab() {
        return new RunTab(owner);
    }

    public ElementFormatter getFormatter() {
        return action.getFormatter();
    }

    /**
     * Returns the baselines for which coverage results are available.
     *
     * @return the available baselines
     */
    @SuppressWarnings("unused") // Called by jelly view
    public List<Baseline> getBaselines() {
        return action.getBaselines();
    }

    /**
     * Returns whether a delta metric for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     *
     * @return {@code true} if a delta is available for the specified metric, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasBaselineResult(final Baseline baseline) {
        return action.hasBaselineResult(baseline);
    }

    /**
     * Returns the title text for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the title for
     *
     * @return the title
     */
    public String getTitle(final Baseline baseline) {
        return action.getTitle(baseline);
    }

    /**
     * Returns all available values for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the values for
     *
     * @return the available values
     * @throws java.util.NoSuchElementException
     *         if this baseline does not provide values
     */
    public List<Value> getAllValues(final Baseline baseline) {
        return action.getAllValues(baseline);
    }

    /**
     * Returns all important values for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the values for
     *
     * @return the available values
     * @throws java.util.NoSuchElementException
     *         if this baseline does not provide values
     */
    public List<Value> getValues(final Baseline baseline) {
        return action.getValues(baseline);
    }

    /**
     * Returns the leaf coverage metrics (metrics that use a percentage scale from 0 to 100 and are not simply an
     * aggregation of the coverage tree structure, e.g. line, branch, or mutation coverage) that provide a value in
     * at least one of the available baselines. These are the metrics teams actually look at day to day, so they are
     * rendered as the prominent dashboard cards, with one gauge for each baseline that has a value for it (see
     * {@link #hasValue(Baseline, Metric)}). Compare with {@link #getCountMetrics()}, which shows the remaining,
     * non-card metrics (including the structural/container ones, e.g. file or package coverage) in a table.
     *
     * @return the leaf coverage metrics to show, in their natural order
     */
    @SuppressWarnings("unused") // Called by jelly view
    public List<Metric> getCoverageMetrics() {
        return getBaselines().stream()
                .flatMap(baseline -> action.getAllValues(baseline).stream())
                .map(Value::getMetric)
                .filter(OverviewModel::isCardMetric)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns whether the specified metric should be rendered as its own gauge card (alongside the leaf coverage
     * metrics), rather than as a row in the "Further metrics" table. This is normally the same as
     * {@link Metric#isCoverage()}, with two deliberate exceptions: a {@link Metric#isContainer() container} metric
     * (e.g. file or package coverage) is excluded even though it is a coverage metric -- it merely aggregates the
     * coverage tree structure and is rarely interesting on its own, so it is left for the "Further metrics" table
     * instead of getting a prominent card; and {@link Metric#TEST_STRENGTH} is included even though it is *not*
     * tagged as a coverage metric by the coverage model library (it is a mutation testing quality score, not code
     * coverage), because it is backed by the same covered/missed {@link Coverage} structure and reads the same way.
     * Package-private (rather than {@code private}) so {@link Widget} can reuse the same definition when it shows
     * the card metrics of a single build result as badges.
     *
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if the metric should be rendered as a card
     */
    static boolean isCardMetric(final Metric metric) {
        if (metric.isContainer()) {
            return false;
        }
        return metric.isCoverage() || metric == Metric.TEST_STRENGTH;
    }

    /**
     * Returns the metrics that are not rendered as a card (see {@link #getCoverageMetrics()}) and that provide a
     * value in at least one of the available baselines. Used to render the comparison table at the bottom of the
     * dashboard.
     *
     * @return the count metrics to show, in their natural order
     */
    @SuppressWarnings("unused") // Called by jelly view
    public List<Metric> getCountMetrics() {
        return getBaselines().stream()
                .flatMap(baseline -> action.getAllValues(baseline).stream())
                .map(Value::getMetric)
                .filter(metric -> !isCardMetric(metric))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns whether a value for the specified metric exists for the specified baseline. Not every build has
     * results for all baselines, and not every baseline provides every metric, so this is used to decide whether a
     * gauge (or table cell) should be rendered for a given metric and baseline at all.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a value is available for the specified metric, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasValue(final Baseline baseline, final Metric metric) {
        return action.hasValue(baseline, metric);
    }

    /**
     * Returns a formatted and localized String representation of the value for the specified metric (with respect
     * to the given baseline), including the number of covered and missed items (e.g. {@code "95.94% (3047/3176)"}).
     * This is too long to fit inside a gauge, so it is used as the gauge's tooltip (see {@link #getShortValue}
     * for the text that is rendered inside the gauge itself).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the value for
     *
     * @return the formatted value, with details
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String formatValue(final Baseline baseline, final Metric metric) {
        return action.formatValue(baseline, metric);
    }

    /**
     * Returns a formatted and localized String representation of the value for the specified metric (with respect
     * to the given baseline), without the number of covered and missed items (e.g. {@code "95.94%"}). Used inside
     * the gauges, which are too small to also fit the covered/missed detail (see {@link #formatValue}).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the value for
     *
     * @return the formatted value, without details
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getShortValue(final Baseline baseline, final Metric metric) {
        return action.getValueForMetric(baseline, metric)
                .map(value -> action.getFormatter().format(value))
                .orElse(Messages.Coverage_Not_Available());
    }

    /**
     * Returns the percentage (in the interval [0, 100]) of the specified coverage metric and baseline, formatted
     * with a period as the decimal separator regardless of the active locale. This value is meant to be read by the
     * client-side JavaScript (as a {@code data-value} attribute) so that it can compute the sweep angle and color of
     * the corresponding gauge; it is not meant to be shown to the user (use {@link #formatValue(Baseline, Metric)}
     * for that).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the (coverage) metric to get the percentage for
     *
     * @return the percentage in the interval [0, 100], formatted with {@link Locale#ENGLISH}, or {@code "0"} if no
     *         value is available
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getPercentage(final Baseline baseline, final Metric metric) {
        return action.getValueForMetric(baseline, metric)
                .map(value -> String.format(Locale.ENGLISH, "%.2f", asPercentage(value)))
                .orElse("0");
    }

    private static double asPercentage(final Value value) {
        if (value instanceof Coverage coverage) {
            return coverage.getCoveredPercentage().toDouble();
        }
        return value.asDouble();
    }

    /**
     * Returns the number of covered and total items for the specified coverage metric and baseline, formatted as
     * {@code "1234 / 2000"}. Shown as a small caption underneath a gauge, since that raw information does not fit
     * inside the gauge itself (see {@link #getShortValue}). Empty if no value is available, or if the value is not
     * backed by covered/total counts (which is only the case for the metrics returned by
     * {@link #getCoverageMetrics()}).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the (coverage) metric to get the covered/total counts for
     *
     * @return the covered and total counts, or an empty string if not available
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getCoveredTotal(final Baseline baseline, final Metric metric) {
        return action.getValueForMetric(baseline, metric)
                .filter(Coverage.class::isInstance)
                .map(Coverage.class::cast)
                .map(coverage -> coverage.getCovered() + " / " + coverage.getTotal())
                .orElse("");
    }

    /**
     * Returns whether a delta metric for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a delta is available for the specified metric, {@code false} otherwise
     */
    public boolean hasDelta(final Baseline baseline, final Metric metric) {
        return action.hasDelta(baseline, metric);
    }

    /**
     * Returns whether the trend of the values for the specific metric is positive or negative.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return a positive value if the trend is positive, a negative value if the trend is negative, or {@code 0} if
     *         there is no significant change in the trend
     */
    @SuppressWarnings("unused") // Called by jelly view
    public double getTrend(final Baseline baseline, final Metric metric) {
        return action.getTrend(baseline, metric);
    }

    /**
     * Returns a formatted and localized String representation of the delta for the specified metric (with respect to
     * the given baseline).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the delta for
     *
     * @return the delta metric
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String formatDelta(final Baseline baseline, final Metric metric) {
        return action.formatDelta(baseline, metric);
    }

    /**
     * Returns the possible reference build that has been used to compute the coverage delta.
     *
     * @return the reference build, if available
     */
    public Optional<Run<?, ?>> getReferenceBuild() {
        return action.getReferenceBuild();
    }

    /**
     * Returns the text for the reference build button in the app bar ("Reference build &lt;full display name&gt;"),
     * e.g. to be shown next to the green/red threshold inputs. Only meaningful when {@link #getReferenceBuild()} is
     * present; returns an empty string otherwise.
     *
     * @return the button text, or an empty string if there is no reference build
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getReferenceBuildText() {
        return getReferenceBuild().map(build -> Messages.OverviewModel_referenceBuildButton(build.getFullDisplayName()))
                .orElse("");
    }

    /**
     * Returns the URL of the reference build (relative to the Jenkins root), to be used as the link target for the
     * reference build button in the app bar. Only meaningful when {@link #getReferenceBuild()} is present; returns
     * an empty string otherwise.
     *
     * @return the URL of the reference build, or an empty string if there is no reference build
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getReferenceBuildUrl() {
        return getReferenceBuild().map(Run::getUrl).orElse("");
    }

    public QualityGateResult getQualityGateResult() {
        return action.getQualityGateResult();
    }
}
