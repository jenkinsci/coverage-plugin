package io.jenkins.plugins.coverage.metrics.source;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies that source painting handles files with extended ASCII characters.
 *
 * @author Akash Manna
 */
class SourceCodePainterTest {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Test
    void shouldPaintSourceFilesWithExtendedAsciiCharacters() throws IOException, InterruptedException {
        Path workspace = Files.createTempDirectory("source-painter");
        Path sourceFile = workspace.resolve("Example.m");
        Files.write(sourceFile, List.of(
                "function y = example()",
                "% Copyright 2026, Caf\u00e9 Corporation",
                "y = 1;"), WINDOWS_1252);

        var painter = new SourceCodePainter.AgentCoveragePainter(
                List.of(new CoverageSourcePrinter(new FileNode("", "Example.m"))), "", "coverage");

        FilteredLog log = painter.invoke(workspace.toFile(), null);

        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(log.getInfoMessages()).contains("-> finished painting successfully");
        assertThat(workspace.resolve(SourceCodeFacade.COVERAGE_SOURCES_ZIP)).exists();
    }
}