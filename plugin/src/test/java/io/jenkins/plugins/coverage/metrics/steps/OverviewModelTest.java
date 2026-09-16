package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junitpioneer.jupiter.DefaultLocale;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.util.FilteredLog;

import java.util.List;
import java.util.Locale;

import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.util.QualityGateResult;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link OverviewModel}.
 *
 * @author Ullrich Hafner
 */
class OverviewModelTest extends AbstractCoverageTest {
    @ParameterizedTest(name = "{0} is shown as a dashboard card")
    @EnumSource(value = Metric.class, names = {"LINE", "BRANCH", "INSTRUCTION", "MCDC_PAIR", "FUNCTION_CALL",
            "MUTATION", "TEST_STRENGTH"})
    void shouldTreatCoverageMetricsAndTestStrengthAsCardMetrics(final Metric metric) {
        assertThat(OverviewModel.isCardMetric(metric)).isTrue();
    }

    @ParameterizedTest(name = "{0} is shown in the further-metrics table, not as a card")
    @EnumSource(value = Metric.class, names = {"MODULE", "CONTAINER", "PACKAGE", "FILE", "CLASS", "METHOD",
            "TESTS", "LOC", "CYCLOMATIC_COMPLEXITY"})
    void shouldNotTreatContainerOrNonCoverageMetricsAsCardMetrics(final Metric metric) {
        assertThat(OverviewModel.isCardMetric(metric)).isFalse();
    }

    @Test
    void shouldPartitionMetricsIntoCardsAndFurtherMetrics() {
        var model = createModel();

        assertThat(model.getCoverageMetrics()).containsExactly(Metric.LINE, Metric.BRANCH, Metric.INSTRUCTION)
                .allMatch(OverviewModel::isCardMetric);
        assertThat(model.getCountMetrics()).isNotEmpty().noneMatch(OverviewModel::isCardMetric);
    }

    @Test
    @DefaultLocale("de")
    void shouldFormatThePercentageWithAPeriodEvenInAGermanLocale() {
        var model = createModel();

        var lineCoverage = (Coverage) model.getAllValues(Baseline.PROJECT).stream()
                .filter(value -> value.getMetric() == Metric.LINE)
                .findFirst()
                .orElseThrow();
        var expected = String.format(Locale.ENGLISH, "%.2f", lineCoverage.getCoveredPercentage().toDouble());

        // the active (German) locale would normally use ',' as the decimal separator -- this must still use '.'
        assertThat(model.getPercentage(Baseline.PROJECT, Metric.LINE)).isEqualTo(expected).doesNotContain(",");
    }

    @Test
    void shouldReturnZeroPercentageWhenNoValueIsAvailable() {
        var model = createModel();

        // MODIFIED_LINES has no results here since createModel() sets up no reference build
        assertThat(model.getPercentage(Baseline.MODIFIED_LINES, Metric.LINE)).isEqualTo("0");
    }

    @Test
    void shouldFormatCoveredAndTotalForACoverageMetric() {
        var model = createModel();

        assertThat(model.getCoveredTotal(Baseline.PROJECT, Metric.LINE))
                .isEqualTo(JACOCO_CODING_STYLE_COVERED + " / " + JACOCO_CODING_STYLE_TOTAL);
    }

    @Test
    void shouldReturnEmptyCoveredTotalWhenNoValueIsAvailable() {
        var model = createModel();

        // MODIFIED_LINES has no results here since createModel() sets up no reference build
        assertThat(model.getCoveredTotal(Baseline.MODIFIED_LINES, Metric.LINE)).isEmpty();
    }

    @Test
    void shouldHaveNoReferenceBuildTextOrUrlWhenThereIsNoReferenceBuild() {
        var model = createModel();

        assertThat(model.getReferenceBuild()).isEmpty();
        assertThat(model.getReferenceBuildText()).isEmpty();
        assertThat(model.getReferenceBuildUrl()).isEmpty();
    }

    private OverviewModel createModel() {
        var node = readJacocoResult(JACOCO_CODING_STYLE_FILE);
        var action = new CoverageBuildAction(mock(Run.class), "coverage", "", "", node,
                new QualityGateResult(), new FilteredLog("Errors"), "-",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false);
        return new OverviewModel(mock(Run.class), action);
    }
}
