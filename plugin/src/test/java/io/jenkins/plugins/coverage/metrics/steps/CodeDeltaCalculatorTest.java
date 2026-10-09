package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

import io.jenkins.plugins.forensics.delta.Delta;
import io.jenkins.plugins.forensics.delta.FileChanges;
import io.jenkins.plugins.forensics.delta.FileEditType;

import static io.jenkins.plugins.coverage.metrics.steps.CodeDeltaCalculator.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link CodeDeltaCalculator}.
 *
 * @author Florian Orendi
 */
class CodeDeltaCalculatorTest {
    private static final String LOG_NAME = "Errors while calculating changes mapping:";

    private static final String EMPTY_PATH = "";

    private static final String OLD_SCM_PATH_RENAME =
            Path.of("src", "main", "example", "Test.java").toString();
    private static final String OLD_REPORT_PATH_RENAME =
            Path.of("example", "Test.java").toString();

    private static final String REPORT_PATH_ADD_1 =
            Path.of("test", "Test.java").toString();
    private static final String REPORT_PATH_ADD_2 =
            Path.of("package", "example", "test", "Test.java").toString();
    private static final String REPORT_PATH_MODIFY =
            Path.of("example", "test", "Test.java").toString();
    private static final String REPORT_PATH_RENAME =
            Path.of("example", "Test_Renamed.java").toString();

    private static final String SCM_PATH_ADD_1 =
            Path.of("test", "Test.java").toString();
    private static final String SCM_PATH_ADD_2 =
            Path.of("src", "package", "example", "test", "Test.java").toString();
    private static final String SCM_PATH_MODIFY =
            Path.of("src", "main", "java", "example", "test", "Test.java").toString();
    private static final String SCM_PATH_DELETE =
            Path.of("src", "main", "example", "test", "Test.java").toString();
    private static final String SCM_PATH_RENAME =
            Path.of("src", "main", "example", "Test_Renamed.java").toString();

    @Test
    void shouldGetCoverageRelevantChanges() {
        var codeDeltaCalculator = createCodeDeltaCalculator();
        var delta = createDeltaWithStubbedFileChanges();
        Map<String, FileChanges> allChanges = delta.getFileChangesMap();

        assertThat(codeDeltaCalculator.getCoverageRelevantChanges(delta))
                .containsExactlyInAnyOrder(
                        allChanges.get(SCM_PATH_ADD_1),
                        allChanges.get(SCM_PATH_ADD_2),
                        allChanges.get(SCM_PATH_MODIFY),
                        allChanges.get(SCM_PATH_RENAME)
                );
    }

    @Test
    void shouldMapScmChangesToReportPaths() throws IllegalStateException {
        var codeDeltaCalculator = createCodeDeltaCalculator();
        var delta = createDeltaWithStubbedFileChanges();
        Set<FileChanges> changes = codeDeltaCalculator.getCoverageRelevantChanges(delta);
        Map<String, FileChanges> changesMap = changes.stream()
                .collect(Collectors.toMap(FileChanges::getFileName, Function.identity()));
        var tree = createStubbedCoverageTree();
        var log = createFilteredLog();

        Map<String, FileChanges> should = new HashMap<>();
        should.put(REPORT_PATH_ADD_1, changesMap.get(SCM_PATH_ADD_1));
        should.put(REPORT_PATH_ADD_2, changesMap.get(SCM_PATH_ADD_2));
        should.put(REPORT_PATH_MODIFY, changesMap.get(SCM_PATH_MODIFY));
        should.put(REPORT_PATH_RENAME, changesMap.get(SCM_PATH_RENAME));

        assertThat(codeDeltaCalculator.mapScmChangesToReportPaths(changes, tree, log))
                .containsExactlyInAnyOrderEntriesOf(should);
    }

    @Test
    void shouldCreateEmptyMappingWithoutChanges() throws IllegalStateException {
        var codeDeltaCalculator = createCodeDeltaCalculator();
        var tree = createStubbedCoverageTree();
        var log = createFilteredLog();
        Set<FileChanges> noChanges = new HashSet<>();

        assertThat(codeDeltaCalculator.mapScmChangesToReportPaths(noChanges, tree, log)).isEmpty();
    }

    @Test
    void shouldNotMapScmChangesWithAmbiguousPaths() throws IllegalStateException {
        var codeDeltaCalculator = createCodeDeltaCalculator();
        var log = createFilteredLog();

        var path = "example";
        Set<FileChanges> changes = createAmbiguousFileChanges(path);

        Node tree = mock(Node.class);
        FileNode file1 = mock(FileNode.class);
        when(file1.getRelativePath()).thenReturn(path);
        when(tree.getAllFileNodes()).thenReturn(List.of(file1));
        when(tree.getFiles()).thenReturn(Set.of(path));

        assertThatThrownBy(() -> codeDeltaCalculator.mapScmChangesToReportPaths(changes, tree, log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(AMBIGUOUS_PATHS_ERROR);
    }

    @Test
    void shouldCreateOldPathMapping() throws IllegalStateException {
        var codeDeltaCalculator = createCodeDeltaCalculator();
        var log = createFilteredLog();
        var tree = createStubbedCoverageTree();
        var referenceTree = createStubbedReferenceCoverageTree();
        Map<String, FileChanges> changes = new HashMap<>();
        changes.put(REPORT_PATH_MODIFY, createFileChanges(SCM_PATH_MODIFY, SCM_PATH_MODIFY, FileEditType.MODIFY));
        changes.put(REPORT_PATH_RENAME, createFileChanges(SCM_PATH_RENAME, OLD_SCM_PATH_RENAME, FileEditType.RENAME));

        Map<String, String> should = new HashMap<>();
        should.put(REPORT_PATH_MODIFY, REPORT_PATH_MODIFY);
        should.put(REPORT_PATH_RENAME, OLD_REPORT_PATH_RENAME);

        assertThat(codeDeltaCalculator.createOldPathMapping(tree, referenceTree, changes, log))
                .containsExactlyInAnyOrderEntriesOf(should);
    }

    @Test
    void shouldNotCreateOldPathMappingWithMissingReferenceNodes() throws IllegalStateException {
        var codeDeltaCalculator = createCodeDeltaCalculator();
        var log = createFilteredLog();

        var tree = new FileNode("Test_Renamed.java", REPORT_PATH_RENAME);
        var referenceTree = new FileNode("Test.java", REPORT_PATH_MODIFY);
        Map<String, FileChanges> changes = new HashMap<>();
        changes.put(REPORT_PATH_RENAME, createFileChanges(SCM_PATH_RENAME, OLD_SCM_PATH_RENAME, FileEditType.RENAME));

        assertThat(codeDeltaCalculator.createOldPathMapping(tree, referenceTree, changes, log)).isEmpty();
        assertThat(log.getInfoMessages()).contains(
                EMPTY_OLD_PATHS_WARNING + System.lineSeparator() + REPORT_PATH_RENAME
        );
    }

    // checks the functionality to prevent exceptions in case of false calculated code deltas
    @Test
    void shouldNotCreateOldPathMappingWithCodeDeltaMismatches() {
        var codeDeltaCalculator = createCodeDeltaCalculator();
        var log = createFilteredLog();
        var tree = createStubbedCoverageTree();
        var referenceTree = createStubbedReferenceCoverageTree();

        // two changes with the same former path
        Map<String, FileChanges> changes = new HashMap<>();
        changes.put(REPORT_PATH_RENAME, createFileChanges(SCM_PATH_RENAME, OLD_SCM_PATH_RENAME, FileEditType.RENAME));
        changes.put(REPORT_PATH_MODIFY, createFileChanges(REPORT_PATH_MODIFY, OLD_SCM_PATH_RENAME, FileEditType.RENAME));

        assertThatThrownBy(() -> codeDeltaCalculator.createOldPathMapping(tree, referenceTree, changes, log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith(CODE_DELTA_TO_COVERAGE_DATA_MISMATCH_ERROR_TEMPLATE)
                .hasMessageContainingAll(
                "new: '%s' - former: '%s',".formatted(REPORT_PATH_RENAME, OLD_REPORT_PATH_RENAME),
                "new: '%s' - former: '%s'".formatted(REPORT_PATH_MODIFY, OLD_REPORT_PATH_RENAME));
    }

    /**
     * Creates an instance of {@link CodeDeltaCalculator}.
     *
     * @return the created instance
     */
    private CodeDeltaCalculator createCodeDeltaCalculator() {
        return new CodeDeltaCalculator(mock(Run.class), mock(FilePath.class),
                mock(TaskListener.class), "");
    }

    private Delta createDeltaWithStubbedFileChanges() {
        Delta delta = mock(Delta.class);
        Map<String, FileChanges> fileChanges = new HashMap<>();
        var fileChangesAdd1 = createFileChanges(SCM_PATH_ADD_1, EMPTY_PATH, FileEditType.ADD);
        var fileChangesAdd2 = createFileChanges(SCM_PATH_ADD_2, EMPTY_PATH, FileEditType.ADD);
        var fileChangesModify = createFileChanges(SCM_PATH_MODIFY, SCM_PATH_MODIFY, FileEditType.MODIFY);
        var fileChangesDelete = createFileChanges(EMPTY_PATH, SCM_PATH_DELETE, FileEditType.DELETE);
        var fileChangesRename = createFileChanges(SCM_PATH_RENAME, OLD_SCM_PATH_RENAME, FileEditType.RENAME);
        fileChanges.put(fileChangesAdd1.getFileName(), fileChangesAdd1);
        fileChanges.put(fileChangesAdd2.getFileName(), fileChangesAdd2);
        fileChanges.put(fileChangesModify.getFileName(), fileChangesModify);
        fileChanges.put(fileChangesDelete.getOldFileName(), fileChangesDelete);
        fileChanges.put(fileChangesRename.getFileName(), fileChangesRename);
        when(delta.getFileChangesMap()).thenReturn(fileChanges);
        return delta;
    }

    /**
     * Creates a set of {@link FileChanges} with ambiguous paths.
     *
     * @param path
     *         The ambiguous path
     *
     * @return the set of changes
     */
    private Set<FileChanges> createAmbiguousFileChanges(final String path) {
        FileChanges change1 = mock(FileChanges.class);
        when(change1.getFileName()).thenReturn(Path.of("src", path).toString());
        FileChanges change2 = mock(FileChanges.class);
        when(change2.getFileName()).thenReturn(Path.of("main", path).toString());
        Set<FileChanges> changes = new HashSet<>();
        changes.add(change1);
        changes.add(change2);
        return changes;
    }

    /**
     * Creates a stub of {@link FileChanges}.
     *
     * @param filePath
     *         The file path
     * @param oldFilePath
     *         The old file path before the modifications
     * @param fileEditType
     *         The {@link FileEditType edit type}
     *
     * @return the created mock
     */
    private FileChanges createFileChanges(final String filePath, final String oldFilePath,
            final FileEditType fileEditType) {
        FileChanges change = mock(FileChanges.class);
        when(change.getFileEditType()).thenReturn(fileEditType);
        when(change.getFileName()).thenReturn(filePath);
        when(change.getOldFileName()).thenReturn(oldFilePath);
        return change;
    }

    /**
     * Mocks a coverage tree which contains file nodes which represent {@link #REPORT_PATH_ADD_1}, {@link
     * #REPORT_PATH_ADD_2} and {@link #REPORT_PATH_MODIFY}.
     *
     * @return the {@link Node root} of the tree
     */
    private Node createStubbedCoverageTree() {
        FileNode addFile1 = mock(FileNode.class);
        when(addFile1.getRelativePath()).thenReturn(REPORT_PATH_ADD_1);
        FileNode addFile2 = mock(FileNode.class);
        when(addFile2.getRelativePath()).thenReturn(REPORT_PATH_ADD_2);
        FileNode modifyFile = mock(FileNode.class);
        when(modifyFile.getRelativePath()).thenReturn(REPORT_PATH_MODIFY);
        FileNode renameFile = mock(FileNode.class);
        when(renameFile.getRelativePath()).thenReturn(REPORT_PATH_RENAME);
        Node root = mock(Node.class);
        when(root.getAllFileNodes()).thenReturn(Arrays.asList(addFile1, addFile2, modifyFile, renameFile));
        var files = root.getAllFileNodes().stream().map(FileNode::getRelativePath).collect(Collectors.toSet());
        when(root.getFiles()).thenReturn(files);

        return root;
    }

    /**
     * Mocks a reference coverage tree which contains file nodes which represent {@link #OLD_REPORT_PATH_RENAME} and
     * {@link #REPORT_PATH_MODIFY}.
     *
     * @return the {@link Node root} of the tree
     */
    private Node createStubbedReferenceCoverageTree() {
        FileNode modifyFile = mock(FileNode.class);
        when(modifyFile.getRelativePath()).thenReturn(REPORT_PATH_MODIFY);
        FileNode renameFile = mock(FileNode.class);
        when(renameFile.getRelativePath()).thenReturn(OLD_REPORT_PATH_RENAME);
        Node root = mock(Node.class);
        when(root.getAllFileNodes()).thenReturn(Arrays.asList(renameFile, modifyFile));
        var files = root.getAllFileNodes().stream().map(FileNode::getRelativePath).collect(Collectors.toSet());
        when(root.getFiles()).thenReturn(files);

        return root;
    }

    /**
     * Creates a {@link FilteredLog}.
     *
     * @return the created log
     */
    private FilteredLog createFilteredLog() {
        return new FilteredLog(LOG_NAME);
    }
}
