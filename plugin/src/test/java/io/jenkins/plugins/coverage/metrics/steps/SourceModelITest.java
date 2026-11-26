package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.ChangeSet.ChangeSetBuilder;
import edu.hm.hafner.coverage.FileDeltas;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.FileNodeFixture;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageITest;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.source.SourceCodeFacade;
import io.jenkins.plugins.prism.SourceCodeViewModel;
import io.jenkins.plugins.util.QualityGateResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the class {@link SourceModel}. These tests require a running Jenkins instance, since the source code view
 * checks the permission to view the source code.
 *
 * @author Ullrich Hafner
 */
class SourceModelITest extends AbstractCoverageITest {
    private static final String ID = "coverage";
    private static final String PATH = "src/main/java/Example.java";
    private static final String URL = "job/example/1/";
    /** The name of the painted HTML file of older builds: the sanitized path. */
    private static final String PAINTED_FILE_NAME = "src_main_java_Example.java";

    /**
     * Verifies that the original source file is shown with the coverage of the lines: the lines are highlighted by
     * markers and the number of covered branches is shown as annotation.
     *
     * @param buildFolder
     *         the build folder of the run
     *
     * @throws IOException
     *         if the source file could not be stored
     */
    @Test
    void shouldShowSourceCodeWithCoverage(@TempDir final Path buildFolder) throws IOException {
        storeOriginal(buildFolder, "class Example {\n    int a;\n    int b;\n}");
        var model = createModel(buildFolder, ChangeSet.empty());

        assertThat(model.getDisplayName()).isEqualTo("Example.java");
        assertThat(model.getPath()).isEqualTo(PATH);
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getDetailsUrl()).isEqualTo(URL + ID + "/" + PATH.hashCode());
        assertThat(model.hasPermissionToViewSourceCode()).isTrue();
        assertThat(model.isSourceFileAvailable()).isTrue();
        assertThat(model.isPaintedSourceFile()).isFalse();
        assertThat(model.hasModifiedLines()).isFalse();
        assertThat(model.hasIndirectCoverageChanges()).isFalse();

        assertThat(model.getSourceCode()).isInstanceOfSatisfying(SourceCodeViewModel.class, view -> {
            var document = Jsoup.parse(view.getSourceCode());
            assertThat(document.select(".prism-source__raw > code").text())
                    .contains("class Example {", "int a;", "int b;");
            assertThat(document.getElementsByClass("prism-marker"))
                    .extracting(Element::id, e -> e.attr("data-severity"), e -> e.attr("data-marker-coverage"))
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("project-L2-2", "success", "covered"),
                            org.assertj.core.groups.Tuple.tuple("project-L3-3", "error", "missed"));
            assertThat(document.getElementsByClass("prism-line__annotation"))
                    .extracting(e -> e.attr("data-line"), Element::text)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("2", "1"),
                            org.assertj.core.groups.Tuple.tuple("3", "0"));
        });
    }

    /**
     * Verifies that the baselines for the modified lines and the indirect coverage changes are available if the file
     * contains such changes.
     *
     * @param buildFolder
     *         the build folder of the run
     *
     * @throws IOException
     *         if the source file could not be stored
     */
    @Test
    void shouldProvideBaselinesOfChanges(@TempDir final Path buildFolder) throws IOException {
        storeOriginal(buildFolder, "class Example {\n    int a;\n    int b;\n}");
        var changes = new ChangeSetBuilder()
                .addModifiedLines(PATH, 2)
                .addIndirectCoverageChange(PATH, 3, -1)
                .build();

        var model = createModel(buildFolder, changes);

        assertThat(model.hasModifiedLines()).isTrue();
        assertThat(model.hasIndirectCoverageChanges()).isTrue();
        assertThat(model.getSourceCode())
                .isInstanceOfSatisfying(
                        SourceCodeViewModel.class,
                        view -> assertThat(Jsoup.parse(view.getSourceCode()).getElementsByClass("prism-marker"))
                                .extracting(
                                        Element::id,
                                        e -> e.attr("data-marker-baseline"),
                                        e -> e.attr("data-marker-modified"))
                                .containsExactly(
                                        org.assertj.core.groups.Tuple.tuple("project-L2-2", "project", "true"),
                                        org.assertj.core.groups.Tuple.tuple("project-L3-3", "project", "false"),
                                        org.assertj.core.groups.Tuple.tuple("indirect-L3-3", "indirect", "false")));
    }

    /**
     * Verifies that the painted HTML file of an older build is shown if the original source file is not available.
     *
     * @param buildFolder
     *         the build folder of the run
     *
     * @throws IOException
     *         if the source file could not be stored
     */
    @Test
    void shouldShowPaintedSourceCodeOfOlderBuilds(@TempDir final Path buildFolder) throws IOException {
        var html = "<tr class=\"coverFull\"><td class=\"code\">class Example {}</td></tr>";
        storeZipped(buildFolder, PAINTED_FILE_NAME + ".zip", PAINTED_FILE_NAME, html);

        var model = createModel(buildFolder, ChangeSet.empty());

        assertThat(model.isSourceFileAvailable()).isTrue();
        assertThat(model.isPaintedSourceFile()).isTrue();
        assertThat(model.getPaintedSourceCode()).isEqualTo(html);
    }

    /**
     * Verifies that a source file that has not been stored is not available.
     *
     * @param buildFolder
     *         the build folder of the run
     */
    @Test
    void shouldNotProvideMissingSourceFile(@TempDir final Path buildFolder) {
        var model = createModel(buildFolder, ChangeSet.empty());

        assertThat(model.isSourceFileAvailable()).isFalse();
        assertThat(model.isPaintedSourceFile()).isFalse();
    }

    private void storeOriginal(final Path buildFolder, final String content) throws IOException {
        storeZipped(buildFolder, SourceCodeFacade.getSourceFileName(PATH), "Example.java", content);
    }

    private void storeZipped(final Path buildFolder, final String zipName, final String entry, final String content)
            throws IOException {
        var folder =
                Files.createDirectories(buildFolder.resolve("coverage-sources").resolve(ID));
        try (var zip = new ZipOutputStream(Files.newOutputStream(folder.resolve(zipName)))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /**
     * Creates the model for a file with the lines 2 (covered) and 3 (not covered).
     *
     * @param buildFolder
     *         the build folder of the run
     * @param changes
     *         the code changes with respect to the reference build
     *
     * @return the created model
     */
    private SourceModel createModel(final Path buildFolder, final ChangeSet changes) {
        Run<?, ?> run = mock(Run.class);
        when(run.getRootDir()).thenReturn(buildFolder.toFile());
        when(run.getUrl()).thenReturn(URL);

        var file = new FileNode("Example.java", PATH);
        new FileNodeFixture(file).withCounters(2, 1, 0).withCounters(3, 0, 1);
        var module = new ModuleNode("module");
        module.addChild(file);

        var parent = new CoverageViewModel(
                run,
                ID,
                "",
                module,
                changes,
                FileDeltas.empty(),
                AbstractCoverageTest.createStatistics(),
                new QualityGateResult(),
                "-",
                new FilteredLog("Errors"),
                Function.identity(),
                Function.identity());
        return new SourceModel(parent, file);
    }
}
