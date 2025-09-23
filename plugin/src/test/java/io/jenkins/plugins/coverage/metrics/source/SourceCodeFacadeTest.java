package io.jenkins.plugins.coverage.metrics.source;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.util.ResourceTest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
