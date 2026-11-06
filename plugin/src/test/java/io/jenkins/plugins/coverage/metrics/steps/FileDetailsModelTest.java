package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.ClassNode;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FileDeltas;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.MethodNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.steps.FileDetailsModel.MetricSummary;
import io.jenkins.plugins.util.QualityGateResult;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link FileDetailsModel}.
 *
 * @author Ullrich Hafner
 */
class FileDetailsModelTest extends AbstractCoverageTest {
    private static final String ID = "id";
    private static final String PATH = "pkg/File.java";

    /**
     * Verifies the basic properties of the model.
     */
    @Test
    void shouldProvideDisplayNameAndOwner() {
        Run<?, ?> run = mock(Run.class);
        var model = createModel(new FileNode("File.java", PATH), run);

        assertThat(model.getDisplayName()).isEqualTo("File.java");
        assertThat(model.getPath()).isEqualTo(PATH);
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(run);
        assertThat(model.getTab()).isNotNull();
    }

    /**
     * Verifies that a file without values and methods provides neither summaries nor methods.
     */
    @Test
    void shouldProvideNothingForEmptyFile() {
        var model = createModel(new FileNode("File.java", PATH), mock(Run.class));

        assertThat(model.getSummaries()).isEmpty();
        assertThat(model.hasMethods()).isFalse();
    }

    /**
     * Verifies that the summaries contain all metrics of the file in the order of the files table. The lines of code
     * are derived from the line coverage.
     */
    @Test
    void shouldProvideSummariesOfAllMetrics() {
        var model = createModel(createFile(), mock(Run.class));

        assertThat(model.hasMethods()).isTrue();
        assertThat(model.getSummaries())
                .extracting(MetricSummary::name)
                .containsExactly(
                        Metric.LINE.getDisplayName(),
                        Metric.LOC.getDisplayName(),
                        Metric.CYCLOMATIC_COMPLEXITY.getDisplayName());
        assertThat(model.getSummaries())
                .extracting(MetricSummary::label)
                .containsExactly(
                        FilesTableModel.getLabel(Metric.LINE),
                        FilesTableModel.getLabel(Metric.LOC),
                        FilesTableModel.getLabel(Metric.CYCLOMATIC_COMPLEXITY));
        assertThat(model.getSummaries().get(0).cell()).contains("coverage-cell-outer");
    }

    /**
     * Verifies that the methods table is provided for the corresponding ID only.
     */
    @Test
    void shouldProvideMethodsTable() {
        var model = createModel(createFile(), mock(Run.class));

        assertThat(model.getTableModel(MethodsTableModel.TABLE_ID)).isInstanceOf(MethodsTableModel.class);
        assertThatThrownBy(() -> model.getTableModel("unknown"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("unknown");
    }

    private FileNode createFile() {
        var file = new FileNode("File.java", PATH);
        file.addValue(
                new CoverageBuilder(Metric.LINE).withCovered(8).withMissed(2).build());
        var classNode = new ClassNode("File");
        file.addChild(classNode);
        var method = new MethodNode("method", "()V", 5);
        method.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 4));
        classNode.addChild(method);
        return file;
    }

    /**
     * Creates the model for the specified file. The file is added to a new module.
     *
     * @param file
     *         the file to show
     * @param run
     *         the owner of the coverage results
     *
     * @return the created model
     */
    private FileDetailsModel createModel(final FileNode file, final Run<?, ?> run) {
        var module = new ModuleNode("module");
        module.addChild(file);
        var parent = new CoverageViewModel(
                run,
                ID,
                "",
                module,
                ChangeSet.empty(),
                FileDeltas.empty(),
                createStatistics(),
                new QualityGateResult(),
                "-",
                new FilteredLog("Errors"),
                Function.identity(),
                Function.identity());
        return new FileDetailsModel(parent, file);
    }
}
