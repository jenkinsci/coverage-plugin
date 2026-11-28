package io.jenkins.plugins.coverage.metrics.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.ChangeSet.ChangeSetBuilder;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.FileNodeFixture;
import edu.hm.hafner.coverage.Mutation;
import edu.hm.hafner.coverage.Mutation.MutationBuilder;
import edu.hm.hafner.coverage.MutationStatus;
import io.jenkins.plugins.prism.LineAnnotation;
import io.jenkins.plugins.prism.Marker;
import io.jenkins.plugins.prism.Marker.Severity;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link CoverageMarkerFactory}.
 *
 * @author Ullrich Hafner
 */
class CoverageMarkerFactoryTest {
    private static final String PATH = "src/main/java/Example.java";

    /**
     * Verifies that consecutive lines with the same coverage are combined into a single marker without a box. Only
     * the blocks that are not completely covered are part of the navigation.
     */
    @Test
    void shouldCombineConsecutiveLinesWithTheSameCoverage() {
        var coverage = new CoverageMarkerFactory().create(createFile(), ChangeSet.empty());

        assertThat(coverage.markers())
                .extracting(
                        Marker::getId,
                        Marker::getLineStart,
                        Marker::getLineEnd,
                        Marker::getSeverity,
                        Marker::isNavigable,
                        Marker::getTitle)
                .containsExactly(
                        tuple("project-L1-2", 1, 2, Severity.SUCCESS, false, ""),
                        tuple("project-L3-4", 3, 4, Severity.ERROR, true, ""),
                        tuple("project-L6-6", 6, 6, Severity.ERROR, true, ""),
                        tuple("project-L7-7", 7, 7, Severity.WARNING, true, ""));
        assertThat(coverage.markers().get(2).getData())
                .containsEntry("coverage", "missed")
                .containsEntry("baseline", "project")
                .containsEntry("modified", "false");
    }

    /**
     * Verifies that the blocks are split at the boundaries of modified lines, so the modified lines can be filtered
     * on the client side.
     */
    @Test
    void shouldSplitBlocksAtModifiedLines() {
        var changes = new ChangeSetBuilder().addModifiedLines(PATH, 2, 3).build();

        var coverage = new CoverageMarkerFactory().create(createFile(), changes);

        assertThat(coverage.markers())
                .extracting(
                        Marker::getLineStart,
                        Marker::getLineEnd,
                        marker -> marker.getData().get("modified"))
                .containsExactly(
                        tuple(1, 1, "false"),
                        tuple(2, 2, "true"),
                        tuple(3, 3, "true"),
                        tuple(4, 4, "false"),
                        tuple(6, 6, "false"),
                        tuple(7, 7, "false"));
    }

    /**
     * Verifies that the indirect coverage changes are shown by additional markers of the baseline "indirect".
     */
    @Test
    void shouldCreateMarkersForIndirectCoverageChanges() {
        var changes = new ChangeSetBuilder()
                .addIndirectCoverageChange(PATH, 1, 1)
                .addIndirectCoverageChange(PATH, 2, 2)
                .addIndirectCoverageChange(PATH, 4, -1)
                .build();

        var coverage = new CoverageMarkerFactory().create(createFile(), changes);

        assertThat(coverage.markers())
                .filteredOn(marker -> "indirect".equals(marker.getData().get("baseline")))
                .extracting(
                        Marker::getId,
                        Marker::getSeverity,
                        Marker::isNavigable,
                        marker -> marker.getData().get("coverage"))
                .containsExactly(
                        tuple("indirect-L1-2", Severity.SUCCESS, true, "increased"),
                        tuple("indirect-L4-4", Severity.ERROR, true, "decreased"));
        assertThat(coverage.annotations())
                .filteredOn(annotation -> annotation.line() == 4)
                .extracting(LineAnnotation::tooltip)
                .containsExactly("Not covered, Indirect coverage change: -1");
    }

    /**
     * Verifies that each line with coverage gets an annotation with the number of covered branches.
     */
    @Test
    void shouldCreateAnnotationsForLinesWithCoverage() {
        var file = createFile();
        new FileNodeFixture(file).withCounters(9, 2, 0).withCounters(10, 0, 3);

        var coverage = new CoverageMarkerFactory().create(file, ChangeSet.empty());

        assertThat(coverage.annotations())
                .extracting(LineAnnotation::line, LineAnnotation::text, LineAnnotation::tooltip)
                .containsExactly(
                        tuple(1, "1", "Covered at least once"),
                        tuple(2, "1", "Covered at least once"),
                        tuple(3, "0", "Not covered"),
                        tuple(4, "0", "Not covered"),
                        tuple(6, "0", "Not covered"),
                        tuple(7, "1/2", "Partially covered: 1 of 2 branches covered"),
                        tuple(9, "2/2", "All 2 branches covered"),
                        tuple(10, "0/3", "None of 3 branches covered"));
    }

    /**
     * Verifies that lines with mutations that have not been killed get a box with all mutations of the line.
     */
    @Test
    void shouldCreateBoxesForMutationsThatHaveNotBeenKilled() {
        var file = new FileNode("Example.java", PATH);
        new FileNodeFixture(file)
                .withCounters(1, 1, 0)
                .withCounters(2, 1, 0)
                .withCounters(3, 0, 1)
                .withMutation(createMutation(1, MutationStatus.KILLED, "Killed <b>one</b>"))
                .withMutation(createMutation(2, MutationStatus.KILLED, "Killed two"))
                .withMutation(createMutation(2, MutationStatus.SURVIVED, "Survived two"))
                .withMutation(createMutation(3, MutationStatus.NO_COVERAGE, "Not covered three"));

        var coverage = new CoverageMarkerFactory().create(file, ChangeSet.empty());

        assertThat(coverage.markers())
                .filteredOn(marker -> "mutation".equals(marker.getData().get("coverage")))
                .extracting(Marker::getId, Marker::getLineStart, Marker::getSeverity, Marker::getTitle)
                .containsExactly(
                        tuple("mutations-L2", 2, Severity.WARNING, "Mutations: 1 killed, 1 survived, 0 not covered"),
                        tuple("mutations-L3", 3, Severity.ERROR, "Mutations: 0 killed, 0 survived, 1 not covered"));
        assertThat(coverage.markers())
                .filteredOn(marker -> "mutations-L2".equals(marker.getId()))
                .extracting(Marker::getDescription)
                .containsExactly("<ul><li><b>Killed</b>: Killed two (Mutator)</li>"
                        + "<li><b>Survived</b>: Survived two (Mutator)</li></ul>");
    }

    /**
     * Verifies that HTML in the description of a mutation is escaped.
     */
    @Test
    void shouldEscapeDescriptionOfMutations() {
        var file = new FileNode("Example.java", PATH);
        new FileNodeFixture(file)
                .withCounters(1, 0, 1)
                .withMutation(createMutation(1, MutationStatus.SURVIVED, "Replaced <script>"));

        var coverage = new CoverageMarkerFactory().create(file, ChangeSet.empty());

        assertThat(coverage.markers())
                .filteredOn(marker -> "mutations-L1".equals(marker.getId()))
                .extracting(Marker::getDescription)
                .containsExactly("<ul><li><b>Survived</b>: Replaced &lt;script&gt; (Mutator)</li></ul>");
    }

    /**
     * Verifies that a file without coverage has neither markers nor annotations.
     */
    @Test
    void shouldCreateNothingForFileWithoutCoverage() {
        var coverage = new CoverageMarkerFactory().create(new FileNode("Example.java", PATH), ChangeSet.empty());

        assertThat(coverage.markers()).isEmpty();
        assertThat(coverage.annotations()).isEmpty();
    }

    /**
     * Creates a file with the lines 1-2 (covered), 3-4 (not covered), 6 (not covered), and 7 (partially covered).
     *
     * @return the file
     */
    private FileNode createFile() {
        var file = new FileNode("Example.java", PATH);
        new FileNodeFixture(file)
                .withCounters(1, 1, 0)
                .withCounters(2, 1, 0)
                .withCounters(3, 0, 1)
                .withCounters(4, 0, 1)
                .withCounters(6, 0, 1)
                .withCounters(7, 1, 1);
        return file;
    }

    private Mutation createMutation(final int line, final MutationStatus status, final String description) {
        return new MutationBuilder()
                .withLine(line)
                .withStatus(status)
                .withMutator("Mutator")
                .withDescription(description)
                .build();
    }
}
