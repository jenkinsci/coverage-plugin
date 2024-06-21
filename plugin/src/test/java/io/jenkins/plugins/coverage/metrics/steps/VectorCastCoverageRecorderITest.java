package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.Result;
import hudson.model.Run;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerSuite;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link CoverageRecorder}.
 *
 * @author Ullrich Hafner
 */
class VectorCastCoverageRecorderITest extends IntegrationTestWithJenkinsPerSuite {
    @Test
    void shouldIgnoreErrors() {
        WorkflowJob job = createPipeline();
        copyFileToWorkspace(job, "vectorcast-duplicate-methods-statement-mcdc-fcc.xml", "xml_data/cobertura/coverage_results_dup_test.xml");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "    recordCoverage tools: [[parser: 'VECTORCAST']]\n"
                        + " }\n", true));

        Run<?, ?> failure = buildWithResult(job, Result.FAILURE);

        assertThat(getConsoleLog(failure))
                .contains("java.lang.IllegalArgumentException: There is already a child [METHOD] Get_Record(int)data_type");

        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "    recordCoverage tools: [[parser: 'VECTORCAST']], ignoreParsingErrors: true\n"
                        + " }\n", true));

        Run<?, ?> success = buildWithResult(job, Result.SUCCESS);

        assertThat(getConsoleLog(success))
                .doesNotContain("java.lang.IllegalArgumentException");
    }

    @Test
    void shouldIgnoreEmptyListOfFiles() {
        WorkflowJob job = createPipeline();
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "    recordCoverage tools: [[parser: 'VECTORCAST']]\n"
                        + " }\n", true));

        Run<?, ?> run = buildWithResult(job, Result.SUCCESS);

        assertThat(getConsoleLog(run))
                .contains("Using default pattern 'xml_data/cobertura/coverage_results*.xml' since user defined pattern is not set",
                        "[-ERROR-] No files found for pattern 'xml_data/cobertura/coverage_results*.xml'. Configuration error?")
                .containsPattern("Searching for all files in '.*' that match the pattern 'xml_data/cobertura/coverage_results\\*.xml'")  
                .doesNotContain("Expanding pattern");
    }

    @Test
    void shouldParseFileWithVectorCast() {
        WorkflowJob job = createPipeline();
        copyFileToWorkspace(job, "vectorcast-statement-mcdc-fcc.xml", "xml_data/cobertura/coverage_results_test.xml");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "    recordCoverage tools: [[parser: 'VECTORCAST']]\n"
                        + " }\n", true));

        Run<?, ?> run = buildWithResult(job, Result.SUCCESS);

        assertThat(getConsoleLog(run))
                .contains("Using default pattern 'xml_data/cobertura/coverage_results*.xml' since user defined pattern is not set",
                        "-> found 1 file",
                        "MODULE: 100.00% (1/1)",
                        "PACKAGE: 100.00% (5/5)",
                        "FILE: 75.00% (6/8)",
                        "CLASS: 75.00% (6/8)",
                        "METHOD: 70.00% (21/30)",
                        "LINE: 79.93% (235/294)",
                        "BRANCH: 66.18% (180/272)",
                        "MCDC_PAIR: 40.68% (24/59)",
                        "FUNCTION_CALL: 78.48% (62/79)",
                        "COMPLEXITY: 100",
                        "COMPLEXITY_MAXIMUM: 26",
                        "COMPLEXITY_DENSITY: 100/294",
                        "LOC: 294")
                .containsPattern("Searching for all files in '.*' that match the pattern 'xml_data/cobertura/coverage_results\\*.xml'")
                .containsPattern("Successfully parsed file '.*/xml_data/cobertura/coverage_results_test.xml")
                .doesNotContain("Expanding pattern");
    }
}
