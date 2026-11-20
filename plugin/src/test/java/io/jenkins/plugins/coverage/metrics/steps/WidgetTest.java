package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.steps.OverviewModel.CoverageBar;
import io.jenkins.plugins.coverage.metrics.steps.Widget.MetricSummary;
import io.jenkins.plugins.util.QualityGate.QualityGateCriticality;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.QualityGateStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;

/**
 * Tests the class {@link Widget}.
 *
 * @author Ullrich Hafner
 */
@DefaultLocale("en")
class WidgetTest extends AbstractCoverageTest {
    private static final String COVERAGE_ID = "coverage";
    private static final String MUTATION_ID = "mutation";

    @Test
    void shouldHandleRunsWithoutResults() {
        var widget = new Widget(List.of());

        assertThat(widget.hasQualityGates()).isFalse();
        assertThat(widget.getQualityGateStatus()).isEqualTo(QualityGateStatus.INACTIVE);
        assertThat(widget.getQualityGateIcon()).isEqualTo("symbol-status-nobuilt");
        assertThat(widget.getMetrics()).isEmpty();
        assertThat(widget.getCoverageBar()).isEmpty();
        assertThat(widget.hasPills()).isFalse();
        assertThat(widget.getResults()).isEmpty();
        assertThat(widget.getErrorMessagesSize()).isZero();
        assertThat(widget.getLogUrl()).isEqualTo(RunTab.URL);
        assertThat(widget.getDecreasedMetricsSize()).isZero();
        assertThat(widget.hasReferenceBuild()).isFalse();
        assertThat(widget.getComparisonCaption()).isEqualTo(Messages.OverviewModel_noReferenceBuild());
        assertThat(widget.getReferenceBuildUrl()).isEmpty();
    }

    @Test
    void shouldShowProjectMetricsWithoutDeltasAndQualityGates() {
        var widget = createWidget(createAction(COVERAGE_ID, new QualityGateResult()));

        assertThat(widget.hasQualityGates()).isFalse();
        assertThat(widget.getSymbol()).isEqualTo("symbol-status-blue");
        assertThat(widget.getLines())
                .containsExactly(Messages.Widget_coverageRecorded(), Messages.Widget_noQualityGates());

        assertThat(widget.getMetrics())
                .extracting(MetricSummary::getLabel)
                .containsExactly(Metric.LINE.getLabel(), Metric.BRANCH.getLabel());
        var line = widget.getMetrics().get(0);
        assertThat(line.getValue()).isEqualTo("91.02%");
        assertThat(line.getDelta()).isEmpty();
        assertThat(line.getTrend()).isEqualTo("none");
        assertThat(line.getTooltip())
                .startsWith(Metric.LINE.getDisplayName())
                .contains(String.valueOf(JACOCO_CODING_STYLE_COVERED));

        assertThat(widget.getCoverageBar()).hasValueSatisfying(bar -> {
            assertThat(bar.getTitle()).contains(Baseline.PROJECT.getTitle(), Metric.LINE.getDisplayName());
            assertThat(bar.getCovered()).isEqualTo(JACOCO_CODING_STYLE_COVERED);
            assertThat(bar.getMissed()).isEqualTo(JACOCO_CODING_STYLE_MISSED);
            assertThat(bar.getCoveredLabel()).isEqualTo(Messages.OverviewModel_covered(JACOCO_CODING_STYLE_COVERED));
            assertThat(bar.getMissedLabel()).isEqualTo(Messages.OverviewModel_missed(JACOCO_CODING_STYLE_MISSED));
        });

        assertThat(widget.hasPills()).isFalse();
        assertThat(widget.getDecreasedMetricsSize()).isZero();
    }

    @Test
    void shouldShowWarningStatusIfCoverageDecreased() {
        var widget = createWidget(createAction(
                COVERAGE_ID,
                new QualityGateResult(),
                List.of(new Difference(Metric.LINE, -2), new Difference(Metric.BRANCH, 1)),
                List.of()));

        assertThat(widget.getDecreasedMetricsSize()).isOne();
        assertThat(widget.getSymbol()).isEqualTo("symbol-status-yellow");
        assertThat(widget.getLines())
                .containsExactly(Messages.Widget_coverageDecreased(1), Messages.Widget_noQualityGates());
        assertThat(widget.getMetrics()).extracting(MetricSummary::getTrend).containsExactly("worse", "better");
        assertThat(widget.getMetrics()).extracting(MetricSummary::getDelta).doesNotContain("");
    }

    @Test
    void shouldPreferTheModifiedLinesForTheCoverageBar() {
        var modifiedLines = new CoverageBuilder()
                .withMetric(Metric.LINE)
                .withCovered(40)
                .withMissed(3)
                .build();
        var widget =
                createWidget(createAction(COVERAGE_ID, new QualityGateResult(), List.of(), List.of(modifiedLines)));

        assertThat(widget.getCoverageBar())
                .map(CoverageBar::getTitle)
                .hasValueSatisfying(title -> assertThat(title).contains(Baseline.MODIFIED_LINES.getTitle()));
        assertThat(widget.getCoverageBar()).map(CoverageBar::getCovered).contains(40);
        assertThat(widget.getCoverageBar()).map(CoverageBar::getMissed).contains(3);
    }

    @Test
    void shouldSummarizeMissedQualityGatesOfAllResults() {
        var coverage =
                createAction(COVERAGE_ID, createQualityGateResult(QualityGateStatus.PASSED, QualityGateStatus.WARNING));
        var mutation = createAction(MUTATION_ID, createQualityGateResult(QualityGateStatus.FAILED));
        var widget = createWidget(coverage, mutation);

        assertThat(widget.hasQualityGates()).isTrue();
        assertThat(widget.getQualityGateStatus()).isEqualTo(QualityGateStatus.FAILED);
        assertThat(widget.getQualityGateIcon()).isEqualTo("symbol-status-red");
        assertThat(widget.getMissedQualityGatesSize()).isEqualTo(2);
        assertThat(widget.getQualityGateSummary()).isEqualTo(Messages.Widget_qualityGatesMissed(2, 3));

        assertThat(widget.hasPills()).isTrue();
        assertThat(widget.getResults()).extracting(OverviewModel::getId).containsExactly(MUTATION_ID, COVERAGE_ID);
        assertThat(widget.getResults()).extracting(widget::getStatusModifier).containsExactly("error", "warning");
    }

    @Test
    void shouldSummarizePassedQualityGates() {
        var widget = createWidget(createAction(COVERAGE_ID, createQualityGateResult(QualityGateStatus.PASSED)));

        assertThat(widget.getQualityGateStatus()).isEqualTo(QualityGateStatus.PASSED);
        assertThat(widget.getQualityGateIcon()).isEqualTo("symbol-status-blue");
        assertThat(widget.getMissedQualityGatesSize()).isZero();
        assertThat(widget.getQualityGateSummary()).isEqualTo(Messages.Widget_qualityGatesPassed(1));
    }

    @Test
    void shouldSortPillsByCoverageDecrease() {
        var stable = createAction(COVERAGE_ID, new QualityGateResult());
        var decreased =
                createAction(MUTATION_ID, new QualityGateResult(), List.of(new Difference(Metric.LINE, -5)), List.of());
        var widget = createWidget(stable, decreased);

        assertThat(widget.getResults()).extracting(OverviewModel::getId).containsExactly(MUTATION_ID, COVERAGE_ID);

        var first = widget.getResults().get(0);
        assertThat(widget.getStatusModifier(first)).isEqualTo("none");
        assertThat(widget.getValueOf(first)).isEqualTo("91.02%");
        assertThat(widget.getTooltipOf(first)).startsWith(Metric.LINE.getDisplayName());
        assertThat(widget.getDecreaseOf(first)).isNotEmpty();
        assertThat(widget.getDecreaseOf(widget.getResults().get(1))).isEmpty();
        assertThat(widget.getMetrics().get(0).getTooltip()).startsWith(Messages.Coverage_Link_Name() + " - ");
        assertThat(widget.getTabUrl()).isEqualTo(RunTab.URL);
    }

    @Test
    void shouldLinkToTheLogOfTheFirstResultWithErrors() {
        var errors = new FilteredLog("Errors");
        errors.logError("First error");
        errors.logError("Second error");
        var widget = createWidget(
                createAction(COVERAGE_ID, new QualityGateResult()),
                createAction(MUTATION_ID, new QualityGateResult(), errors));

        assertThat(widget.getErrorMessagesSize()).isEqualTo(2);
        assertThat(widget.getLogUrl()).isEqualTo(MUTATION_ID + "/log");
    }

    @Test
    void shouldNameTheReferenceBuildOfTheSameJob() {
        var job = mock(Job.class);
        var owner = createRun(job, "#49", "job/x/49/");
        var reference = createRun(job, "#48", "job/x/48/");
        var widget = new Widget(List.of(new OverviewModel(owner, createActionWithReference(COVERAGE_ID, reference))));

        assertThat(widget.hasReferenceBuild()).isTrue();
        assertThat(widget.getComparisonCaption()).isEqualTo(Messages.OverviewModel_comparedTo("#48"));
        assertThat(widget.getReferenceBuildUrl()).isEqualTo("job/x/48/");
        assertThat(widget.getReferenceBuildTooltip()).isEqualTo(Messages.Coverage_Link_Name() + ": #48");
        assertThat(widget.getLines())
                .containsExactly(Messages.Widget_noCoverageDecrease(), Messages.Widget_noQualityGates());
    }

    @Test
    void shouldUseTheFullNameForReferenceBuildsOfOtherJobs() {
        var owner = createRun(mock(Job.class), "#49", "job/x/PR-1/49/");
        var reference = createRun(mock(Job.class), "#48", "job/x/main/48/");
        when(reference.getFullDisplayName()).thenReturn("x » main #48");

        var overview = new OverviewModel(owner, createActionWithReference(COVERAGE_ID, reference));

        assertThat(overview.getReferenceBuildName()).isEqualTo("x » main #48");
        assertThat(overview.getReferenceBuildText())
                .isEqualTo(Messages.OverviewModel_referenceBuildButton("x » main #48"));
    }

    @Test
    void shouldShowTheNumberOfReferenceBuildsIfTheResultsUseDifferentBuilds() {
        var job = mock(Job.class);
        var owner = createRun(job, "#49", "job/x/49/");
        var widget = new Widget(List.of(
                new OverviewModel(owner, createActionWithReference(COVERAGE_ID, createRun(job, "#48", "job/x/48/"))),
                new OverviewModel(owner, createActionWithReference(MUTATION_ID, createRun(job, "#47", "job/x/47/")))));

        assertThat(widget.getComparisonCaption())
                .isEqualTo(Messages.OverviewModel_comparedTo(Messages.OverviewModel_referenceBuilds(2)));
        assertThat(widget.getReferenceBuildUrl()).isEmpty();
        assertThat(widget.getReferenceBuildTooltip())
                .isEqualTo("%1$s: #48, %1$s: #47".formatted(Messages.Coverage_Link_Name()));
    }

    private Run<?, ?> createRun(final Job<?, ?> job, final String displayName, final String url) {
        Run<?, ?> run = mock(Run.class);
        doReturn(job).when(run).getParent();
        when(run.getDisplayName()).thenReturn(displayName);
        when(run.getFullDisplayName()).thenReturn("x " + displayName);
        when(run.getUrl()).thenReturn(url);
        return run;
    }

    private CoverageBuildAction createActionWithReference(final String id, final Run<?, ?> reference) {
        var action = spy(createAction(id, new QualityGateResult()));
        doReturn(Optional.of(reference)).when(action).getReferenceBuild();
        return action;
    }

    private QualityGateResult createQualityGateResult(final QualityGateStatus... statuses) {
        var result = new QualityGateResult();
        for (QualityGateStatus status : statuses) {
            result.add(
                    new CoverageQualityGate(100, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE),
                    status,
                    "-");
        }
        return result;
    }

    private Widget createWidget(final CoverageBuildAction... actions) {
        return new Widget(Arrays.stream(actions)
                .map(action -> new OverviewModel(mock(Run.class), action))
                .toList());
    }

    private CoverageBuildAction createAction(final String id, final QualityGateResult qualityGateResult) {
        return createAction(id, qualityGateResult, new FilteredLog("Errors"));
    }

    private CoverageBuildAction createAction(
            final String id, final QualityGateResult qualityGateResult, final FilteredLog log) {
        return createAction(id, qualityGateResult, log, List.of(), List.of());
    }

    private CoverageBuildAction createAction(
            final String id,
            final QualityGateResult qualityGateResult,
            final List<Difference> differences,
            final List<Value> modifiedLinesCoverage) {
        return createAction(id, qualityGateResult, new FilteredLog("Errors"), differences, modifiedLinesCoverage);
    }

    private CoverageBuildAction createAction(
            final String id,
            final QualityGateResult qualityGateResult,
            final FilteredLog log,
            final List<Difference> differences,
            final List<Value> modifiedLinesCoverage) {
        var node = readJacocoResult(JACOCO_CODING_STYLE_FILE);
        return new CoverageBuildAction(
                mock(Run.class),
                id,
                "",
                "",
                node,
                qualityGateResult,
                log,
                "-",
                differences,
                modifiedLinesCoverage,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);
    }
}
