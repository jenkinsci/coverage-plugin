package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.util.QualityGateResult;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junitpioneer.jupiter.DefaultLocale;

/**
 * Tests the class {@link HelpModel}.
 *
 * @author Ullrich Hafner
 */
@DefaultLocale("en")
class HelpModelTest extends AbstractCoverageTest {
    private static final String ID = "coverage";

    @Test
    void shouldProvideDisplayNameAndOwner() {
        Run<?, ?> run = mock(Run.class);
        var model = createModel(readJacocoResult(JACOCO_CODING_STYLE_FILE), run);

        assertThat(model.getDisplayName()).isEqualTo(Messages.HelpModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(run);
        assertThat(model.getTab()).isNotNull();
        assertThat(model.getFormatter()).isNotNull();
    }

    @ParameterizedTest(name = "{0} should have a description")
    @EnumSource(Metric.class)
    void shouldDescribeAllMetrics(final Metric metric) {
        var model = createModel(readJacocoResult(JACOCO_CODING_STYLE_FILE));

        assertThat(model.getMetricDescription(metric)).isNotBlank();
        assertThat(model.getComputation(metric)).isNotBlank();
        assertThat(model.getGroup(metric)).isNotBlank();
        assertThat(model.getTendency(metric)).isNotBlank();
        assertThat(model.getAggregationElements(metric)).isNotBlank();
    }

    @ParameterizedTest(name = "{0} should have a description")
    @EnumSource(Baseline.class)
    void shouldDescribeAllBaselines(final Baseline baseline) {
        var model = createModel(readJacocoResult(JACOCO_CODING_STYLE_FILE));

        assertThat(model.getBaselines()).contains(baseline);
        assertThat(model.getBaselineDescription(baseline)).isNotBlank();
    }

    @ParameterizedTest(name = "{0} should have a description")
    @EnumSource(MetricAggregation.class)
    void shouldDescribeAllAggregations(final MetricAggregation aggregation) {
        var model = createModel(readJacocoResult(JACOCO_CODING_STYLE_FILE));

        assertThat(model.getAggregations()).contains(aggregation);
        assertThat(model.getAggregationDescription(aggregation)).isNotBlank();
    }

    @Test
    void shouldShowOnlyMetricsOfReport() {
        var model = createModel(readJacocoResult(JACOCO_CODING_STYLE_FILE));

        assertThat(model.getMetrics())
                .contains(Metric.LINE, Metric.BRANCH, Metric.CYCLOMATIC_COMPLEXITY)
                .doesNotContain(Metric.MUTATION, Metric.COHESION);
    }

    @Test
    void shouldDescribeRulesOfMetrics() {
        var model = createModel(readJacocoResult(JACOCO_CODING_STYLE_FILE));

        assertThat(model.getGroup(Metric.LINE)).isEqualTo("Percentage");
        assertThat(model.getTendency(Metric.LINE)).isEqualTo("Larger is better");
        assertThat(model.getComputation(Metric.LINE)).isEqualTo("Sum of all values");
        assertThat(model.getAggregationElements(Metric.LINE)).isEqualTo("Files");

        assertThat(model.getGroup(Metric.CYCLOMATIC_COMPLEXITY)).isEqualTo("Absolute");
        assertThat(model.getTendency(Metric.CYCLOMATIC_COMPLEXITY)).isEqualTo("Smaller is better");
        assertThat(model.getAggregationElements(Metric.CYCLOMATIC_COMPLEXITY)).isEqualTo("Methods");

        assertThat(model.getComputation(Metric.COHESION)).isEqualTo("Mean of the values of all classes");
        assertThat(model.getAggregationElements(Metric.COHESION)).isEqualTo("Classes");

        assertThat(model.getAggregationElements(Metric.FILE)).isEqualTo("–");
    }

    private static HelpModel createModel(final Node node) {
        return createModel(node, mock(Run.class));
    }

    private static HelpModel createModel(final Node node, final Run<?, ?> run) {
        return new HelpModel(new CoverageViewModel(
                run,
                ID,
                "",
                node,
                ChangeSet.empty(),
                createStatistics(),
                new QualityGateResult(),
                "-",
                new FilteredLog("Errors"),
                Function.identity(),
                Function.identity()));
    }
}
