package io.jenkins.plugins.coverage.metrics.source;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.util.FilteredLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
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

    private static final byte[] CAFE_NBSP_CORPORATION_UTF8 = {
            (byte) 'C', (byte) 'a', (byte) 'f',
            (byte) 0xC3, (byte) 0xA9,
            (byte) 0xC2, (byte) 0xA0,
            (byte) 'C', (byte) 'o', (byte) 'r', (byte) 'p',
            (byte) 'o', (byte) 'r', (byte) 'a', (byte) 't',
            (byte) 'i', (byte) 'o', (byte) 'n'
    };

    private static final byte[] REPLACEMENT_CHAR_UTF8 = {
            (byte) 0xEF, (byte) 0xBF, (byte) 0xBD
    };

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

        assertThat(containsBytes(paintedBytes, CAFE_NBSP_CORPORATION_UTF8))
                .as("Painted HTML must contain UTF-8 bytes for 'Cafe-acute NBSP Corporation' "
                        + "(43 61 66 C3 A9 C2 A0 43 6F 72 70 6F 72 61 74 69 6F 6E). "
                        + "Actual painted bytes: " + toHex(paintedBytes))
                .isTrue();

        assertThat(containsBytes(paintedBytes, REPLACEMENT_CHAR_UTF8))
                .as("Painted HTML must NOT contain UTF-8 replacement character EF BF BD "
                        + "— that would mean 0xE9 was not decoded correctly as windows-1252")
                .isFalse();
    }

    private boolean containsBytes(final byte[] haystack, final byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            boolean match = true;

            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return true;
            }
        }

        return false;
    }

    private String toHex(final byte[] bytes) {
        var sb = new StringBuilder();

        for (byte b : bytes) {
            if (sb.length() > 0) {
                sb.append(' ');
            }

            sb.append(String.format("%02X", b & 0xFF));
        }

        return sb.toString();
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