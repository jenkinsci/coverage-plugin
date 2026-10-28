package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.FileDeltas;
import edu.hm.hafner.coverage.Metric;
import io.jenkins.plugins.coverage.metrics.AbstractModifiedFilesCoverageTest;
import java.util.AbstractMap.SimpleEntry;
import org.junit.jupiter.api.Test;

/**
 * Test class for {@link FileChangesProcessor}.
 *
 * @author Florian Orendi
 */
class FileChangesProcessorTest extends AbstractModifiedFilesCoverageTest {
    @Test
    void shouldAttachChangedCodeLines() {
        var changes = createChangeSet(createCoverageTree());

        assertThat(changes.getModifiedLines(getPathOfFileWithModifiedLines()))
                .containsExactly(5, 6, 7, 8, 9, 14, 15, 16, 17, 18, 20, 21, 22, 33, 34, 35, 36);
        assertThat(changes.getModifiedLines(getNameOfFileWithoutModifiedLines()))
                .isEmpty();
    }

    @Test
    void shouldAttachFileCoverageDelta() {
        var deltas = createFileDeltas(createCoverageTree());

        verifyFileCoverageDeltaOfTestFile1(deltas);
    }

    @Test
    void shouldAttachIndirectCoverageChanges() {
        var changes = createChangeSet(createCoverageTree());

        assertThat(changes.getIndirectCoverageChanges(getPathOfFileWithModifiedLines()))
                .containsExactly(new SimpleEntry<>(11, -1), new SimpleEntry<>(29, -1), new SimpleEntry<>(31, 1));
    }

    /**
     * Verifies the file coverage delta of {@link #getPathOfFileWithModifiedLines() the modified file}.
     *
     * @param deltas
     *         The coverage deltas of the files
     */
    private void verifyFileCoverageDeltaOfTestFile1(final FileDeltas deltas) {
        var path = getPathOfFileWithModifiedLines();
        assertThat(deltas.getDelta(path, Metric.LINE)).isEqualTo(new Difference(Metric.LINE, 100, 39));
        assertThat(deltas.getDelta(path, Metric.BRANCH)).isEqualTo(new Difference(Metric.BRANCH, 25, 2));
        assertThat(deltas.getDelta(path, Metric.INSTRUCTION)).isEqualTo(new Difference(Metric.INSTRUCTION, 1000, 111));
        assertThat(deltas.getDelta(path, Metric.METHOD)).isEqualTo(new Difference(Metric.METHOD, -40, 3));
        assertThat(deltas.getDelta(path, Metric.CLASS)).isEqualTo(Difference.nullObject(Metric.CLASS));
        assertThat(deltas.getDelta(path, Metric.FILE)).isEqualTo(Difference.nullObject(Metric.FILE));
    }
}
