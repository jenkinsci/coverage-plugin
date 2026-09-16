package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Optional;

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
     * Renders the reference build as an HTML link.
     *
     * @return the reference build
     * @see #getReferenceBuild()
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getReferenceBuildLink() {
        return action.getReferenceBuildLink();
    }

    public QualityGateResult getQualityGateResult() {
        return action.getQualityGateResult();
    }
}
