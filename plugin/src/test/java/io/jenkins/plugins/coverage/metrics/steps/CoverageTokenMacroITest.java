package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageITest;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;

import static io.jenkins.plugins.coverage.metrics.AbstractCoverageTest.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests with token macro plugin.
 */
class CoverageTokenMacroITest extends AbstractCoverageITest {
    @Test
    void shouldUseQualityGateInPipeline() throws Exception {
        WorkflowJob project = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE);

        setPipelineScript(project,
                "recordCoverage("
                        + "tools: [[parser: '" + Parser.JACOCO.name() + "', pattern: '**/*xml']])\n"
                        + "def lineCoverage = tm('${COVERAGE}')\n"
                        + "def branchCoverage = tm('${COVERAGE, metric=\"BRANCH\"}')\n"
                        + "def delta = tm('${COVERAGE, baseline=\"MODIFIED_LINES\"}')\n"
                        + "def na = tm('${COVERAGE, id=\"unknown\"}')\n"
                        + "echo '[lineCoverage=' + lineCoverage + ']' \n"
                        + "echo '[branchCoverage=' + branchCoverage + ']' \n"
                        + "echo '[delta=' + delta + ']' \n"
                        + "echo '[na=' + na + ']' \n"
        );

        Run<?, ?> build = buildSuccessfully(project);

        assertThat(getConsoleLog(build)).contains("[lineCoverage=95.39%]");
        assertThat(getConsoleLog(build)).contains("[branchCoverage=88.28%]");
        assertThat(getConsoleLog(build)).contains("[delta=n/a]");
        assertThat(getConsoleLog(build)).contains("[na=n/a]");
    }
}
