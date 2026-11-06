package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Run;
import hudson.widgets.HistoryWidget;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import jenkins.model.HistoricalBuild;
import jenkins.widgets.HistoryPageEntryDecorator;

/**
 * Shows the line and branch coverage of a build in the build history of the job page. Only the results of the
 * default coverage recorder (ID {@value CoverageRecorder#DEFAULT_ID}) are shown. The values are read from the
 * summary that is stored with the build, so rendering the history never loads the coverage tree of a build.
 *
 * @author Ullrich Hafner
 */
@Extension
public class CoverageHistoryPageEntryDecorator extends HistoryPageEntryDecorator {
    static final List<Metric> METRICS = List.of(Metric.LINE, Metric.BRANCH);

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
     * @return the summary, or an empty summary if the build has no coverage results
     */
    @SuppressWarnings("unused") // Called by jelly view
    public CoverageHistorySummary getSummary(final HistoricalBuild build) {
        return getSummary(build, Functions.getCurrentLocale());
    }

    CoverageHistorySummary getSummary(final HistoricalBuild build, final Locale locale) {
        return findAction(build)
                .map(action -> CoverageHistorySummary.create(action, METRICS, locale))
                .orElseGet(CoverageHistorySummary::empty);
    }

    private Optional<CoverageBuildAction> findAction(final HistoricalBuild build) {
        if (build instanceof Run<?, ?> run) {
            return run.getActions(CoverageBuildAction.class).stream()
                    .filter(action -> CoverageRecorder.DEFAULT_ID.equals(action.getUrlName()))
                    .findFirst();
        }
        return Optional.empty();
    }
}
