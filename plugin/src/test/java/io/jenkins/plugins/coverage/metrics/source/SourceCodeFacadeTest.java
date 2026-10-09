package io.jenkins.plugins.coverage.metrics.source;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.util.ResourceTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for {@link SourceCodeFacade}.
 *
 * @author Florian Orendi
 */
class SourceCodeFacadeTest extends ResourceTest {
    private static final String WHOLE_SOURCE_CODE = "SourcecodeTest.html";
    private static final String MODIFIED_LINES_COVERAGE_SOURCE_CODE = "SourcecodeTestCC.html";
    private static final String INDIRECT_COVERAGE_SOURCE_CODE = "SourcecodeTestICC.html";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void shouldReadStoredSourceCodeAsUtf8() throws IOException, InterruptedException {
        var sourceCodeFacade = createSourceCodeFacade();
        var id = "coverage";
        var path = "src/main/java/Café.java";
        var html = "<td class=\"code\">String value = \"Café — 你好\";</td>";

        Path sourceFolder = temporaryDirectory
                .resolve(SourceCodeFacade.COVERAGE_SOURCES_DIRECTORY)
                .resolve(id);
        Files.createDirectories(sourceFolder);
        createZippedSource(sourceFolder, path, html);

        assertThat(sourceCodeFacade.read(temporaryDirectory.toFile(), id, path))
                .isEqualTo(html);
    }

    @Test
    void shouldUseIdSpecificTransferArchiveName() {
        assertThat(SourceCodeFacade.getCoverageSourcesZip("jacoco-unit"))
                .isEqualTo("coverage-sources-jacoco-unit.zip");
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

        var requiredHtml = Jsoup.parse(readHtml(MODIFIED_LINES_COVERAGE_SOURCE_CODE), Parser.xmlParser()).html();

        var modifiedLinesCoverageHtml = sourceCodeFacade.calculateModifiedLinesCoverageSourceCode(originalHtml, node);
        assertThat(modifiedLinesCoverageHtml).isEqualTo(requiredHtml);
    }

    @Test
    void shouldCalculateSourcecodeForIndirectCoverageChanges() throws IOException {
        var sourceCodeFacade = createSourceCodeFacade();
        var originalHtml = readHtml(WHOLE_SOURCE_CODE);
        var node = createFileCoverageNode();

        var requiredHtml = Jsoup.parse(readHtml(INDIRECT_COVERAGE_SOURCE_CODE), Parser.xmlParser()).html();

        var modifiedLinesCoverageHtml = sourceCodeFacade.calculateIndirectCoverageChangesSourceCode(originalHtml, node);
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
        List<Integer> lines = Arrays.asList(10, 11, 12, 16, 17, 18, 19);
        for (Integer line : lines) {
            file.addModifiedLines(line);
        }
        file.addIndirectCoverageChange(6, -1);
        file.addIndirectCoverageChange(7, -1);
        file.addIndirectCoverageChange(14, 1);
        file.addIndirectCoverageChange(15, 1);
        for (int i = 1; i <= 25; i++) {
            file.addCounters(i, 1, 0);
        }
        return file;
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
