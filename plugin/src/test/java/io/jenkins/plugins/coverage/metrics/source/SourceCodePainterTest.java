package io.jenkins.plugins.coverage.metrics.source;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;

import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.util.FilteredLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import hudson.FilePath;
import hudson.model.Run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link SourceCodePainter}.
 * Verifies that source painting handles files with extended ASCII characters
 * and the printer factory selection fix (JENKINS-75871).

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

    // -----------------------------------------------------------------------------------------
    // Tests for JENKINS-75871: printer factory is determined once per build, not once per file.
    // -----------------------------------------------------------------------------------------

    @Test
    @Issue("JENKINS-75871")
    void shouldSelectCoverageSourcePrinterForStandardCoverage() {
        var root = new ModuleNode("root");
        root.addValue(new CoverageBuilder(Metric.LINE).withCovered(5).withMissed(2).build());

        Function<FileNode, CoverageSourcePrinter> factory = createPainterAndGetFactory(root);
        var fileNode = new FileNode("", "Foo.java");
        var printer = factory.apply(fileNode);

        assertThat(printer).isExactlyInstanceOf(CoverageSourcePrinter.class);
    }

    @Test
    @Issue("JENKINS-75871")
    void shouldSelectMutationSourcePrinterWhenRootHasMutationMetric() {
        var root = new ModuleNode("root");
        root.addValue(new CoverageBuilder(Metric.LINE).withCovered(5).withMissed(2).build());
        root.addValue(new CoverageBuilder(Metric.MUTATION).withCovered(3).withMissed(1).build());

        Function<FileNode, CoverageSourcePrinter> factory = createPainterAndGetFactory(root);
        var fileNode = new FileNode("", "Foo.java");
        var printer = factory.apply(fileNode);

        assertThat(printer).isExactlyInstanceOf(MutationSourcePrinter.class);
    }

    @Test
    @Issue("JENKINS-75871")
    void shouldSelectVectorCastSourcePrinterWhenRootHasMcdcPairMetric() {
        var root = new ModuleNode("root");
        root.addValue(new CoverageBuilder(Metric.LINE).withCovered(5).withMissed(2).build());
        root.addValue(new CoverageBuilder(Metric.MCDC_PAIR).withCovered(4).withMissed(1).build());

        Function<FileNode, CoverageSourcePrinter> factory = createPainterAndGetFactory(root);
        var fileNode = new FileNode("", "Foo.java");
        var printer = factory.apply(fileNode);

        assertThat(printer).isExactlyInstanceOf(VectorCastSourcePrinter.class);
    }

    @Test
    @Issue("JENKINS-75871")
    void shouldSelectVectorCastSourcePrinterWhenRootHasFunctionCallMetric() {
        var root = new ModuleNode("root");
        root.addValue(new CoverageBuilder(Metric.LINE).withCovered(5).withMissed(2).build());
        root.addValue(new CoverageBuilder(Metric.FUNCTION_CALL).withCovered(10).withMissed(2).build());

        Function<FileNode, CoverageSourcePrinter> factory = createPainterAndGetFactory(root);
        var fileNode = new FileNode("", "Foo.java");
        var printer = factory.apply(fileNode);

        assertThat(printer).isExactlyInstanceOf(VectorCastSourcePrinter.class);
    }

    @Test
    @Issue("JENKINS-75871")
    void shouldUseSamePrinterTypeForAllFilesWhenRootHasMutationMetric() {
        var root = new ModuleNode("root");
        root.addValue(new CoverageBuilder(Metric.LINE).withCovered(5).withMissed(2).build());
        root.addValue(new CoverageBuilder(Metric.MUTATION).withCovered(3).withMissed(1).build());

        Function<FileNode, CoverageSourcePrinter> factory = createPainterAndGetFactory(root);

        var files = List.of(
                new FileNode("", "Foo.java"),
                new FileNode("", "Bar.java"),
                new FileNode("", "Baz.java")
        );
        for (FileNode file : files) {
            assertThat(factory.apply(file)).isExactlyInstanceOf(MutationSourcePrinter.class);
        }
    }

    @Test
    @Issue("JENKINS-75871")
    void shouldUseSamePrinterTypeForAllFilesWhenRootHasStandardCoverage() {
        var root = new ModuleNode("root");
        root.addValue(new CoverageBuilder(Metric.LINE).withCovered(5).withMissed(2).build());

        Function<FileNode, CoverageSourcePrinter> factory = createPainterAndGetFactory(root);

        var files = List.of(
                new FileNode("", "Alpha.java"),
                new FileNode("", "Beta.java"),
                new FileNode("", "Gamma.java")
        );
        for (FileNode file : files) {
            assertThat(factory.apply(file)).isExactlyInstanceOf(CoverageSourcePrinter.class);
        }
    }

    @Test
    @Issue("JENKINS-75871")
    void shouldUseSamePrinterTypeForAllFilesWhenRootHasVectorCastMetrics() {
        var root = new ModuleNode("root");
        root.addValue(new CoverageBuilder(Metric.LINE).withCovered(10).withMissed(3).build());
        root.addValue(new CoverageBuilder(Metric.MCDC_PAIR).withCovered(4).withMissed(1).build());
        root.addValue(new CoverageBuilder(Metric.FUNCTION_CALL).withCovered(8).withMissed(2).build());

        Function<FileNode, CoverageSourcePrinter> factory = createPainterAndGetFactory(root);

        var files = List.of(
                new FileNode("", "driver.c"),
                new FileNode("", "database.c"),
                new FileNode("", "network.c")
        );
        for (FileNode file : files) {
            assertThat(factory.apply(file)).isExactlyInstanceOf(VectorCastSourcePrinter.class);
        }
    }

    /**
     * Creates a {@link SourceCodePainter} with mocked dependencies and returns the printer factory
     * derived from the given root node. This exposes the package-private {@code createPrinterFactory}
     * method for testing.
     *
     * @param rootNode
     *         the root coverage node
     *
     * @return the printer factory for the given root node
     */
    private Function<FileNode, CoverageSourcePrinter> createPainterAndGetFactory(final ModuleNode rootNode) {
        var build = mock(Run.class);
        var workspace = new FilePath(Path.of(".").toFile());
        var painter = new SourceCodePainter(build, workspace, "coverage");
        return painter.createPrinterFactory(rootNode);
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
