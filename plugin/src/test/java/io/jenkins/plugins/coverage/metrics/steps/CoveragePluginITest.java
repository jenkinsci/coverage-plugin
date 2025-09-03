package io.jenkins.plugins.coverage.metrics.steps;

import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junitpioneer.jupiter.Issue;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;

import java.util.List;

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
 * Integration tests for different parsers.
 */
class CoveragePluginITest extends AbstractCoverageITest {
    private static final String COBERTURA_HIGHER_COVERAGE_FILE = "cobertura-higher-coverage.xml";
    private static final int COBERTURA_COVERED_LINES = 8;
    private static final int COBERTURA_MISSED_LINES = 0;
    private static final String NO_FILES_FOUND_ERROR_MESSAGE = "[-ERROR-] No files found for pattern '**/*xml'. Configuration error?";
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
    private static final int VECTORCAST_MISSED_METHOD = 9;

    @Test
    void shouldFailWithoutParserInFreestyleJob() {
        var project = createFreeStyleProject();

        project.getPublishersList().add(new CoverageRecorder());

        verifyNoParserError(project);
    }

    @Test
    void shouldFailWithoutParserInPipeline() {
        var job = createPipeline();

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
        var project = createFreestyleJob(parser);

        verifyLogMessageThatNoFilesFound(project);
    }

    @EnumSource
    @ParameterizedTest(name = "{index} => Pipeline with parser {0}")
    @DisplayName("Report error but do not fail build in pipeline when no input files are found")
    void shouldReportErrorWhenNoFilesHaveBeenFoundInPipeline(final Parser parser) {
        var job = createPipeline(parser);

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
        var project = createFreestyleJob(parser, r -> r.setFailOnError(true));

        verifyFailureWhenNoFilesFound(project);
    }

    @EnumSource
    @ParameterizedTest(name = "{index} => Pipeline with parser {0}")
    @DisplayName("Report error and fail build in pipeline when no input files are found")
    void shouldFailBuildWhenNoFilesHaveBeenFoundInPipeline(final Parser parser) {
        var job = createPipeline();

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
        var project = createFreestyleJob(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE);

        verifyOneJacocoResult(project);
    }

    @Test
    void shouldRecordOneJacocoResultInPipeline() {
        var job = createPipeline(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE);

        verifyOneJacocoResult(job);
    }

    @Test
    void shouldRecordOneJacocoResultInDeclarativePipeline() {
        var job = createDeclarativePipeline(Parser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE);

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
                        Metric.LOC,
                        Metric.CYCLOMATIC_COMPLEXITY);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .contains(createLineCoverageBuilder()
                        .withCovered(JACOCO_ANALYSIS_MODEL_COVERED)
                        .withMissed(JACOCO_ANALYSIS_MODEL_TOTAL - JACOCO_ANALYSIS_MODEL_COVERED)
                        .build());
        var tableModel = coverageResult.getTarget().getTableModel(CoverageViewModel.ABSOLUTE_COVERAGE_TABLE_ID);
        assertThat(tableModel)
                .extracting(TableModel::getColumns)
                .asInstanceOf(LIST)
                .extracting("headerLabel")
                .containsExactly("Hash",
                        "Modified",
                        "File",
                        "Package",
                        "Line",
                        "Branch",
                        "LOC",
                        "Complexity");
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
        assertThat(r.getCyclomaticComplexity()).isEqualTo(6);
        assertThat(r.getLoc()).isEqualTo(28);
        assertThat(r.getNcss()).isEqualTo(0);
        assertThatCell(r.getLineCoverage()).contains("title=\"Covered: 26 - Missed: 2\">92.86%");
        assertThatCell(r.getLineCoverageDelta()).contains("N/A");
        assertThatCell(r.getBranchCoverage()).contains("title=\"Covered: 4 - Missed: 0\">100.00%");
        assertThatCell(r.getBranchCoverageDelta()).contains("N/A");
        assertThatCell(r.getMutationCoverage()).contains("N/A");
        assertThatCell(r.getMutationCoverageDelta()).contains("N/A");
        assertThatCell(r.getTestStrength()).contains("N/A");
        assertThatCell(r.getTestStrengthDelta()).contains("N/A");
    }

    @Test
    void shouldRecordTwoJacocoResultsInFreestyleJob() {
        var project = createFreestyleJob(Parser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);
        verifyTwoJacocoResults(project);
    }

    @Test
    void shouldRecordTwoJacocoResultsInPipeline() {
        var job = createPipeline(Parser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        verifyTwoJacocoResults(job);
    }

    @Test
    void shouldRecordTwoJacocoResultsInDeclarativePipeline() {
        var job = createDeclarativePipeline(Parser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        verifyTwoJacocoResults(job);
    }

    private void verifyTwoJacocoResults(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        var coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .contains(createLineCoverageBuilder()
                        .withCovered(JACOCO_ANALYSIS_MODEL_COVERED + JACOCO_CODING_STYLE_COVERED)
                        .withMissed(JACOCO_ANALYSIS_MODEL_MISSED + JACOCO_CODING_STYLE_MISSED)
                        .build());
    }

    @Test
    void shouldRecordOneCoberturaResultInFreestyleJob() {
        var project = createFreestyleJob(Parser.COBERTURA, COBERTURA_HIGHER_COVERAGE_FILE);

        verifyOneCoberturaResult(project);
    }

    @Test
    void shouldRecordOneCoberturaResultInPipeline() {
        var job = createPipeline(Parser.COBERTURA, COBERTURA_HIGHER_COVERAGE_FILE);

        verifyOneCoberturaResult(job);
    }

    @Test
    void shouldRecordOneCoberturaResultInDeclarativePipeline() {
        var job = createDeclarativePipeline(Parser.COBERTURA, COBERTURA_HIGHER_COVERAGE_FILE);

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
        var project = createFreeStyleProjectWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE,
                COBERTURA_HIGHER_COVERAGE_FILE);

        var recorder = new CoverageRecorder();

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
        var job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, COBERTURA_HIGHER_COVERAGE_FILE);

        setPipelineScript(job,
                "recordCoverage tools: ["
                        + "[parser: 'COBERTURA', pattern: '" + COBERTURA_HIGHER_COVERAGE_FILE + "'],"
                        + "[parser: 'JACOCO', pattern: '" + JACOCO_ANALYSIS_MODEL_FILE + "']"
                        + "]");

        verifyForOneCoberturaAndOneJacoco(job);
    }

    @Test
    void shouldRecordCoberturaAndJacocoResultsInDeclarativePipeline() {
        var job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, COBERTURA_HIGHER_COVERAGE_FILE);

        job.setDefinition(createPipelineScript("pipeline {\n"
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
                + "}"));

        verifyForOneCoberturaAndOneJacoco(job);
    }

    private void verifyForOneCoberturaAndOneJacoco(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        var coverageResult = build.getAction(CoverageBuildAction.class);
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
                "Obtaining result action of reference build",
                "Reference build recorder is not configured",
                "-> Found no reference build",
                "No quality gates have been set - skipping",
                "Executing source code painting...",
                "Painting 308 source files on agent",
                "-> finished painting (0 files have been painted, 308 files failed)",
                "Copying painted sources from agent to build folder",
                "-> extracting...",
                "-> done",
                "Finished coverage processing - adding the action to the build...");
        assertThat(log.getErrorMessages()).contains(
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
        var project = createFreestyleJob(Parser.OPENCOVER, "opencover.xml");

        Run<?, ?> build = buildSuccessfully(project);

        verifyOpenCoverResults(build);
    }

    private void verifyOpenCoverResults(final Run<?, ?> build) {
        var coverageResult = build.getAction(CoverageBuildAction.class);
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
        var job = createPipelineWithWorkspaceFiles(fileName);

        setPipelineScript(job,
                "recordCoverage tools: [[parser: 'OPENCOVER', pattern: '" + fileName + "']]");

        Run<?, ?> build = buildSuccessfully(job);

        verifyOpenCoverResults(build);
    }

    @Test
    void shouldRecordOneNUnitResultInFreestyleJob() {
        var project = createFreestyleJob(Parser.NUNIT, "nunit.xml");

        Run<?, ?> build = buildSuccessfully(project);

        var coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .filteredOn(Value::getMetric, Metric.TESTS)
                .first()
                .satisfies(m -> assertThat(m.asInteger()).isEqualTo(4));
    }

    @Test
    void shouldRecordOneXUnitResultInFreestyleJob() {
        var project = createFreestyleJob(Parser.XUNIT, "xunit.xml");

        Run<?, ?> build = buildSuccessfully(project);

        var coverageResult = build.getAction(CoverageBuildAction.class);
        assertThat(coverageResult.getAllValues(Baseline.PROJECT))
                .filteredOn(Value::getMetric, Metric.TESTS)
                .first()
                .satisfies(m -> assertThat(m.asInteger()).isEqualTo(3));
    }

    @Test
    void shouldRecordOnePitResultInFreestyleJob() {
        var project = createFreestyleJob(Parser.PIT, "mutations.xml");

        verifyOnePitResult(project);
    }

    private void verifyOnePitResult(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        var coverageResult = build.getAction(CoverageBuildAction.class);
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
                .extracting(TableModel::getColumns)
                .asInstanceOf(LIST)
                .extracting("headerLabel")
                .containsExactly("Hash",
                        "Modified",
                        "File",
                        "Package",
                        "Line",
                        "Mutation",
                        "Test Strength",
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
        assertThat(r.getCyclomaticComplexity()).isEqualTo(0);
        assertThat(r.getLoc()).isEqualTo(87);
        assertThat(r.getCognitiveComplexity()).isEqualTo(0);
        assertThatCell(r.getLineCoverage()).contains("title=\"Covered: 85 - Missed: 2\">97.70%");
        assertThatCell(r.getLineCoverageDelta()).contains("N/A");
        assertThatCell(r.getMutationCoverage()).contains("Killed: 95 - Survived: 2\">97.94%");
        assertThatCell(r.getMutationCoverageDelta()).contains("N/A");
        assertThatCell(r.getTestStrength()).contains("Killed: 95 - Survived: 0\">100.00%");
        assertThatCell(r.getTestStrengthDelta()).contains("N/A");
        assertThatCell(r.getBranchCoverage()).contains("N/A");
        assertThatCell(r.getBranchCoverageDelta()).contains("N/A");
    }

    private AbstractStringAssert<?> assertThatCell(final DetailedCell<?> cell) {
        return assertThat(cell).extracting(DetailedCell::getDisplay).asString();
    }

    private static CoverageBuilder createLineCoverageBuilder() {
        return new CoverageBuilder(Metric.LINE);
    }

    @Test
    void shouldRecordResultsWithDifferentId() {
        var job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, COBERTURA_HIGHER_COVERAGE_FILE);

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

    @Test @Issue("785")
    void shouldIgnoreErrors() {
        var job = createPipeline();
        copyFileToWorkspace(job, "cobertura-duplicate-methods.xml", "cobertura.xml");
        job.setDefinition(createPipelineScript(
                """
                node {
                    recordCoverage tools: [[parser: 'COBERTURA']]
                }
                """));

        Run<?, ?> failure = buildWithResult(job, Result.FAILURE);

        assertThat(getConsoleLog(failure))
                .contains("java.lang.IllegalArgumentException: There is already the same child [METHOD] Enumerate()");

        job.setDefinition(createPipelineScript(
                """
                node {
                    recordCoverage tools: [[parser: 'COBERTURA']], ignoreParsingErrors: true
                }
                """));

        Run<?, ?> success = buildWithResult(job, Result.SUCCESS);

        assertThat(getConsoleLog(success))
                .doesNotContain("java.lang.IllegalArgumentException");
    }

    @Test
    void shouldIgnoreEmptyListOfFiles() {
        var job = createPipeline();
        job.setDefinition(createPipelineScript(
                """
                node {
                    recordCoverage tools: [[parser: 'JACOCO']]
                }
                """));

        Run<?, ?> run = buildWithResult(job, Result.SUCCESS);

        assertThat(getConsoleLog(run))
                .contains("Using default pattern '**/jacoco.xml,**/jacocoTestReport.xml' since user defined pattern is not set",
                        "[-ERROR-] No files found for pattern '**/jacoco.xml,**/jacocoTestReport.xml'. Configuration error?")
                .containsPattern("Searching for all files in '.*' that match the pattern '\\*\\*/jacoco.xml,\\*\\*/jacocoTestReport.xml'")
                .doesNotContain("Expanding pattern");
    }

    @Test
    void shouldParseFileWithJaCoCo() {
        var job = createPipeline();
        copyFilesToWorkspace(job, "jacoco.xml");
        job.setDefinition(createPipelineScript(
                """
                node {
                    recordCoverage tools: [[parser: 'JACOCO']]
                }
                """));

        Run<?, ?> run = buildWithResult(job, Result.SUCCESS);

        assertThat(getConsoleLog(run))
                .contains("Using default pattern '**/jacoco.xml,**/jacocoTestReport.xml' since user defined pattern is not set",
                        "-> found 1 file",
                        "MODULE: 100.00% (1/1)",
                        "PACKAGE: 100.00% (1/1)",
                        "FILE: 87.50% (7/8)",
                        "CLASS: 93.75% (15/16)",
                        "METHOD: 95.10% (97/102)",
                        "INSTRUCTION: 93.33% (1260/1350)",
                        "LINE: 91.02% (294/323)",
                        "BRANCH: 93.97% (109/116)",
                        "COMPLEXITY: 160")
                .containsPattern("Searching for all files in '.*' that match the pattern '\\*\\*/jacoco.xml,\\*\\*/jacocoTestReport.xml'")
                .containsPattern("Successfully parsed file .*/jacoco.xml")
                .doesNotContain("Expanding pattern");
    }

    @Test
    void shouldParseFileWithClover() {
        var job = createPipeline();
        copyFilesToWorkspace(job, "clover.xml");
        job.setDefinition(createPipelineScript(
                """
                node {
                    recordCoverage tools: [[parser: 'CLOVER']]
                }
                """));

        Run<?, ?> run = buildWithResult(job, Result.SUCCESS);

        assertThat(getConsoleLog(run))
                .contains("Using default pattern '**/*clover*.xml,**/*Clover*.xml' since user defined pattern is not set",
                        "-> found 1 file",
                        "MODULE: 100.00% (1/1)",
                        "PACKAGE: 100.00% (3/3)",
                        "FILE: 100.00% (6/6)",
                        "METHOD: 73.38% (736/1003)",
                        "INSTRUCTION: 87.56% (169/193)",
                        "LINE: 86.67% (143/165)",
                        "BRANCH: 73.96% (1068/1444)",
                        "LOC: 165")
                .containsPattern("Searching for all files in '.*' that match the pattern '.*clover\\*.xml,.*Clover\\*.xml'")
                .containsPattern("Successfully parsed file .*/clover.xml")
                .doesNotContain("Expanding pattern");
    }

    @Test
    void shouldParseFileWithGoCov() {
        var job = createPipeline();
        copyFilesToWorkspace(job, "go-coverage.out");
        job.setDefinition(createPipelineScript(
                """
                node {
                    recordCoverage tools: [[parser: 'GO_COV', pattern: 'go-coverage.out']]
                }
                """));

        Run<?, ?> run = buildWithResult(job, Result.SUCCESS);

        assertThat(getConsoleLog(run))
                .contains("that match the pattern 'go-coverage.out'",
                        "-> found 1 file",
                        "MODULE: 100.00% (2/2)",
                        "PACKAGE: 75.00% (3/4)",
                        "FILE: 75.00% (3/4)",
                        "LINE: 69.70% (23/33)",
                        "INSTRUCTION: 63.64% (14/22)",
                        "LOC: 33")
                .containsPattern("Searching for all files in '.*' that match the pattern 'go-coverage.out'")
                .containsPattern("Successfully parsed file .*/go-coverage.out")
                .doesNotContain("Expanding pattern");
    }

    private void assertContentOfFirstVectorCastRow(final CoverageRow r) {
        assertThatCell(r.getFileName())
                .contains("title=\"CurrentRelease/database/src/database.c\"");
        assertThat(r.getPackageName()).isEqualTo("CurrentRelease.database.src");
        assertThat(r.getTests()).isEqualTo(0);
        assertThat(r.getCyclomaticComplexity()).isEqualTo(5);
        assertThat(r.getLoc()).isEqualTo(17);
        assertThat(r.getNcss()).isEqualTo(0);
        assertThatCell(r.getLineCoverage())
                .contains("title=\"Covered: 17 - Missed: 0\">100.00%");
        assertThatCell(r.getLineCoverageDelta()).contains("N/A");
        assertThatCell(r.getBranchCoverage())
                .contains("title=\"Covered: 9 - Missed: 2\">81.82%");
        assertThatCell(r.getBranchCoverageDelta()).contains("N/A");
        assertThatCell(r.getMethodCoverage())
                .contains("title=\"Covered: 3 - Missed: 0\">100.00%");
        assertThatCell(r.getMcdcPairCoverage())
                .contains("title=\"Covered: 1 - Missed: 1\">50.00%");
        assertThatCell(r.getFunctionCallCoverage())
                .contains("title=\"Covered: 4 - Missed: 0\">100.00%");
        assertThatCell(r.getTestStrengthDelta()).contains("N/A");
    }

    @Test
    void shouldRecordOneVectorCastResultInFreestyleJob() {
        var project = createFreestyleJob(Parser.VECTORCAST, VECTORCAST_HIGHER_COVERAGE_FILE);

        verifyOneVectorCastResult(project);
    }

    @Test
    void shouldRecordOneVectorCastResultInPipeline() {
        var job = createPipeline(Parser.VECTORCAST, VECTORCAST_HIGHER_COVERAGE_FILE);

        verifyOneVectorCastResult(job);
    }

    @Test
    void shouldRecordOneVectorCastResultInDeclarativePipeline() {
        var job = createDeclarativePipeline(Parser.VECTORCAST, VECTORCAST_HIGHER_COVERAGE_FILE);

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
                        Metric.LOC,
                        Metric.CYCLOMATIC_COMPLEXITY);
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
                .extracting(TableModel::getColumns)
                .asInstanceOf(LIST)
                .extracting("headerLabel")
                .containsExactly("Hash",
                        "Modified",
                        "File",
                        "Package",
                        "Line",
                        "Branch",
                        "MC/DC Pairs",
                        "Function Call",
                        "LOC",
                        "Complexity");
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
        var project = createFreeStyleProjectWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE,
                VECTORCAST_HIGHER_COVERAGE_FILE);

        var recorder = new CoverageRecorder();

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
        var job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, VECTORCAST_HIGHER_COVERAGE_FILE);

        setPipelineScript(job,
                "recordCoverage tools: ["
                        + "[parser: 'VECTORCAST', pattern: '" + VECTORCAST_HIGHER_COVERAGE_FILE + "'],"
                        + "[parser: 'JACOCO', pattern: '" + JACOCO_ANALYSIS_MODEL_FILE + "']"
                        + "]");

        verifyForOneVectorCastAndOneJacoco(job);
    }

    @Test
    void shouldRecordVectorCastAndJacocoResultsInDeclarativePipeline() {
        var job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, VECTORCAST_HIGHER_COVERAGE_FILE);

        job.setDefinition(createPipelineScript(
                "pipeline {\n"
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
                + "}"));

        verifyForOneVectorCastAndOneJacoco(job);
    }

    private void verifyForOneVectorCastAndOneJacoco(final ParameterizedJob<?, ?> project) {
        Run<?, ?> build = buildSuccessfully(project);

        var coverageResult = build.getAction(CoverageBuildAction.class);
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
    void shouldRecordVerctorCastResultsWithDifferentId() {
        var job = createPipelineWithWorkspaceFiles(JACOCO_ANALYSIS_MODEL_FILE, VECTORCAST_HIGHER_COVERAGE_FILE);

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

    @Test
    void shouldParseFileWithVectorCast() {
        var job = createPipeline();
        copyFileToWorkspace(job, "vectorcast-statement-mcdc-fcc.xml", "xml_data/cobertura/coverage_results_test.xml");
        job.setDefinition(createPipelineScript(
                """
                node {
                    recordCoverage tools: [[parser: 'VECTORCAST']]
                }
                """));

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
                        "CYCLOMATIC_COMPLEXITY: 100",
                        "LOC: 294")
                .containsPattern("Searching for all files in '.*' that match the pattern 'xml_data/cobertura/coverage_results\\*.xml'")
                .containsPattern("Successfully parsed file '.*/xml_data/cobertura/coverage_results_test.xml")
                .doesNotContain("Expanding pattern");
    }
}
