package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import hudson.widgets.HistoryWidget;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.steps.CoverageHistorySummary.MetricSummary;
import io.jenkins.plugins.coverage.metrics.steps.CoverageHistorySummary.Trend;
import io.jenkins.plugins.util.QualityGateResult;
import java.util.List;
import java.util.Locale;
import jenkins.model.HistoricalBuild;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;

/**
 * Tests the class {@link CoverageHistoryPageEntryDecorator}.
 *
 * @author Ullrich Hafner
 */
@DefaultLocale("en")
class CoverageHistoryPageEntryDecoratorTest extends AbstractCoverageTest {
    private static final String OTHER_ID = "other";

    @Test
    void shouldIgnoreBuildsThatAreNoRuns() {
        var decorator = new CoverageHistoryPageEntryDecorator();
        var build = mock(HistoricalBuild.class);

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isFalse();
        assertThat(decorator.getSummary(build, Locale.ENGLISH).isEmpty()).isTrue();
    }

    @Test
    void shouldIgnoreBuildsWithoutCoverage() {
        var decorator = new CoverageHistoryPageEntryDecorator();
        var build = createBuild();

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isFalse();
        assertThat(decorator.getSummary(build, Locale.ENGLISH).isEmpty()).isTrue();
    }

    @Test
    void shouldIgnoreResultsOfOtherIds() {
        var decorator = new CoverageHistoryPageEntryDecorator();
        var build = createBuild();
        attachAction(build, OTHER_ID, List.of());

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isFalse();
        assertThat(decorator.getSummary(build, Locale.ENGLISH).isEmpty()).isTrue();
    }

    @Test
    void shouldShowLineAndBranchCoverageWithoutDelta() {
        var decorator = new CoverageHistoryPageEntryDecorator();
        var build = createBuild();
        attachAction(build, CoverageRecorder.DEFAULT_ID, List.of());

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isTrue();

        var summary = decorator.getSummary(build, Locale.ENGLISH);
        assertThat(summary.isEmpty()).isFalse();
        assertThat(summary.getUrlName()).isEqualTo(CoverageRecorder.DEFAULT_ID);
        assertThat(summary.getIcon()).isEqualTo("icon");
        assertThat(summary.getMetrics()).hasSize(2);
        assertThat(summary.getTrend()).isEqualTo(Trend.UNCHANGED);

        assertThat(summary.getMetrics().get(0)).satisfies(line -> {
            assertThat(line.getLabel()).isEqualTo("Line");
            assertThat(line.getValue()).isEqualTo("91.02%");
            assertThat(line.hasDelta()).isFalse();
            assertThat(line.getDelta()).isEmpty();
            assertThat(line.getTrend()).isEqualTo(Trend.UNCHANGED);
            assertThat(line.getTooltip()).isEqualTo("Line Coverage: 91.02%");
        });
        assertThat(summary.getMetrics().get(1)).satisfies(branch -> {
            assertThat(branch.getLabel()).isEqualTo("Branch");
            assertThat(branch.getValue()).isEqualTo("93.97%");
            assertThat(branch.hasDelta()).isFalse();
        });
    }

    @Test
    void shouldShowDeltaToReferenceBuild() {
        var decorator = new CoverageHistoryPageEntryDecorator();
        var build = createBuild();
        attachAction(
                build,
                CoverageRecorder.DEFAULT_ID,
                List.of(new Difference(Metric.LINE, -2), new Difference(Metric.BRANCH, 5)));

        var summary = decorator.getSummary(build, Locale.ENGLISH);
        assertThat(summary.getTrend()).isEqualTo(Trend.UNCHANGED);

        var metrics = summary.getMetrics();
        assertThat(metrics).hasSize(2);

        assertThat(metrics.get(0)).satisfies(line -> {
            assertThat(line.hasDelta()).isTrue();
            assertThat(line.getDelta()).isEqualTo("-2.00%");
            assertThat(line.getTrend()).isEqualTo(Trend.DECREASED);
            assertThat(line.getTrend().getCssModifier()).isEqualTo("decreased");
            assertThat(line.getTooltip()).isEqualTo("Line Coverage: 91.02% (-2.00% compared to the reference build)");
        });
        assertThat(metrics.get(1)).satisfies(branch -> {
            assertThat(branch.getDelta()).isEqualTo("+5.00%");
            assertThat(branch.getTrend()).isEqualTo(Trend.INCREASED);
        });
    }

    @Test
    void shouldShowOverallTrend() {
        var decorator = new CoverageHistoryPageEntryDecorator();

        assertThat(createSummary(decorator, 1, 2).getTrend()).isEqualTo(Trend.INCREASED);
        assertThat(createSummary(decorator, 1, 0).getTrend()).isEqualTo(Trend.INCREASED);
        assertThat(createSummary(decorator, -1, -2).getTrend()).isEqualTo(Trend.DECREASED);
        assertThat(createSummary(decorator, 0, -2).getTrend()).isEqualTo(Trend.DECREASED);
        assertThat(createSummary(decorator, 1, -2).getTrend()).isEqualTo(Trend.UNCHANGED);
        assertThat(createSummary(decorator, 0, 0).getTrend()).isEqualTo(Trend.UNCHANGED);
    }

    @Test
    void shouldUseFirstActionWithDefaultId() {
        var decorator = new CoverageHistoryPageEntryDecorator();
        var build = createBuild();
        attachAction(
                build,
                OTHER_ID,
                List.of(new Difference(Metric.LINE, 7)),
                CoverageRecorder.DEFAULT_ID,
                List.of(new Difference(Metric.LINE, 3)));

        assertThat(decorator.getSummary(build, Locale.ENGLISH).getMetrics())
                .first()
                .extracting(MetricSummary::getDelta)
                .isEqualTo("+3.00%");
    }

    @Test
    void shouldDetectTrend() {
        assertThat(Trend.of(1)).isEqualTo(Trend.INCREASED);
        assertThat(Trend.of(-1)).isEqualTo(Trend.DECREASED);
        assertThat(Trend.of(0)).isEqualTo(Trend.UNCHANGED);
        assertThat(Trend.of(0.004)).isEqualTo(Trend.UNCHANGED);
        assertThat(Trend.of(-0.004)).isEqualTo(Trend.UNCHANGED);
        assertThat(Trend.of(0.006)).isEqualTo(Trend.INCREASED);
        assertThat(Trend.of(-0.006)).isEqualTo(Trend.DECREASED);
    }

    @Test
    void shouldCombineTrends() {
        assertThat(Trend.combine(List.of())).isEqualTo(Trend.UNCHANGED);
        assertThat(Trend.combine(List.of(Trend.INCREASED, Trend.UNCHANGED))).isEqualTo(Trend.INCREASED);
        assertThat(Trend.combine(List.of(Trend.DECREASED, Trend.DECREASED))).isEqualTo(Trend.DECREASED);
        assertThat(Trend.combine(List.of(Trend.INCREASED, Trend.DECREASED))).isEqualTo(Trend.UNCHANGED);
    }

    /**
     * Creates a summary for a build with the specified line and branch deltas.
     *
     * @param decorator
     *         the decorator to create the summary with
     * @param lineDelta
     *         the delta of the line coverage
     * @param branchDelta
     *         the delta of the branch coverage
     *
     * @return the summary
     */
    private CoverageHistorySummary createSummary(
            final CoverageHistoryPageEntryDecorator decorator, final int lineDelta, final int branchDelta) {
        var build = createBuild();
        attachAction(
                build,
                CoverageRecorder.DEFAULT_ID,
                List.of(new Difference(Metric.LINE, lineDelta), new Difference(Metric.BRANCH, branchDelta)));
        return decorator.getSummary(build, Locale.ENGLISH);
    }

    /**
     * Creates a build stub without any coverage actions.
     *
     * @return the build stub
     */
    private Run<?, ?> createBuild() {
        return mock(Run.class);
    }

    /**
     * Attaches a coverage action to the specified build.
     *
     * @param build
     *         the build to attach the action to
     * @param id
     *         the ID of the action
     * @param deltas
     *         the deltas to the reference build
     */
    private void attachAction(final Run<?, ?> build, final String id, final List<Difference> deltas) {
        when(build.getActions(CoverageBuildAction.class)).thenReturn(List.of(createAction(build, id, deltas)));
    }

    /**
     * Attaches two coverage actions to the specified build.
     *
     * @param build
     *         the build to attach the actions to
     * @param firstId
     *         the ID of the first action
     * @param firstDeltas
     *         the deltas of the first action
     * @param secondId
     *         the ID of the second action
     * @param secondDeltas
     *         the deltas of the second action
     */
    private void attachAction(
            final Run<?, ?> build,
            final String firstId,
            final List<Difference> firstDeltas,
            final String secondId,
            final List<Difference> secondDeltas) {
        when(build.getActions(CoverageBuildAction.class))
                .thenReturn(List.of(
                        createAction(build, firstId, firstDeltas), createAction(build, secondId, secondDeltas)));
    }

    /**
     * Creates a coverage action for the JaCoCo results of the codingstyle project.
     *
     * @param build
     *         the owner of the action
     * @param id
     *         the ID of the action
     * @param deltas
     *         the deltas to the reference build
     *
     * @return the created action
     */
    private CoverageBuildAction createAction(final Run<?, ?> build, final String id, final List<Difference> deltas) {
        return new CoverageBuildAction(
                build,
                id,
                "Code Coverage",
                "icon",
                readJacocoResult(JACOCO_CODING_STYLE_FILE),
                new QualityGateResult(),
                new FilteredLog("Test"),
                "-",
                deltas,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);
    }
}
