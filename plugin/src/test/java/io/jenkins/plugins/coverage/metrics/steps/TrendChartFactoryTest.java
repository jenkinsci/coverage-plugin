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
                """)).containsExactly(Metric.BRANCH);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": true,
                        "BRANCH": false
                    }
                }
                """)).containsExactly(Metric.LINE);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": true,
                        "BRANCH": true
                    }
                }
                """)).containsExactlyInAnyOrder(Metric.LINE, Metric.BRANCH);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": false,
                        "BRANCH": false
                    }
                }
                """)).isEmpty();
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                    }
                }
                """)).isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "LINE": 1.0
                    }
                }
                """)).isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("""
                {
                    "metrics": {
                        "WRONG-METRIC": true
                    }
                }
                """)).isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("{}"))
                .isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
        assertThat(jobAction.getVisibleMetrics("broken"))
                .isEqualTo(TrendChartFactory.DEFAULT_TREND_METRICS);
    }
}
