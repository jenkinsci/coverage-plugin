package io.jenkins.plugins.coverage.metrics.steps;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;

import edu.hm.hafner.coverage.Metric;

import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageITest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.util.QualityGate.QualityGateCriticality;
import io.jenkins.plugins.util.QualityGateStatus;

import static io.jenkins.plugins.coverage.metrics.AbstractCoverageTest.*;
import static io.jenkins.plugins.util.assertions.Assertions.*;

/**
 * Integration tests with active quality gates.
 */
class QualityGateITest extends AbstractCoverageITest {
    @Test
    void shouldNotHaveQualityGate() {
        WorkflowJob job = createPipeline(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE);

        Run<?, ?> build = buildWithResult(job, Result.SUCCESS);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getQualityGateResult()).hasOverallStatus(QualityGateStatus.INACTIVE);
        assertThat(coverageResult.getLog().getInfoMessages()).contains("No quality gates have been set - skipping");
    }

    @Test
    void shouldNotHaveValuesForQualityGate() {
        var qualityGates = List.of(new CoverageQualityGate(-100.0, Metric.LINE, Baseline.PROJECT_DELTA, QualityGateCriticality.UNSTABLE));
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO, r -> r.setQualityGates(qualityGates), JACOCO_ANALYSIS_MODEL_FILE);

        Run<?, ?> build = buildWithResult(project, Result.SUCCESS);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getQualityGateResult()).hasOverallStatus(QualityGateStatus.INACTIVE);
        assertThat(coverageResult.getLog().getInfoMessages()).contains("Evaluating quality gates",
                "-> All quality gates have been passed",
                "-> Details for each quality gate:",
                "   - [Overall project (difference to reference job) - Line Coverage]: ≪Not built≫ - (Actual value: n/a, Quality gate: -100.00)");
    }

    @Test
    void shouldPassQualityGate() {
        var qualityGates = List.of(new CoverageQualityGate(-100.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO, r -> r.setQualityGates(qualityGates), JACOCO_ANALYSIS_MODEL_FILE);

        Run<?, ?> build = buildWithResult(project, Result.SUCCESS);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getQualityGateResult()).hasOverallStatus(QualityGateStatus.PASSED);
        assertThat(coverageResult.getLog().getInfoMessages()).contains("Evaluating quality gates",
                "-> All quality gates have been passed",
                "-> Details for each quality gate:",
                "   - [Overall project - Line Coverage]: ≪Success≫ - (Actual value: 95.39%, Quality gate: -100.00)");
    }

    @Test
    void shouldFailQualityGateWithUnstable() {
        var qualityGates = List.of(new CoverageQualityGate(100, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO, r -> r.setQualityGates(qualityGates), JACOCO_ANALYSIS_MODEL_FILE);

        Run<?, ?> build = buildWithResult(project, Result.UNSTABLE);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getQualityGateResult()).hasOverallStatus(QualityGateStatus.WARNING);
        assertThat(coverageResult.getLog().getInfoMessages()).contains("Evaluating quality gates",
                "-> Some quality gates have been missed: overall result is UNSTABLE",
                "-> Details for each quality gate:",
                "   - [Overall project - Line Coverage]: ≪Unstable≫ - (Actual value: 95.39%, Quality gate: 100.00)");
    }

    @Test
    void shouldFailQualityGateWithFailure() {
        var qualityGates = List.of(new CoverageQualityGate(100, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.FAILURE));
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO, r -> r.setQualityGates(qualityGates), JACOCO_ANALYSIS_MODEL_FILE);

        Run<?, ?> build = buildWithResult(project, Result.FAILURE);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getQualityGateResult()).hasOverallStatus(QualityGateStatus.FAILED);
        assertThat(coverageResult.getLog().getInfoMessages()).contains("Evaluating quality gates",
                "-> Some quality gates have been missed: overall result is FAILURE",
                "-> Details for each quality gate:",
                "   - [Overall project - Line Coverage]: ≪Failed≫ - (Actual value: 95.39%, Quality gate: 100.00)");
    }

    @Test
    void shouldUseQualityGateInPipeline() {
        WorkflowJob project = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE);

        setPipelineScript(project,
                "recordCoverage("
                        + "tools: [[parser: '" + Parser.JACOCO.name() + "', pattern: '**/*xml']],\n"
                        + "qualityGates: ["
                        + "     [threshold: 90.0, metric: 'LINE', baseline: 'PROJECT', criticality: 'UNSTABLE'], "
                        + "     [threshold: 90.0, metric: 'BRANCH', baseline: 'PROJECT', criticality: 'UNSTABLE']])\n");

        WorkflowRun build = (WorkflowRun)buildWithResult(project, Result.UNSTABLE);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getQualityGateResult()).hasOverallStatus(QualityGateStatus.WARNING);

        assertThat(coverageResult.getLog().getInfoMessages()).contains("Evaluating quality gates",
                "-> Some quality gates have been missed: overall result is UNSTABLE",
                "-> Details for each quality gate:",
                "   - [Overall project - Line Coverage]: ≪Success≫ - (Actual value: 95.39%, Quality gate: 90.00)",
                "   - [Overall project - Branch Coverage]: ≪Unstable≫ - (Actual value: 88.28%, Quality gate: 90.00)");

        assertThatFlowNodeHasWarningAction(build);
    }

    @Test @Issue("JENKINS-72059")
    void shouldUseStageQualityGateInPipeline() {
        WorkflowJob project = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE);

        setPipelineScript(project,
                "recordCoverage("
                        + "tools: [[parser: '" + Parser.JACOCO.name() + "', pattern: '**/*xml']],\n"
                        + "qualityGates: ["
                        + "     [threshold: 90.0, metric: 'LINE', baseline: 'PROJECT', criticality: 'NOTE'], "
                        + "     [threshold: 90.0, metric: 'BRANCH', baseline: 'PROJECT', criticality: 'NOTE']])\n");

        WorkflowRun build = (WorkflowRun)buildSuccessfully(project);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getQualityGateResult()).hasOverallStatus(QualityGateStatus.NOTE);

        assertThat(coverageResult.getLog().getInfoMessages()).contains("Evaluating quality gates",
                "-> Some quality gates have been missed: overall result is UNSTABLE",
                "-> Details for each quality gate:",
                "   - [Overall project - Line Coverage]: ≪Success≫ - (Actual value: 95.39%, Quality gate: 90.00)",
                "   - [Overall project - Branch Coverage]: ≪Unstable≫ - (Actual value: 88.28%, Quality gate: 90.00)");

        assertThatFlowNodeHasWarningAction(build);
    }

    private void assertThatFlowNodeHasWarningAction(final WorkflowRun build) {
        FlowNode flowNode = new DepthFirstScanner().findFirstMatch(build.getExecution(),
                node -> "recordCoverage".equals(Objects.requireNonNull(node).getDisplayFunctionName()));
        assertThat(flowNode).isNotNull();

        WarningAction warningAction = flowNode.getPersistentAction(WarningAction.class);
        assertThat(warningAction).isNotNull();
        assertThat(warningAction.getResult()).isEqualTo(Result.UNSTABLE);
        assertThat(warningAction.getMessage()).isEqualTo(
                "-> Some quality gates have been missed: overall result is UNSTABLE");
    }
}
