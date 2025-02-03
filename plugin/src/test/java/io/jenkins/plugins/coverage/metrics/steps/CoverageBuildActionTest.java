package io.jenkins.plugins.coverage.metrics.steps;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.Fraction;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;

import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;

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
        Node module = new ModuleNode("module");

        var coverageBuilder = new CoverageBuilder();
        var percent50 = coverageBuilder.withMetric(Metric.BRANCH).withCovered(1).withMissed(1).build();
        var percent80 = coverageBuilder.withMetric(Metric.LINE).withCovered(8).withMissed(2).build();

        module.addValue(percent50);
        module.addValue(percent80);

        var deltas = new TreeMap<Metric, Difference>();
        var lineDelta = new Difference(Metric.LINE, 30);
        deltas.put(Metric.LINE, lineDelta);
        var branchDelta = new Difference(Metric.BRANCH, -30);
        deltas.put(Metric.BRANCH, branchDelta);

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
                new TreeMap<>(), List.of(), new TreeMap<>(), List.of(), new TreeMap<>(), List.of(), false);
    }

    private static FilteredLog createLog() {
        return new FilteredLog("Errors");
    }

    @Test
    void shouldCreateViewModel() {
        Node root = new ModuleNode("top-level");
        CoverageBuildAction action = createEmptyAction(root);

        assertThat(action.getTarget()).extracting(CoverageViewModel::getNode).isSameAs(root);
        assertThat(action.getTarget()).extracting(CoverageViewModel::getOwner).isSameAs(action.getOwner());
    }

    @Test
    void shouldReturnPositiveTrendForLineMetric() {
        CoverageBuildAction action = createCoverageBuildActionWithDelta(createMap(Fraction.getFraction(1, 1000)));
        assertThat(action.getTrend(Baseline.PROJECT, Metric.LINE)).isPositive();
    }

    @Test
    void shouldReturnNegativeTrendForLineMetric() {
        CoverageBuildAction action = createCoverageBuildActionWithDelta(createMap(Fraction.getFraction(-1, 1000)));
        assertThat(action.getTrend(Baseline.PROJECT, Metric.LINE)).isNegative();
    }

    @Test
    void shouldReturnZeroForDeltaWithinBoundaries() {
        CoverageBuildAction action = createCoverageBuildActionWithDelta(createMap(Fraction.getFraction(9, 10_000)));
        assertThat(action.getTrend(Baseline.PROJECT, Metric.LINE)).isZero();
    }

    @Test
    void shouldReturnZeroWhenDeltaIsNotPresentForGivenMetric() {
        CoverageBuildAction action = createCoverageBuildActionWithDelta(Map.of());
        assertThat(action.getTrend(Baseline.PROJECT, Metric.LINE)).isZero();
    }

    private Map<Metric, Difference> createMap(final Fraction fraction) {
        return Map.of(Metric.LINE, new Difference(Metric.LINE, fraction));
    }

    private CoverageBuildAction createCoverageBuildActionWithDelta(final Map<Metric, Difference> deltas) {
        Node module = new ModuleNode("module");

        var coverageBuilder = new CoverageBuilder();
        var percent = coverageBuilder.withMetric(Metric.LINE).withCovered(1).withMissed(1).build();

        module.addValue(percent);

        var coverages = List.of(percent);

        var sortedDeltas = new TreeMap<>(deltas);
        return spy(new CoverageBuildAction(mock(FreeStyleBuild.class), CoverageRecorder.DEFAULT_ID,
                StringUtils.EMPTY, StringUtils.EMPTY, module, new QualityGateResult(),
                createLog(), "-", sortedDeltas, coverages,
                sortedDeltas, coverages, sortedDeltas, coverages, false));
    }
}
