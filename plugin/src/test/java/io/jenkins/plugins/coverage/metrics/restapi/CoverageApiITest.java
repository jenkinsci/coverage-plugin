package io.jenkins.plugins.coverage.metrics.restapi;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Metric;

import java.util.List;
import net.sf.json.JSON;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageITest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.steps.CoverageQualityGate;
import io.jenkins.plugins.coverage.metrics.steps.CoverageRecorder;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.util.QualityGate.QualityGateCriticality;

import static io.jenkins.plugins.coverage.metrics.AbstractCoverageTest.*;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;

/**
 * Tests the class {@link CoverageApi}.
 *
 * @author Ullrich Hafner
 */
class CoverageApiITest extends AbstractCoverageITest {
    @Test
    void shouldProvideRemoteApi() {
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE);

        Run<?, ?> build = buildWithResult(project, Result.SUCCESS);

        var remoteApiResult = callRemoteApi(build);
        assertThatJson(remoteApiResult)
                .node("projectStatistics")
                .isEqualTo("""
                          {
                            "branch": "88.28%",
                            "cyclomatic-complexity": "2558",
                            "file": "99.67%",
                            "instruction": "96.11%",
                            "line": "95.39%",
                            "loc": "5798",
                            "method": "97.29%",
                            "module": "100.00%",
                            "package": "100.00%"}
                        """);
        assertThatJson(remoteApiResult)
                .node("modifiedFilesStatistics").isEqualTo("{}");
        assertThatJson(remoteApiResult)
                .node("modifiedLinesStatistics").isEqualTo("{}");
    }

    @Test
    void shouldShowQualityGatesInRemoteApi() {
        var qualityGate = new CoverageQualityGate(Metric.LINE);
        qualityGate.setThreshold(100);
        qualityGate.setBaseline(Baseline.PROJECT);
        qualityGate.setCriticality(QualityGateCriticality.UNSTABLE);
        var qualityGates = List.of(qualityGate);
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO, r -> r.setQualityGates(qualityGates),
                JACOCO_ANALYSIS_MODEL_FILE);

        Run<?, ?> build = buildWithResult(project, Result.UNSTABLE);

        var remoteApiResult = callRemoteApi(build);
        assertThatJson(remoteApiResult)
                .node("qualityGates.overallResult").isEqualTo("WARNING");
        assertThatJson(remoteApiResult)
                .node("qualityGates.resultItems").isEqualTo("""
                        [{
                          "qualityGate": "Overall project - Line Coverage",
                          "result": "UNSTABLE",
                          "threshold": 100.0,
                          "value": "95.39%"
                        }]
                        """);
    }

    @Test
    void shouldShowDeltaInRemoteApi() {
        var project = createFreestyleJob(Parser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        buildSuccessfully(project);

        // update the parser pattern to pick only the coding style results
        project.getPublishersList().get(CoverageRecorder.class).getTools().get(0).setPattern(JACOCO_CODING_STYLE_FILE);
        var secondBuild = buildSuccessfully(project);

        var remoteApiResult = callRemoteApi(secondBuild);
        assertThatJson(remoteApiResult)
                .node("projectDelta").isEqualTo("""
                        {
                          "branch": "+5.33%",
                          "cyclomatic-complexity": "-2558",
                          "file": "-11.87%",
                          "instruction": "-2.63%",
                          "line": "-4.14%",
                          "loc": "-5798",
                          "method": "-2.06%",
                          "module": "±0%",
                          "package": "±0%"
                        }""");
        assertThatJson(remoteApiResult).node("referenceBuild").asString()
                .matches("<a href=\".*jenkins/job/test0/1/\".*>test0 #1</a>");
    }

    private JSON callRemoteApi(final Run<?, ?> build) {
        return callJsonRemoteApi(build.getUrl() + "coverage/api/json").getJSONObject();
    }
}
