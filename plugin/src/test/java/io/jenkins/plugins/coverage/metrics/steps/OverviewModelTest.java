package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.ClassNode;
import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.MethodNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.util.QualityGate.QualityGateCriticality;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.QualityGateResult.QualityGateResultItem;
import io.jenkins.plugins.util.QualityGateStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junitpioneer.jupiter.DefaultLocale;

/**
 * Tests the class {@link OverviewModel}.
 *
 * @author Ullrich Hafner
 */
class OverviewModelTest extends AbstractCoverageTest {
    @ParameterizedTest(name = "{0} is shown as a dashboard card")
    @EnumSource(
            value = Metric.class,
            names = {"LINE", "BRANCH", "INSTRUCTION", "MCDC_PAIR", "FUNCTION_CALL", "MUTATION", "TEST_STRENGTH"})
    void shouldTreatCoverageMetricsAndTestStrengthAsCardMetrics(final Metric metric) {
        assertThat(OverviewModel.isCardMetric(metric)).isTrue();
    }

    @ParameterizedTest(name = "{0} is shown in the further-metrics table, not as a card")
    @EnumSource(
            value = Metric.class,
            names = {
                "MODULE",
                "CONTAINER",
                "PACKAGE",
                "FILE",
                "CLASS",
                "METHOD",
                "TESTS",
                "LOC",
                "CYCLOMATIC_COMPLEXITY"
            })
    void shouldNotTreatContainerOrNonCoverageMetricsAsCardMetrics(final Metric metric) {
        assertThat(OverviewModel.isCardMetric(metric)).isFalse();
    }

    @Test
    void shouldPartitionMetricsIntoCardsAndFurtherMetrics() {
        var model = createModel();

        assertThat(model.getCoverageMetrics())
                .containsExactly(Metric.LINE, Metric.BRANCH, Metric.INSTRUCTION)
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
        var expected = String.format(
                Locale.ENGLISH, "%.2f", lineCoverage.getCoveredPercentage().toDouble());

        // the active (German) locale would normally use ',' as the decimal separator -- this must still use '.'
        assertThat(model.getPercentage(Baseline.PROJECT, Metric.LINE))
                .isEqualTo(expected)
                .doesNotContain(",");
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
    void shouldFormatMissedItemsForACoverageMetric() {
        var model = createModel();

        assertThat(model.getMissedText(Baseline.PROJECT, Metric.LINE))
                .isEqualTo(Messages.OverviewModel_missed(JACOCO_CODING_STYLE_TOTAL - JACOCO_CODING_STYLE_COVERED));
    }

    @Test
    void shouldReturnEmptyMissedTextWhenNoValueIsAvailable() {
        var model = createModel();

        // MODIFIED_LINES has no results here since createModel() sets up no reference build
        assertThat(model.getMissedText(Baseline.MODIFIED_LINES, Metric.LINE)).isEmpty();
    }

    @Test
    void shouldNotBePerfectIfItemsAreMissed() {
        var model = createModel();

        assertThat(model.isPerfect(Baseline.PROJECT, Metric.LINE)).isFalse();
    }

    @Test
    void shouldNotBePerfectWhenNoValueIsAvailable() {
        var model = createModel();

        // MODIFIED_LINES has no results here since createModel() sets up no reference build
        assertThat(model.isPerfect(Baseline.MODIFIED_LINES, Metric.LINE)).isFalse();
    }

    @Test
    void shouldBePerfectIfNothingIsMissed() {
        var file = new FileNode("Covered.java", "src/Covered.java");
        file.addValue(new CoverageBuilder()
                .withMetric(Metric.LINE)
                .withCovered(10)
                .withMissed(0)
                .build());
        var module = new ModuleNode("module");
        module.addChild(file);
        var model = createModel(module);

        assertThat(model.isPerfect(Baseline.PROJECT, Metric.LINE)).isTrue();
        assertThat(model.getMissedText(Baseline.PROJECT, Metric.LINE)).isEmpty();
    }

    @ParameterizedTest(name = "covered={0}, missed={1}, threshold={2} -> {3} items to cover")
    @CsvSource({
        "30, 20, 80.0, 10",
        "40, 10, 80.0, 0",
        "45, 5, 80.0, 0",
        "0, 10, 100.0, 10",
        "700, 300, 79.9, 99",
        "45, 5, 150.0, 5"
    })
    void shouldCountItemsToCoverForThreshold(
            final int covered, final int missed, final double threshold, final int expected) {
        var coverage = new CoverageBuilder()
                .withMetric(Metric.LINE)
                .withCovered(covered)
                .withMissed(missed)
                .build();

        assertThat(OverviewModel.countMissingItems(coverage, threshold)).isEqualTo(expected);
    }

    @Test
    void shouldSuggestMissingLinesForFailedQualityGate() {
        var model = createModel(
                readJacocoResult(JACOCO_CODING_STYLE_FILE), createResult(Baseline.PROJECT, QualityGateStatus.WARNING));

        assertThat(model.getQualityGateHint(getFirstItem(model)))
                .isEqualTo(Messages.OverviewModel_hintLine(JACOCO_CODING_STYLE_MISSED));
    }

    @Test
    void shouldNotSuggestAnythingForPassedQualityGate() {
        var model = createModel(
                readJacocoResult(JACOCO_CODING_STYLE_FILE), createResult(Baseline.PROJECT, QualityGateStatus.PASSED));

        assertThat(model.getQualityGateHint(getFirstItem(model))).isEmpty();
    }

    @Test
    void shouldNotSuggestAnythingForDeltaOrMissingBaselines() {
        var delta = createModel(
                readJacocoResult(JACOCO_CODING_STYLE_FILE),
                createResult(Baseline.PROJECT_DELTA, QualityGateStatus.WARNING));
        assertThat(delta.getQualityGateHint(getFirstItem(delta))).isEmpty();

        // MODIFIED_LINES has no results here since createModel() sets up no reference build
        var missing = createModel(
                readJacocoResult(JACOCO_CODING_STYLE_FILE),
                createResult(Baseline.MODIFIED_LINES, QualityGateStatus.WARNING));
        assertThat(missing.getQualityGateHint(getFirstItem(missing))).isEmpty();
    }

    private static QualityGateResult createResult(final Baseline baseline, final QualityGateStatus status) {
        var result = new QualityGateResult();
        result.add(new CoverageQualityGate(100, Metric.LINE, baseline, QualityGateCriticality.UNSTABLE), status, "-");
        return result;
    }

    private static QualityGateResultItem getFirstItem(final OverviewModel model) {
        return model.getQualityGateResult().getResultItems().get(0);
    }

    @Test
    void shouldHaveNoReferenceBuildTextOrUrlWhenThereIsNoReferenceBuild() {
        var model = createModel();

        assertThat(model.getReferenceBuild()).isEmpty();
        assertThat(model.getReferenceBuildText()).isEmpty();
        assertThat(model.getReferenceBuildUrl()).isEmpty();
    }

    @Test
    @DefaultLocale("en")
    void shouldProvideWorstValuesOfSoftwareMetrics() {
        var node = createModuleWithMethods();
        var statistics = new CoverageStatistics(
                Map.of(
                        MetricAggregation.OVERALL,
                        node.aggregateValues(),
                        MetricAggregation.MAXIMUM,
                        node.aggregateValues(MetricAggregation.MAXIMUM),
                        MetricAggregation.MINIMUM,
                        node.aggregateValues(MetricAggregation.MINIMUM)),
                Map.of(
                        MetricAggregation.MAXIMUM,
                        List.of(new Difference(Metric.CYCLOMATIC_COMPLEXITY, 3)),
                        MetricAggregation.MINIMUM,
                        List.of(new Difference(Metric.COHESION, 1, 10))),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
        var worst = createModel(node, statistics).getWorstValues();

        assertThat(worst.hasValue(Baseline.PROJECT, Metric.CYCLOMATIC_COMPLEXITY))
                .isTrue();
        assertThat(worst.formatValue(Baseline.PROJECT, Metric.CYCLOMATIC_COMPLEXITY))
                .isEqualTo("Maximum: 10");
        assertThat(worst.getTooltip(Metric.CYCLOMATIC_COMPLEXITY)).isEqualTo("Maximum of all methods");
        assertThat(worst.hasDelta(Baseline.PROJECT, Metric.CYCLOMATIC_COMPLEXITY))
                .isTrue();
        assertThat(worst.formatDelta(Baseline.PROJECT, Metric.CYCLOMATIC_COMPLEXITY))
                .contains("3");
        assertThat(worst.getTrend(Baseline.PROJECT, Metric.CYCLOMATIC_COMPLEXITY))
                .as("an increased maximum complexity is a deterioration")
                .isNegative();

        assertThat(worst.hasValue(Baseline.PROJECT, Metric.COHESION)).isTrue();
        assertThat(worst.formatValue(Baseline.PROJECT, Metric.COHESION)).isEqualTo("Minimum: 20.00%");
        assertThat(worst.getTooltip(Metric.COHESION)).isEqualTo("Minimum of all classes");
        assertThat(worst.getTrend(Baseline.PROJECT, Metric.COHESION))
                .as("an increased minimum cohesion is an improvement")
                .isPositive();

        assertThat(worst.hasValue(Baseline.PROJECT, Metric.METHOD))
                .as("container metrics have no worst value")
                .isFalse();
        assertThat(worst.hasValue(Baseline.MODIFIED_LINES, Metric.CYCLOMATIC_COMPLEXITY))
                .isFalse();
        assertThat(worst.hasDelta(Baseline.MODIFIED_LINES, Metric.CYCLOMATIC_COMPLEXITY))
                .isFalse();
        assertThat(worst.formatValue(Baseline.MODIFIED_LINES, Metric.CYCLOMATIC_COMPLEXITY))
                .isEqualTo(Messages.Coverage_Not_Available());
        assertThat(worst.formatDelta(Baseline.MODIFIED_LINES, Metric.CYCLOMATIC_COMPLEXITY))
                .isEqualTo(Messages.Coverage_Not_Available());
        assertThat(worst.getTrend(Baseline.MODIFIED_LINES, Metric.CYCLOMATIC_COMPLEXITY))
                .isZero();
    }

    @Test
    void shouldConsiderTendencyForTrendOfDeltas() {
        var deltas = List.of(
                new Difference(Metric.LINE, 5),
                new Difference(Metric.CYCLOMATIC_COMPLEXITY, 10),
                new Difference(Metric.LOC, -3),
                new Difference(Metric.TESTS, 0));
        var action = new CoverageBuildAction(
                mock(Run.class),
                "coverage",
                "",
                "",
                createModuleWithMethods(),
                new QualityGateResult(),
                new FilteredLog("Errors"),
                "-",
                deltas,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);
        var model = new OverviewModel(mock(Run.class), action);

        assertThat(model.getTrend(Baseline.PROJECT, Metric.LINE))
                .as("an increased coverage is an improvement")
                .isPositive();
        assertThat(model.getTrend(Baseline.PROJECT, Metric.CYCLOMATIC_COMPLEXITY))
                .as("an increased complexity is a deterioration")
                .isNegative();
        assertThat(model.getTrend(Baseline.PROJECT, Metric.LOC))
                .as("a decreased size is an improvement")
                .isPositive();
        assertThat(model.getTrend(Baseline.PROJECT, Metric.TESTS)).isZero();
        assertThat(model.getTrend(Baseline.PROJECT, Metric.BRANCH)).isZero();
    }

    @Test
    void shouldProvideNoWorstValuesForBuildsWithoutAggregatedValues() {
        var worst = createModel(createModuleWithMethods()).getWorstValues();

        assertThat(worst.hasValue(Baseline.PROJECT, Metric.CYCLOMATIC_COMPLEXITY))
                .isFalse();
        assertThat(worst.hasDelta(Baseline.PROJECT, Metric.CYCLOMATIC_COMPLEXITY))
                .isFalse();
    }

    private static ModuleNode createModuleWithMethods() {
        var module = new ModuleNode("module");
        var file = new FileNode("File.java", "src/File.java");
        module.addChild(file);

        var first = new ClassNode("First");
        first.addValue(new Value(Metric.COHESION, 2, 10));
        first.addChild(createMethod("one", 2));
        first.addChild(createMethod("two", 10));
        file.addChild(first);

        var second = new ClassNode("Second");
        second.addValue(new Value(Metric.COHESION, 6, 10));
        second.addChild(createMethod("three", 4));
        file.addChild(second);

        return module;
    }

    private static MethodNode createMethod(final String name, final int complexity) {
        var method = new MethodNode(name, "()V");
        method.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, complexity));
        return method;
    }

    private OverviewModel createModel(final Node node, final CoverageStatistics statistics) {
        var action = new CoverageBuildAction(
                mock(Run.class),
                "coverage",
                "",
                "",
                node,
                new QualityGateResult(),
                new FilteredLog("Errors"),
                "-",
                statistics,
                ChangeSet.empty(),
                List.of(),
                false);
        return new OverviewModel(mock(Run.class), action);
    }

    private OverviewModel createModel() {
        return createModel(readJacocoResult(JACOCO_CODING_STYLE_FILE));
    }

    private OverviewModel createModel(final Node node) {
        return createModel(node, new QualityGateResult());
    }

    private OverviewModel createModel(final Node node, final QualityGateResult qualityGateResult) {
        var action = new CoverageBuildAction(
                mock(Run.class),
                "coverage",
                "",
                "",
                node,
                qualityGateResult,
                new FilteredLog("Errors"),
                "-",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);
        return new OverviewModel(mock(Run.class), action);
    }
}
