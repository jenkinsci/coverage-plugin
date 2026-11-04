package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.ClassNode;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.FileDeltas;
import edu.hm.hafner.coverage.FileDeltas.FileDeltasBuilder;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.MethodNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.PackageNode;
import edu.hm.hafner.coverage.Value;
import io.jenkins.plugins.coverage.metrics.color.ColorProviderFactory;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTableModel.InlineRowRenderer;
import io.jenkins.plugins.datatables.DetailedCell;
import io.jenkins.plugins.datatables.TableColumn;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link FilesTableModel}.
 *
 * @author Ullrich Hafner
 */
class FilesTableModelTest {
    private static final String PATH = "pkg/File.java";

    /**
     * Verifies that only the value columns are provided if there is no reference build. Method metrics show the
     * maximum value of all methods of a file.
     */
    @Test
    void shouldProvideValueColumnsOnlyWithoutReferenceBuild() {
        var model = createModel(FileDeltas.empty());

        assertThat(model.getColumns())
                .extracting(TableColumn::getHeaderLabel)
                .containsExactly("Hash", "Modified", "File", "Package", "Line", "LOC", "Max. Complexity");

        var row = getSingleRow(model);
        assertThat(row)
                .containsOnlyKeys(
                        "fileHash",
                        "modified",
                        "fileName",
                        "packageName",
                        "lineValue",
                        "locValue",
                        "cyclomaticComplexityValue");
        assertThat(getCell(row, "cyclomaticComplexityValue").getDisplay())
                .as("Maximum complexity of all methods")
                .contains(">7<");
        assertThat(getCell(row, "cyclomaticComplexityValue").getSort()).isEqualTo(7.0);
    }

    /**
     * Verifies that the value, delta, and combined columns are provided for all metrics with a delta. Metrics without
     * a delta (here: LOC) provide a value column only.
     */
    @Test
    void shouldProvideValueDeltaAndCombinedColumnsWithReferenceBuild() {
        var deltas = new FileDeltasBuilder()
                .addDelta(PATH, new Difference(Metric.LINE, 5))
                .addDelta(PATH, new Difference(Metric.CYCLOMATIC_COMPLEXITY, 2))
                .build();
        var model = createModel(deltas);

        assertThat(model.getColumns())
                .extracting(TableColumn::getHeaderLabel)
                .containsExactly(
                        "Hash",
                        "Modified",
                        "File",
                        "Package",
                        "Line",
                        "Line Δ",
                        "Line",
                        "LOC",
                        "Max. Complexity",
                        "Max. Complexity Δ",
                        "Max. Complexity");

        var row = getSingleRow(model);
        assertThat(row)
                .containsKeys(
                        "lineValue",
                        "lineDelta",
                        "lineCombined",
                        "cyclomaticComplexityValue",
                        "cyclomaticComplexityDelta",
                        "cyclomaticComplexityCombined");

        var combined = getCell(row, "cyclomaticComplexityCombined");
        assertThat(combined.getDisplay()).contains("coverage-cell-combined", "coverage-cell-delta", ">7<", "+2");
        assertThat(combined.getSort()).as("Combined cells are sorted by value").isEqualTo(7.0);
        assertThat(getCell(row, "cyclomaticComplexityDelta").getSort()).isEqualTo(2.0);
    }

    /**
     * Creates a table model for a single file with two methods.
     *
     * @param deltas
     *         the deltas of the file with respect to the reference build
     *
     * @return the table model
     */
    private FilesTableModel createModel(final FileDeltas deltas) {
        return new FilesTableModel(new CoverageTableModel(
                "absolute-coverage-table",
                createTree(),
                ChangeSet.empty(),
                deltas,
                new InlineRowRenderer(),
                ColorProviderFactory.createDefaultColorProvider()));
    }

    /**
     * Returns the single row of the specified table model.
     *
     * @param model
     *         the table model
     *
     * @return the cells of the row, mapped by the column key
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getSingleRow(final FilesTableModel model) {
        assertThat(model.getRows()).hasSize(1).first().isInstanceOf(Map.class);

        return (Map<String, Object>) model.getRows().get(0);
    }

    /**
     * Returns the cell of the specified column.
     *
     * @param row
     *         the row that contains the cell
     * @param key
     *         the key of the column
     *
     * @return the cell
     */
    private DetailedCell<?> getCell(final Map<String, Object> row, final String key) {
        assertThat(row.get(key)).isInstanceOf(DetailedCell.class);

        return (DetailedCell<?>) row.get(key);
    }

    private Node createTree() {
        var root = new ModuleNode("module");
        var packageNode = new PackageNode("pkg");
        root.addChild(packageNode);
        var file = new FileNode("File.java", PATH);
        packageNode.addChild(file);
        file.addValue(
                new CoverageBuilder(Metric.LINE).withCovered(8).withMissed(2).build());
        var classNode = new ClassNode("File");
        file.addChild(classNode);
        classNode.addChild(createMethod("simple", 3));
        classNode.addChild(createMethod("complex", 7));
        return root;
    }

    /**
     * Creates a method with the specified cyclomatic complexity.
     *
     * @param name
     *         the name of the method
     * @param complexity
     *         the cyclomatic complexity of the method
     *
     * @return the method
     */
    private MethodNode createMethod(final String name, final int complexity) {
        var method = new MethodNode(name, "()V");
        method.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, complexity));
        return method;
    }
}
