package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.CyclomaticComplexity;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.LinesOfCode;
import edu.hm.hafner.coverage.Node;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.FreeStyleProject;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageITest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;

import static edu.hm.hafner.coverage.Metric.*;
import static io.jenkins.plugins.coverage.metrics.AbstractCoverageTest.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for delta computation of reference builds.
 */
class DeltaComputationITest extends AbstractCoverageITest {
    private static final String REFERENCE_BUILD = "discoverReferenceBuild()\n";

    @Test
    void shouldComputeDeltaInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        Run<?, ?> firstBuild = buildSuccessfully(project);
        verifyFirstBuild(firstBuild);

        // update the parser pattern to pick only the coding style results
        project.getPublishersList().get(CoverageRecorder.class).getTools().get(0).setPattern(JACOCO_CODING_STYLE_FILE);

        Run<?, ?> secondBuild = buildSuccessfully(project);
        var action = secondBuild.getAction(CoverageBuildAction.class);
        verifyJaCoCoProjectValues(action);
        verifyDeltaComputation(firstBuild, secondBuild);
    }

    @Test
    void shouldComputeDeltaInPipeline() throws Exception {
        WorkflowJob job = createPipeline(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        Run<?, ?> firstBuild = buildSuccessfully(job);
        verifyFirstBuild(firstBuild);

        setPipelineScript(job, REFERENCE_BUILD
                + "recordCoverage tools: [[parser: 'JACOCO', pattern: '" + JACOCO_CODING_STYLE_FILE + "']]");

        Run<?, ?> secondBuild = buildSuccessfully(job);
        var action = secondBuild.getAction(CoverageBuildAction.class);
        verifyJaCoCoProjectValues(action);
        verifyDeltaComputation(firstBuild, secondBuild);
    }

    @Test
    void shouldSelectResultByIdInReferenceBuild() throws Exception {
        WorkflowJob job = createPipelineWithWorkspaceFiles(
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE,
                "mutations.xml", "mutations-codingstyle.xml");

        // Create a build with two different actions
        setPipelineScript(job,
                REFERENCE_BUILD
                        + "recordCoverage tools: [[parser: '" + Parser.PIT.name()
                        + "', pattern: '**/mutations*.xml']], id: 'pit'\n"
                        + "recordCoverage tools: [[parser: '" + Parser.JACOCO.name()
                        + "', pattern: '**/jacoco*xml']]\n");

        Run<?, ?> firstBuild = buildSuccessfully(job);

        setPipelineScript(job,
                REFERENCE_BUILD
                        + "recordCoverage tools: [[parser: '" + Parser.PIT.name()
                        + "', pattern: '**/mutations.xml']], id: 'pit'\n"
                        + "recordCoverage tools: [[parser: 'JACOCO', pattern: '" + JACOCO_CODING_STYLE_FILE + "']]");

        Run<?, ?> secondBuild = buildSuccessfully(job);
        var actions = secondBuild.getActions(CoverageBuildAction.class);

        var jacoco = actions.get(1);
        assertThat(jacoco.getReferenceBuild())
                .isPresent()
                .satisfies(reference -> assertThat(reference.get()).isEqualTo(firstBuild));

        verifyJaCoCoProjectValues(jacoco);
        verifyJaCoCoDelta(jacoco);

        var pit = actions.get(0);

        assertThat(pit.getReferenceBuild())
                .isPresent()
                .satisfies(reference -> assertThat(reference.get()).isEqualTo(firstBuild));

        verifyPitProjectValues(pit);
        verifyPitDelta(pit);
    }

    private void verifyPitDelta(final CoverageBuildAction pit) {
        assertThat(pit.formatDelta(Baseline.PROJECT, LINE)).isEqualTo("-2.16%");
        assertThat(pit.formatDelta(Baseline.PROJECT, MUTATION)).isEqualTo("+3.37%");
        assertThat(pit.formatDelta(Baseline.PROJECT, LOC)).isEqualTo(String.valueOf(-214));
    }

    private void verifyPitProjectValues(final CoverageBuildAction pit) {
        CoverageBuilder builder = new CoverageBuilder();
        assertThat(pit.getAllValues(Baseline.PROJECT)).contains(
                builder.withMetric(LINE)
                        .withCovered(198)
                        .withMissed(211 - 198)
                        .build(),
                builder.withMetric(MUTATION)
                        .withCovered(222)
                        .withMissed(246 - 222)
                        .build(),
                new LinesOfCode(211));
    }

    private static void verifyFirstBuild(final Run<?, ?> firstBuild) {
        var action = firstBuild.getAction(CoverageBuildAction.class);

        var builder = new CoverageBuilder();
        assertThat(action.getAllValues(Baseline.PROJECT)).contains(
                builder.withMetric(LINE)
                        .withCovered(JACOCO_ANALYSIS_MODEL_COVERED + JACOCO_CODING_STYLE_COVERED)
                        .withMissed(JACOCO_ANALYSIS_MODEL_MISSED + JACOCO_CODING_STYLE_MISSED)
                        .build(),
                builder.withMetric(BRANCH)
                        .withCovered(1544 + 109)
                        .withMissed(1865 - (1544 + 109))
                        .build(),
                new LinesOfCode(JACOCO_ANALYSIS_MODEL_TOTAL + JACOCO_CODING_STYLE_TOTAL),
                new CyclomaticComplexity(2718));
    }

    private void verifyJaCoCoProjectValues(final CoverageBuildAction action) {
        var builder = new CoverageBuilder();
        assertThat(action.getAllValues(Baseline.PROJECT)).contains(
                builder.withMetric(LINE)
                        .withCovered(JACOCO_CODING_STYLE_COVERED)
                        .withMissed(JACOCO_CODING_STYLE_MISSED)
                        .build(),
                builder.withMetric(BRANCH)
                        .withCovered(109)
                        .withMissed(7)
                        .build(),
                new LinesOfCode(JACOCO_CODING_STYLE_TOTAL),
                new CyclomaticComplexity(160));
    }

    /**
     * Verifies the coverageComputation of the first and second build of the job.
     *
     * @param firstBuild
     *         of the project which is used as a reference
     * @param secondBuild
     *         of the project
     */
    private void verifyDeltaComputation(final Run<?, ?> firstBuild, final Run<?, ?> secondBuild) {
        assertThat(secondBuild.getAction(CoverageBuildAction.class)).isNotNull();

        CoverageBuildAction action = secondBuild.getAction(CoverageBuildAction.class);

        assertThat(action).isNotNull();
        assertThat(action.getReferenceBuild())
                .isPresent()
                .satisfies(reference -> assertThat(reference.get()).isEqualTo(firstBuild));

        verifyJaCoCoDelta(action);
    }

    private void verifyJaCoCoDelta(final CoverageBuildAction action) {
        assertThat(action.formatDelta(Baseline.PROJECT, LINE)).isEqualTo("-4.14%");
        assertThat(action.formatDelta(Baseline.PROJECT, BRANCH)).isEqualTo("+5.33%");
        assertThat(action.formatDelta(Baseline.PROJECT, LOC)).isEqualTo(String.valueOf(-JACOCO_ANALYSIS_MODEL_TOTAL));
        assertThat(action.formatDelta(Baseline.PROJECT, COMPLEXITY)).isEqualTo(String.valueOf(160 - 2718));

        verifyModifiedLinesCoverage(action);
    }

    /**
     * Verifies the calculated modified lines coverage including the modified lines coverage delta and the code delta.
     * This makes sure these metrics are set properly even if there are no code changes.
     *
     * @param action
     *         The created Jenkins action
     */
    private void verifyModifiedLinesCoverage(final CoverageBuildAction action) {
        Node root = action.getResult();
        assertThat(root).isNotNull();
        assertThat(root.getAllFileNodes()).flatExtracting(FileNode::getModifiedLines).isEmpty();
    }
}
