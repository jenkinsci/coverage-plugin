package io.jenkins.plugins.coverage.metrics.source;

import org.apache.commons.io.FileUtils;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import hudson.FilePath;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

import io.jenkins.plugins.prism.SourceCodeRetention;
import io.jenkins.plugins.util.ValidationUtilities;

/**
 * Highlights the code coverage information in all source code files. This process is executed on the agent node that
 * has all source files checked out.
 */
public class SourceCodePainter {
    private final Run<?, ?> build;
    private final FilePath workspace;
    private final String id;

    /**
     * Creates a painter for the passed build, using the passed properties.
     *
     * @param build
     *         The build which processes the source code
     * @param workspace
     *         The workspace which contains the source code files
     * @param id
     *         the ID of the coverage results - each ID will store the files in a separate directory
     */
    public SourceCodePainter(@NonNull final Run<?, ?> build, @NonNull final FilePath workspace, final String id) {
        this.build = build;
        this.workspace = workspace;
        this.id = id;
    }

    /**
     * Processes the source code painting.
     *
     * @param rootNode
     *         the root of the tree
     * @param files
     *         the files to paint
     * @param sourceCodeEncoding
     *         the encoding of the source code files
     * @param sourceCodeRetention
     *         the source code retention strategy
     * @param log
     *         The log
     *
     * @throws InterruptedException
     *         if the painting process has been interrupted
     */
    public void processSourceCodePainting(final Node rootNode, final List<FileNode> files,
            final String sourceCodeEncoding, final SourceCodeRetention sourceCodeRetention, final FilteredLog log)
            throws InterruptedException {
        var sourceCodeFacade = new SourceCodeFacade();
        if (sourceCodeRetention != SourceCodeRetention.NEVER) {
            var printerFactory = createPrinterFactory(rootNode);
            var paintedFiles = files.stream()
                    .map(printerFactory)
                    .collect(Collectors.toList());
            log.logInfo("Painting %d source files on agent", paintedFiles.size());

            paintFilesOnAgent(paintedFiles, sourceCodeEncoding, log);
            log.logInfo("Copying painted sources from agent to build folder");

            sourceCodeFacade.copySourcesToBuildFolder(build, workspace, log);
        }
        sourceCodeRetention.cleanup(build, sourceCodeFacade.getCoverageSourcesDirectory(), log);
    }

    /**
     * Creates a factory function that produces the correct {@link CoverageSourcePrinter} subtype for a given
     * {@link FileNode}. The printer type is determined once from the root node's available metrics, avoiding
     * repeated metric lookups for every file when painting large numbers of source files.
     *
     * @param rootNode
     *         the root of the coverage tree, used to determine which printer type to use
     *
     * @return a function that maps a {@link FileNode} to the appropriate {@link CoverageSourcePrinter}
     */
    Function<FileNode, CoverageSourcePrinter> createPrinterFactory(final Node rootNode) {
        if (rootNode.getValue(Metric.MUTATION).isPresent()) {
            return MutationSourcePrinter::new;
        }
        else if (rootNode.getValue(Metric.MCDC_PAIR).isPresent()
                || rootNode.getValue(Metric.FUNCTION_CALL).isPresent()) {
            return VectorCastSourcePrinter::new;
        }
        else {
            return CoverageSourcePrinter::new;
        }
    }

    private void paintFilesOnAgent(final List<? extends CoverageSourcePrinter> paintedFiles,
            final String sourceCodeEncoding, final FilteredLog log) throws InterruptedException {
        try {
            var painter = new AgentCoveragePainter(paintedFiles, sourceCodeEncoding, id);
            var agentLog = workspace.act(painter);
            log.merge(agentLog);
        }
        catch (IOException exception) {
            log.logException(exception, "Can't paint and zip sources on the agent");
        }
    }

    /**
     * Paints source code files on the agent using the recorded coverage information. All files are stored as zipped
     * HTML files that contain the painted source code. In the last step all zipped source files are aggregated into a
     * single archive to simplify copying to the controller.
     */
    static class AgentCoveragePainter extends MasterToSlaveFileCallable<FilteredLog> {
        @Serial
        private static final long serialVersionUID = 3966282357309568323L;

        @SuppressWarnings("serial")
        private final List<? extends CoverageSourcePrinter> paintedFiles;
        private final String sourceCodeEncoding;
        private final String directory;

        /**
         * Creates a new instance of {@link AgentCoveragePainter}.
         *
         * @param files
         *         the pretty printers for the files to create the HTML reports for
         * @param sourceCodeEncoding
         *         the encoding of the source code files
         * @param directory
         *         the subdirectory where the source files will be stored in
         */
        AgentCoveragePainter(final List<? extends CoverageSourcePrinter> files, final String sourceCodeEncoding,
                final String directory) {
            super();

            this.paintedFiles = new ArrayList<>(files);
            this.sourceCodeEncoding = sourceCodeEncoding;
            this.directory = directory;
        }

        @Override
        public FilteredLog invoke(final File workspaceFile, final VirtualChannel channel) {
            var log = new FilteredLog("Errors during source code painting:");
            var workspace = new FilePath(workspaceFile);

            try {
                var outputFolder = workspace.createTempDir("coverage-sources-", "");

                Path temporaryFolder = Files.createTempDirectory(directory);

                try {
                    int count = paintedFiles.parallelStream()
                            .mapToInt(file -> paintSource(file, workspace, outputFolder, temporaryFolder, log))
                            .sum();

                    if (count == paintedFiles.size()) {
                        log.logInfo("-> finished painting successfully");
                    }
                    else {
                        log.logInfo("-> finished painting (%d files have been painted, %d files failed)",
                                count, paintedFiles.size() - count);
                    }

                    var zipFile = workspace.child(SourceCodeFacade.COVERAGE_SOURCES_ZIP);
                    outputFolder.zip(zipFile);
                    log.logInfo("-> zipping sources from folder '%s' as '%s'", outputFolder, zipFile);
                }
                finally {
                    deleteFolder(temporaryFolder.toFile(), log);
                    outputFolder.deleteRecursive();
                    log.logInfo("-> deleted temporary source folder '%s'", outputFolder);
                }
            }
            catch (IOException exception) {
                log.logException(exception,
                        "Cannot create temporary directory in folder '%s' for the painted source files", workspace);
            }
            catch (InterruptedException exception) {
                log.logException(exception,
                        "Processing has been interrupted: skipping zipping of source files", workspace);
            }

            return log;
        }

        private Charset getCharset() {
            return new ValidationUtilities().getCharset(sourceCodeEncoding);
        }

        private int paintSource(final CoverageSourcePrinter fileNode, final FilePath workspace,
                final FilePath outputFolder, final Path temporaryFolder, final FilteredLog log) {
            var relativePathIdentifier = fileNode.getPath();
            return findSourceFile(workspace, relativePathIdentifier, log)
                    .map(resolvedPath -> paint(fileNode, relativePathIdentifier, resolvedPath,
                            outputFolder, temporaryFolder, getCharset(), log))
                    .orElse(0);
        }

        private int paint(final CoverageSourcePrinter paint, final String relativePathIdentifier,
                final FilePath resolvedPath, final FilePath paintedFilesDirectory,
                final Path temporaryFolder, final Charset charset, final FilteredLog log) {
            String sanitizedFileName = SourceCodeFacade.sanitizeFilename(relativePathIdentifier);
            var zipOutputPath = paintedFilesDirectory.child(
                    sanitizedFileName + SourceCodeFacade.ZIP_FILE_EXTENSION);
            try {
                Path paintedFilesFolder = Files.createTempDirectory(temporaryFolder, directory);
                var fullSourcePath = paintedFilesFolder.resolve(sanitizedFileName);
                try (BufferedWriter output = Files.newBufferedWriter(fullSourcePath, StandardCharsets.UTF_8)) {
                    List<String> lines = readSourceLines(Path.of(resolvedPath.getRemote()), charset);

                    // added a header to display what is being shown in each column
                    output.write(paint.getColumnHeader());
                    for (int line = 0; line < lines.size(); line++) {
                        output.write(paint.renderLine(line + 1, lines.get(line)));
                    }
                }
                new FilePath(fullSourcePath.toFile()).zip(zipOutputPath);
                FileUtils.deleteDirectory(paintedFilesFolder.toFile());
                return 1;
            }
            catch (IOException | InterruptedException exception) {
                log.logException(exception, "Can't write coverage paint of '%s' to zipped source file '%s'",
                        relativePathIdentifier, zipOutputPath);
                return 0;
            }
        }

        private List<String> readSourceLines(final Path sourcePath, final Charset charset) throws IOException {
            try (var reader = new BufferedReader(new InputStreamReader(Files.newInputStream(sourcePath),
                    charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                            .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
                return reader.lines().collect(Collectors.toList());
            }
        }

        private Optional<FilePath> findSourceFile(final FilePath workspace, final String fileName,
                final FilteredLog log) {
            try {
                var absolutePath = new FilePath(new File(fileName));
                if (absolutePath.exists()) {
                    return Optional.of(absolutePath);
                }

                var relativePath = workspace.child(fileName);
                if (relativePath.exists()) {
                    return Optional.of(relativePath);
                }
            }
            catch (InvalidPathException | IOException | InterruptedException exception) {
                log.logException(exception, "No valid path in coverage node: '%s'", fileName);
            }
            return Optional.empty();
        }

        /**
         * Deletes a folder.
         *
         * @param folder
         *         The directory to be deleted
         * @param log
         *         The log
         */
        private void deleteFolder(final File folder, final FilteredLog log) {
            if (folder.isDirectory()) {
                try {
                    FileUtils.deleteDirectory(folder);
                }
                catch (IOException e) {
                    log.logError("The folder '%s' could not be deleted",
                            folder.getAbsolutePath());
                }
            }
        }
    }
}
