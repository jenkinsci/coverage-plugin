package io.jenkins.plugins.coverage.metrics.charts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.Build;
import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.ChartModelConfiguration.AxisType;
import edu.hm.hafner.echarts.line.LinesChartModel;
import edu.hm.hafner.util.ResourceTest;
import edu.hm.hafner.util.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link CoverageSeriesBuilder}.
 *
 * @author Ullrich Hafner
 */
class CoverageSeriesBuilderTest extends ResourceTest {
    private static final String LINE_COVERAGE = Metric.LINE.toTagName();
    private static final String BRANCH_COVERAGE = Metric.BRANCH.toTagName();

    @Test
    void shouldHaveEmptyDataSetForEmptyIterator() {
        var builder = new CoverageSeriesBuilder();

        var model = builder.createDataSet(createConfiguration(), new ArrayList<>());

        assertThat(model.getDomainAxisSize()).isEqualTo(0);
        assertThat(model.getDataSetIds()).isEmpty();
    }

    @Test
    void shouldCreateChart() {
        var trendChart = createTrend();

        BuildResult<CoverageStatistics> smallLineCoverage = createResult(1,
                new CoverageBuilder().withMetric(Metric.LINE).withCovered(1).withMissed(1).build(),
                new CoverageBuilder().withMetric(Metric.BRANCH).withCovered(3).withMissed(1).build());

        var lineCoverage = trendChart.create(Collections.singletonList(smallLineCoverage),
                createConfiguration());
        verifySeriesDetails(lineCoverage);

        BuildResult<CoverageStatistics> smallBranchCoverage = createResult(1,
                new CoverageBuilder().withMetric(Metric.LINE).withCovered(3).withMissed(1).build(),
                new CoverageBuilder().withMetric(Metric.BRANCH).withCovered(1).withMissed(1).build());

        var branchCoverage = trendChart.create(Collections.singletonList(smallBranchCoverage),
                createConfiguration());
        verifySeriesDetails(branchCoverage);
    }

    @Test
    void shouldCreateStackedChartByDefault() {
        var trendChart = createTrend();

        BuildResult<CoverageStatistics> smallLineCoverage = createResult(1,
                new CoverageBuilder().withMetric(Metric.LINE).withCovered(1).withMissed(1).build(),
                new CoverageBuilder().withMetric(Metric.BRANCH).withCovered(3).withMissed(1).build());

        var lineCoverage = trendChart.create(Collections.singletonList(smallLineCoverage),
                createConfiguration());
        assertThat(lineCoverage.getBuildNumbers()).containsExactly(1);
        assertThat(lineCoverage.getSeries()).hasSize(2).allSatisfy(
                series -> assertThat(series.getAreaStyle()).isNotNull()
        );
        assertThat(lineCoverage.getRangeMax()).isEqualTo(100.0);
        assertThat(lineCoverage.getRangeMin()).isEqualTo(50.0);
    }

    private CoverageTrendChart createTrend() {
        return new CoverageTrendChart(Set.of(Metric.LINE, Metric.BRANCH), false);
    }

    @ParameterizedTest @EnumSource(value = Metric.class, names = {"MCDC_PAIR", "FUNCTION_CALL"})
    void shouldCreateLineChartForVectorCoverage(final Metric vector) {
        var trendChart = new CoverageTrendChart(Set.of(Metric.LINE, Metric.BRANCH, vector), true);

        BuildResult<CoverageStatistics> smallLineCoverage = createResult(1,
                new CoverageBuilder().withMetric(Metric.LINE).withCovered(1).withMissed(1).build(),
                new CoverageBuilder().withMetric(Metric.BRANCH).withCovered(2).withMissed(1).build(),
                new CoverageBuilder().withMetric(vector).withCovered(1).withMissed(2).build());

        var lineCoverage = trendChart.create(Collections.singletonList(smallLineCoverage),
                createConfiguration());
        assertThat(lineCoverage.getBuildNumbers()).containsExactly(1);
        assertThat(lineCoverage.getSeries()).hasSize(3).allSatisfy(
                series -> assertThat(series.getAreaStyle()).isNull()
        );
        assertThat(lineCoverage.getRangeMax()).isEqualTo(100.0);
        assertThat(lineCoverage.getRangeMin()).isEqualTo(33.33);
    }

    @VisibleForTesting
    private BuildResult<CoverageStatistics> createResult(final int buildNumber,
            final Coverage... coverages) {
        var statistics = new CoverageStatistics(
                List.of(coverages), List.of(), List.of(), List.of(), List.of(), List.of());
        var build = new Build(buildNumber);

        return new BuildResult<>(build, statistics);
    }

    private void verifySeriesDetails(final LinesChartModel lineCoverage) {
        assertThat(lineCoverage.getBuildNumbers()).containsExactly(1);
        assertThat(lineCoverage.getSeries()).hasSize(2);
        assertThat(lineCoverage.getRangeMax()).isEqualTo(100.0);
        assertThat(lineCoverage.getRangeMin()).isEqualTo(50.0);
    }

    @Test
    void shouldHaveTwoValuesForSingleBuild() {
        var builder = new CoverageSeriesBuilder();

        BuildResult<CoverageStatistics> singleResult = createResult(1,
                new CoverageBuilder().withMetric(Metric.LINE).withCovered(1).withMissed(1).build(),
                new CoverageBuilder().withMetric(Metric.BRANCH).withCovered(3).withMissed(1).build());

        var dataSet = builder.createDataSet(createConfiguration(), Collections.singletonList(singleResult));

        assertThat(dataSet.getDomainAxisSize()).isEqualTo(1);
        assertThat(dataSet.getDomainAxisLabels()).containsExactly("#1");

        assertThat(dataSet.getDataSetIds()).containsExactlyInAnyOrder(
                LINE_COVERAGE,
                BRANCH_COVERAGE);

        assertThat(dataSet.getSeries(LINE_COVERAGE)).containsExactly(50.0);
        assertThat(dataSet.getSeries(BRANCH_COVERAGE)).containsExactly(75.0);
    }

    @Test
    void shouldHaveTwoValuesForTwoBuilds() {
        var builder = new CoverageSeriesBuilder();

        BuildResult<CoverageStatistics> first = createResult(1,
                new CoverageBuilder().withMetric(Metric.LINE).withCovered(1).withMissed(1).build(),
                new CoverageBuilder().withMetric(Metric.BRANCH).withCovered(3).withMissed(1).build());
        BuildResult<CoverageStatistics> second = createResult(2,
                new CoverageBuilder().withMetric(Metric.LINE).withCovered(1).withMissed(3).build(),
                new CoverageBuilder().withMetric(Metric.BRANCH).withCovered(1).withMissed(3).build());

        var dataSet = builder.createDataSet(createConfiguration(), List.of(first, second));

        assertThat(dataSet.getDomainAxisSize()).isEqualTo(2);
        assertThat(dataSet.getDomainAxisLabels()).containsExactly("#1", "#2");

        assertThat(dataSet.getDataSetIds()).containsExactlyInAnyOrder(
                LINE_COVERAGE,
                BRANCH_COVERAGE);

        assertThat(dataSet.getSeries(LINE_COVERAGE))
                .containsExactly(50.0, 25.0);
        assertThat(dataSet.getSeries(BRANCH_COVERAGE))
                .containsExactly(75.0, 25.0);

        var trendChart = createTrend();
        var model = trendChart.create(List.of(first, second), createConfiguration());

        assertThatJson(model).isEqualTo(toString("chart.json"));
    }

    private ChartModelConfiguration createConfiguration() {
        ChartModelConfiguration configuration = mock(ChartModelConfiguration.class);
        when(configuration.getAxisType()).thenReturn(AxisType.BUILD);
        return configuration;
    }
}
