package io.jenkins.plugins.coverage.metrics.steps;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import io.jenkins.plugins.bootstrap5.MessagesViewModel;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.restapi.FileCoverageApiModel;
import io.jenkins.plugins.coverage.metrics.restapi.ModifiedLinesCoverageApiModel;
import io.jenkins.plugins.coverage.metrics.source.SourceViewModel;
import io.jenkins.plugins.coverage.metrics.steps.CoverageViewModel.UsePropertyFacade;
import io.jenkins.plugins.datatables.DetailedCell;
import io.jenkins.plugins.prism.SourceCodeViewModel;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.QualityGateStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Tests the class {@link RunTab}.
 *
 * @author Ullrich Hafner
 */
class RunTabTest extends AbstractCoverageTest {
    private static final String ID = "coverage";
    private static final int BUILD_NUMBER = 15;
    private static final String INFO_MESSAGE = "Info message";
    private static final String ERROR_MESSAGE = "Error message";

    @Test
    void shouldUseCoverageAsTheUrlNameAndDisplayName() {
        var tab = new RunTab(mock(Run.class));

        assertThat(tab.getUrlName()).isEqualTo("coverage");
        assertThat(tab.getDisplayName()).isEqualTo(Messages.RunTab_displayName());
    }

    @Test
    void shouldHaveNoIconAndNoBadgeWhenThereAreNoCoverageActions() {
        var build = CoverageMetricColumnTest.createBuildWithActions();
        var tab = new RunTab(build);

        assertThat(tab.getIconFileName()).isNull();
        assertThat(tab.getBadge()).isNull();
        assertThat(tab.getActions()).isEmpty();
        assertThat(tab.getWidget().getResults()).isEmpty();
    }

    @Test
    void shouldShowAnIconButNoBadgeWhenAllQualityGatesPass() {
        var action = createAction("coverage", new QualityGateResult(QualityGateStatus.PASSED));
        var build = CoverageMetricColumnTest.createBuildWithActions(action);
        var tab = new RunTab(build);

        assertThat(tab.getIconFileName()).isEqualTo("symbol-footsteps-outline plugin-ionicons-api");
        assertThat(tab.getBadge()).isNull();
        assertThat(tab.getActions()).containsExactly(action);
    }

    @Test
    void shouldShowAWarningBadgeWithTheFailedGateCountWhenGatesFail() {
        var first = createAction("coverage", new QualityGateResult(QualityGateStatus.ERROR));
        var second = createAction("mutation", new QualityGateResult(QualityGateStatus.FAILED));
        var build = CoverageMetricColumnTest.createBuildWithActions(first, second);
        var tab = new RunTab(build);

        var badge = tab.getBadge();
        assertThat(badge).isNotNull();
        assertThat(badge.getText()).isEqualTo("2");
        assertThat(badge.getTooltip()).isEqualTo(Messages.RunTab_failedQualityGates(2));
        assertThat(badge.getSeverity()).containsIgnoringCase("warning");
    }

    @Test
    void shouldWrapTheActionsInAWidget() {
        var action = createAction("coverage", new QualityGateResult());
        var build = CoverageMetricColumnTest.createBuildWithActions(action);
        var tab = new RunTab(build);

        assertThat(tab.getWidget().getResults()).containsExactly(action);
    }

    @Test
    void shouldResolveTheMatchingActionByUrlName() {
        var coverage = createAction("coverage", new QualityGateResult());
        var mutation = createAction("mutation", new QualityGateResult());
        var build = CoverageMetricColumnTest.createBuildWithActions(coverage, mutation);
        var tab = new RunTab(build);

        assertThat(tab.getDynamic("mutation")).isEqualTo(mutation);
    }

    /**
     * Verifies that the index page redirects to the overview of the first coverage action, using an absolute URL
     * that is derived from the URL of the run.
     *
     * @throws IOException
     *         never thrown
     * @throws ServletException
     *         never thrown
     */
    @Test
    void shouldRedirectToOverviewOfFirstActionIfRunTabIsEnabled() throws IOException, ServletException {
        var build = CoverageMetricColumnTest.createBuildWithActions(
                createAction("jacoco", new QualityGateResult()), createAction("mutation", new QualityGateResult()));
        when(build.getUrl()).thenReturn("job/x/42/");
        var response = mock(StaplerResponse2.class);

        new RunTab(build, () -> true).doIndex().generateResponse(createRequest(), response, new Object());

        verify(response).sendRedirect(anyInt(), eq("/jenkins/job/x/42/jacoco/overview"));
    }

    /**
     * Verifies that the index page is not available if the run tab is disabled.
     *
     * @throws IOException
     *         never thrown
     * @throws ServletException
     *         never thrown
     */
    @Test
    void shouldNotRedirectIfRunTabIsDisabled() throws IOException, ServletException {
        var build = CoverageMetricColumnTest.createBuildWithActions(createAction("jacoco", new QualityGateResult()));
        var response = mock(StaplerResponse2.class);

        new RunTab(build, () -> false).doIndex().generateResponse(createRequest(), response, new Object());

        verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
        verify(response, never()).sendRedirect(anyInt(), anyString());
    }

    /**
     * Verifies that the index page is not available if there are no coverage results.
     *
     * @throws IOException
     *         never thrown
     * @throws ServletException
     *         never thrown
     */
    @Test
    void shouldNotRedirectWithoutCoverageResults() throws IOException, ServletException {
        var build = CoverageMetricColumnTest.createBuildWithActions();
        var response = mock(StaplerResponse2.class);

        new RunTab(build, () -> true).doIndex().generateResponse(createRequest(), response, new Object());

        verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
        verify(response, never()).sendRedirect(anyInt(), anyString());
    }

    private StaplerRequest2 createRequest() {
        var request = mock(StaplerRequest2.class);
        when(request.getContextPath()).thenReturn("/jenkins");
        return request;
    }

    @Test
    void shouldThrowWhenNoActionMatchesTheRequestedUrlName() {
        var build = CoverageMetricColumnTest.createBuildWithActions(createAction("coverage", new QualityGateResult()));
        var tab = new RunTab(build);

        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> tab.getDynamic("does-not-exist"));
    }

    @ParameterizedTest(name = "URL \"{0}\" is ignored")
    @ValueSource(strings = {"overview", "trend", "treemap", "table", "log", "help"})
    void shouldIgnoreRunTabUrlsIfRunTabIsDisabled(final String url, @TempDir final Path buildFolder) {
        var viewModel = createViewModel(false, buildFolder);

        assertThat(viewModel.getDynamic(url)).isNull();
    }

    @Test
    void shouldResolveRunTabUrlsIfRunTabIsEnabled(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(true, buildFolder);

        assertThat(viewModel.getDynamic("overview")).isInstanceOf(OverviewModel.class);
        assertThat(viewModel.getDynamic("trend")).isInstanceOf(TrendModel.class);
        assertThat(viewModel.getDynamic("treemap")).isInstanceOf(TreeMapModel.class);
        assertThat(viewModel.getDynamic("scatter")).isInstanceOf(ScatterModel.class);
        assertThat(viewModel.getDynamic("table")).isInstanceOf(FilesModel.class);
        assertThat(viewModel.getDynamic("log")).isInstanceOf(LogModel.class);
        assertThat(viewModel.getDynamic("help")).isInstanceOf(HelpModel.class);
    }

    @ParameterizedTest(name = "Run tab enabled: {0}")
    @ValueSource(booleans = {true, false})
    void shouldResolveRemoteApiAndInfoUrlsIndependentOfRunTab(
            final boolean isRunTabEnabled, @TempDir final Path buildFolder) {
        var viewModel = createViewModel(isRunTabEnabled, buildFolder);

        assertThat(viewModel.getDynamic("files")).isInstanceOf(FileCoverageApiModel.class);
        assertThat(viewModel.getDynamic("modified")).isInstanceOf(ModifiedLinesCoverageApiModel.class);
        assertThat(viewModel.getDynamic("info")).isInstanceOfSatisfying(MessagesViewModel.class, messages -> {
            assertThat(messages.getInfoMessages()).contains(INFO_MESSAGE);
            assertThat(messages.getErrorMessages()).contains(ERROR_MESSAGE);
        });
    }

    @Test
    void shouldResolveFileDetailsByHashCodeIfRunTabIsEnabled(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(true, buildFolder);
        var fileNode = viewModel.getNode().getAllFileNodes().getFirst();

        assertThat(viewModel.getDynamic(
                        String.valueOf(fileNode.getRelativePath().hashCode())))
                .isInstanceOfSatisfying(FileDetailsModel.class, details -> {
                    assertThat(details.getPath()).isEqualTo(fileNode.getRelativePath());
                    assertThat(details.getDisplayName()).isEqualTo(fileNode.getName());
                    assertThat(details.getObject()).isSameAs(viewModel.getOwner());
                    assertThat(details.isSourceFileAvailable()).isFalse();
                });
    }

    @Test
    void shouldResolveSourceFileByHashCodeIfRunTabIsDisabled(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(false, buildFolder);
        var fileNode = viewModel.getNode().getAllFileNodes().getFirst();

        try (var sourceCode = mockStatic(SourceCodeViewModel.class)) {
            sourceCode
                    .when(() -> SourceCodeViewModel.protectedSourceCodeView(any(), any(), anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(viewModel.getDynamic(
                            String.valueOf(fileNode.getRelativePath().hashCode())))
                    .isInstanceOfSatisfying(SourceViewModel.class, view -> {
                        assertThat(view.getNode()).isSameAs(fileNode);
                        assertThat(view.getOwner()).isSameAs(viewModel.getOwner());
                        assertThat(view.getDisplayName()).isNotBlank();
                        assertThat(view.isSourceFileAvailable()).isFalse();
                    });
        }
    }

    @ParameterizedTest(name = "Run tab enabled: {0}")
    @ValueSource(booleans = {true, false})
    void shouldReturnNullForBrokenUrls(final boolean isRunTabEnabled, @TempDir final Path buildFolder) {
        var viewModel = createViewModel(isRunTabEnabled, buildFolder);

        assertThat(viewModel.getDynamic("")).isNull();
        assertThat(viewModel.getDynamic("does-not-exist")).isNull();
        assertThat(viewModel.getDynamic("0")).isNull();
        var packageNode = viewModel.getNode().getAll(Metric.PACKAGE).getFirst();
        assertThat(viewModel.getDynamic(String.valueOf(packageNode.getId().hashCode())))
                .as("Only file nodes should be resolved by hash code")
                .isNull();
    }

    @Test
    void shouldProvideOverviewModel(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(true, buildFolder);
        var build = viewModel.getOwner();
        var action = getAction(viewModel);
        var model = getDynamic(viewModel, "overview", OverviewModel.class);

        assertThat(model.getDisplayName()).isEqualTo(Messages.CoverageOverviewModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getUrlName()).isEqualTo(ID);
        assertThat(model.getOwner()).isSameAs(build);
        assertThat(model.getObject()).isSameAs(build);
        assertThat(model.getTab().getActions()).containsExactly(action);
        assertThat(model.getFormatter()).isNotNull();
        assertThat(model.getQualityGateResult()).isSameAs(action.getQualityGateResult());

        assertThat(model.getBaselines()).contains(Baseline.PROJECT);
        assertThat(model.hasBaselineResult(Baseline.PROJECT)).isTrue();
        assertThat(model.hasBaselineResult(Baseline.MODIFIED_LINES)).isFalse();
        assertThat(model.getTitle(Baseline.PROJECT)).isEqualTo(Baseline.PROJECT.getTitle());
        assertThat(model.getValues(Baseline.PROJECT)).isNotEmpty();
        assertThat(model.getAllValues(Baseline.PROJECT)).isNotEmpty();

        assertThat(model.getCoverageMetrics()).containsExactly(Metric.LINE, Metric.BRANCH, Metric.INSTRUCTION);
        assertThat(model.getCountMetrics()).isNotEmpty().noneMatch(OverviewModel::isCardMetric);

        assertThat(model.hasValue(Baseline.PROJECT, Metric.LINE)).isTrue();
        assertThat(model.hasValue(Baseline.MODIFIED_LINES, Metric.LINE)).isFalse();
        assertThat(model.formatValue(Baseline.PROJECT, Metric.LINE))
                .contains(String.valueOf(JACOCO_CODING_STYLE_COVERED), String.valueOf(JACOCO_CODING_STYLE_TOTAL));
        assertThat(model.getShortValue(Baseline.PROJECT, Metric.LINE)).endsWith("%");
        assertThat(model.getShortValue(Baseline.MODIFIED_LINES, Metric.LINE))
                .isEqualTo(Messages.Coverage_Not_Available());
        assertThat(model.getPercentage(Baseline.PROJECT, Metric.LINE)).isEqualTo("91.02");
        assertThat(model.getCoveredTotal(Baseline.PROJECT, Metric.LINE))
                .isEqualTo(JACOCO_CODING_STYLE_COVERED + " / " + JACOCO_CODING_STYLE_TOTAL);

        assertThat(model.hasDelta(Baseline.PROJECT, Metric.LINE)).isFalse();
        assertThat(model.getTrend(Baseline.PROJECT, Metric.LINE)).isZero();
        assertThat(model.formatDelta(Baseline.PROJECT, Metric.LINE)).isEqualTo(Messages.Coverage_Not_Available());

        assertThat(model.getReferenceBuild()).isEmpty();
        assertThat(model.getReferenceBuildText()).isEmpty();
        assertThat(model.getReferenceBuildUrl()).isEmpty();
    }

    @Test
    void shouldProvideTrendModel(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(true, buildFolder);
        var build = viewModel.getOwner();
        var action = getAction(viewModel);
        var model = getDynamic(viewModel, "trend", TrendModel.class);

        assertThat(model.getDisplayName()).isEqualTo(Messages.CoverageTrendModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(build);
        assertThat(model.getTab().getActions()).containsExactly(action);

        var chart = model.getTrendChart("{}");
        assertThatJson(chart).node("buildNumbers").isArray().containsExactly(BUILD_NUMBER);
        assertThatJson(chart).node("domainAxisLabels").isArray().containsExactly("#" + BUILD_NUMBER);
        assertThatJson(chart).inPath("$.series[*].name").isArray().contains("Line Coverage", "Branch Coverage");

        assertThat(model.hasCoverageMetrics()).isTrue();
        assertThat(model.hasSoftwareMetrics()).isTrue();
        assertThatJson(model.getTrendChart("{\"softwareMetrics\": false}"))
                .inPath("$.series[*].name")
                .isArray()
                .contains("Line Coverage", "Branch Coverage");
        assertThatJson(model.getTrendChart("{\"softwareMetrics\": true}"))
                .inPath("$.series[*].name")
                .isArray()
                .doesNotContain("Line Coverage", "Branch Coverage");
    }

    @Test
    void shouldProvideTreeMapModel(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(true, buildFolder);
        var build = viewModel.getOwner();
        var action = getAction(viewModel);
        var model = getDynamic(viewModel, "treemap", TreeMapModel.class);

        assertThat(model.getDisplayName()).isEqualTo(Messages.CoverageHierarchyModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(build);
        assertThat(model.getTab().getActions()).containsExactly(action);
        assertThat(model.getFormatter()).isNotNull();

        assertThat(model.getTreeMetrics()).contains(Metric.LINE, Metric.BRANCH).doesNotContain(Metric.INSTRUCTION);
        assertThat(model.getCoverageTreeMetrics())
                .contains(Metric.LINE, Metric.BRANCH)
                .allMatch(Metric::isCoverage);
        assertThat(model.getSoftwareTreeMetrics())
                .contains(Metric.CYCLOMATIC_COMPLEXITY)
                .noneMatch(Metric::isCoverage);
        assertThat(model.sharesOverviewThresholds(Metric.LINE)).isTrue();
        assertThat(model.sharesOverviewThresholds(Metric.CYCLOMATIC_COMPLEXITY)).isFalse();
        assertThat(model.getJenkinsColorIDs()).isNotEmpty();
        model.setJenkinsColors("broken JSON"); // falls back to the default colors

        var tree = model.getThresholdCoverageTree("line", "OVERALL", 80.0, 60.0);
        assertThat(tree.getName()).isEqualTo("Java coding style");
        assertThat(tree.getValue()).contains(String.valueOf(JACOCO_CODING_STYLE_TOTAL));
        assertThat(tree.getChildren())
                .hasSize(1)
                .first()
                .satisfies(child -> assertThat(child.getName()).isEqualTo("edu.hm.hafner.util"));

        assertThat(model.getMetricValueRange("line", "OVERALL"))
                .hasSize(2)
                .satisfies(range -> assertThat(range.getFirst()).isLessThanOrEqualTo(range.get(1)));
        assertThat(model.getMetricValueRange("mutation", "OVERALL")).containsExactly(0.0, 100.0);
        assertThat(model.getMetricValueRange("line", "MAXIMUM"))
                .isEqualTo(model.getMetricValueRange("line", "OVERALL"));
        assertThat(model.getMetricValueRange("file", "MAXIMUM"))
                .isEqualTo(model.getMetricValueRange("file", "OVERALL"));
        assertThat(model.getThresholdCoverageTree("line", "MINIMUM", 80.0, 60.0).getValue())
                .as("the size is derived from the overall value, the tooltip from the minimum")
                .first()
                .isEqualTo(tree.getValue().getFirst());
        assertThat(model.getThresholdCoverageTree("line", "MINIMUM", 80.0, 60.0).getValue())
                .last()
                .asString()
                .endsWith("(" + model.getFormatter().getDisplayName(MetricAggregation.MINIMUM) + ")");
    }

    @Test
    void shouldProvideScatterModel(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(true, buildFolder);
        var build = viewModel.getOwner();
        var action = getAction(viewModel);
        var model = getDynamic(viewModel, "scatter", ScatterModel.class);

        assertThat(model.getDisplayName()).isEqualTo(Messages.CoverageScatterModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(build);
        assertThat(model.getTab().getActions()).containsExactly(action);
        assertThat(model.getMetrics())
                .contains(Metric.LINE, Metric.BRANCH, Metric.CYCLOMATIC_COMPLEXITY)
                .noneMatch(Metric::isContainer);
    }

    @Test
    void shouldProvideFilesModel(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(true, buildFolder);
        var model = getDynamic(viewModel, "table", FilesModel.class);

        assertThat(model.getDisplayName()).isEqualTo(Messages.CoverageFilesModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(viewModel.getOwner());
        assertThat(model.getTab().getActions()).containsExactly(getAction(viewModel));
        assertThat(model.getFormatter()).isNotNull();

        assertThat(model.hasModifiedLinesCoverage()).isFalse();
        assertThat(model.hasIndirectCoverageChanges()).isFalse();

        var node = viewModel.getNode();
        assertThat(model.hasFileDeltas()).isFalse();
        assertThat(model.hasModifiedFiles()).isFalse();

        assertThat(model.getTableModel(CoverageViewModel.ABSOLUTE_COVERAGE_TABLE_ID))
                .isInstanceOf(FilesTableModel.class)
                .satisfies(table -> assertThat(table.getId()).isEqualTo(CoverageViewModel.ABSOLUTE_COVERAGE_TABLE_ID))
                .satisfies(table -> assertThat(table.getRows())
                        .hasSize(node.getAllFileNodes().size()));
        assertThat(model.getTableModel(CoverageViewModel.MODIFIED_LINES_COVERAGE_TABLE_ID))
                .isInstanceOf(FilesTableModel.class);
        assertThat(model.getTableModel(CoverageViewModel.INDIRECT_COVERAGE_TABLE_ID))
                .isInstanceOf(FilesTableModel.class);

        // All other links are delegated to the parent view model
        assertThat(model.getDynamic("info")).isInstanceOf(MessagesViewModel.class);
        assertThat(model.getDynamic("table")).isInstanceOf(FilesModel.class);
        assertThat(model.getDynamic("does-not-exist")).isNull();

        var fileNode = node.getAllFileNodes().getFirst();
        assertThat(model.getTableModel(CoverageViewModel.ABSOLUTE_COVERAGE_TABLE_ID)
                        .getRows())
                .first()
                .isInstanceOfSatisfying(
                        Map.class,
                        row -> assertThat(row.get("fileName"))
                                .isInstanceOfSatisfying(
                                        DetailedCell.class,
                                        cell -> assertThat(cell.getDisplay())
                                                .as("File names link to the details page of the file")
                                                .asString()
                                                .contains("href=\""
                                                        + fileNode.getRelativePath()
                                                                .hashCode() + "\"")));
    }

    @Test
    void shouldProvideLogModel(@TempDir final Path buildFolder) {
        var viewModel = createViewModel(true, buildFolder);
        var model = getDynamic(viewModel, "log", LogModel.class);

        assertThat(model.getDisplayName()).isEqualTo(Messages.CoverageInfoModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(viewModel.getOwner());
        assertThat(model.getTab().getActions()).containsExactly(getAction(viewModel));
        assertThat(model.getFormatter()).isNotNull();

        assertThat(model.getInfoMessages()).contains(INFO_MESSAGE);
        assertThat(model.getErrorMessages()).contains(ERROR_MESSAGE);
        assertThat(model.hasErrors()).isTrue();
    }

    private CoverageBuildAction getAction(final CoverageViewModel viewModel) {
        return viewModel.getOwner().getActions(CoverageBuildAction.class).getFirst();
    }

    private <T> T getDynamic(final CoverageViewModel viewModel, final String url, final Class<T> type) {
        var model = viewModel.getDynamic(url);

        assertThat(model).isInstanceOf(type);

        return type.cast(model);
    }

    /**
     * Creates a build with a single coverage action for the JaCoCo coding style report and returns the
     * {@link CoverageViewModel} of this action. The experimental flag for the new run tab is replaced by a stub that
     * returns the specified value.
     *
     * @param isRunTabEnabled
     *         determines whether the new run tab should be enabled
     * @param buildFolder
     *         the build folder of the created build
     *
     * @return the view model of the coverage action
     */
    private CoverageViewModel createViewModel(final boolean isRunTabEnabled, final Path buildFolder) {
        var node = readJacocoResult(JACOCO_CODING_STYLE_FILE);

        var log = new FilteredLog("Errors");
        log.logInfo(INFO_MESSAGE);
        log.logError(ERROR_MESSAGE);

        Run<?, ?> build = mock(Run.class);
        when(build.getNumber()).thenReturn(BUILD_NUMBER);
        when(build.getDisplayName()).thenReturn("#" + BUILD_NUMBER);
        when(build.getRootDir()).thenReturn(buildFolder.toFile());

        var action = new CoverageBuildAction(
                build,
                ID,
                "",
                "",
                node,
                new QualityGateResult(),
                log,
                "-",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);
        when(build.getActions(CoverageBuildAction.class)).thenReturn(List.of(action));

        var usePropertyFacade = mock(UsePropertyFacade.class);
        when(usePropertyFacade.isRunTabEnabled()).thenReturn(isRunTabEnabled);

        return action.getTarget(usePropertyFacade);
    }

    private CoverageBuildAction createAction(final String id, final QualityGateResult qualityGateResult) {
        var node = readJacocoResult(JACOCO_CODING_STYLE_FILE);
        return new CoverageBuildAction(
                mock(Run.class),
                id,
                "",
                "",
                node,
                qualityGateResult,
                new FilteredLog("Errors"),
                "-",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);
    }
}
