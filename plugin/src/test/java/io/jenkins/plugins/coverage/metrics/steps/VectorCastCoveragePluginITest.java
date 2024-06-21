package io.jenkins.plugins.coverage.metrics.steps;

import java.util.List;

import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junitpioneer.jupiter.Issue;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.TestCount;
import edu.hm.hafner.coverage.Value;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageITest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTableModel.CoverageRow;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.datatables.DetailedCell;
import io.jenkins.plugins.datatables.TableModel;

import static io.jenkins.plugins.coverage.metrics.AbstractCoverageTest.*;
import static io.jenkins.plugins.coverage.metrics.AbstractCoverageTest.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for different JaCoCo and VectorCast files.
 */
class VectorCastCoveragePluginITest extends AbstractCoverageITest {
    private static final String VECTORCAST_HIGHER_COVERAGE_FILE = "vectorcast-statement-mcdc-fcc.xml";
    private static final int VECTORCAST_COVERED_LINES = 235;
    private static final int VECTORCAST_MISSED_LINES = 59;
    private static final int VECTORCAST_COVERED_BRANCH = 180;
    private static final int VECTORCAST_MISSED_BRANCH = 92;
    private static final int VECTORCAST_COVERED_MCDC_PAIR = 24;
    private static final int VECTORCAST_MISSED_MCDC_PAIR = 35;
    private static final int VECTORCAST_COVERED_FUNCTION_CALL = 62;
    private static final int VECTORCAST_MISSED_FUNCTION_CALL = 17;
    private static final int VECTORCAST_COVERED_METHOD = 21;
    private static final int VECTORCAST_MISSED_METHOD= 9;
    private static final String NO_FILES_FOUND_ERROR_MESSAGE = "[-ERROR-] No files found for pattern '**/*xml'. Configuration error?";

    @Test
    void shouldFailWithoutParserInFreestyleJob() {
        FreeStyleProject project = createFreeStyleProject();

        project.getPublishersList().add(new CoverageRecorder());

        verifyNoParserError(project);
    }

    @Test
    void shouldFailWithoutParserInPipeline() {
        WorkflowJob job = createPipeline();

        setPipelineScript(job, "recordCoverage()");

        verifyNoParserError(job);
    }

    private void verifyNoParserError(final ParameterizedJob<?, ?> project) {
        Run<?, ?> run = buildWithResult(project, Result.FAILURE);

        assertThat(getConsoleLog(run)).contains("[-ERROR-] No tools defined that will record the coverage files");
    }

    @EnumSource
    @ParameterizedTest(name = "{index} => Freestyle job with parser {0}")
    @DisplayName("Report error but do not fail build in freestyle job when no input files are found")
    void shouldReportErrorWhenNoFilesHaveBeenFoundInFreestyleJob(final Parser parser) {
        FreeStyleProject project = createFreestyleJob(parser);

        verifyLogMessageThatNoFilesFound(project);
    }

    @EnumSource
    @ParameterizedTest(name = "{index} => Pipeline with parser {0}")
    @DisplayName("Report error but do not fail build in pipeline when no input files are found")
    void shouldReportErrorWhenNoFilesHaveBeenFoundInPipeline(final Parser parser) {
        WorkflowJob job = createPipeline(parser);

        verifyLogMessageThatNoFilesFound(job);
    }

    private void verifyLogMessageThatNoFilesFound(final ParameterizedJob<?, ?> project) {
        Run<?, ?> run = buildWithResult(project, Result.SUCCESS);

        assertThat(getConsoleLog(run)).contains(NO_FILES_FOUND_ERROR_MESSAGE,
                "Ignore errors and continue processing");
    }

    @EnumSource
    @ParameterizedTest(name = "{index} => Freestyle job with parser {0}")
    @DisplayName("Report error and fail build in freestyle job when no input files are found")
    void shouldFailBuildWhenNoFilesHaveBeenFoundInFreestyleJob(final Parser parser) {
        FreeStyleProject project = createFreestyleJob(parser, r -> r.setFailOnError(true));

        verifyFailureWhenNoFilesFound(project);
    }

    @EnumSource
    @ParameterizedTest(name = "{index} => Pipeline with parser {0}")
    @DisplayName("Report error and fail build in pipeline when no input files are found")
    void shouldFailBuildWhenNoFilesHaveBeenFoundInPipeline(final Parser parser) {
        WorkflowJob job = createPipeline();

        setPipelineScript(job,
                "recordCoverage tools: [[parser: '" + parser.name() + "', pattern: '**/*xml']], "
                        + "failOnError: 'true'");

        verifyFailureWhenNoFilesFound(job);
    }

    private void verifyFailureWhenNoFilesFound(final ParameterizedJob<?, ?> project) {
        Run<?, ?> run = buildWithResult(project, Result.FAILURE);

        assertThat(getConsoleLog(run)).contains(NO_FILES_FOUND_ERROR_MESSAGE,
                "Failing build due to some errors during recording of the coverage");
    }

    @Test
    void shouldRecordOneJacocoResultInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE);

        verifyOneJacocoResult(project);
    }

    @Test
    void shouldRecordOneJacocoResultInPipeline() {
        WorkflowJob job = createPipeline(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE);

        verifyOneJacocoResult(job);
    }

    @Test
    void shouldRecordOneJacocoResultInDeclarativePipeline() {
        WorkflowJob job = createDeclarativePipeline(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE);

        verifyOneJacocoResult(job);
    }

    private void verifyOneJacocoResult(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        verifyJaCoCoAction(build.getAction(CoverageBuildAction.class));
    }

    private void verifyJaCoCoAction(final CoverageBuildAction coverageResult) {
        assertThat(coverageResult.getAllValues(Baseline.PROJECT)).extracting(Value::getMetric)
                .containsExactly(Metric.MODULE,
                        Metric.PACKAGE,
                        Metric.FILE,
                        Metric.CLASS,
                        Metric.METHOD,
                        Metric.LINE,
                        Metric.BRANCH,
                        Metric.INSTRUCTION,
                        Metric.COMPLEXITY,
                        Metric.COMPLEXITY_MAXIMUM,
                        Metric.COMPLEXITY_DENSITY,
                        Metric.LOC);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .contains(createLineCoverageBuilder()
                        .withCovered(JACOCO_ANALYSIS_MODEL_COVERED)
                        .withMissed(JACOCO_ANALYSIS_MODEL_TOTAL - JACOCO_ANALYSIS_MODEL_COVERED)
                        .build());
        var tableModel = coverageResult.getTarget().getTableModel(CoverageViewModel.ABSOLUTE_COVERAGE_TABLE_ID);
        assertThat(tableModel)
                .extracting(TableModel::getColumns).asList()
                .extracting("headerLabel")
                .containsExactly("Hash",
                        "Modified",
                        "File",
                        "Package",
                        "Line",
                        "Line Δ",
                        "Branch",
                        "Branch Δ",
                        "LOC",
                        "Complexity",
                        "Max. Complexity",
                        "Complexity / LOC");
        assertThat(tableModel.getRows())
                .hasSize(307)
                .first()
                .isInstanceOfSatisfying(CoverageRow.class, this::assertContentOfFirstJacocoRow);
    }

    private void assertContentOfFirstJacocoRow(final CoverageRow r) {
        assertThatCell(r.getFileName())
                .contains("title=\"edu/hm/hafner/analysis/parser/dry/simian/SimianParser.java\"");
        assertThat(r.getPackageName()).isEqualTo("edu.hm.hafner.analysis.parser.dry.simian");
        assertThat(r.getTests()).isEqualTo(0);
        assertThat(r.getComplexity()).isEqualTo(6);
        assertThat(r.getLoc()).isEqualTo(28);
        assertThat(r.getMaxComplexity()).isEqualTo(3);
        assertThatCell(r.getLineCoverage()).contains("title=\"Covered: 26 - Missed: 2\">92.86%");
        assertThatCell(r.getLineCoverageDelta()).contains("n/a");
        assertThatCell(r.getBranchCoverage()).contains("title=\"Covered: 4 - Missed: 0\">100.00%");
        assertThatCell(r.getBranchCoverageDelta()).contains("n/a");
        assertThatCell(r.getMutationCoverage()).contains("n/a");
        assertThatCell(r.getMutationCoverageDelta()).contains("n/a");
        assertThatCell(r.getTestStrength()).contains("n/a");
        assertThatCell(r.getTestStrengthDelta()).contains("n/a");
        assertThatCell(r.getDensity()).contains("0.21");
    }

    private void assertContentOfFirstVectorCastRow(final CoverageRow r) {
        assertThatCell(r.getFileName())
                .contains("title=\"CurrentRelease/database/src/database.c\"");
        assertThat(r.getPackageName()).isEqualTo("CurrentRelease.database.src");
        assertThat(r.getTests()).isEqualTo(0);
        assertThat(r.getComplexity()).isEqualTo(5);
        assertThat(r.getLoc()).isEqualTo(17);
        assertThat(r.getMaxComplexity()).isEqualTo(2);
        assertThatCell(r.getLineCoverage())
                .contains("title=\"Covered: 17 - Missed: 0\">100.00%");
        assertThatCell(r.getLineCoverageDelta()).contains("n/a");
        assertThatCell(r.getBranchCoverage())
                .contains("title=\"Covered: 9 - Missed: 2\">81.82%");        
        assertThatCell(r.getBranchCoverageDelta()).contains("n/a");
        assertThatCell(r.getMethodCoverage())
                .contains("title=\"Covered: 3 - Missed: 0\">100.00%");
        assertThatCell(r.getMcdcPairCoverage())
                .contains("title=\"Covered: 1 - Missed: 1\">50.00%");
        assertThatCell(r.getFunctionCallCoverage())
                .contains("title=\"Covered: 4 - Missed: 0\">100.00%");
        assertThatCell(r.getDensity()).contains("0.29");
    }

    @Test
    void shouldRecordOneVectorCastResultInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.VECTORCAST, VECTORCAST_HIGHER_COVERAGE_FILE);

        verifyOneVectorCastResult(project);
    }

    @Test
    void shouldRecordOneVectorCastResultInPipeline() {
        WorkflowJob job = createPipeline(Parser.VECTORCAST, VECTORCAST_HIGHER_COVERAGE_FILE);

        verifyOneVectorCastResult(job);
    }

    @Test
    void shouldRecordOneVectorCastResultInDeclarativePipeline() {
        WorkflowJob job = createDeclarativePipeline(Parser.VECTORCAST, VECTORCAST_HIGHER_COVERAGE_FILE);

        verifyOneVectorCastResult(job);
    }

    private void verifyOneVectorCastResult(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        verifyVectorCastAction(build.getAction(CoverageBuildAction.class));
    }

    private void verifyVectorCastAction(final CoverageBuildAction coverageResult) {
        assertThat(coverageResult.getAllValues(Baseline.PROJECT)).extracting(Value::getMetric)
                .containsExactly(Metric.MODULE,
                        Metric.PACKAGE,
                        Metric.FILE,
                        Metric.CLASS,
                        Metric.METHOD,
                        Metric.LINE,
                        Metric.BRANCH,
                        Metric.MCDC_PAIR,
                        Metric.FUNCTION_CALL,
                        Metric.COMPLEXITY,
                        Metric.COMPLEXITY_MAXIMUM,
                        Metric.COMPLEXITY_DENSITY,
                        Metric.LOC);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT)).contains(
                new CoverageBuilder()
                        .withMetric(Metric.LINE)
                        .withCovered(VECTORCAST_COVERED_LINES)
                        .withMissed(VECTORCAST_MISSED_LINES)
                        .build());
        assertThat(coverageResult.getAllValues(Baseline.PROJECT)).contains(
                new CoverageBuilder()
                        .withMetric(Metric.BRANCH)
                        .withCovered(VECTORCAST_COVERED_BRANCH)
                        .withMissed(VECTORCAST_MISSED_BRANCH)
                        .build());
        assertThat(coverageResult.getAllValues(Baseline.PROJECT)).contains(
                new CoverageBuilder()
                        .withMetric(Metric.FUNCTION_CALL)
                        .withCovered(VECTORCAST_COVERED_FUNCTION_CALL)
                        .withMissed(VECTORCAST_MISSED_FUNCTION_CALL)
                        .build());
        assertThat(coverageResult.getAllValues(Baseline.PROJECT)).contains(
                new CoverageBuilder()
                        .withMetric(Metric.MCDC_PAIR)
                        .withCovered(VECTORCAST_COVERED_MCDC_PAIR)
                        .withMissed(VECTORCAST_MISSED_MCDC_PAIR)
                        .build());
        assertThat(coverageResult.getAllValues(Baseline.PROJECT)).contains(
                new CoverageBuilder()
                        .withMetric(Metric.METHOD)
                        .withCovered(VECTORCAST_COVERED_METHOD)
                        .withMissed(VECTORCAST_MISSED_METHOD)
                        .build());

        var tableModel = coverageResult.getTarget().getTableModel(CoverageViewModel.ABSOLUTE_COVERAGE_TABLE_ID);
        assertThat(tableModel)
                .extracting(TableModel::getColumns).asList()
                .extracting("headerLabel")
                .containsExactly("Hash",
                        "Modified",
                        "File",
                        "Package",
                        "Line",
                        "Line Δ",
                        "Branch",
                        "Branch Δ",
                        "MC/DC Pairs",
                        "Function Call",
                        "LOC",
                        "Complexity",
                        "Max. Complexity",
                        "Complexity / LOC");
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .contains(createLineCoverageBuilder()
                        .withCovered(VECTORCAST_COVERED_LINES)
                        .withMissed(VECTORCAST_MISSED_LINES)
                        .build());
        assertThat(tableModel.getRows())
                .hasSize(8)
                .first()
                .isInstanceOfSatisfying(CoverageRow.class, this::assertContentOfFirstVectorCastRow);
    }

    @Test
    void shouldRecordVectorCastAndJacocoResultsInFreestyleJob() {
        FreeStyleProject project = createFreeStyleProjectWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE,
                VECTORCAST_HIGHER_COVERAGE_FILE);

        CoverageRecorder recorder = new CoverageRecorder();

        var vectorcast = new CoverageTool();
        vectorcast.setParser(Parser.VECTORCAST);
        vectorcast.setPattern(VECTORCAST_HIGHER_COVERAGE_FILE);

        var jacoco = new CoverageTool();
        jacoco.setParser(Parser.JACOCO);
        jacoco.setPattern(JACOCO_ANALYSIS_MODEL_FILE);

        recorder.setTools(List.of(jacoco, vectorcast));
        project.getPublishersList().add(recorder);

        verifyForOneVectorCastAndOneJacoco(project);
    }

    @Test
    void shouldRecordVectorCastAndJacocoResultsInPipeline() {
        WorkflowJob job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, VECTORCAST_HIGHER_COVERAGE_FILE);

        setPipelineScript(job,
                "recordCoverage tools: ["
                        + "[parser: 'VECTORCAST', pattern: '" + VECTORCAST_HIGHER_COVERAGE_FILE + "'],"
                        + "[parser: 'JACOCO', pattern: '" + JACOCO_ANALYSIS_MODEL_FILE + "']"
                        + "]");

        verifyForOneVectorCastAndOneJacoco(job);
    }

    @Test
    void shouldRecordVectorCastAndJacocoResultsInDeclarativePipeline() {
        WorkflowJob job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, VECTORCAST_HIGHER_COVERAGE_FILE);

        job.setDefinition(new CpsFlowDefinition("pipeline {\n"
                + "    agent any\n"
                + "    stages {\n"
                + "        stage('Test') {\n"
                + "            steps {\n"
                + "                 recordCoverage(tools: [\n"
                + "                     [parser: 'VECTORCAST', pattern: '" + VECTORCAST_HIGHER_COVERAGE_FILE + "'],\n"
                + "                     [parser: 'JACOCO', pattern: '" + JACOCO_ANALYSIS_MODEL_FILE + "']\n"
                + "                 ])\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}", true));

        verifyForOneVectorCastAndOneJacoco(job);
    }

    private void verifyForOneVectorCastAndOneJacoco(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .contains(createLineCoverageBuilder()
                        .withCovered(JACOCO_ANALYSIS_MODEL_COVERED + VECTORCAST_COVERED_LINES)
                        .withMissed(JACOCO_ANALYSIS_MODEL_MISSED + VECTORCAST_MISSED_LINES)
                        .build());

        assertThat(getConsoleLog(build)).contains(
                "[Coverage] Recording coverage results",
                "[Coverage] Creating parser for VectorCAST Coverage Results",
                "that match the pattern 'vectorcast-statement-mcdc-fcc.xml'",
                "Successfully processed file 'vectorcast-statement-mcdc-fcc.xml'",
                "[Coverage] Creating parser for JaCoCo Coverage Reports",
                "that match the pattern 'jacoco-analysis-model.xml'",
                "Successfully processed file 'jacoco-analysis-model.xml'");
        var log = coverageResult.getLog();
        assertThat(log.getInfoMessages()).contains("Recording coverage results",
                "Creating parser for VectorCAST Coverage Results",
                "Successfully processed file 'vectorcast-statement-mcdc-fcc.xml'",
                "Creating parser for JaCoCo Coverage Reports",
                "Successfully processed file 'jacoco-analysis-model.xml'",
                "Resolving source code files...",
                "-> finished resolving of absolute paths (found: 0, not found: 315)",
                "Obtaining result action of reference build",
                "Reference build recorder is not configured",
                "-> Found no reference build",
                "No quality gates have been set - skipping",
                "Executing source code painting...",
                "Painting 315 source files on agent",
                "-> finished painting (0 files have been painted, 315 files failed)",
                "Copying painted sources from agent to build folder",
                "-> extracting...",
                "-> done",
                "Finished coverage processing - adding the action to the build...");
        assertThat(log.getErrorMessages()).contains(
                "Errors while resolving source files on agent:",
                "- Source file 'edu/hm/hafner/analysis/parser/PerlCriticParser.java' not found",
                "- Source file 'edu/hm/hafner/analysis/parser/StyleCopParser.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/RoboCopyDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/parser/fxcop/FxCopRuleSet.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/SpotBugsDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/parser/AcuCobolParser.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/FlexSdkDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/BrakemanDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/PyDocStyleDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/parser/AjcParser.java' not found",
                "- Source file 'edu/hm/hafner/analysis/ReaderFactory.java' not found",
                "- Source file 'edu/hm/hafner/analysis/parser/DiabCParser.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/OELintAdvDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/GnuFortranDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/parser/Armcc5CompilerParser.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/DoxygenDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/ProtoLintDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/registry/GoLintDescriptor.java' not found",
                "- Source file 'edu/hm/hafner/analysis/ModuleResolver.java' not found",
                "  ... skipped logging of 295 additional errors ...");
    }

    @Test
    void shouldRecordOneOpenCoverResultInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.OPENCOVER, "opencover.xml");

        Run<?, ?> build = buildSuccessfully(project);

        verifyOpenCoverResults(build);
    }

    private void verifyOpenCoverResults(final Run<?, ?> build) {
        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .filteredOn(Value::getMetric, Metric.LINE)
                .first()
                .isInstanceOfSatisfying(Coverage.class, m -> {
                    assertThat(m.getCovered()).isEqualTo(9);
                    assertThat(m.getTotal()).isEqualTo(15);
                });
    }

    @Test @Issue("JENKINS-72595")
    void shouldGracefullyHandleBomEncodedFiles() {
        assumeThatTestIsRunningOnUnix();

        var fileName = "opencover-with-bom.xml";
        WorkflowJob job = createPipelineWithWorkspaceFiles(fileName);

        setPipelineScript(job,
                "recordCoverage tools: [[parser: 'OPENCOVER', pattern: '" + fileName + "']]");

        Run<?, ?> build = buildSuccessfully(job);

        verifyOpenCoverResults(build);
    }

    @Test
    void shouldRecordOneNUnitResultInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.NUNIT, "nunit.xml");

        Run<?, ?> build = buildSuccessfully(project);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .filteredOn(Value::getMetric, Metric.TESTS)
                .first()
                .isInstanceOfSatisfying(TestCount.class, m -> {
                    assertThat(m.getValue()).isEqualTo(4);
                });
    }

    @Test
    void shouldRecordOneXUnitResultInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.XUNIT, "xunit.xml");

        Run<?, ?> build = buildSuccessfully(project);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .filteredOn(Value::getMetric, Metric.TESTS)
                .first()
                .isInstanceOfSatisfying(TestCount.class, m -> {
                    assertThat(m.getValue()).isEqualTo(3);
                });
    }

    private AbstractStringAssert<?> assertThatCell(final DetailedCell<?> cell) {
        return assertThat(cell).extracting(DetailedCell::getDisplay).asString();
    }

    private static CoverageBuilder createLineCoverageBuilder() {
        return new CoverageBuilder(Metric.LINE);
    }

    @Test
    void shouldRecordResultsWithDifferentId() {
        WorkflowJob job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, VECTORCAST_HIGHER_COVERAGE_FILE);

        setPipelineScript(job,
                "recordCoverage "
                        + "tools: [[parser: 'VECTORCAST', pattern: '" + VECTORCAST_HIGHER_COVERAGE_FILE + "']],"
                        + "id: 'vectorcast', name: 'VectorCast Results'\n"
                        + "recordCoverage "
                        + "tools: ["
                        + "[parser: 'JACOCO', pattern: '" + JACOCO_ANALYSIS_MODEL_FILE + "']],"
                        + "id: 'jacoco', name: 'JaCoCo Results'\n");

        Run<?, ?> build = buildSuccessfully(job);

        List<CoverageBuildAction> coverageResult = build.getActions(CoverageBuildAction.class);
        assertThat(coverageResult).hasSize(2);

        assertThat(coverageResult).element(0).satisfies(
                a -> {
                    assertThat(a.getUrlName()).isEqualTo("vectorcast");
                    assertThat(a.getDisplayName()).isEqualTo("VectorCast Results");
                    verifyVectorCastAction(a);
                }
        );
        assertThat(coverageResult).element(1).satisfies(
                a -> {
                    assertThat(a.getUrlName()).isEqualTo("jacoco");
                    assertThat(a.getDisplayName()).isEqualTo("JaCoCo Results");
                    verifyJaCoCoAction(a);
                });

        // TODO: verify that two different trend charts are returned!
    }
}
