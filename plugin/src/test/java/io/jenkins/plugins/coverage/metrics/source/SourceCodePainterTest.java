package io.jenkins.plugins.coverage.metrics.source;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.util.FilteredLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

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
                "% Copyright 2026, Café Corporation",
                "y = 1;"), WINDOWS_1252);

        var painter = new SourceCodePainter.AgentCoveragePainter(
                List.of(new CoverageSourcePrinter(new FileNode("", "Example.m"))),
                "windows-1252",
                "coverage");

        FilteredLog log = painter.invoke(workspace.toFile(), null);

        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(log.getInfoMessages()).contains("-> finished painting successfully");

        Path outerZipPath = workspace.resolve(SourceCodeFacade.COVERAGE_SOURCES_ZIP);
        assertThat(outerZipPath).exists();

        byte[] paintedBytes = readPaintedBytesFromNestedZip(outerZipPath);

        var renderedText = new String(paintedBytes, StandardCharsets.UTF_8).replace("\u00A0", " ");
        assertThat(renderedText).contains("Copyright 2026, Café Corporation");
    }

    private byte[] readPaintedBytesFromNestedZip(final Path outerZipPath) throws IOException {
        try (var outerZip = new ZipInputStream(Files.newInputStream(outerZipPath))) {
            ZipEntry outerEntry = outerZip.getNextEntry();

            while (outerEntry != null) {
                if (outerEntry.getName().endsWith(SourceCodeFacade.ZIP_FILE_EXTENSION)) {
                    byte[] innerZipBytes = outerZip.readAllBytes();
                    byte[] content = extractBytesFromZip(innerZipBytes);

                    if (content.length > 0) {
                        return content;
                    }
                }

                outerEntry = outerZip.getNextEntry();
            }
        }

        return new byte[0];
    }

    private byte[] extractBytesFromZip(final byte[] zipBytes) throws IOException {
        var out = new ByteArrayOutputStream();

        try (var innerZip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = innerZip.getNextEntry();

            while (entry != null) {
                if (!entry.isDirectory()) {
                    out.write(innerZip.readAllBytes());
                }

                entry = innerZip.getNextEntry();
            }
        }

        return out.toByteArray();
    }
}