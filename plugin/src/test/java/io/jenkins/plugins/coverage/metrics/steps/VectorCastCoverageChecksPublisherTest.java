package io.jenkins.plugins.coverage.metrics.steps;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.Fraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junitpioneer.jupiter.DefaultLocale;

import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.parser.PitestParser;

import hudson.model.Run;

import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationLevel;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.steps.CoverageRecorder.ChecksAnnotationScope;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.QualityGateResult;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DefaultLocale("en")
class VectorCastCoverageChecksPublisherTest extends CoverageChecksPublisherTest {
    @Test
    void shouldShowVectorCastQualityGateDetails() {
        var result = readVectorCastResult("vectorcast-statement-mcdc-fcc.xml");

        var publisher = new CoverageChecksPublisher(createActionWithoutDelta(result,
                CoverageQualityGateEvaluatorTest.createQualityGateResult()), result, REPORT_NAME,
                ChecksAnnotationScope.SKIP, createJenkins());

        var checkDetails = publisher.extractChecksDetails();

        var expectedQualityGateSummary = toString("vectorcast-coverage-publisher-quality-gate.checks-expected-result");
        assertThat(checkDetails.getOutput()).isPresent().get().satisfies(output -> {
            assertThat(output.getSummary()).isPresent()
                    .get()
                    .asString()
                    .containsIgnoringWhitespaces(expectedQualityGateSummary);
        });

        var expectedOverview = toString("vectorcast-coverage-publisher-quality-gate-overview.checks-expected-result");
        assertThat(checkDetails.getOutput()).isPresent().get().satisfies(output -> {
            assertThat(output.getSummary()).isPresent()
                    .get()
                    .asString()
                    .containsIgnoringWhitespaces(expectedOverview);
        });    
    }

    @Test
    void shouldShowProjectBaselineForVectorCast() {
        var result = readVectorCastResult("vectorcast-statement-mcdc-fcc.xml");

        var publisher = new CoverageChecksPublisher(createActionWithoutDelta(result), result, REPORT_NAME,
                ChecksAnnotationScope.SKIP, createJenkins());

        assertThatTitleIs(publisher, "Line Coverage: 79.93%, Branch Coverage: 66.18%");
    }

    @ParameterizedTest(name = "should create checks (scope = {0}, expected annotations = {1})")
    @CsvSource({"SKIP, 0", "ALL_LINES, 6", "MODIFIED_LINES, 0"})
    void shouldCreateChecksReport_SB(final ChecksAnnotationScope scope, final int expectedAnnotations) {
        shouldCreateChecksReport(scope, expectedAnnotations, 
                "vectorcast-statement-branch.xml", 
                "vectorcast-coverage-publisher-s+b-details.checks-expected-result",
                "vectorcast-coverage-publisher-s+b-overview.checks-expected-result");
    }

    @ParameterizedTest(name = "should create checks (scope = {0}, expected annotations = {1})")
    @CsvSource({"SKIP, 0", "ALL_LINES, 8", "MODIFIED_LINES, 0"})
    void shouldCreateChecksReport_SMCDC(final ChecksAnnotationScope scope, final int expectedAnnotations) {
        shouldCreateChecksReport(scope, expectedAnnotations, 
                "vectorcast-statement-mcdc.xml",
                "vectorcast-coverage-publisher-s+mcdc-details.checks-expected-result",
                "vectorcast-coverage-publisher-s+mcdc-overview.checks-expected-result");
    }
    
    @ParameterizedTest(name = "should create checks (scope = {0}, expected annotations = {1})")
    @CsvSource({"SKIP, 0", "ALL_LINES, 59", "MODIFIED_LINES, 0"})
    void shouldCreateChecksReport_SMCDCFCC(final ChecksAnnotationScope scope, final int expectedAnnotations) {
        shouldCreateChecksReport(scope, expectedAnnotations, 
                "vectorcast-statement-mcdc-fcc.xml", 
                "vectorcast-coverage-publisher-s+mcdc+fcc-details.checks-expected-result",
                "vectorcast-coverage-publisher-s+mcdc+fcc-overview.checks-expected-result");
    }
    
    void shouldCreateChecksReport(final ChecksAnnotationScope scope, final int expectedAnnotations, 
            final String inFile, final String checkDetailsFile, final String checkOverviewFile) {
        var result = readVectorCastResult(inFile);

        var publisher = new CoverageChecksPublisher(createCoverageBuildAction(result), result, REPORT_NAME, scope, createJenkins());

        var checkDetails = publisher.extractChecksDetails();

        assertThat(checkDetails.getName()).isPresent().contains(REPORT_NAME);
        assertThat(checkDetails.getStatus()).isEqualTo(ChecksStatus.COMPLETED);
        assertThat(checkDetails.getConclusion()).isEqualTo(ChecksConclusion.SUCCESS);
        assertThat(checkDetails.getDetailsURL()).isPresent()
                .contains("http://127.0.0.1:8080/job/pipeline-coding-style/job/5/coverage");
        assertThatDetailsAreCorrect(checkDetails, expectedAnnotations, checkDetailsFile, checkOverviewFile);
    }
    
    private void assertThatDetailsAreCorrect(final ChecksDetails checkDetails, final int expectedAnnotations,
            final String checkDetailsFile, final String checkOverviewFile) {
        assertThat(checkDetails.getOutput()).isPresent().get().satisfies(output -> {
            assertThat(output.getTitle()).isPresent().contains("Line Coverage: 50.00% (+50.00%)");
            var expectedDetails = toString(checkDetailsFile);
            assertThat(output.getText()).isPresent().get().asString().isEqualToNormalizingWhitespace(expectedDetails);
            assertChecksAnnotations(output, expectedAnnotations);
            assertSummary(output, checkOverviewFile);
        });
    }
}
