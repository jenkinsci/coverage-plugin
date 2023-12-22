package io.jenkins.plugins.coverage.metrics.steps;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.Fraction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.LinesOfCode;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageITest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.restapi.ModifiedLinesCoverageApiModel;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.prism.SourceCodeRetention;

import static edu.hm.hafner.coverage.Metric.*;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;

/**
 * Tests the integration of the Forensics API Plugin while using its Git implementation.
 *
 * @author Florian Orendi
 */
@Testcontainers(disabledWithoutDocker = true)
class GitForensicsITest extends AbstractCoverageITest {
    /** The JaCoCo coverage report, generated for the commit {@link #COMMIT}. */
    private static final String JACOCO_FILE = "forensics_integration.xml";
    /** The JaCoCo coverage report, generated for the reference commit {@link #COMMIT_REFERENCE}. */
    private static final String JACOCO_REFERENCE_FILE = "forensics_integration_reference.xml";

    private static final String COMMIT = "518eebd";
    private static final String COMMIT_REFERENCE = "fd43cd0";

    private static final String REPOSITORY = "https://github.com/jenkinsci/forensics-api-plugin.git";

    @Container
    private static final AgentContainer AGENT_CONTAINER = new AgentContainer();

    @ParameterizedTest(name = "Source code retention {0} should store {1} files")
    @CsvSource({
            "EVERY_BUILD, 37",
            "MODIFIED, 2"
    })
    @DisplayName("Should compute delta report and store selected source files")
    void shouldComputeDeltaInPipelineOnDockerAgent(final SourceCodeRetention sourceCodeRetention,
            final int expectedNumberOfFilesToBeStored) {
        assumeThat(isWindows()).as("Running on Windows").isFalse();

        Node agent = createDockerAgent(AGENT_CONTAINER);
        String node = "node('" + DOCKER_AGENT_NAME + "')";
        WorkflowJob project = createPipeline();
        copySingleFileToAgentWorkspace(agent, project, JACOCO_REFERENCE_FILE, JACOCO_REFERENCE_FILE);
        copySingleFileToAgentWorkspace(agent, project, JACOCO_FILE, JACOCO_FILE);

        project.setDefinition(createPipelineForCommit(node, COMMIT_REFERENCE, JACOCO_REFERENCE_FILE));
        Run<?, ?> referenceBuild = buildSuccessfully(project);
        verifyGitRepositoryForCommit(referenceBuild, COMMIT_REFERENCE);

        project.setDefinition(createPipelineForCommit(node, COMMIT, JACOCO_FILE, sourceCodeRetention));
        Run<?, ?> build = buildSuccessfully(project);
        verifyGitRepositoryForCommit(build, COMMIT);

        verifyGitIntegration(build, referenceBuild);

        assertThat(getConsoleLog(build)).contains(
                "[Coverage] -> 18 files contain changes",
                "[Coverage] Painting " + expectedNumberOfFilesToBeStored + " source files on agent");

        verifyModifiedLinesCoverageApi(build);
    }

    @ParameterizedTest(name = "Baseline {0}, Threshold {1}, Actual Value {2}")
    @CsvSource({
            "PROJECT, 60, 56.46",
            "PROJECT_DELTA, 1, 0.72",
            "MODIFIED_FILES, 60, 54.55",
            "MODIFIED_FILES_DELTA, 5, +4.55",
            "MODIFIED_LINES, 60, 50.00",
            "MODIFIED_LINES_DELTA, -1, -4.55"
    })
    @DisplayName("Should compute quality gates")
    void shouldVerifyQualityGate(final Baseline baseline, final double threshold, final double value) {
        assumeThat(isWindows()).as("Running on Windows").isFalse();

        Node agent = createDockerAgent(AGENT_CONTAINER);
        String node = "node('" + DOCKER_AGENT_NAME + "')";
        WorkflowJob project = createPipeline();
        copySingleFileToAgentWorkspace(agent, project, JACOCO_REFERENCE_FILE, JACOCO_REFERENCE_FILE);
        copySingleFileToAgentWorkspace(agent, project, JACOCO_FILE, JACOCO_FILE);

        project.setDefinition(createPipelineForCommit(node, COMMIT_REFERENCE, JACOCO_REFERENCE_FILE));
        Run<?, ?> referenceBuild = buildSuccessfully(project);
        verifyGitRepositoryForCommit(referenceBuild, COMMIT_REFERENCE);

        String qualityGate = String.format(", qualityGates: ["
                + "     [threshold: %f, metric: 'LINE', baseline: '%s', criticality: 'UNSTABLE']]", threshold, baseline.name());
        project.setDefinition(createPipelineForCommit(node, COMMIT, JACOCO_FILE, SourceCodeRetention.EVERY_BUILD, qualityGate));
        Run<?, ?> build = buildWithResult(project, Result.UNSTABLE);

        verifyCoverage(build.getAction(CoverageBuildAction.class), referenceBuild.getAction(CoverageBuildAction.class));

        assertThat(getConsoleLog(build))
                .contains("[Coverage] -> Some quality gates have been missed: overall result is UNSTABLE");
        if (baseline == Baseline.PROJECT_DELTA
                || baseline == Baseline.MODIFIED_FILES_DELTA
                || baseline == Baseline.MODIFIED_LINES_DELTA) {
            assertThat(getConsoleLog(build)).contains(String.format(
                    "≪Unstable≫ - (Actual value: %+.2f%%, Quality gate: %.2f)", value, threshold));
        }
        else {
            assertThat(getConsoleLog(build)).contains(String.format(
                    "≪Unstable≫ - (Actual value: %.2f%%, Quality gate: %.2f)", value, threshold));
        }
    }

    @Test
    void shouldComputeDeltaInFreestyleJobOnDockerAgent() throws IOException {
        assumeThat(isWindows()).as("Running on Windows").isFalse();

        Node agent = createDockerAgent(AGENT_CONTAINER);
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO);
        project.setAssignedNode(agent);

        configureGit(project, COMMIT_REFERENCE);
        addCoverageRecorder(project, Parser.JACOCO, JACOCO_REFERENCE_FILE);

        copySingleFileToAgentWorkspace(agent, project, JACOCO_FILE, JACOCO_FILE);
        copySingleFileToAgentWorkspace(agent, project, JACOCO_REFERENCE_FILE, JACOCO_REFERENCE_FILE);

        Run<?, ?> referenceBuild = buildSuccessfully(project);

        configureGit(project, COMMIT);
        addCoverageRecorder(project, Parser.JACOCO, JACOCO_FILE);

        Run<?, ?> build = buildSuccessfully(project);

        verifyGitIntegration(build, referenceBuild);

        verifyModifiedLinesCoverageApi(build);
    }

    /**
     * Verifies the Git repository for the commit with the passed ID.
     *
     * @param build
     *         The current build
     * @param commit
     *         The commit ID
     */
    private void verifyGitRepositoryForCommit(final Run<?, ?> build, final String commit) {
        String consoleLog = getConsoleLog(build);
        assertThat(consoleLog)
                .contains("Recording commits of 'git " + REPOSITORY)
                .contains("Checking out Revision " + commit);
    }

    /**
     * Verifies the Git integration.
     *
     * @param build
     *         The current build
     * @param referenceBuild
     *         The reference build
     */
    private void verifyGitIntegration(final Run<?, ?> build, final Run<?, ?> referenceBuild) {
        CoverageBuildAction action = build.getAction(CoverageBuildAction.class);
        assertThat(action).isNotNull();
        assertThat(action.getReferenceBuild())
                .isPresent().hasValueSatisfying(reference ->
                        assertThat(reference.getExternalizableId()).isEqualTo(
                                referenceBuild.getExternalizableId()));
        verifyCodeDelta(action);
        verifyCoverage(action, referenceBuild.getAction(CoverageBuildAction.class));
    }

    private void verifyCoverage(final CoverageBuildAction action, final CoverageBuildAction reference) {
        verifyOverallCoverage(action);
        verifyModifiedFilesCoverage(action, reference);
        verifyModifiedLinesCoverage(action);
        verifyIndirectCoverageChanges(action);
    }

    private void verifyOverallCoverage(final CoverageBuildAction action) {
        assertThat(action.getAllValues(Baseline.PROJECT)).contains(
                Coverage.valueOf(LINE, "529/937"),
                Coverage.valueOf(BRANCH, "136/230"),
                new LinesOfCode(937));
        assertThat(action.getAllDeltas(Baseline.PROJECT_DELTA)).contains(
                entry(LINE, Fraction.getFraction("6424/897646")),
                entry(BRANCH, Fraction.getFraction("0/1")),
                entry(LOC, Fraction.getFraction("-21/1")));
    }

    private void verifyModifiedFilesCoverage(final CoverageBuildAction action, final CoverageBuildAction reference) {
        assertThat(action.getAllValues(Baseline.MODIFIED_FILES)).contains(
                Coverage.valueOf(LINE, "12/22"),
                Coverage.valueOf(BRANCH, "1/2"),
                new LinesOfCode(22));
        var affectedFiles = action.getResult().filterByModifiedFiles().getFiles();
        assertThat(reference.getResult().filterByFileNames(affectedFiles).aggregateValues()).contains(
                Coverage.valueOf(LINE, "17/34"),
                Coverage.valueOf(BRANCH, "1/2"),
                new LinesOfCode(34));
        assertThat(action.getAllDeltas(Baseline.MODIFIED_FILES_DELTA)).contains(
                entry(LINE, Fraction.getFraction("17/374")),
                entry(BRANCH, Fraction.getFraction("0/1")),
                entry(LOC, Fraction.getFraction("-12/1")));
    }

    private void verifyModifiedLinesCoverage(final CoverageBuildAction action) {
        assertThat(action.getAllValues(Baseline.MODIFIED_LINES)).contains(
                Coverage.valueOf(LINE, "1/2"));
        assertThat(action.getAllDeltas(Baseline.MODIFIED_LINES_DELTA)).contains(
                entry(LINE, Fraction.getFraction("-1/22")));
    }

    private void verifyIndirectCoverageChanges(final CoverageBuildAction action) {
        assertThat(action.getAllValues(Baseline.INDIRECT))
                .filteredOn(coverage -> coverage.getMetric().equals(LINE))
                .first()
                .isInstanceOfSatisfying(Coverage.class, coverage -> {
                    assertThat(coverage.getCovered()).isEqualTo(4);
                    assertThat(coverage.getMissed()).isEqualTo(0);
                });
        assertThat(action.getAllValues(Baseline.INDIRECT))
                .filteredOn(coverage -> coverage.getMetric().equals(BRANCH))
                .isEmpty();
    }

    private void verifyCodeDelta(final CoverageBuildAction action) {
        edu.hm.hafner.coverage.Node root = action.getResult();
        assertThat(root).isNotNull();

        List<FileNode> modifiedFiles = root.getAllFileNodes().stream()
                .filter(FileNode::hasModifiedLines)
                .collect(Collectors.toList());
        assertThat(modifiedFiles).hasSize(4);
        assertThat(modifiedFiles).extracting(FileNode::getName)
                .containsExactlyInAnyOrder("MinerFactory.java", "RepositoryMinerStep.java",
                        "SimpleReferenceRecorder.java", "CommitDecoratorFactory.java");
        assertThat(modifiedFiles).flatExtracting(FileNode::getModifiedLines)
                .containsExactlyInAnyOrder(15, 17, 63, 68, 80, 90, 130);
    }

    /**
     * Verifies that the {@link ModifiedLinesCoverageApiModel#getApi() modified lines coverage api} provides the
     * expected coverage data for the modified files and code lines.
     *
     * @param build
     *         The build for that the coverage API should be called
     */
    private void verifyModifiedLinesCoverageApi(final Run<?, ?> build) {
        var json = callJsonRemoteApi(build.getUrl() + "coverage/modified/api/json").getJSONObject();
        assertThatJson(json).node("files").isEqualTo("["
                + "{"
                + "\"fullyQualifiedFileName\":\"io/jenkins/plugins/forensics/util/CommitDecoratorFactory.java\","
                + "\"modifiedLinesBlocks\":["
                + "{"
                + "\"endLine\":68,"
                + "\"startLine\":68,"
                + "\"type\":\"MISSED\"}"
                + "]},"
                + "{"
                + "\"fullyQualifiedFileName\":\"io/jenkins/plugins/forensics/miner/MinerFactory.java\","
                + "\"modifiedLinesBlocks\":["
                + "{"
                + "\"endLine\":80,"
                + "\"startLine\":80,"
                + "\"type\":\"COVERED\""
                + "}]"
                + "}]");
    }

    /**
     * Creates a {@link FlowDefinition} for a Jenkins pipeline which processes a JaCoCo coverage report.
     *
     * @param node
     *         The node
     * @param commit
     *         The processed commit
     * @param fileName
     *         The content of the processed JaCoCo report
     *
     * @return the created definition
     */
    private FlowDefinition createPipelineForCommit(final String node, final String commit, final String fileName) {
        return createPipelineForCommit(node, commit, fileName, SourceCodeRetention.EVERY_BUILD);
    }

    /**
     * Creates a {@link FlowDefinition} for a Jenkins pipeline which processes a JaCoCo coverage report.
     *
     * @param node
     *         The node
     * @param commit
     *         The processed commit
     * @param fileName
     *         The content of the processed JaCoCo report
     * @param sourceCodeRetentionStrategy
     *         the source code retention strategy
     *
     * @return the created definition
     */
    private FlowDefinition createPipelineForCommit(final String node, final String commit, final String fileName,
            final SourceCodeRetention sourceCodeRetentionStrategy) {
        return createPipelineForCommit(node, commit, fileName, sourceCodeRetentionStrategy, StringUtils.EMPTY);
    }

    private FlowDefinition createPipelineForCommit(final String node, final String commit, final String fileName,
            final SourceCodeRetention sourceCodeRetentionStrategy, final String qualityGate) {
        return new CpsFlowDefinition(node + " {"
                + "    checkout([$class: 'GitSCM', "
                + "         branches: [[name: '" + commit + "' ]],\n"
                + "         userRemoteConfigs: [[url: '" + REPOSITORY + "']],\n"
                + "         extensions: [[$class: 'RelativeTargetDirectory', \n"
                + "             relativeTargetDir: 'checkout']]])\n"
                + "    recordCoverage tools: [[parser: 'JACOCO', pattern: '" + fileName + "']], "
                + "         sourceCodeRetention: '" + sourceCodeRetentionStrategy.name() + "'"
                + qualityGate
                + "\n"
                + "}", true);
    }

    private void configureGit(final FreeStyleProject project, final String commit) throws IOException {
        GitSCM scm = new GitSCM(GitSCM.createRepoList(REPOSITORY, null),
                Collections.singletonList(new BranchSpec(commit)), null, null,
                Collections.singletonList(new RelativeTargetDirectory("code-coverage-api")));
        project.setScm(scm);
    }
}
