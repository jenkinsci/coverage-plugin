package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import java.util.List;
import java.util.Optional;

import hudson.model.Run;

import io.jenkins.plugins.forensics.reference.ReferenceFinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link CoverageReporter}.
 *
 * @author Akash Manna
 */
class CoverageReporterTest {
    private CoverageReporter createReporterWithReferenceFinder(final ReferenceFinder referenceFinder) {
        return new CoverageReporter() {
            @Override
            ReferenceFinder createReferenceFinder() {
                return referenceFinder;
            }
        };
    }

    @Test
    void shouldAdjustReferenceBuildIfSelectedBuildHasNoAction() {
        Run<?, ?> currentBuild = createBuildWithAction("current-id");
        Run<?, ?> previousReferenceBuild = createBuildWithAction("coverage");
        Run<?, ?> selectedReferenceBuild = createBuildWithActionAndPreviousBuild("other-id", previousReferenceBuild);
        var log = new FilteredLog("Errors");

        Run<?, ?> ownerBuild = mock(Run.class);
        when(ownerBuild.toString()).thenReturn("#42");
        CoverageBuildAction adjustedAction = previousReferenceBuild.getActions(CoverageBuildAction.class).get(0);
        doReturn(ownerBuild).when(adjustedAction).getOwner();

        var referenceFinder = mock(ReferenceFinder.class);
        when(referenceFinder.findReference(eq(currentBuild), eq(log)))
                .thenReturn(Optional.of(selectedReferenceBuild));

        var reporter = createReporterWithReferenceFinder(referenceFinder);
        var action = reporter.getReferenceBuildAction(currentBuild, "coverage", log);

        assertThat(action).isPresent();
        assertThat(action).get()
                .extracting(CoverageBuildAction::getUrlName)
                .isEqualTo("coverage");

        assertThat(log.getInfoMessages())
                .contains("-> Reference build information adjusted to '#42'");
    }

    @Test
    void shouldHandleMissingPreviousBuildWhenSelectedReferenceHasNoAction() {
        Run<?, ?> currentBuild = createBuildWithAction("current-id");
        Run<?, ?> selectedReferenceBuild = createBuildWithAction("other-id");
        var log = new FilteredLog("Errors");

        var referenceFinder = mock(ReferenceFinder.class);
        when(referenceFinder.findReference(eq(currentBuild), eq(log)))
                .thenReturn(Optional.of(selectedReferenceBuild));

        var reporter = createReporterWithReferenceFinder(referenceFinder);
        var action = reporter.getReferenceBuildAction(currentBuild, "coverage", log);

        assertThat(action).isEmpty();

        assertThat(log.getInfoMessages())
                .contains("-> Reference build has no action for ID 'coverage'");

        assertThat(log.getInfoMessages())
                .noneMatch(msg -> msg.startsWith("-> Reference build information adjusted"));
    }

    @Test
    void shouldFindActionInCurrentBuild() {
        Run<?, ?> build = createBuildWithAction("coverage");

        var action = CoverageReporter.findActionInBuildHistory("coverage", build);

        assertThat(action).isPresent();
    }

    @Test
    void shouldFindActionInPreviousBuilds() {
        Run<?, ?> oldestBuild = createBuildWithAction("coverage");
        Run<?, ?> middleBuild = createBuildWithActionAndPreviousBuild("other-id", oldestBuild);
        Run<?, ?> latestBuild = createBuildWithActionAndPreviousBuild("another-id", middleBuild);

        var action = CoverageReporter.findActionInBuildHistory("coverage", latestBuild);

        assertThat(action).isPresent();
        assertThat(action.get().getUrlName()).isEqualTo("coverage");
    }

    @Test
    void shouldReturnEmptyIfNoMatchingActionExists() {
        Run<?, ?> oldestBuild = createBuildWithAction("first");
        Run<?, ?> latestBuild = createBuildWithActionAndPreviousBuild("second", oldestBuild);

        var action = CoverageReporter.findActionInBuildHistory("coverage", latestBuild);

        assertThat(action).isEmpty();
    }

    private Run<?, ?> createBuildWithAction(final String id) {
        Run<?, ?> build = mock(Run.class);

        CoverageBuildAction action = mock(CoverageBuildAction.class);
        when(action.getUrlName()).thenReturn(id);

        when(build.getActions(CoverageBuildAction.class))
                .thenReturn(List.of(action));

        return build;
    }

    private Run<?, ?> createBuildWithActionAndPreviousBuild(final String id, final Run<?, ?> previousBuild) {
        Run<?, ?> build = createBuildWithAction(id);

        when(build.getPreviousBuild())
                .thenAnswer(invocation -> previousBuild);

        return build;
    }
}