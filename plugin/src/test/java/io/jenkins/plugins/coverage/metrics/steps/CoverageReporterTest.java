package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import java.util.List;

import hudson.model.Run;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link CoverageReporter}.
 * 
 * @author Akash Manna
 */
class CoverageReporterTest {
    @Test
    void shouldFindActionInCurrentBuild() {
        Run<?, ?> build = createBuildWithAction("coverage", null);

        var action = CoverageReporter.findActionInBuildHistory("coverage", build);

        assertThat(action).isPresent();
    }

    @Test
    void shouldFindActionInPreviousBuilds() {
        Run<?, ?> oldestBuild = createBuildWithAction("coverage", null);
        Run<?, ?> middleBuild = createBuildWithAction("other-id", oldestBuild);
        Run<?, ?> latestBuild = createBuildWithAction("another-id", middleBuild);

        var action = CoverageReporter.findActionInBuildHistory("coverage", latestBuild);

        assertThat(action).isPresent();
        assertThat(action.get().getUrlName()).isEqualTo("coverage");
    }

    @Test
    void shouldReturnEmptyIfNoMatchingActionExists() {
        Run<?, ?> oldestBuild = createBuildWithAction("first", null);
        Run<?, ?> latestBuild = createBuildWithAction("second", oldestBuild);

        var action = CoverageReporter.findActionInBuildHistory("coverage", latestBuild);

        assertThat(action).isEmpty();
    }

    private Run<?, ?> createBuildWithAction(final String id, final Run<?, ?> previousBuild) {
        Run<?, ?> build = mock(Run.class);
        CoverageBuildAction action = mock(CoverageBuildAction.class);
        when(action.getUrlName()).thenReturn(id);
        when(build.getActions(CoverageBuildAction.class)).thenReturn(List.of(action));
        when(build.getPreviousBuild()).thenAnswer(invocation -> previousBuild);
        return build;
    }
}
