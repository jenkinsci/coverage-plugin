package io.jenkins.plugins.coverage.metrics.source;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.ChangeSet.ChangeSetBuilder;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.FileNodeFixture;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.util.ResourceTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.Issue;

/**
 * Test class for {@link SourceCodeFacade}.
 *
 * @author Florian Orendi
 */
class SourceCodeFacadeTest extends ResourceTest {
    private static final String WHOLE_SOURCE_CODE = "SourcecodeTest.html";
    private static final String MODIFIED_LINES_COVERAGE_SOURCE_CODE = "SourcecodeTestCC.html";
    private static final String INDIRECT_COVERAGE_SOURCE_CODE = "SourcecodeTestICC.html";
    private static final String ID = "coverage";
    private static final String PATH = "src/main/java/Café.java";

    /**
     * Verifies that the HTML files of older builds are read as UTF-8.
     *
     * @param buildFolder
     *         the build folder with the stored files
     *
     * @throws IOException
     *         if the files could not be read or written
     * @throws InterruptedException
     *         if reading has been interrupted
     */
    @Test
    void shouldReadStoredSourceCodeAsUtf8(@TempDir final Path buildFolder) throws IOException, InterruptedException {
        var sourceCodeFacade = createSourceCodeFacade();
        var html = "<td class=\"code\">String value = \"Café — 你好\";</td>";

        createZippedSource(createSourceFolder(buildFolder), PATH, html);

        assertThat(sourceCodeFacade.read(buildFolder.toFile(), ID, PATH)).isEqualTo(html);
    }

    /**
     * Verifies that the names of the archives of the original source files are unique, even if the sanitized names of
     * the paths are the same.
     */
    @Test
    void shouldCreateUniqueNamesForOriginalSourceFiles() {
        assertThat(SourceCodeFacade.sanitizeFilename("a/b_c.java"))
                .isEqualTo(SourceCodeFacade.sanitizeFilename("a_b/c.java"));

        assertThat(SourceCodeFacade.getSourceFileName("a/b_c.java"))
                .isNotEqualTo(SourceCodeFacade.getSourceFileName("a_b/c.java"))
                .endsWith(SourceCodeFacade.SOURCE_FILE_EXTENSION)
                .hasSize(64 + SourceCodeFacade.SOURCE_FILE_EXTENSION.length());
        assertThat(SourceCodeFacade.getSourceFileName(PATH)).isEqualTo(SourceCodeFacade.getSourceFileName(PATH));
    }

    /**
     * Verifies that the lines of an original source file are read, and that the HTML of the classic source code view
     * is created on demand.
     *
     * @param buildFolder
     *         the build folder with the stored files
     *
     * @throws IOException
     *         if the files could not be read or written
     * @throws InterruptedException
     *         if reading has been interrupted
     */
    @Test
    void shouldReadOriginalSourceFile(@TempDir final Path buildFolder) throws IOException, InterruptedException {
        var sourceCodeFacade = createSourceCodeFacade();
        createZippedOriginal(createSourceFolder(buildFolder), PATH, "class Café {\n    int a;\n    int b;\n}");

        assertThat(sourceCodeFacade.hasSourceFile(buildFolder.toFile(), ID, PATH))
                .isTrue();
        assertThat(sourceCodeFacade.canRead(buildFolder.toFile(), ID, PATH)).isTrue();
        assertThat(sourceCodeFacade.readLines(buildFolder.toFile(), ID, PATH))
                .containsExactly("class Café {", "    int a;", "    int b;", "}");

        var file = new FileNode("Café.java", PATH);
        new FileNodeFixture(file).withCounters(2, 1, 0).withCounters(3, 0, 1);
        var root = new ModuleNode("root");
        root.addValue(
                new CoverageBuilder(Metric.LINE).withCovered(1).withMissed(1).build());

        var rows = Jsoup.parse(
                        "<table>" + sourceCodeFacade.readHtml(buildFolder.toFile(), ID, root, file, ChangeSet.empty())
                                + "</table>")
                .select("tr");
        assertThat(rows).extracting(Element::className).containsExactly("noCover", "coverFull", "coverNone", "noCover");
        assertThat(rows.first())
                .isNotNull()
                .extracting(Element::text)
                .asString()
                .contains("class Café {");
    }

    /**
     * Verifies that the stored HTML of older builds is used if the original source file is not available.
     *
     * @param buildFolder
     *         the build folder with the stored files
     *
     * @throws IOException
     *         if the files could not be read or written
     * @throws InterruptedException
     *         if reading has been interrupted
     */
    @Test
    void shouldUseHtmlOfOlderBuilds(@TempDir final Path buildFolder) throws IOException, InterruptedException {
        var sourceCodeFacade = createSourceCodeFacade();
        var html = "<tr class=\"coverFull\"><td class=\"code\">class Café {}</td></tr>";
        createZippedSource(createSourceFolder(buildFolder), PATH, html);

        assertThat(sourceCodeFacade.hasSourceFile(buildFolder.toFile(), ID, PATH))
                .isFalse();
        assertThat(sourceCodeFacade.canRead(buildFolder.toFile(), ID, PATH)).isTrue();
        assertThat(sourceCodeFacade.readHtml(
                        buildFolder.toFile(),
                        ID,
                        new ModuleNode("root"),
                        new FileNode("Café.java", PATH),
                        ChangeSet.empty()))
                .isEqualTo(html);
    }

    /**
     * Verifies that a source file that has not been stored cannot be read.
     *
     * @param buildFolder
     *         the empty build folder
     */
    @Test
    void shouldNotReadMissingSourceFile(@TempDir final Path buildFolder) {
        var sourceCodeFacade = createSourceCodeFacade();

        assertThat(sourceCodeFacade.hasSourceFile(buildFolder.toFile(), ID, PATH))
                .isFalse();
        assertThat(sourceCodeFacade.canRead(buildFolder.toFile(), ID, PATH)).isFalse();
    }

    /**
     * Verifies that the printer of the source code is selected based on the metrics of the root node.
     */
    @Test
    @Issue("JENKINS-75871")
    void shouldSelectPrinterBasedOnMetricsOfRoot() {
        var file = new FileNode("", "Foo.java");

        assertThat(createPrinter(file, Metric.LINE)).isExactlyInstanceOf(CoverageSourcePrinter.class);
        assertThat(createPrinter(file, Metric.LINE, Metric.MUTATION)).isExactlyInstanceOf(MutationSourcePrinter.class);
        assertThat(createPrinter(file, Metric.LINE, Metric.MCDC_PAIR))
                .isExactlyInstanceOf(VectorCastSourcePrinter.class);
        assertThat(createPrinter(file, Metric.LINE, Metric.FUNCTION_CALL))
                .isExactlyInstanceOf(VectorCastSourcePrinter.class);
    }

    private CoverageSourcePrinter createPrinter(final FileNode file, final Metric... metrics) {
        var root = new ModuleNode("root");
        for (Metric metric : metrics) {
            root.addValue(
                    new CoverageBuilder(metric).withCovered(5).withMissed(2).build());
        }
        return SourceCodeFacade.createPrinterFactory(root, ChangeSet.empty()).apply(file);
    }

    private Path createSourceFolder(final Path buildFolder) throws IOException {
        return Files.createDirectories(
                buildFolder.resolve(SourceCodeFacade.COVERAGE_SOURCES_DIRECTORY).resolve(ID));
    }

    private void createZippedOriginal(final Path sourceFolder, final String path, final String content)
            throws IOException {
        try (var zip = new ZipOutputStream(
                Files.newOutputStream(sourceFolder.resolve(SourceCodeFacade.getSourceFileName(path))))) {
            zip.putNextEntry(new ZipEntry(SourceCodeFacade.sanitizeFilename(path)));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    @Test
    void shouldUseIdSpecificTransferArchiveName() {
        assertThat(SourceCodeFacade.getCoverageSourcesZip("jacoco-unit")).isEqualTo("coverage-sources-jacoco-unit.zip");
        assertThat(SourceCodeFacade.getCoverageSourcesZip("jacoco-ui"))
                .isNotEqualTo(SourceCodeFacade.getCoverageSourcesZip("jacoco-unit"));
        assertThat(SourceCodeFacade.getCoverageSourcesZip("weird/id name"))
                .isEqualTo("coverage-sources-weird_id_name.zip");
    }

    @Test
    void shouldCalculateSourcecodeForModifiedLinesCoverage() throws IOException {
        var sourceCodeFacade = createSourceCodeFacade();
        var originalHtml = readHtml(WHOLE_SOURCE_CODE);
        var node = createFileCoverageNode();

        var requiredHtml = Jsoup.parse(readHtml(MODIFIED_LINES_COVERAGE_SOURCE_CODE), Parser.xmlParser())
                .html();

        var modifiedLinesCoverageHtml =
                sourceCodeFacade.calculateModifiedLinesCoverageSourceCode(originalHtml, node, createChanges());
        assertThat(modifiedLinesCoverageHtml).isEqualTo(requiredHtml);
    }

    @Test
    void shouldCalculateSourcecodeForIndirectCoverageChanges() throws IOException {
        var sourceCodeFacade = createSourceCodeFacade();
        var originalHtml = readHtml(WHOLE_SOURCE_CODE);
        var node = createFileCoverageNode();

        var requiredHtml = Jsoup.parse(readHtml(INDIRECT_COVERAGE_SOURCE_CODE), Parser.xmlParser())
                .html();

        var modifiedLinesCoverageHtml =
                sourceCodeFacade.calculateIndirectCoverageChangesSourceCode(originalHtml, node, createChanges());
        assertThat(modifiedLinesCoverageHtml).isEqualTo(requiredHtml);
    }

    /**
     * Creates an instance of {@link SourceCodeFacade}.
     *
     * @return the created instance
     */
    private SourceCodeFacade createSourceCodeFacade() {
        return new SourceCodeFacade();
    }

    private void createZippedSource(final Path sourceFolder, final String path, final String html) throws IOException {
        String sanitizedFileName = SourceCodeFacade.sanitizeFilename(path);
        Path zipFile = sourceFolder.resolve(sanitizedFileName + SourceCodeFacade.ZIP_FILE_EXTENSION);

        try (var zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zip.putNextEntry(new ZipEntry(sanitizedFileName));
            zip.write(html.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private FileNode createFileCoverageNode() {
        var file = new FileNode("", "path");
        for (int i = 1; i <= 25; i++) {
            new FileNodeFixture(file).withCounters(i, 1, 0);
        }
        return file;
    }

    private ChangeSet createChanges() {
        return new ChangeSetBuilder()
                .addModifiedLines("path", 10, 11, 12, 16, 17, 18, 19)
                .addIndirectCoverageChange("path", 6, -1)
                .addIndirectCoverageChange("path", 7, -1)
                .addIndirectCoverageChange("path", 14, 1)
                .addIndirectCoverageChange("path", 15, 1)
                .build();
    }

    /**
     * Reads a sourcecode HTML file for testing.
     *
     * @param name
     *         The name of the file
     *
     * @return the file content
     * @throws IOException
     *         if reading failed
     */
    private String readHtml(final String name) throws IOException {
        return toString(name);
    }
}
