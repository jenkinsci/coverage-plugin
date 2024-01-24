package io.jenkins.plugins.coverage.metrics.steps;

import java.util.List;

import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for different JaCoCo, Cobertura, and PIT files.
 */
class CoveragePluginITest extends AbstractCoverageITest {
    private static final String COBERTURA_HIGHER_COVERAGE_FILE = "cobertura-higher-coverage.xml";
    private static final int COBERTURA_COVERED_LINES = 8;
    private static final int COBERTURA_MISSED_LINES = 0;
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

    @Test
    void shouldRecordTwoJacocoResultsInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);
        verifyTwoJacocoResults(project);
    }

    @Test
    void shouldRecordTwoJacocoResultsInPipeline() {
        WorkflowJob job = createPipeline(Parser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        verifyTwoJacocoResults(job);
    }

    @Test
    void shouldRecordTwoJacocoResultsInDeclarativePipeline() {
        WorkflowJob job = createDeclarativePipeline(Parser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        verifyTwoJacocoResults(job);
    }

    private void verifyTwoJacocoResults(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .contains(createLineCoverageBuilder()
                        .withCovered(JACOCO_ANALYSIS_MODEL_COVERED + JACOCO_CODING_STYLE_COVERED)
                        .withMissed(JACOCO_ANALYSIS_MODEL_MISSED + JACOCO_CODING_STYLE_MISSED)
                        .build());
    }

    @Test
    void shouldRecordOneCoberturaResultInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.COBERTURA, COBERTURA_HIGHER_COVERAGE_FILE);

        verifyOneCoberturaResult(project);
    }

    @Test
    void shouldRecordOneCoberturaResultInPipeline() {
        WorkflowJob job = createPipeline(Parser.COBERTURA, COBERTURA_HIGHER_COVERAGE_FILE);

        verifyOneCoberturaResult(job);
    }

    @Test
    void shouldRecordOneCoberturaResultInDeclarativePipeline() {
        WorkflowJob job = createDeclarativePipeline(Parser.COBERTURA, COBERTURA_HIGHER_COVERAGE_FILE);

        verifyOneCoberturaResult(job);
    }

    private void verifyOneCoberturaResult(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        verifyCoberturaAction(build.getAction(CoverageBuildAction.class));
    }

    private static void verifyCoberturaAction(final CoverageBuildAction coverageResult) {
        assertThat(coverageResult.getAllValues(Baseline.PROJECT)).contains(
                new CoverageBuilder()
                        .withMetric(Metric.LINE)
                        .withCovered(COBERTURA_COVERED_LINES)
                        .withMissed(COBERTURA_MISSED_LINES)
                        .build());
    }

    @Test
    void shouldRecordCoberturaAndJacocoResultsInFreestyleJob() {
        FreeStyleProject project = createFreeStyleProjectWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE,
                COBERTURA_HIGHER_COVERAGE_FILE);

        CoverageRecorder recorder = new CoverageRecorder();

        var cobertura = new CoverageTool();
        cobertura.setParser(Parser.COBERTURA);
        cobertura.setPattern(COBERTURA_HIGHER_COVERAGE_FILE);

        var jacoco = new CoverageTool();
        jacoco.setParser(Parser.JACOCO);
        jacoco.setPattern(JACOCO_ANALYSIS_MODEL_FILE);

        recorder.setTools(List.of(jacoco, cobertura));
        project.getPublishersList().add(recorder);

        verifyForOneCoberturaAndOneJacoco(project);
    }

    @Test
    void shouldRecordCoberturaAndJacocoResultsInPipeline() {
        WorkflowJob job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, COBERTURA_HIGHER_COVERAGE_FILE);

        setPipelineScript(job,
                "recordCoverage tools: ["
                        + "[parser: 'COBERTURA', pattern: '" + COBERTURA_HIGHER_COVERAGE_FILE + "'],"
                        + "[parser: 'JACOCO', pattern: '" + JACOCO_ANALYSIS_MODEL_FILE + "']"
                        + "]");

        verifyForOneCoberturaAndOneJacoco(job);
    }

    @Test
    void shouldRecordCoberturaAndJacocoResultsInDeclarativePipeline() {
        WorkflowJob job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, COBERTURA_HIGHER_COVERAGE_FILE);

        job.setDefinition(new CpsFlowDefinition("pipeline {\n"
                + "    agent any\n"
                + "    stages {\n"
                + "        stage('Test') {\n"
                + "            steps {\n"
                + "                 recordCoverage(tools: [\n"
                + "                     [parser: 'COBERTURA', pattern: '" + COBERTURA_HIGHER_COVERAGE_FILE + "'],\n"
                + "                     [parser: 'JACOCO', pattern: '" + JACOCO_ANALYSIS_MODEL_FILE + "']\n"
                + "                 ])\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}", true));

        verifyForOneCoberturaAndOneJacoco(job);
    }

    private void verifyForOneCoberturaAndOneJacoco(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .contains(createLineCoverageBuilder()
                        .withCovered(JACOCO_ANALYSIS_MODEL_COVERED + COBERTURA_COVERED_LINES)
                        .withMissed(JACOCO_ANALYSIS_MODEL_MISSED)
                        .build());

        assertThat(getConsoleLog(build)).contains(
                "[Coverage] Recording coverage results",
                "[Coverage] Creating parser for Cobertura Coverage Reports",
                "that match the pattern 'cobertura-higher-coverage.xml'",
                "Successfully processed file 'cobertura-higher-coverage.xml'",
                "[Coverage] Creating parser for JaCoCo Coverage Reports",
                "that match the pattern 'jacoco-analysis-model.xml'",
                "Successfully processed file 'jacoco-analysis-model.xml'");
        var log = coverageResult.getLog();
        assertThat(log.getInfoMessages()).contains("Recording coverage results",
                "Creating parser for Cobertura Coverage Reports",
                "Successfully processed file 'cobertura-higher-coverage.xml'",
                "Creating parser for JaCoCo Coverage Reports",
                "Successfully processed file 'jacoco-analysis-model.xml'",
                "Resolving source code files...",
                "-> finished resolving of absolute paths (found: 0, not found: 308)",
                "Obtaining action of reference build",
                "Reference build recorder is not configured",
                "-> Found no reference result in reference build",
                "No quality gates have been set - skipping",
                "Executing source code painting...",
                "Painting 308 source files on agent",
                "-> finished painting (0 files have been painted, 308 files failed)",
                "Copying painted sources from agent to build folder",
                "-> extracting...",
                "-> done",
                "Finished coverage processing - adding the action to the build...");
        assertThat(log.getErrorMessages()).contains("Errors while recording code coverage:",
                "Errors during source path resolving:",
                "Errors while resolving source files on agent:",
                "Removing non-workspace source directory '/Users/leobalter/dev/testing/solutions/3' - it has not been approved in Jenkins' global configuration.",
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
                "  ... skipped logging of 289 additional errors ...");
    }

    @Test
    void shouldRecordOneOpenCoverResultInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.OPENCOVER, "opencover.xml");

        Run<?, ?> build = buildSuccessfully(project);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .filteredOn(Value::getMetric, Metric.LINE)
                .first()
                .isInstanceOfSatisfying(Coverage.class, m -> {
                    assertThat(m.getCovered()).isEqualTo(9);
                    assertThat(m.getTotal()).isEqualTo(15);
                });
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
    void shouldRecordOnePitResultInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(Parser.PIT, "mutations.xml");

        verifyOnePitResult(project);
    }

    private void verifyOnePitResult(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        CoverageBuildAction coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .filteredOn(Value::getMetric, Metric.MUTATION)
                .first()
                .isInstanceOfSatisfying(Coverage.class, m -> {
                    assertThat(m.getCovered()).isEqualTo(222);
                    assertThat(m.getTotal()).isEqualTo(246);
                });
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .filteredOn(Value::getMetric, Metric.TEST_STRENGTH)
                .first()
                .isInstanceOfSatisfying(Coverage.class, m -> {
                    assertThat(m.getCovered()).isEqualTo(222);
                    assertThat(m.getTotal()).isEqualTo(230);
                });

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
                        "Mutation",
                        "Mutation Δ",
                        "Test Strength",
                        "Test Strength Δ",
                        "LOC");
        assertThat(tableModel.getRows())
                .hasSize(10)
                .first()
                .isInstanceOfSatisfying(CoverageRow.class, this::assertContentOfFirstPitRow);
    }

    private void assertContentOfFirstPitRow(final CoverageRow r) {
        assertThatCell(r.getFileName())
                .contains("title=\"edu/hm/hafner/coverage/CoverageNode.java\"");
        assertThat(r.getPackageName()).isEqualTo("edu.hm.hafner.coverage");
        assertThat(r.getTests()).isEqualTo(0);
        assertThat(r.getComplexity()).isEqualTo(0);
        assertThat(r.getLoc()).isEqualTo(87);
        assertThat(r.getMaxComplexity()).isEqualTo(0);
        assertThatCell(r.getLineCoverage()).contains("title=\"Covered: 85 - Missed: 2\">97.70%");
        assertThatCell(r.getLineCoverageDelta()).contains("n/a");
        assertThatCell(r.getMutationCoverage()).contains("Killed: 95 - Survived: 2\">97.94%");
        assertThatCell(r.getMutationCoverageDelta()).contains("n/a");
        assertThatCell(r.getTestStrength()).contains("Killed: 95 - Survived: 0\">100.00%");
        assertThatCell(r.getTestStrengthDelta()).contains("n/a");
        assertThatCell(r.getBranchCoverage()).contains("n/a");
        assertThatCell(r.getBranchCoverageDelta()).contains("n/a");
        assertThatCell(r.getDensity()).contains("0.0");
    }

    private AbstractStringAssert<?> assertThatCell(final DetailedCell<?> cell) {
        return assertThat(cell).extracting(DetailedCell::getDisplay).asString();
    }

    private static CoverageBuilder createLineCoverageBuilder() {
        return new CoverageBuilder(Metric.LINE);
    }

    @Test
    void shouldRecordResultsWithDifferentId() {
        WorkflowJob job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, COBERTURA_HIGHER_COVERAGE_FILE);

        setPipelineScript(job,
                "recordCoverage "
                        + "tools: [[parser: 'COBERTURA', pattern: '" + COBERTURA_HIGHER_COVERAGE_FILE + "']],"
                        + "id: 'cobertura', name: 'Cobertura Results'\n"
                        + "recordCoverage "
                        + "tools: ["
                        + "[parser: 'JACOCO', pattern: '" + JACOCO_ANALYSIS_MODEL_FILE + "']],"
                        + "id: 'jacoco', name: 'JaCoCo Results'\n");

        Run<?, ?> build = buildSuccessfully(job);

        List<CoverageBuildAction> coverageResult = build.getActions(CoverageBuildAction.class);
        assertThat(coverageResult).hasSize(2);

        assertThat(coverageResult).element(0).satisfies(
                a -> {
                    assertThat(a.getUrlName()).isEqualTo("cobertura");
                    assertThat(a.getDisplayName()).isEqualTo("Cobertura Results");
                    verifyCoberturaAction(a);
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
