package io.jenkins.plugins.coverage.metrics.source;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.prism.SourceCodeRetention;
import io.jenkins.plugins.util.ValidationUtilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serial;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jenkins.MasterToSlaveFileCallable;

/**
 * Copies the source code files with coverage information from the agent to the build folder of the controller. The
 * files are stored in their original form (encoded as UTF-8): the coverage is highlighted when a file is shown in the
 * user interface, see {@link SourceCodeFacade}. In contrast to the painted HTML files of previous versions, the
 * coverage information of the files does not need to be sent to the agent.
 */
public class SourceCodeCopier {
    private final Run<?, ?> build;
    private final FilePath workspace;
    private final String id;

    /**
     * Creates a copier for the passed build.
     *
     * @param build
     *         the build which processes the source code
     * @param workspace
     *         the workspace which contains the source code files
     * @param id
     *         the ID of the coverage results - each ID will store the files in a separate directory
     */
    public SourceCodeCopier(@NonNull final Run<?, ?> build, @NonNull final FilePath workspace, final String id) {
        this.build = build;
        this.workspace = workspace;
        this.id = id;
    }

    /**
     * Copies the source code files to the build folder, depending on the retention strategy. Afterward, the source
     * code files of previous builds are deleted, if required by the retention strategy.
     *
     * @param files
     *         the files to copy
     * @param sourceCodeEncoding
     *         the encoding of the source code files
     * @param sourceCodeRetention
     *         the source code retention strategy
     * @param log
     *         the log
     *
     * @throws InterruptedException
     *         if the copying has been interrupted
     */
    public void copySourceFiles(
            final List<FileNode> files,
            final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention,
            final FilteredLog log)
            throws InterruptedException {
        var sourceCodeFacade = new SourceCodeFacade();
        if (sourceCodeRetention != SourceCodeRetention.NEVER) {
            var paths = files.stream().map(FileNode::getRelativePath).toList();
            log.logInfo("Zipping %d source files on agent", paths.size());

            zipFilesOnAgent(paths, sourceCodeEncoding, log);
            log.logInfo("Copying zipped sources from agent to build folder");

            sourceCodeFacade.copySourcesToBuildFolder(build, workspace, id, log);
        }
        sourceCodeRetention.cleanup(build, sourceCodeFacade.getCoverageSourcesDirectory(id), log);
    }

    private void zipFilesOnAgent(final List<String> paths, final String sourceCodeEncoding, final FilteredLog log)
            throws InterruptedException {
        try {
            var agentLog = workspace.act(new AgentSourceCopier(paths, sourceCodeEncoding, id));
            log.merge(agentLog);
        } catch (IOException exception) {
            log.logException(exception, "Can't zip sources on the agent");
        }
    }

    /**
     * Zips the source code files on the agent. Each file is read with the configured encoding and stored as a zipped
     * UTF-8 file. In the last step, all zipped source files are aggregated into a single archive to simplify copying
     * to the controller.
     */
    static class AgentSourceCopier extends MasterToSlaveFileCallable<FilteredLog> {
        @Serial
        private static final long serialVersionUID = -2263744870384437236L;

        @SuppressWarnings("serial")
        private final List<String> paths;

        private final String sourceCodeEncoding;
        private final String directory;

        /**
         * Creates a new instance of {@link AgentSourceCopier}.
         *
         * @param paths
         *         the paths of the source code files, relative to the workspace (or absolute)
         * @param sourceCodeEncoding
         *         the encoding of the source code files
         * @param directory
         *         the subdirectory where the source files will be stored in
         */
        AgentSourceCopier(final List<String> paths, final String sourceCodeEncoding, final String directory) {
            super();

            this.paths = new ArrayList<>(paths);
            this.sourceCodeEncoding = sourceCodeEncoding;
            this.directory = directory;
        }

        @Override
        public FilteredLog invoke(final File workspaceFile, final VirtualChannel channel) {
            var log = new FilteredLog("Errors during zipping of source code files:");
            var workspace = new FilePath(workspaceFile);

            try {
                var tempParent = workspace.createTempDir("coverage-sources-", "");
                try {
                    var outputFolder = Path.of(tempParent.child(directory).getRemote());
                    Files.createDirectories(outputFolder);

                    var charset = new ValidationUtilities().getCharset(sourceCodeEncoding);
                    int count = paths.parallelStream()
                            .mapToInt(path -> zipSource(path, workspace, outputFolder, charset, log))
                            .sum();
                    if (count == paths.size()) {
                        log.logInfo("-> finished zipping successfully");
                    } else {
                        log.logInfo(
                                "-> finished zipping (%d files have been zipped, %d files failed)",
                                count, paths.size() - count);
                    }

                    var zipFile = workspace.child(SourceCodeFacade.getCoverageSourcesZip(directory));
                    tempParent.child(directory).zip(zipFile);
                    log.logInfo("-> zipping sources from folder '%s' as '%s'", outputFolder, zipFile);
                } finally {
                    tempParent.deleteRecursive();
                    log.logInfo("-> deleted temporary source folder '%s'", tempParent);
                }
            } catch (IOException exception) {
                log.logException(
                        exception, "Cannot create temporary directory in folder '%s' for the source files", workspace);
            } catch (InterruptedException exception) {
                log.logException(
                        exception, "Processing has been interrupted: skipping zipping of source files", workspace);
            }

            return log;
        }

        private int zipSource(
                final String path,
                final FilePath workspace,
                final Path outputFolder,
                final Charset charset,
                final FilteredLog log) {
            return findSourceFile(workspace, path, log)
                    .map(sourceFile -> zip(path, sourceFile, outputFolder, charset, log))
                    .orElse(0);
        }

        private int zip(
                final String path,
                final Path sourceFile,
                final Path outputFolder,
                final Charset charset,
                final FilteredLog log) {
            var zipFile = outputFolder.resolve(SourceCodeFacade.getSourceFileName(path));
            try (var zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
                zip.putNextEntry(new ZipEntry(SourceCodeFacade.sanitizeFilename(path)));
                zip.write(
                        String.join("\n", readSourceLines(sourceFile, charset)).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                return 1;
            } catch (IOException exception) {
                log.logException(exception, "Can't write zipped source file '%s' of '%s'", zipFile, path);
                return 0;
            }
        }

        private List<String> readSourceLines(final Path sourcePath, final Charset charset) throws IOException {
            try (var reader = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(sourcePath),
                    charset.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPLACE)
                            .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
                return reader.lines().toList();
            }
        }

        private Optional<Path> findSourceFile(final FilePath workspace, final String fileName, final FilteredLog log) {
            try {
                var absolutePath = new FilePath(new File(fileName));
                if (absolutePath.exists()) {
                    return Optional.of(Path.of(absolutePath.getRemote()));
                }

                var relativePath = workspace.child(fileName);
                if (relativePath.exists()) {
                    return Optional.of(Path.of(relativePath.getRemote()));
                }
            } catch (InvalidPathException | IOException | InterruptedException exception) {
                log.logException(exception, "No valid path in coverage node: '%s'", fileName);
            }
            return Optional.empty();
        }
    }
}
