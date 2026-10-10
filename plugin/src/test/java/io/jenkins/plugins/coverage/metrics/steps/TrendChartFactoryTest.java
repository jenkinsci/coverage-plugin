package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.Metric;
import org.junit.jupiter.api.Test;

class TrendChartFactoryTest {
    @Test
    void shouldSelectMetrics() {
        var jobAction = new TrendChartFactory();

        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": false,
                        "BRANCH": true
                    }
                }
                """, TrendChartFactory.COVERAGE_TREND_METRICS))
                .containsExactly(Metric.BRANCH);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": true,
                        "BRANCH": false
                    }
                }
                """, TrendChartFactory.COVERAGE_TREND_METRICS))
                .containsExactly(Metric.LINE);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": true,
                        "BRANCH": true
                    }
                }
                """, TrendChartFactory.COVERAGE_TREND_METRICS))
                .containsExactlyInAnyOrder(Metric.LINE, Metric.BRANCH);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": false,
                        "BRANCH": false
                    }
                }
                """, TrendChartFactory.COVERAGE_TREND_METRICS))
                .isEmpty();
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                    }
                }
                """, TrendChartFactory.COVERAGE_TREND_METRICS))
                .isEqualTo(TrendChartFactory.COVERAGE_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": 1.0
                    }
                }
                """, TrendChartFactory.COVERAGE_TREND_METRICS))
                .isEqualTo(TrendChartFactory.COVERAGE_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "WRONG-METRIC": true
                    }
                }
                """, TrendChartFactory.COVERAGE_TREND_METRICS))
                .isEqualTo(TrendChartFactory.COVERAGE_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("{}", TrendChartFactory.COVERAGE_TREND_METRICS))
                .isEqualTo(TrendChartFactory.COVERAGE_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("broken", TrendChartFactory.COVERAGE_TREND_METRICS))
                .isEqualTo(TrendChartFactory.COVERAGE_TREND_METRICS);
    }

    @Test
    void shouldFallBackToTheExplicitlyPassedDefaultMetrics() {
        var jobAction = new TrendChartFactory();

        assertThat(jobAction.getVisibleMetrics("{}", TrendChartFactory.LEGACY_DEFAULT_TREND_METRICS))
                .isEqualTo(TrendChartFactory.LEGACY_DEFAULT_TREND_METRICS)
                .doesNotContain(Metric.INSTRUCTION);
        assertThat(TrendChartFactory.COVERAGE_TREND_METRICS).contains(Metric.INSTRUCTION);
    }

    @Test
    void shouldSeparatePercentageAndAbsoluteMetrics() {
        assertThat(TrendChartFactory.COVERAGE_TREND_METRICS)
                .contains(Metric.LINE, Metric.BRANCH, Metric.INSTRUCTION, Metric.MUTATION, Metric.TEST_STRENGTH)
                .doesNotContain(Metric.LOC, Metric.CYCLOMATIC_COMPLEXITY);
        assertThat(TrendChartFactory.SOFTWARE_TREND_METRICS)
                .contains(Metric.LOC, Metric.NCSS, Metric.CYCLOMATIC_COMPLEXITY, Metric.COGNITIVE_COMPLEXITY)
                .doesNotContain(Metric.LINE, Metric.BRANCH, Metric.TEST_STRENGTH);
        assertThat(TrendChartFactory.COVERAGE_TREND_METRICS)
                .contains(Metric.COHESION, Metric.WEIGHT_OF_CLASS) // ignored metrics are shown if they are percentages
                .doesNotContain(Metric.FAN_OUT, Metric.WEIGHED_METHOD_COUNT)
                .noneMatch(Metric::isContainer);
        assertThat(TrendChartFactory.SOFTWARE_TREND_METRICS)
                .doesNotContainAnyElementsOf(TrendChartFactory.IGNORED_TREND_METRICS)
                .noneMatch(Metric::isContainer);
    }
}
