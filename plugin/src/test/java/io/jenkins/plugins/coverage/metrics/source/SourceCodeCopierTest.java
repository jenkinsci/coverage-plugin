package io.jenkins.plugins.coverage.metrics.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.util.FilteredLog;
import hudson.FilePath;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.source.SourceCodeCopier.AgentSourceCopier;
import io.jenkins.plugins.prism.SourceCodeRetention;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.Issue;

/**
 * Tests the class {@link SourceCodeCopier}. The files that are zipped on the agent are extracted in the same way as
 * the controller does and are read with the {@link SourceCodeFacade}.
 *
 * @author Ullrich Hafner
 */
class SourceCodeCopierTest {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final String ID = "coverage";

    /**
     * Verifies that the source files are stored as UTF-8 files, even if the source files use another encoding.
     *
     * @param workspace
     *         the workspace with the source files
     * @param buildFolder
     *         the build folder on the controller
     *
     * @throws IOException
     *         if the files could not be read or written
     * @throws InterruptedException
     *         if the extraction has been interrupted
     */
    @Test
    void shouldStoreSourceFilesWithExtendedAsciiCharactersAsUtf8(
            @TempDir final Path workspace, @TempDir final Path buildFolder) throws IOException, InterruptedException {
        Files.write(
                workspace.resolve("Example.m"),
                List.of("function y = example()", "% Copyright 2026, Café Corporation", "y = 1;"),
                WINDOWS_1252);

        FilteredLog log =
                new AgentSourceCopier(List.of("Example.m"), "windows-1252", ID).invoke(workspace.toFile(), null);

        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(log.getInfoMessages()).contains("-> finished zipping successfully");
        assertThat(extractAndReadLines(workspace, buildFolder, "Example.m"))
                .containsExactly("function y = example()", "% Copyright 2026, Café Corporation", "y = 1;");
    }

    /**
     * Verifies that files that are not found in the workspace are skipped.
     *
     * @param workspace
     *         the workspace with the source files
     * @param buildFolder
     *         the build folder on the controller
     *
     * @throws IOException
     *         if the files could not be read or written
     * @throws InterruptedException
     *         if the extraction has been interrupted
     */
    @Test
    void shouldSkipMissingSourceFiles(@TempDir final Path workspace, @TempDir final Path buildFolder)
            throws IOException, InterruptedException {
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/Foo.java"), "class Foo {}");

        FilteredLog log = new AgentSourceCopier(List.of("src/Foo.java", "src/Missing.java"), "UTF-8", ID)
                .invoke(workspace.toFile(), null);

        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(log.getInfoMessages()).contains("-> finished zipping (1 files have been zipped, 1 files failed)");
        assertThat(extractAndReadLines(workspace, buildFolder, "src/Foo.java")).containsExactly("class Foo {}");
        assertThat(new SourceCodeFacade().canRead(buildFolder.toFile(), ID, "src/Missing.java"))
                .isFalse();
    }

    /**
     * Regression test for issue #771: pre-existing workspace directories must not be deleted.
     *
     * @param workspace
     *         the workspace with the source files
     *
     * @throws IOException
     *         if the files could not be written
     */
    @Test
    @Issue("771")
    void shouldNotDeletePreExistingWorkspaceDirectoryWithSameNameAsCoverageId(@TempDir final Path workspace)
            throws IOException {
        Path existingDir = workspace.resolve(ID);
        Files.createDirectories(existingDir);
        Path sentinelFile = existingDir.resolve("important-project-file.txt");
        Files.writeString(sentinelFile, "This file must not be deleted by the coverage plugin!");
        Files.writeString(workspace.resolve("Foo.java"), "class Foo {}");

        FilteredLog log = new AgentSourceCopier(List.of("Foo.java"), StandardCharsets.UTF_8.name(), ID)
                .invoke(workspace.toFile(), null);

        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(log.getInfoMessages()).contains("-> finished zipping successfully");
        assertThat(existingDir)
                .as("Pre-existing workspace directory '%s' must not have been deleted", existingDir)
                .exists();
        assertThat(sentinelFile)
                .as("Sentinel file inside pre-existing directory must not have been deleted")
                .exists()
                .hasContent("This file must not be deleted by the coverage plugin!");
    }

    /**
     * Verifies that no source files are stored if the retention strategy is {@link SourceCodeRetention#NEVER}.
     *
     * @param workspace
     *         the workspace with the source files
     * @param buildFolder
     *         the build folder on the controller
     *
     * @throws IOException
     *         if the files could not be written
     * @throws InterruptedException
     *         if copying has been interrupted
     */
    @Test
    void shouldNotStoreSourceFilesIfRetentionIsNever(@TempDir final Path workspace, @TempDir final Path buildFolder)
            throws IOException, InterruptedException {
        Files.writeString(workspace.resolve("Foo.java"), "class Foo {}");
        Run<?, ?> build = mock(Run.class);
        when(build.getRootDir()).thenReturn(buildFolder.toFile());
        var log = new FilteredLog("Errors");

        new SourceCodeCopier(build, new FilePath(workspace.toFile()), ID)
                .copySourceFiles(
                        List.of(new FileNode("", "Foo.java")),
                        StandardCharsets.UTF_8.name(),
                        SourceCodeRetention.NEVER,
                        log);

        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(workspace.resolve(SourceCodeFacade.getCoverageSourcesZip(ID)))
                .doesNotExist();
        assertThat(buildFolder.resolve(SourceCodeFacade.COVERAGE_SOURCES_DIRECTORY))
                .doesNotExist();
    }

    /**
     * Extracts the transfer archive of the agent into the build folder (in the same way as
     * {@link SourceCodeFacade#copySourcesToBuildFolder} does) and reads the lines of the specified file.
     *
     * @param workspace
     *         the workspace that contains the transfer archive
     * @param buildFolder
     *         the build folder to extract the archive to
     * @param path
     *         the path of the source file to read
     *
     * @return the lines of the stored source file
     * @throws IOException
     *         if the files could not be read
     * @throws InterruptedException
     *         if the extraction has been interrupted
     */
    private List<String> extractAndReadLines(final Path workspace, final Path buildFolder, final String path)
            throws IOException, InterruptedException {
        var transferArchive = workspace.resolve(SourceCodeFacade.getCoverageSourcesZip(ID));
        assertThat(transferArchive).exists();
        new FilePath(transferArchive.toFile())
                .unzip(new FilePath(buildFolder
                        .resolve(SourceCodeFacade.COVERAGE_SOURCES_DIRECTORY)
                        .toFile()));

        var facade = new SourceCodeFacade();
        assertThat(facade.hasSourceFile(buildFolder.toFile(), ID, path)).isTrue();
        return facade.readLines(buildFolder.toFile(), ID, path);
    }
}
