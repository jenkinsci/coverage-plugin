package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.hm.hafner.coverage.ClassNode;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.MethodNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.util.NullResultHandler;
import io.jenkins.plugins.util.QualityGate.QualityGateCriticality;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.QualityGateStatus;

import static io.jenkins.plugins.util.assertions.Assertions.*;

class CoverageQualityGateEvaluatorTest extends AbstractCoverageTest {
    @Test
    void shouldBeInactiveIfGatesAreEmpty() {
        var evaluator = new CoverageQualityGateEvaluator(new ArrayList<>(), createStatistics());

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result)
                .hasNoMessages()
                .isInactive()
                .isSuccessful()
                .hasOverallStatus(QualityGateStatus.INACTIVE);
        assertThat(log.getInfoMessages()).containsExactly(
                "No quality gates have been set - skipping");
    }

    @Test
    void shouldPassForTooLowThresholds() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(0, Metric.FILE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.FILE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.FILE, Baseline.MODIFIED_FILES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.MODIFIED_FILES, QualityGateCriticality.UNSTABLE));

        var minimum = -10;
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.MODIFIED_LINES_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.MODIFIED_LINES_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.MODIFIED_FILES_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.MODIFIED_FILES_DELTA, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        assertThat(evaluator).isEnabled();

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.PASSED).isSuccessful().isNotInactive().hasMessages(
                "[Overall project - File Coverage]: ≪Success≫ - (Actual value: 75.00%, Quality gate: 0.00)",
                "[Overall project - Line Coverage]: ≪Success≫ - (Actual value: 50.00%, Quality gate: 0.00)",
                "[Modified code lines - File Coverage]: ≪Success≫ - (Actual value: 75.00%, Quality gate: 0.00)",
                "[Modified code lines - Line Coverage]: ≪Success≫ - (Actual value: 50.00%, Quality gate: 0.00)",
                "[Modified files - File Coverage]: ≪Success≫ - (Actual value: 75.00%, Quality gate: 0.00)",
                "[Modified files - Line Coverage]: ≪Success≫ - (Actual value: 50.00%, Quality gate: 0.00)",
                "[Overall project (difference to reference job) - File Coverage]: ≪Success≫ - (Actual value: -10.00%, Quality gate: -10.00)",
                "[Overall project (difference to reference job) - Line Coverage]: ≪Success≫ - (Actual value: +5.00%, Quality gate: -10.00)",
                "[Modified code lines (difference to modified files) - File Coverage]: ≪Success≫ - (Actual value: -10.00%, Quality gate: -10.00)",
                "[Modified code lines (difference to modified files) - Line Coverage]: ≪Success≫ - (Actual value: +5.00%, Quality gate: -10.00)",
                "[Modified files (difference to reference job) - File Coverage]: ≪Success≫ - (Actual value: -10.00%, Quality gate: -10.00)",
                "[Modified files (difference to reference job) - Line Coverage]: ≪Success≫ - (Actual value: +5.00%, Quality gate: -10.00)");
    }

    @Test
    void shouldSkipIfValueNotDefined() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(0, Metric.FILE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.FILE, Baseline.MODIFIED_FILES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.FILE, Baseline.MODIFIED_LINES_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.MODIFIED_FILES_DELTA, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createOnlyProjectStatistics());

        assertThat(evaluator).isEnabled();

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.INACTIVE).isInactive().hasMessages(
                "[Modified code lines - File Coverage]: ≪Not built≫ - (Actual value: n/a, Quality gate: 0.00)",
                "[Modified files - File Coverage]: ≪Not built≫ - (Actual value: n/a, Quality gate: 0.00)",
                "[Overall project (difference to reference job) - Line Coverage]: ≪Not built≫ - (Actual value: n/a, Quality gate: 0.00)",
                "[Modified code lines (difference to modified files) - File Coverage]: ≪Not built≫ - (Actual value: n/a, Quality gate: 0.00)",
                "[Modified files (difference to reference job) - Line Coverage]: ≪Not built≫ - (Actual value: n/a, Quality gate: 0.00)");
    }

    @Test
    void shouldReportUnstableIfBelowThreshold() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(76.0, Metric.FILE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(76.0, Metric.FILE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(76.0, Metric.FILE, Baseline.MODIFIED_FILES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.MODIFIED_FILES, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().isNotInactive().hasMessages(
                "[Overall project - File Coverage]: ≪Unstable≫ - (Actual value: 75.00%, Quality gate: 76.00)",
                "[Overall project - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 51.00)",
                "[Modified code lines - File Coverage]: ≪Unstable≫ - (Actual value: 75.00%, Quality gate: 76.00)",
                "[Modified code lines - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 51.00)",
                "[Modified files - File Coverage]: ≪Unstable≫ - (Actual value: 75.00%, Quality gate: 76.00)",
                "[Modified files - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 51.00)");
    }

    @Test
    void shouldReportUnstableIfWorseAndSuccessIfBetter() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var minimum = 0;
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.MODIFIED_LINES_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.MODIFIED_LINES_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.MODIFIED_FILES_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.MODIFIED_FILES_DELTA, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().isNotInactive().hasMessages(
                "[Overall project (difference to reference job) - File Coverage]: ≪Unstable≫ - (Actual value: -10.00%, Quality gate: 0.00)",
                "[Overall project (difference to reference job) - Line Coverage]: ≪Success≫ - (Actual value: +5.00%, Quality gate: 0.00)",
                "[Modified code lines (difference to modified files) - File Coverage]: ≪Unstable≫ - (Actual value: -10.00%, Quality gate: 0.00)",
                "[Modified code lines (difference to modified files) - Line Coverage]: ≪Success≫ - (Actual value: +5.00%, Quality gate: 0.00)",
                "[Modified files (difference to reference job) - File Coverage]: ≪Unstable≫ - (Actual value: -10.00%, Quality gate: 0.00)",
                "[Modified files (difference to reference job) - Line Coverage]: ≪Success≫ - (Actual value: +5.00%, Quality gate: 0.00)");
    }

    @ParameterizedTest(name = "A quality gate of {0} should not be passed if the coverage drops by 10%")
    @ValueSource(ints = {8, 1, 0, -1, -8})
    void shouldHandleNegativeValues(final double minimum) {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().isNotInactive().hasMessages(
                "[Overall project (difference to reference job) - File Coverage]: ≪Unstable≫ - (Actual value: -10.00%%, Quality gate: %.2f)".formatted(minimum));
    }

    @ParameterizedTest(name = "A quality gate of {0} should be passed if the coverage is at 50%")
    @ValueSource(ints = {-10, 0, 10, 50})
    void shouldPassAllThresholds(final double minimum) {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.PASSED).isSuccessful().isNotInactive().hasMessages(
                "[Overall project - Line Coverage]: ≪Success≫ - (Actual value: 50.00%%, Quality gate: %.2f)".formatted(minimum));
    }

    @ParameterizedTest(name = "A quality gate of {0} should not be passed if the coverage is at 50%")
    @ValueSource(ints = {51, 60, 70, 200})
    void shouldFailAllThresholds(final double minimum) {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().isNotInactive().hasMessages(
                "[Overall project - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%%, Quality gate: %.2f)".formatted(minimum));
    }

    @Test
    void shouldReportUnstableIfLargerThanThreshold() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(149.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(14, Metric.NPATH_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(999, Metric.LOC, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().isNotInactive().hasMessages(
                "[Overall project - Cyclomatic Complexity]: ≪Unstable≫ - (Actual value: 150, Quality gate: 149.00)",
                "[Overall project - N-Path Complexity]: ≪Unstable≫ - (Actual value: 15, Quality gate: 14.00)",
                "[Modified code lines - Lines of Code]: ≪Unstable≫ - (Actual value: 1000, Quality gate: 999.00)");
    }

    @Test
    void shouldReportUnstableIfWorseAndSuccessIfLargerThanThreshold() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var minimum = 0;
        qualityGates.add(new CoverageQualityGate(minimum, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LOC, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().isNotInactive().hasMessages(
                "[Overall project (difference to reference job) - Cyclomatic Complexity]: ≪Success≫ - (Actual value: -10, Quality gate: 0.00)",
                "[Overall project (difference to reference job) - Lines of Code]: ≪Unstable≫ - (Actual value: +5, Quality gate: 0.00)");
    }

    @Test
    void shouldReportFailureIfBelowThreshold() {
        QualityGateResult result = createQualityGateResult();

        assertThat(result).hasOverallStatus(QualityGateStatus.FAILED).isNotSuccessful().isNotInactive().hasMessages(
                "[Overall project - File Coverage]: ≪Failed≫ - (Actual value: 75.00%, Quality gate: 76.00)",
                "[Overall project - Line Coverage]: ≪Failed≫ - (Actual value: 50.00%, Quality gate: 51.00)",
                "[Modified code lines - File Coverage]: ≪Failed≫ - (Actual value: 75.00%, Quality gate: 76.00)",
                "[Modified code lines - Line Coverage]: ≪Failed≫ - (Actual value: 50.00%, Quality gate: 51.00)",
                "[Modified files - File Coverage]: ≪Failed≫ - (Actual value: 75.00%, Quality gate: 76.00)",
                "[Modified files - Line Coverage]: ≪Failed≫ - (Actual value: 50.00%, Quality gate: 51.00)",
                "[Overall project (difference to reference job) - File Coverage]: ≪Failed≫ - (Actual value: -10.00%, Quality gate: 10.00)",
                "[Overall project (difference to reference job) - Line Coverage]: ≪Failed≫ - (Actual value: +5.00%, Quality gate: 10.00)",
                "[Modified code lines (difference to modified files) - File Coverage]: ≪Failed≫ - (Actual value: -10.00%, Quality gate: 10.00)",
                "[Modified code lines (difference to modified files) - Line Coverage]: ≪Failed≫ - (Actual value: +5.00%, Quality gate: 10.00)",
                "[Modified files (difference to reference job) - File Coverage]: ≪Failed≫ - (Actual value: -10.00%, Quality gate: 10.00)",
                "[Modified files (difference to reference job) - Line Coverage]: ≪Failed≫ - (Actual value: +5.00%, Quality gate: 10.00)");
    }

    static QualityGateResult createQualityGateResult() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(76.0, Metric.FILE, Baseline.PROJECT, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(76.0, Metric.FILE, Baseline.MODIFIED_LINES, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.MODIFIED_LINES, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(76.0, Metric.FILE, Baseline.MODIFIED_FILES, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.MODIFIED_FILES, QualityGateCriticality.FAILURE));

        var minimum = 10;
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.PROJECT_DELTA, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.PROJECT_DELTA, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.MODIFIED_LINES_DELTA, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.MODIFIED_LINES_DELTA, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.FILE, Baseline.MODIFIED_FILES_DELTA, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LINE, Baseline.MODIFIED_FILES_DELTA, QualityGateCriticality.FAILURE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        return evaluator.evaluate(new NullResultHandler(), new FilteredLog("Errors"));
    }

    @Test
    void shouldOverwriteStatus() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(76.0, Metric.FILE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.FAILURE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        assertThatStatusWillBeOverwritten(evaluator);
    }

    @Test
    void shouldOverwriteStageStatus() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(76.0, Metric.FILE, Baseline.PROJECT, QualityGateCriticality.NOTE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);
        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().hasMessages(
                "[Overall project - File Coverage]: ≪Unstable≫ - (Actual value: 75.00%, Quality gate: 76.00)",
                "[Overall project - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 51.00)");
    }

    @Test
    void shouldAddAllQualityGates() {
        Collection<CoverageQualityGate> qualityGates = List.of(
                new CoverageQualityGate(76.0, Metric.FILE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE),
                new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.FAILURE));

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        assertThatStatusWillBeOverwritten(evaluator);
    }

    @Test
    void shouldSupportMaximumAggregation() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(10.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.MAXIMUM);
        qualityGates.add(gate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful();
    }

    @Test
    void shouldSupportMaximumAggregationWithRootNode() {
        var rootNode = createNodeTreeWithComplexity();
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(15.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.MAXIMUM);
        qualityGates.add(gate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics(), rootNode);

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // The maximum complexity in the tree is 20, which is > 15.0
        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful();
    }

    @Test
    void shouldSupportAverageAggregation() {
        var rootNode = createNodeTreeWithComplexity();
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(10.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.AVERAGE);
        qualityGates.add(gate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics(), rootNode);

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // The average complexity is (5 + 10 + 15 + 20) / 4 = 12.5, which is > 10.0
        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful();
    }

    @Test
    void shouldHandleTotalAggregationAsDefault() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(149.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        // Do not set aggregation, should default to TOTAL
        qualityGates.add(gate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // Should use total value from statistics (150) which is > 149.0
        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().hasMessages(
                "[Overall project - Cyclomatic Complexity]: ≪Unstable≫ - (Actual value: 150, Quality gate: 149.00)");
    }

    @Test
    void shouldNotApplyAggregationForCoverageMetrics() {
        var rootNode = createNodeTreeWithCoverage();
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(60.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.MAXIMUM);
        qualityGates.add(gate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics(), rootNode);

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // For coverage metrics, aggregation should be ignored and use statistics value (50%)
        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().hasMessages(
                "[Overall project - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 60.00)");
    }

    @Test
    void shouldSupportMultipleAggregationsInSameEvaluation() {
        var rootNode = createNodeTreeWithComplexity();
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var maxGate = new CoverageQualityGate(15.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        maxGate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.MAXIMUM);
        qualityGates.add(maxGate);

        var avgGate = new CoverageQualityGate(10.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.FAILURE);
        avgGate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.AVERAGE);
        qualityGates.add(avgGate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics(), rootNode);

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // Both should fail: maximum is 20 > 15.0 and average is 12.5 > 10.0
        assertThat(result).hasOverallStatus(QualityGateStatus.FAILED).isNotSuccessful();
    }

    @Test
    void shouldHandleEmptyNodeTree() {
        var rootNode = new ModuleNode("empty");
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(10.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.MAXIMUM);
        qualityGates.add(gate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics(), rootNode);

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // Empty node tree has no values to aggregate, should mark as inactive
        assertThat(result).hasOverallStatus(QualityGateStatus.INACTIVE).isInactive();
    }

    @Test
    void shouldHandleNullRootNodeForAggregation() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(10.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.MAXIMUM);
        qualityGates.add(gate);

        // No root node provided
        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // Should fall back to statistics value when root node is null
        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful();
    }

    @Test
    void shouldFallBackToStatisticsForNonProjectBaseline() {
        var rootNode = createNodeTreeWithComplexity();
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(10.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.MAXIMUM);
        qualityGates.add(gate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics(), rootNode);

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // For non-PROJECT baselines, should use statistics even with custom aggregation
        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful();
    }

    @Test
    void shouldSupportMaximumAggregationForLinesOfCode() {
        var rootNode = createNodeTreeWithLOC();
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var gate = new CoverageQualityGate(55.0, Metric.LOC, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(io.jenkins.plugins.coverage.metrics.model.MetricAggregation.MAXIMUM);
        qualityGates.add(gate);

        var evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics(), rootNode);

        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);

        // The maximum LOC is 60, which is > 55.0
        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful();
    }

    /**
     * Creates a node tree with cyclomatic complexity values for testing.
     * Structure: Module -> Class -> Methods with complexity values: 5, 10, 15, 20
     *
     * @return a node tree with complexity values
     */
    private static Node createNodeTreeWithComplexity() {
        var root = new ModuleNode("TestProject");
        var classNode = new ClassNode("TestClass");
        root.addChild(classNode);

        var method1 = new MethodNode("method1", "()V");
        method1.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 5));
        classNode.addChild(method1);

        var method2 = new MethodNode("method2", "()V");
        method2.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 10));
        classNode.addChild(method2);

        var method3 = new MethodNode("method3", "()V");
        method3.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 15));
        classNode.addChild(method3);

        var method4 = new MethodNode("method4", "()V");
        method4.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 20));
        classNode.addChild(method4);

        return root;
    }

    /**
     * Creates a node tree with LOC values for testing.
     * Structure: Module -> Class -> Methods with LOC values: 30, 40, 50, 60
     *
     * @return a node tree with LOC values
     */
    private static Node createNodeTreeWithLOC() {
        var root = new ModuleNode("TestProject");
        var classNode = new ClassNode("TestClass");
        root.addChild(classNode);

        var method1 = new MethodNode("method1", "()V");
        method1.addValue(new Value(Metric.LOC, 30));
        classNode.addChild(method1);

        var method2 = new MethodNode("method2", "()V");
        method2.addValue(new Value(Metric.LOC, 40));
        classNode.addChild(method2);

        var method3 = new MethodNode("method3", "()V");
        method3.addValue(new Value(Metric.LOC, 50));
        classNode.addChild(method3);

        var method4 = new MethodNode("method4", "()V");
        method4.addValue(new Value(Metric.LOC, 60));
        classNode.addChild(method4);

        return root;
    }

    /**
     * Creates a node tree with coverage values for testing.
     * Structure: Module -> Class -> Methods with line coverage
     *
     * @return a node tree with coverage values
     */
    private static Node createNodeTreeWithCoverage() {
        var root = new ModuleNode("TestProject");
        var classNode = new ClassNode("TestClass");
        root.addChild(classNode);

        var method1 = new MethodNode("method1", "()V");
        method1.addValue(new CoverageBuilder().withMetric(Metric.LINE).withCovered(5).withMissed(5).build());
        classNode.addChild(method1);

        var method2 = new MethodNode("method2", "()V");
        method2.addValue(new CoverageBuilder().withMetric(Metric.LINE).withCovered(8).withMissed(2).build());
        classNode.addChild(method2);

        return root;
    }

    private static void assertThatStatusWillBeOverwritten(final CoverageQualityGateEvaluator evaluator) {
        var log = new FilteredLog("Errors");
        var result = evaluator.evaluate(new NullResultHandler(), log);
        assertThat(result).hasOverallStatus(QualityGateStatus.FAILED).isNotSuccessful().hasMessages(
                "[Overall project - File Coverage]: ≪Unstable≫ - (Actual value: 75.00%, Quality gate: 76.00)",
                "[Overall project - Line Coverage]: ≪Failed≫ - (Actual value: 50.00%, Quality gate: 51.00)");
    }
}
