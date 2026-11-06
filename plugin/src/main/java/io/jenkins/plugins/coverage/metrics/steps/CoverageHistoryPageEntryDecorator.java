package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Run;
import hudson.widgets.HistoryWidget;
import java.util.Locale;
import java.util.Optional;
import jenkins.model.HistoricalBuild;
import jenkins.widgets.HistoryPageEntryDecorator;

/**
 * Shows the coverage of a single metric of a build in the build history of the job page. Each metric is shown by a
 * separate decorator (see {@link LineCoverage} and {@link BranchCoverage}), so that the build history can hide the
 * entries of the less important metrics if there is not enough space. Only the results of the default coverage
 * recorder (ID {@value CoverageRecorder#DEFAULT_ID}) are shown. The values are read from the summary that is stored
 * with the build, so rendering the history never loads the coverage tree of a build.
 *
 * @author Ullrich Hafner
 */
public abstract class CoverageHistoryPageEntryDecorator extends HistoryPageEntryDecorator {
    private final Metric metric;

    /**
     * Creates a new decorator for the specified metric.
     *
     * @param metric
     *         the metric to show
     */
    CoverageHistoryPageEntryDecorator(final Metric metric) {
        super();

        this.metric = metric;
    }

    @Override
    public boolean isApplicable(@NonNull final HistoryWidget<?, ?> widget, @NonNull final HistoricalBuild build) {
        return !getSummary(build).isEmpty();
    }

    /**
     * Returns the coverage summary of the specified build. Since the decorator is a singleton that is shared by all
     * entries of the history, the build specific state is stored in the summary.
     *
     * @param build
     *         the build to get the summary for
     *
     * @return the summary, or an empty summary if the build has no coverage results for the metric
     */
    @SuppressWarnings("unused") // Called by jelly view
    public CoverageHistorySummary getSummary(final HistoricalBuild build) {
        return getSummary(build, Functions.getCurrentLocale());
    }

    CoverageHistorySummary getSummary(final HistoricalBuild build, final Locale locale) {
        return findAction(build)
                .map(action -> CoverageHistorySummary.create(action, metric, this::getMissedDescription, locale))
                .orElseGet(CoverageHistorySummary::empty);
    }

    /**
     * Returns a localized description of the number of missed elements of the metric, e.g. "12 missed lines".
     *
     * @param missed
     *         the number of missed elements
     *
     * @return the description
     */
    protected abstract String getMissedDescription(int missed);

    /**
     * Returns the action of the default coverage recorder of the specified build.
     *
     * @param build
     *         the build to get the action for
     *
     * @return the action, or an empty optional if the build is no {@link Run} or has no such action
     */
    private Optional<CoverageBuildAction> findAction(final HistoricalBuild build) {
        if (build instanceof Run<?, ?> run) {
            return run.getActions(CoverageBuildAction.class).stream()
                    .filter(action -> CoverageRecorder.DEFAULT_ID.equals(action.getUrlName()))
                    .findFirst();
        }
        return Optional.empty();
    }

    /**
     * Shows the line coverage of a build in the build history. The ordinal places the entry right of the test
     * results (ordinal 0) and left of the branch coverage and the warnings.
     */
    @Extension(ordinal = -10)
    public static class LineCoverage extends CoverageHistoryPageEntryDecorator {
        /**
         * Creates a new decorator for the line coverage.
         */
        public LineCoverage() {
            super(Metric.LINE);
        }

        @Override
        protected String getMissedDescription(final int missed) {
            return Messages.HistoryEntry_Missed_Lines(missed);
        }
    }

    /**
     * Shows the branch coverage of a build in the build history. The ordinal places the entry right of the line
     * coverage and left of the warnings (ordinal -30).
     */
    @Extension(ordinal = -20)
    public static class BranchCoverage extends CoverageHistoryPageEntryDecorator {
        /**
         * Creates a new decorator for the branch coverage.
         */
        public BranchCoverage() {
            super(Metric.BRANCH);
        }

        @Override
        protected String getMissedDescription(final int missed) {
            return Messages.HistoryEntry_Missed_Branches(missed);
        }
    }
}
