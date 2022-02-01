package io.jenkins.plugins.coverage.model;

import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import hudson.model.Run;

import static io.jenkins.plugins.coverage.model.testutil.CoverageStubs.*;
import static io.jenkins.plugins.coverage.model.testutil.JobStubs.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link CoverageBuildAction}.
 *
 * @author Ullrich Hafner
 */
class CoverageBuildActionTest {

    private static final double COVERAGE_PERCENTAGE = 0.5;
    private static final CoverageMetric COVERAGE_METRIC = CoverageMetric.LINE;

    @Test
    void shouldCreateViewModel() {
        Run<?, ?> build = mock(Run.class);
        CoverageNode root = new CoverageNode(COVERAGE_METRIC, "top-level");
        SortedMap<CoverageMetric, Double> metrics = new TreeMap<>();
        CoverageBuildAction action = new CoverageBuildAction(build, root, "-", metrics, false);

        assertThat(action.getTarget()).extracting(CoverageViewModel::getNode).isEqualTo(root);
        assertThat(action.getTarget()).extracting(CoverageViewModel::getOwner).isEqualTo(build);
    }

    @Test
    void shouldGetCoverageForSpecifiedMetric() {
        CoverageBuildAction action = createCoverageBuildActionWithMocks();
        assertThat(action.hasCoverage(COVERAGE_METRIC)).isTrue();
        assertThat(action.hasCoverage(CoverageMetric.BRANCH)).isFalse();
        assertThat(action.getCoverage(COVERAGE_METRIC))
                .isNotNull()
                .satisfies(coverage -> assertThat(coverage.getCoveredPercentage()).isEqualTo(COVERAGE_PERCENTAGE));
    }

    @Test
    void shouldGetDeltaValueForSpecifiedMetric() {
        CoverageBuildAction action = createCoverageBuildActionWithMocks();
        assertThat(action.hasDelta(COVERAGE_METRIC)).isTrue();
        assertThat(action.hasDelta(CoverageMetric.BRANCH)).isFalse();
        assertThat(action.getDelta())
                .hasSize(1)
                .containsKey(COVERAGE_METRIC)
                .containsValue(COVERAGE_PERCENTAGE);
    }

    /**
     * Creates a {@link CoverageBuildAction} which represents the coverage for the metric {@link #COVERAGE_METRIC} with
     * the value {@link #COVERAGE_PERCENTAGE}.
     *
     * @return the creates action
     */
    private CoverageBuildAction createCoverageBuildActionWithMocks() {
        Run<?, ?> build = createBuild();
        CoverageNode root = createCoverageNode(COVERAGE_PERCENTAGE, COVERAGE_METRIC);
        TreeMap<CoverageMetric, Double> deltas = new TreeMap<>();
        deltas.put(COVERAGE_METRIC, COVERAGE_PERCENTAGE);
        return new CoverageBuildAction(build, root, "-", deltas, false);
    }
}
