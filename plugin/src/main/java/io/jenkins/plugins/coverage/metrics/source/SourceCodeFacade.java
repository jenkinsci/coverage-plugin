package io.jenkins.plugins.coverage.metrics.source;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;
import hudson.FilePath;
import hudson.model.Run;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * Facade to the source code file structure in Jenkins build folder. Access of those files should be done using an
 * instance of this class only.
 *
 * <p>
 * The source code files are stored as zipped original files (encoded as UTF-8) in the folder
 * {@code coverage-sources/<id>}, see {@link SourceCodeCopier}. The coverage is highlighted when a file is shown. Older
 * builds contain zipped HTML files instead, where the coverage has been painted on the agent during the build. Both
 * formats can be read: the HTML of the classic source code view is created on demand for the original files (see
 * {@link #readHtml(File, String, Node, FileNode, ChangeSet)}).
 * </p>
 *
 * @author Ullrich Hafner
 * @author Florian Orendi
 */
public class SourceCodeFacade {
    /** Toplevel directory in the build folder of the controller that contains the zipped source files. */
    static final String COVERAGE_SOURCES_DIRECTORY = "coverage-sources";

    static final int MAX_FILENAME_LENGTH = 245; // Windows has limitations on long file names
    static final String ZIP_FILE_EXTENSION = ".zip";
    /** Extension of the zipped original source code files (the HTML files of older builds use {@code .zip}). */
    static final String SOURCE_FILE_EXTENSION = ".source.zip";

    static String sanitizeFilename(final String inputName) {
        return StringUtils.right(inputName.replaceAll("[^a-zA-Z0-9-_.]", "_"), MAX_FILENAME_LENGTH);
    }

    /**
     * Returns the name of the archive that contains the original source code file with the specified path. The name
     * is derived from the SHA-256 hash of the path: in contrast to the sanitized names of the HTML files of older
     * builds, different paths never get the same name.
     *
     * @param path
     *         the path of the source code file
     *
     * @return the name of the archive
     */
    static String getSourceFileName(final String path) {
        try {
            var hash = MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash) + SOURCE_FILE_EXTENSION;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * Creates a factory that produces the correct {@link CoverageSourcePrinter} subtype for a given {@link FileNode}.
     * The printer type is determined once from the available metrics of the root node, avoiding repeated metric
     * lookups for every file.
     *
     * @param rootNode
     *         the root of the coverage tree, used to determine which printer type to use
     * @param changes
     *         the changes that should be highlighted in the painted files
     *
     * @return a function that maps a {@link FileNode} to the appropriate {@link CoverageSourcePrinter}
     */
    static Function<FileNode, CoverageSourcePrinter> createPrinterFactory(
            final Node rootNode, final ChangeSet changes) {
        if (rootNode.getValue(Metric.MUTATION).isPresent()) {
            return file -> new MutationSourcePrinter(file, changes);
        } else if (rootNode.getValue(Metric.MCDC_PAIR).isPresent()
                || rootNode.getValue(Metric.FUNCTION_CALL).isPresent()) {
            return file -> new VectorCastSourcePrinter(file, changes);
        } else {
            return file -> new CoverageSourcePrinter(file, changes);
        }
    }

    /**
     * Returns the name of the transfer archive that holds the painted sources for a single coverage result. The name
     * is unique per coverage ID so that concurrent {@code recordCoverage} steps of the same build (which all share the
     * same build folder on the controller) do not overwrite or delete each other's archive.
     *
     * @param id
     *         the ID of the coverage results
     *
     * @return the ID-specific name of the transfer archive
     */
    static String getCoverageSourcesZip(final String id) {
        return COVERAGE_SOURCES_DIRECTORY + "-" + sanitizeFilename(id) + ZIP_FILE_EXTENSION;
    }

    /**
     * Returns the source code of the specified file as HTML for the classic source code view: each line is a row of
     * a table, the coverage is highlighted using CSS classes. For original source code files, the HTML is created on
     * demand. For older builds, the stored HTML is returned.
     *
     * @param buildResults
     *         Jenkins directory for build results
     * @param id
     *         ID of the coverage results
     * @param rootNode
     *         the root of the coverage tree, used to determine the highlighted metrics
     * @param file
     *         the file to show
     * @param changes
     *         the changes that should be highlighted
     *
     * @return the source code as HTML
     * @throws IOException
     *         if the file could not be read
     * @throws InterruptedException
     *         if reading has been interrupted
     */
    public String readHtml(
            final File buildResults, final String id, final Node rootNode, final FileNode file, final ChangeSet changes)
            throws IOException, InterruptedException {
        var path = file.getRelativePath();
        if (!hasSourceFile(buildResults, id, path)) {
            return read(buildResults, id, path);
        }
        var printer = createPrinterFactory(rootNode, changes).apply(file);
        var lines = readLines(buildResults, id, path);
        var html = new StringBuilder(printer.getColumnHeader());
        for (int line = 0; line < lines.size(); line++) {
            html.append(printer.renderLine(line + 1, lines.get(line)));
        }
        return html.toString();
    }

    /**
     * Reads the lines of the original source code file with the specified path.
     *
     * @param buildResults
     *         Jenkins directory for build results
     * @param id
     *         ID of the coverage results
     * @param path
     *         the path of the source code file
     *
     * @return the lines of the file
     * @throws IOException
     *         if the file could not be read
     */
    public List<String> readLines(final File buildResults, final String id, final String path) throws IOException {
        try (var zip = new ZipFile(getSourceFile(buildResults, id, path), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            if (!entries.hasMoreElements()) {
                throw new IOException("Zipped source file of '%s' is empty".formatted(path));
            }
            try (var reader = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(entries.nextElement()), StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
    }

    /**
     * Returns whether the original source code file with the specified path has been stored. Older builds contain
     * HTML files only, see {@link #canRead(File, String, String)}.
     *
     * @param buildResults
     *         Jenkins directory for build results
     * @param id
     *         ID of the coverage results
     * @param path
     *         the path of the source code file
     *
     * @return {@code true} if the original source code file is available, {@code false} otherwise
     */
    public boolean hasSourceFile(final File buildResults, final String id, final String path) {
        return getSourceFile(buildResults, id, path).canRead();
    }

    private File getSourceFile(final File buildResults, final String id, final String path) {
        return new File(getElementFolder(buildResults, id), getSourceFileName(path));
    }

    private File getElementFolder(final File buildResults, final String id) {
        return new File(new File(buildResults, COVERAGE_SOURCES_DIRECTORY), id);
    }

    /**
     * Reads the contents of the HTML file of the given file into a String. These HTML files are available in older
     * builds only, see {@link #readHtml(File, String, Node, FileNode, ChangeSet)}.
     *
     * @param buildResults
     *         Jenkins directory for build results
     * @param id
     *         if of the coverage results
     * @param path
     *         relative path to the coverage node base filename of the coverage node
     *
     * @return the file content as String
     */
    String read(final File buildResults, final String id, final String path) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory(COVERAGE_SOURCES_DIRECTORY);
        var unzippedSourcesDir = new FilePath(tempDir.toFile());
        try {
            var inputZipFile = new FilePath(createFileInBuildFolder(buildResults, id, path));
            inputZipFile.unzip(unzippedSourcesDir);
            final String str = sanitizeFilename(path);
            String actualPaintedSourceFileName = Strings.CS.removeEnd(str, ZIP_FILE_EXTENSION);
            var sourceFile = tempDir.resolve(actualPaintedSourceFileName).toFile();

            return Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
        } finally {
            unzippedSourcesDir.deleteRecursive();
        }
    }

    /**
     * Returns whether the source code is available for the specified source file: either as original file or as HTML
     * file of an older build.
     *
     * @param buildResults
     *         Jenkins directory for build results
     * @param id
     *         if of the coverage results
     * @param path
     *         relative path to the source code filename name
     *
     * @return {@code true} if the source code is available, {@code false} otherwise
     */
    public boolean canRead(final File buildResults, final String id, final String path) {
        return hasSourceFile(buildResults, id, path)
                || createFileInBuildFolder(buildResults, id, path).canRead();
    }

    /**
     * Checks whether any source files has been stored. Even if it is wanted, there might have been errors which cause
     * the absence of any source files.
     *
     * @param buildResults
     *         Jenkins directory for build results
     * @param id
     *         id of the coverage results
     *
     * @return {@code true} whether source files has been stored, else {@code false}
     */
    public boolean hasStoredSourceCode(final File buildResults, final String id) {
        var sourceFolder = new File(buildResults, COVERAGE_SOURCES_DIRECTORY);
        var elementFolder = new File(sourceFolder, id);
        var files = elementFolder.listFiles();
        return files != null && files.length > 0;
    }

    String getCoverageSourcesDirectory(final String id) {
        return COVERAGE_SOURCES_DIRECTORY + "/" + id;
    }

    /**
     * Copies the zipped source files from the agent to the controller and unpacks them in the coverage-sources folder
     * of the current build.
     *
     * @param build
     *         the build with the coverage result
     * @param workspace
     *         the workspace on the agent that created the ZIP file
     * @param id
     *         the ID of the coverage results, used to select the ID-specific transfer archive
     * @param log
     *         the log
     *
     * @throws InterruptedException
     *         in case the user terminated the job
     */
    void copySourcesToBuildFolder(
            final Run<?, ?> build, final FilePath workspace, final String id, final FilteredLog log)
            throws InterruptedException {
        var zipName = getCoverageSourcesZip(id);
        var buildFolder = new FilePath(build.getRootDir()).child(COVERAGE_SOURCES_DIRECTORY);
        FilePath buildZip = buildFolder.child(zipName);
        var workspaceZip = workspace.child(zipName);

        try {
            workspaceZip.copyTo(buildZip);
            log.logInfo("-> extracting...");
            buildZip.unzip(buildFolder);
            log.logInfo("-> done");
        } catch (IOException exception) {
            log.logException(exception, "Can't copy zipped sources from agent to controller");
        } finally {
            delete(buildZip, log);
            delete(workspaceZip, log);
        }
    }

    /**
     * Deletes a file without throwing an IOException if the delete fails.
     *
     * @param file
     *         the file to delete
     * @param log
     *         the log
     *
     * @throws InterruptedException
     *         if the user terminated the job
     */
    private void delete(final FilePath file, final FilteredLog log) throws InterruptedException {
        try {
            if (file.exists()) {
                file.delete();
            }
        } catch (IOException exception) {
            log.logException(exception, "Can't delete temporary file: '%s'", file);
        }
    }

    /**
     * Returns a file to an HTML file of an older build in Jenkins' build folder. Note that the file might not exist.
     *
     * @param buildResults
     *         Jenkins directory for build results
     * @param id
     *         if of the coverage results
     * @param path
     *         relative path to the coverage node base filename of the coverage node
     *
     * @return the file
     */
    File createFileInBuildFolder(final File buildResults, final String id, final String path) {
        return new File(getElementFolder(buildResults, id), sanitizeFilename(path) + ZIP_FILE_EXTENSION);
    }

    /**
     * Filters the sourcecode coverage highlighting for analyzing the modified lines coverage only.
     *
     * @param content
     *         The original HTML content
     * @param fileNode
     *         The {@link FileNode node} which represents the coverage of the file
     * @param changes
     *         The changes of the build
     *
     * @return the filtered HTML sourcecode view
     */
    public String calculateModifiedLinesCoverageSourceCode(
            final String content, final FileNode fileNode, final ChangeSet changes) {
        Set<Integer> lines = fileNode.getLinesWithCoverage();
        lines.retainAll(changes.getModifiedLines(fileNode.getRelativePath()));
        Set<String> linesAsText = lines.stream().map(String::valueOf).collect(Collectors.toSet());
        Document doc = Jsoup.parse(content, Parser.xmlParser());
        int maxLine = Integer.parseInt(
                Objects.requireNonNull(doc.select("tr").last()).select("a").text());
        Map<String, Boolean> linesMapping = calculateLineMapping(lines, maxLine);
        var elements = doc.select("tr");
        for (Element element : elements) {
            var line = element.select("td > a").text();
            if (linesMapping.containsKey(line)) {
                if (linesMapping.get(line)) {
                    changeCodeToSkipLine(element);
                } else if (!linesAsText.contains(line)) {
                    element.removeClass(element.className());
                    element.addClass("noCover");
                    Objects.requireNonNull(element.select("td.hits").first()).text("");
                }
            } else {
                element.remove();
            }
        }
        return doc.html();
    }

    /**
     * Filters the sourcecode coverage highlighting for analyzing indirect coverage changes only.
     *
     * @param content
     *         The original HTML content
     * @param fileNode
     *         The {@link FileNode node} which represents the coverage of the file
     * @param changes
     *         The changes of the build
     *
     * @return the filtered HTML sourcecode view
     */
    public String calculateIndirectCoverageChangesSourceCode(
            final String content, final FileNode fileNode, final ChangeSet changes) {
        Map<Integer, Integer> lines = changes.getIndirectCoverageChanges(fileNode.getRelativePath());
        Map<String, String> indirectCoverageChangesAsText = lines.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()), entry -> String.valueOf(entry.getValue())));
        Document doc = Jsoup.parse(content, Parser.xmlParser());
        int maxLine = Integer.parseInt(
                Objects.requireNonNull(doc.select("tr").last()).select("a").text());
        Map<String, Boolean> linesMapping = calculateLineMapping(lines.keySet(), maxLine);
        doc.select("tr").forEach(element -> {
            var line = element.select("td > a").text();
            if (linesMapping.containsKey(line)) {
                colorIndirectCoverageChangeLine(element, line, linesMapping, indirectCoverageChangesAsText);
            } else {
                element.remove();
            }
        });
        return doc.html();
    }

    /**
     * Highlights a line to be a skip line which represents a bunch of not visible lines.
     *
     * @param element
     *         The HTML element which represents the line
     */
    private void changeCodeToSkipLine(final Element element) {
        element.removeClass(element.className());
        element.addClass("coverSkip");
        Objects.requireNonNull(element.select("td.line").first()).text("..");
        Objects.requireNonNull(element.select("td.hits").first()).text("");
        Objects.requireNonNull(element.select("td.code").first()).text("");
    }

    /**
     * Colors one line within the indirect coverage changes code view.
     *
     * @param element
     *         The HTML element which represents the line
     * @param line
     *         The line number
     * @param linesMapping
     *         The mapping which classifies how the line should be treated
     * @param indirectCoverageChangesAsText
     *         The indirect coverage changes mapping
     */
    private void colorIndirectCoverageChangeLine(
            final Element element,
            final String line,
            final Map<String, Boolean> linesMapping,
            final Map<String, String> indirectCoverageChangesAsText) {
        if (linesMapping.get(line)) {
            changeCodeToSkipLine(element);
        } else if (indirectCoverageChangesAsText.containsKey(line)) {
            element.removeClass(element.className());
            var hits = indirectCoverageChangesAsText.get(line);
            if (hits.startsWith("-")) {
                element.addClass("coverNone");
            } else {
                element.addClass("coverFull");
            }
            Objects.requireNonNull(element.select("td.hits").first()).text(hits);
        } else {
            element.removeClass(element.className());
            element.addClass("noCover");
            Objects.requireNonNull(element.select("td.hits").first()).text("");
        }
    }

    /**
     * Calculates a mapping of lines which should be shown. The mapping contains the passed line intervals surrounded by
     * +-3 lines each.
     *
     * @param lines
     *         The lines which build the line intervals to be shown
     * @param maxLine
     *         The maximum line number
     *
     * @return the line mapping as a map with the line number text as key and {@code true} if the line should be marked
     *         as a filling line, {@code false} if the line shows code
     */
    private Map<String, Boolean> calculateLineMapping(final Set<Integer> lines, final int maxLine) {
        SortedSet<Integer> linesWithSurroundings = new TreeSet<>(lines);
        lines.forEach(line -> {
            for (int i = 1; i <= 3; i++) {
                linesWithSurroundings.add(line + i);
                linesWithSurroundings.add(line - i);
            }
        });
        List<Integer> sortedLines = linesWithSurroundings.stream()
                .filter(line -> line >= 1 && line <= maxLine)
                .collect(Collectors.toList());
        SortedMap<String, Boolean> linesMapping = new TreeMap<>();
        for (int i = 0; i < sortedLines.size(); i++) {
            int line = sortedLines.get(i);
            linesMapping.put(String.valueOf(line), false);
            if (i < sortedLines.size() - 1 && line + 1 != sortedLines.get(i + 1)) {
                linesMapping.put(String.valueOf(line + 1), true);
            }
        }
        int highestLine = sortedLines.get(sortedLines.size() - 1);
        if (sortedLines.get(0) > 1) {
            linesMapping.put("1", true);
        }
        if (highestLine < maxLine) {
            linesMapping.put(String.valueOf(highestLine + 1), true);
        }
        return linesMapping;
    }
}
