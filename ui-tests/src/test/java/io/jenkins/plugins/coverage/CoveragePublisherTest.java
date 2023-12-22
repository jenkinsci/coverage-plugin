package io.jenkins.plugins.coverage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;

import io.jenkins.plugins.coverage.CoveragePublisher.SourceFileResolver;

import static org.assertj.core.api.AssertionsForClassTypes.*;

/**
 * Acceptance tests for CoveragePublisher. Verifies if set options in CoveragePublisher are used and lead to excepted
 * results.
 */
public class CoveragePublisherTest extends UiTest {
    /**
     * Creates two successful builds. Tests the reference values in summary, coverage report and main panel.
     */
    @Test
    public void testCodeCoveragePlugin() {
        FreeStyleJob job = getJobWithFirstBuildAndDifferentReports(InCaseCoverageDecreasedConfiguration.DONT_FAIL);
        Build secondBuild = buildSuccessfully(job);

        HashMap<String, Double> expectedCoverageFifthBuild = new HashMap<>();
        expectedCoverageFifthBuild.put("Line", 91.02);
        expectedCoverageFifthBuild.put("Branch", 93.97);
        List<Double> expectedReferenceCoverageFifthBuild = new ArrayList<>();
        expectedReferenceCoverageFifthBuild.add(-4.5);
        expectedReferenceCoverageFifthBuild.add(5.38);

        CoverageSummaryTest.verifySummaryWithReferenceBuild(secondBuild, expectedCoverageFifthBuild,
                expectedReferenceCoverageFifthBuild);

        CoverageReport report = new CoverageReport(secondBuild);
        report.open();

        FileCoverageTable fileCoverageTable = report.openFileCoverageTable();
        CoverageReportTest.verifyFileCoverageTableContent(fileCoverageTable,
                new String[] {"edu.hm.hafner.util", "edu.hm.hafner.util", "edu.hm.hafner.util"},
                new String[] {"Ensure.java", "FilteredLog.java", "Generated.java"},
                new String[] {"80.00%", "100.00%", "n/a"},
                new String[] {"86.96%", "100.00%", "n/a"});
        CoverageReportTest.verifyFileCoverageTableNumberOfMaxEntries(fileCoverageTable, 10);

        String coverageTree = report.getCoverageTree();
        CoverageReportTest.verifyCoverageTreeAfterSomeBuildsWithReports(coverageTree);

        String coverageOverview = report.getCoverageOverview();
        CoverageReportTest.verifyCoverageOverviewAfterSomeBuildsWithReports(coverageOverview);

        MainPanel mainPanel = new MainPanel(job);
        MainPanelTest.verifyTrendChartWithTwoReports(mainPanel, 1, 2);
    }

    /**
     * Verifies that job with no report fails when setFailNoReports(true).
     */
    @Test
    public void testFailOnNoReport() {
        FreeStyleJob job = getJobWithoutAnyReports(InCaseNoReportsConfiguration.FAIL);
        buildWithErrors(job);
    }

    /**
     * Verifies that job with decreased coverage fails when setFailBuildIfCoverageDecreasedInChangeRequest(true).
     */
    @Test
    public void testFailOnDecreasedCoverage() {
        FreeStyleJob job = getJobWithFirstBuildAndDifferentReports(InCaseCoverageDecreasedConfiguration.FAIL);
        buildWithErrors(job);
    }

    /**
     * Test if build fails if setFailUnhealthy is true and thresholds set.
     */
    @Test
    @Ignore("This bug needs to be fixed")
    public void testAdapterThresholdsAndFailOnUnhealthySetter() {
        FreeStyleJob job = getJobWithAdapterThresholdAndFailOnUnhealthySetter(97, 99, true, ThresholdLevel.ADAPTER);
        buildWithErrors(job);
    }

    /**
     * Test if global thresholds are set.
     */
    @Test
    public void testGlobalThresholdsAndFailSetter() {
        FreeStyleJob job = getJobWithAdapterThresholdAndFailOnUnhealthySetter(97, 99, true, ThresholdLevel.GLOBAL);
        buildUnstable(job);
    }

    /**
     * Tests if source file storing level and display is correct.
     */
    @Test
    @WithPlugins("git")
    public void testSourceFileStoringLevelAllBuilds() {
        FreeStyleJob job = getJobWithReportAndSourceCode(SourceFileResolver.STORE_ALL_BUILD);
        Build build = buildSuccessfully(job);
        buildSuccessfully(job);
        buildSuccessfully(job);
        buildSuccessfully(job);
        buildSuccessfully(job);

        verifyClickableFileSelection(build, true);
    }

    /**
     * Tests if source file is only available for last build.
     */
    @Test
    @WithPlugins("git")
    public void testSourceFileStoringLevelLastBuild() {
        FreeStyleJob job = getJobWithReportAndSourceCode(SourceFileResolver.STORE_LAST_BUIlD);
        Build firstBuild = buildSuccessfully(job);
        Build secondBuild = buildSuccessfully(job);
        Build thirdBuild = buildSuccessfully(job);

        verifyClickableFileSelection(firstBuild, false);
        verifyClickableFileSelection(secondBuild, false);
        verifyClickableFileSelection(thirdBuild, true);
    }

    /**
     * Tests if source file storing is off.
     */
    @Test
    @WithPlugins("git")
    public void testSourceFileStoringLevelNever() {
        FreeStyleJob job = getJobWithReportAndSourceCode(SourceFileResolver.NEVER_STORE);
        Build firstBuild = buildSuccessfully(job);

        verifyClickableFileSelection(firstBuild, false);
    }

    /**
     * Verifies if a file in a {@link FileCoverageTableRow} is clickable.
     *
     * @param build
     *         The current build with file coverage
     * @param sourceCodeAvailable
     *         {@code true} if the source code is available and should be displayed
     */
    private void verifyClickableFileSelection(final Build build, final boolean sourceCodeAvailable) {
        CoverageReport report = new CoverageReport(build);
        report.open();
        FileCoverageTable fileCoverageTable = report.openFileCoverageTable();
        FileCoverageTableRow row = fileCoverageTable.getRow(0);
        row.openSourceCode();
        assertThat(report.isExpectedSourceFileContentDisplayed(sourceCodeAvailable)).isEqualTo(sourceCodeAvailable);
    }
}
