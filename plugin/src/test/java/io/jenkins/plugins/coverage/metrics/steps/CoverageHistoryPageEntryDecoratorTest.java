package io.jenkins.plugins.coverage.metrics.steps;

import static io.jenkins.plugins.coverage.metrics.Assertions.*;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import hudson.widgets.HistoryWidget;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.steps.CoverageHistoryPageEntryDecorator.BranchCoverage;
import io.jenkins.plugins.coverage.metrics.steps.CoverageHistoryPageEntryDecorator.LineCoverage;
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
        var decorator = new LineCoverage();
        var build = mock(HistoricalBuild.class);

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isFalse();
        assertThat(decorator.getSummary(build, Locale.ENGLISH)).isEmpty();
    }

    @Test
    void shouldIgnoreBuildsWithoutCoverage() {
        var decorator = new LineCoverage();
        var build = createBuild();

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isFalse();
        assertThat(decorator.getSummary(build, Locale.ENGLISH)).isEmpty();
    }

    @Test
    void shouldIgnoreResultsOfOtherIds() {
        var decorator = new LineCoverage();
        var build = createBuild();
        attachAction(build, OTHER_ID, List.of());

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isFalse();
        assertThat(decorator.getSummary(build, Locale.ENGLISH)).isEmpty();
    }

    @Test
    void shouldShowLineCoverageWithoutDelta() {
        var decorator = new LineCoverage();
        var build = createBuild();
        attachAction(build, CoverageRecorder.DEFAULT_ID, List.of());

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isTrue();
        assertThat(decorator.getSummary(build, Locale.ENGLISH))
                .isNotEmpty()
                .hasUrlName(CoverageRecorder.DEFAULT_ID)
                .hasIcon("symbol-reorder-four-outline plugin-ionicons-api")
                .hasValue("91.02%")
                .hasDelta("")
                .hasTrend(Trend.UNCHANGED)
                .hasTooltip("Line Coverage: 91.02%")
                .hasDetails("29 missed lines");
    }

    @Test
    void shouldShowBranchCoverageWithoutDelta() {
        var decorator = new BranchCoverage();
        var build = createBuild();
        attachAction(build, CoverageRecorder.DEFAULT_ID, List.of());

        assertThat(decorator.isApplicable(mock(HistoryWidget.class), build)).isTrue();
        assertThat(decorator.getSummary(build, Locale.ENGLISH))
                .isNotEmpty()
                .hasIcon("symbol-git-branch-outline plugin-ionicons-api")
                .hasValue("93.97%")
                .hasDelta("")
                .hasTrend(Trend.UNCHANGED)
                .hasTooltip("Branch Coverage: 93.97%")
                .hasDetails("7 missed branches");
    }

    @Test
    void shouldShowDeltaToReferenceBuild() {
        var build = createBuild();
        attachAction(
                build,
                CoverageRecorder.DEFAULT_ID,
                List.of(new Difference(Metric.LINE, -2), new Difference(Metric.BRANCH, 5)));

        assertThat(new LineCoverage().getSummary(build, Locale.ENGLISH))
                .hasDelta("-2.00%")
                .hasTrend(Trend.DECREASED)
                .hasTooltip("Line Coverage: 91.02% (-2.00% compared to the reference build)");
        assertThat(new BranchCoverage().getSummary(build, Locale.ENGLISH))
                .hasDelta("+5.00%")
                .hasTrend(Trend.INCREASED);
    }

    @Test
    void shouldUseFirstActionWithDefaultId() {
        var decorator = new LineCoverage();
        var build = createBuild();
        attachAction(
                build,
                OTHER_ID,
                List.of(new Difference(Metric.LINE, 7)),
                CoverageRecorder.DEFAULT_ID,
                List.of(new Difference(Metric.LINE, 3)));

        assertThat(decorator.getSummary(build, Locale.ENGLISH)).hasDelta("+3.00%");
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
        assertThat(Trend.INCREASED.getCssModifier()).isEqualTo("increased");
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
