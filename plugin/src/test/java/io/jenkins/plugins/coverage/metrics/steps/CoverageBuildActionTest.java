package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.Fraction;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;

import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import hudson.model.FreeStyleBuild;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.util.QualityGateResult;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link CoverageBuildAction}.
 *
 * @author Ullrich Hafner
 */
@DefaultLocale("en")
class CoverageBuildActionTest {
    @Test
    void shouldNotLoadResultIfCoverageValuesArePersistedInAction() {
        var module = new ModuleNode("module");

        var coverageBuilder = new CoverageBuilder();
        var percent50 = coverageBuilder.withMetric(Metric.BRANCH).withCovered(1).withMissed(1).build();
        var percent80 = coverageBuilder.withMetric(Metric.LINE).withCovered(8).withMissed(2).build();

        module.addValue(percent50);
        module.addValue(percent80);

        var lineDelta = new Difference(Metric.LINE, 30);
        var branchDelta = new Difference(Metric.BRANCH, -30);
        var deltas = List.of(lineDelta, branchDelta);

        var coverages = List.of(percent50, percent80);
        var action = spy(new CoverageBuildAction(mock(FreeStyleBuild.class), CoverageRecorder.DEFAULT_ID,
                StringUtils.EMPTY, StringUtils.EMPTY, module, new QualityGateResult(),
                createLog(), "-", deltas, coverages, deltas, coverages, deltas, coverages, false));

        when(action.getResult()).thenThrow(new IllegalStateException("Result should not be accessed with getResult() when getting a coverage metric that is persisted in the build"));

        assertThat(action.getReferenceBuild()).isEmpty();

        assertThat(action.getStatistics().getValue(Baseline.PROJECT, Metric.BRANCH))
                .hasValue(percent50);
        assertThat(action.getStatistics().getValue(Baseline.PROJECT, Metric.LINE))
                .hasValue(percent80);
        assertThat(action.getStatistics().getValue(Baseline.MODIFIED_LINES, Metric.BRANCH))
                .hasValue(percent50);
        assertThat(action.getStatistics().getValue(Baseline.MODIFIED_LINES, Metric.LINE))
                .hasValue(percent80);
        assertThat(action.getStatistics().getValue(Baseline.MODIFIED_FILES, Metric.BRANCH))
                .hasValue(percent50);
        assertThat(action.getStatistics().getValue(Baseline.MODIFIED_FILES, Metric.LINE))
                .hasValue(percent80);
        assertThat(action.getStatistics().getValue(Baseline.PROJECT_DELTA, Metric.LINE))
                .hasValue(lineDelta);
        assertThat(action.getStatistics().getValue(Baseline.PROJECT_DELTA, Metric.BRANCH))
                .hasValue(branchDelta);

        assertThat(action.getAllValues(Baseline.PROJECT)).containsAll(coverages);
    }

    private static CoverageBuildAction createEmptyAction(final Node module) {
        return new CoverageBuildAction(mock(FreeStyleBuild.class), CoverageRecorder.DEFAULT_ID,
                StringUtils.EMPTY, StringUtils.EMPTY, module, new QualityGateResult(), createLog(), "-",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false);
    }

    private static FilteredLog createLog() {
        return new FilteredLog("Errors");
    }

    @Test
    void shouldCreateViewModel() {
        var root = new ModuleNode("top-level");
        CoverageBuildAction action = createEmptyAction(root);

        assertThat(action.getTarget()).extracting(CoverageViewModel::getNode).isSameAs(root);
        assertThat(action.getTarget()).extracting(CoverageViewModel::getOwner).isSameAs(action.getOwner());
    }

    @Test
    void shouldReturnPositiveTrendForLineMetric() {
        var action = createCoverageBuildActionWithDelta(createSingleton(Fraction.getFraction(1, 1000)));
        assertThat(action.getTrend(Baseline.PROJECT, Metric.LINE)).isPositive();
    }

    @Test
    void shouldReturnNegativeTrendForLineMetric() {
        var action = createCoverageBuildActionWithDelta(createSingleton(Fraction.getFraction(-1, 1000)));
        assertThat(action.getTrend(Baseline.PROJECT, Metric.LINE)).isNegative();
    }

    @Test
    void shouldReturnZeroForDeltaWithinBoundaries() {
        var action = createCoverageBuildActionWithDelta(createSingleton(Fraction.getFraction(9, 10_000)));
        assertThat(action.getTrend(Baseline.PROJECT, Metric.LINE)).isZero();

        assertThat(action.hasDelta(Baseline.PROJECT)).isTrue();
    }

    @Test
    void shouldReturnZeroWhenDeltaIsNotPresentForGivenMetric() {
        var action = createCoverageBuildActionWithDelta(List.of());
        assertThat(action.getTrend(Baseline.PROJECT, Metric.LINE)).isZero();

        assertThat(action.hasDelta(Baseline.PROJECT)).isFalse();
    }

    private List<Difference> createSingleton(final Fraction fraction) {
        return List.of(new Difference(Metric.LINE, fraction));
    }

    private CoverageBuildAction createCoverageBuildActionWithDelta(final List<Difference> deltas) {
        var module = new ModuleNode("module");

        var coverageBuilder = new CoverageBuilder();
        var percent = coverageBuilder.withMetric(Metric.LINE).withCovered(1).withMissed(1).build();

        module.addValue(percent);

        var coverages = List.of(percent);

        var sortedDeltas = new ArrayList<>(deltas);
        sortedDeltas.sort(Comparator.comparing(Value::getMetric));
        return spy(new CoverageBuildAction(mock(FreeStyleBuild.class), CoverageRecorder.DEFAULT_ID,
                StringUtils.EMPTY, StringUtils.EMPTY, module, new QualityGateResult(),
                createLog(), "-", sortedDeltas, coverages,
                sortedDeltas, coverages, sortedDeltas, coverages, false));
    }
}
