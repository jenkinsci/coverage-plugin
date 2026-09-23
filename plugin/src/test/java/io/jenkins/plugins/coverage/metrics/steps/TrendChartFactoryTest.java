package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Metric;

import static org.assertj.core.api.Assertions.*;

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
                """, TrendChartFactory.DEFAULT_TREND_METRICS)).containsExactly(Metric.BRANCH);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": true,
                        "BRANCH": false
                    }
                }
                """, TrendChartFactory.DEFAULT_TREND_METRICS)).containsExactly(Metric.LINE);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": true,
                        "BRANCH": true
                    }
                }
                """, TrendChartFactory.DEFAULT_TREND_METRICS)).containsExactlyInAnyOrder(Metric.LINE, Metric.BRANCH);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": false,
                        "BRANCH": false
                    }
                }
                """, TrendChartFactory.DEFAULT_TREND_METRICS)).isEmpty();
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                    }
                }
                """, TrendChartFactory.DEFAULT_TREND_METRICS)).isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": 1.0
                    }
                }
                """, TrendChartFactory.DEFAULT_TREND_METRICS)).isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "WRONG-METRIC": true
                    }
                }
                """, TrendChartFactory.DEFAULT_TREND_METRICS)).isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("{}", TrendChartFactory.DEFAULT_TREND_METRICS))
                .isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("broken", TrendChartFactory.DEFAULT_TREND_METRICS))
                .isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
    }

    @Test
    void shouldFallBackToTheExplicitlyPassedDefaultMetrics() {
        var jobAction = new TrendChartFactory();

        assertThat(jobAction.getVisibleMetrics("{}", TrendChartFactory.LEGACY_DEFAULT_TREND_METRICS))
                .isEqualTo(TrendChartFactory.LEGACY_DEFAULT_TREND_METRICS)
                .doesNotContain(Metric.INSTRUCTION);
        assertThat(TrendChartFactory.DEFAULT_TREND_METRICS).contains(Metric.INSTRUCTION);
    }
}
