package io.jenkins.plugins.coverage.metrics.steps;

import java.util.AbstractMap.SimpleEntry;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;

import io.jenkins.plugins.coverage.metrics.AbstractModifiedFilesCoverageTest;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for {@link FileChangesProcessor}.
 *
 * @author Florian Orendi
 */
class FileChangesProcessorTest extends AbstractModifiedFilesCoverageTest {
    @Test
    void shouldAttachChangedCodeLines() {
        var tree = createCoverageTree();

        assertThat(tree.findByHashCode(Metric.FILE, getPathOfFileWithModifiedLines().hashCode()))
                .isNotEmpty()
                .satisfies(node -> assertThat(node.get())
                        .isInstanceOfSatisfying(FileNode.class, f -> assertThat(f.getModifiedLines())
                                .containsExactly(
                                        5, 6, 7, 8, 9, 14, 15, 16, 17, 18, 20, 21, 22, 33, 34, 35, 36)));
        assertThat(tree.findByHashCode(Metric.FILE, getNameOfFileWithoutModifiedLines().hashCode()))
                .isNotEmpty()
                .satisfies(node -> assertThat(node.get())
                        .isInstanceOfSatisfying(FileNode.class, f -> assertThat(f.getModifiedLines())
                                .isEmpty()));
    }

    @Test
    void shouldAttachFileCoverageDelta() {
        var tree = createCoverageTree();

        assertThat(tree.findByHashCode(Metric.FILE, getPathOfFileWithModifiedLines().hashCode()))
                .isNotEmpty()
                .satisfies(node -> {
                    assertThat(node.get()).isInstanceOf(FileNode.class);
                    verifyFileCoverageDeltaOfTestFile1((FileNode) node.get());
                });
    }

    @Test
    void shouldAttachIndirectCoverageChanges() {
        var tree = createCoverageTree();

        assertThat(tree.findByHashCode(Metric.FILE, getPathOfFileWithModifiedLines().hashCode()))
                .isNotEmpty()
                .satisfies(node -> {
                    assertThat(node.get()).isInstanceOf(FileNode.class);
                    FileNode file = (FileNode) node.get();
                    assertThat(file.getIndirectCoverageChanges()).containsExactly(
                            new SimpleEntry<>(11, -1),
                            new SimpleEntry<>(29, -1),
                            new SimpleEntry<>(31, 1)
                    );
                });
    }

    /**
     * Verifies the file coverage delta of {@link #getPathOfFileWithModifiedLines() the modified file}.
     *
     * @param file
     *         The referencing coverage tree {@link FileNode node}
     */
    private void verifyFileCoverageDeltaOfTestFile1(final FileNode file) {
        assertThat(file.getName()).isEqualTo(getNameOfFileWithModifiedLines());
        assertThat(file.getDelta(Metric.LINE)).isEqualTo(new Difference(Metric.LINE, 100, 39));
        assertThat(file.getDelta(Metric.BRANCH)).isEqualTo(new Difference(Metric.BRANCH, 25, 2));
        assertThat(file.getDelta(Metric.INSTRUCTION)).isEqualTo(new Difference(Metric.INSTRUCTION, 1000, 111));
        assertThat(file.getDelta(Metric.METHOD)).isEqualTo(new Difference(Metric.METHOD, -40, 3));
        assertThat(file.getDelta(Metric.CLASS)).isEqualTo(Difference.nullObject(Metric.CLASS));
        assertThat(file.getDelta(Metric.FILE)).isEqualTo(Difference.nullObject(Metric.FILE));
    }
}
