package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.line.LinesChartModel;

import java.io.IOException;
import java.util.List;

import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

import static io.jenkins.plugins.coverage.metrics.AbstractCoverageTest.*;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link CoverageJobAction}.
 *
 * @author Ullrich Hafner
 */
class CoverageJobActionTest {
    private static final String URL = "coverage";

    @Test
    void shouldIgnoreIndexIfNoActionFound() throws IOException {
        FreeStyleProject job = mock(FreeStyleProject.class);

        CoverageJobAction action = createAction(job);

        assertThat(action.getProject()).isSameAs(job);

        StaplerResponse2 response = mock(StaplerResponse2.class);
        action.doIndex(mock(StaplerRequest2.class), response);

        verifyNoInteractions(response);
    }

    private static CoverageJobAction createAction(final FreeStyleProject job) {
        return new CoverageJobAction(job, URL, "Coverage Results", StringUtils.EMPTY);
    }

    @Test
    void shouldNavigateToLastAction() throws IOException {
        FreeStyleBuild build = mock(FreeStyleBuild.class);

        CoverageBuildAction action = createBuildAction(build);

        when(build.getActions(CoverageBuildAction.class)).thenReturn(List.of(action));
        when(build.getNumber()).thenReturn(15);

        FreeStyleProject job = mock(FreeStyleProject.class);
        when(job.getLastBuild()).thenReturn(build);
        when(job.getUrl()).thenReturn(URL);

        CoverageJobAction jobAction = createAction(job);

        StaplerResponse2 response = mock(StaplerResponse2.class);
        jobAction.doIndex(mock(StaplerRequest2.class), response);

        verify(response).sendRedirect2("../15/coverage");
    }

    @Test
    void shouldCreateTrendChartForLineAndBranchCoverage() {
        FreeStyleBuild build = mock(FreeStyleBuild.class);

        CoverageBuildAction action = createBuildAction(build);
        when(build.getActions(CoverageBuildAction.class)).thenReturn(List.of(action));
        when(action.getStatistics()).thenReturn(createStatistics());

        int buildNumber = 15;
        when(build.getNumber()).thenReturn(buildNumber);
        when(build.getDisplayName()).thenReturn("#" + buildNumber);

        FreeStyleProject job = mock(FreeStyleProject.class);
        when(job.getLastBuild()).thenReturn(build);

        CoverageJobAction jobAction = createAction(job);

        LinesChartModel chart = jobAction.createChartModel("{}");

        assertThatJson(chart).node("buildNumbers").isArray().hasSize(1).containsExactly(buildNumber);
        assertThatJson(chart).node("domainAxisLabels").isArray().hasSize(1).containsExactly("#15");
        assertThatJson(chart).node("series").isArray().hasSize(2);

        assertThatJson(chart.getSeries().get(0)).satisfies(series -> {
            assertThatJson(series).node("name").isEqualTo("Line Coverage");
            assertThatJson(series).node("data").isArray().containsExactly("50.0");
        });
        assertThatJson(chart.getSeries().get(1)).satisfies(series -> {
            assertThatJson(series).node("name").isEqualTo("Branch Coverage");
            assertThatJson(series).node("data").isArray().containsExactly("90.0");
        });
    }

    private CoverageBuildAction createBuildAction(final FreeStyleBuild build) {
        CoverageBuildAction action = mock(CoverageBuildAction.class);
        when(action.getOwner()).thenAnswer(i -> build);
        when(action.getUrlName()).thenReturn(URL);
        when(action.getAllValues(any())).thenReturn(List.of(
                Value.nullObject(Metric.LINE),
                Value.nullObject(Metric.BRANCH)));
        return action;
    }
}
