package io.jenkins.plugins.coverage.metrics.charts;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.Build;
import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.ChartModelConfiguration.AxisType;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link MetricsTrendChart}.
 *
 * @author Ullrich Hafner
 */
class MetricsTrendChartTest {
    private static final Set<Metric> METRICS = Set.of(Metric.LOC, Metric.CYCLOMATIC_COMPLEXITY);

    @Test
    void shouldFixMinimumAndMaximumByDefault() {
        var model = new MetricsTrendChart(METRICS, true).create(createResults(), createConfiguration());

        assertThat(model.getSeries()).hasSize(2);
        assertThat(model.getRangeMin()).isEqualTo(100.0);
        assertThat(model.getRangeMax()).isEqualTo(9000.0);
    }

    @Test
    void shouldFixOnlyMinimumIfMaximumIsAdaptive() {
        var model = new MetricsTrendChart(METRICS, true, true).create(createResults(), createConfiguration());

        assertThat(model.getSeries()).hasSize(2);
        assertThat(model.getRangeMin()).isEqualTo(100.0);
        assertThat(model.getRangeMax()).isNull();
    }

    private List<BuildResult<CoverageStatistics>> createResults() {
        return List.of(new BuildResult<>(
                new Build(1),
                new CoverageStatistics(
                        List.of(new Value(Metric.LOC, 9000), new Value(Metric.CYCLOMATIC_COMPLEXITY, 100)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())));
    }

    private ChartModelConfiguration createConfiguration() {
        ChartModelConfiguration configuration = mock(ChartModelConfiguration.class);
        when(configuration.getAxisType()).thenReturn(AxisType.BUILD);
        return configuration;
    }
}
