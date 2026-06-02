package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import edu.hm.hafner.util.FilteredLog;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
    @Test
    void shouldAdjustReferenceBuildIfSelectedBuildHasNoAction() throws Throwable {
        Run<?, ?> currentBuild = createBuildWithAction("current-id", null);
        Run<?, ?> previousReferenceBuild = createBuildWithAction("coverage", null);
        Run<?, ?> selectedReferenceBuild = createBuildWithAction("other-id", previousReferenceBuild);
        var log = new FilteredLog("Errors");

        try (MockedConstruction<ReferenceFinder> mockedReferenceFinder = mockConstruction(
                ReferenceFinder.class,
                (mock, context) -> when(mock.findReference(eq(currentBuild), eq(log)))
                        .thenReturn(Optional.of(selectedReferenceBuild)))) {

            var action = invokeGetReferenceBuildAction(currentBuild, "coverage", log);

            assertThat(action).isPresent();
            assertThat(action).get()
                    .extracting(CoverageBuildAction::getUrlName)
                    .isEqualTo("coverage");

            assertThat(log.getInfoMessages())
                    .contains("-> Reference build information adjusted");

            assertThat(mockedReferenceFinder.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldHandleMissingPreviousBuildWhenSelectedReferenceHasNoAction() throws Throwable {
        Run<?, ?> currentBuild = createBuildWithAction("current-id", null);
        Run<?, ?> selectedReferenceBuild = createBuildWithAction("other-id", null);
        var log = new FilteredLog("Errors");

        try (MockedConstruction<ReferenceFinder> mockedReferenceFinder = mockConstruction(
                ReferenceFinder.class,
                (mock, context) -> when(mock.findReference(eq(currentBuild), eq(log)))
                        .thenReturn(Optional.of(selectedReferenceBuild)))) {

            var action = invokeGetReferenceBuildAction(currentBuild, "coverage", log);

            assertThat(action).isEmpty();

            assertThat(log.getInfoMessages())
                    .contains("-> Reference build has no action for ID 'coverage'");

            assertThat(log.getInfoMessages())
                    .doesNotContain("-> Reference build information adjusted");

            assertThat(mockedReferenceFinder.constructed()).hasSize(1);
        }
    }

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

        when(build.getActions(CoverageBuildAction.class))
                .thenReturn(List.of(action));

        when(build.getPreviousBuild())
                .thenAnswer(invocation -> previousBuild);

        return build;
    }

    private Optional<CoverageBuildAction> invokeGetReferenceBuildAction(
            final Run<?, ?> build,
            final String id,
            final FilteredLog log) throws Throwable {

        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                CoverageReporter.class,
                MethodHandles.lookup());

        MethodHandle methodHandle = lookup.findVirtual(
                CoverageReporter.class,
                "getReferenceBuildAction",
                MethodType.methodType(
                        Optional.class,
                        Run.class,
                        String.class,
                        FilteredLog.class));

        return (Optional<CoverageBuildAction>) methodHandle.invoke(
                new CoverageReporter(),
                build,
                id,
                log);
    }
}