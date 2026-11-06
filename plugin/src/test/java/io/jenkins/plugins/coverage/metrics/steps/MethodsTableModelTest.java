package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;

import edu.hm.hafner.coverage.ClassNode;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.MethodNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import io.jenkins.plugins.coverage.metrics.color.ColorProviderFactory;
import io.jenkins.plugins.datatables.DetailedCell;
import io.jenkins.plugins.datatables.TableColumn;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link MethodsTableModel}.
 *
 * @author Ullrich Hafner
 */
class MethodsTableModelTest {
    private static final String LINE_KEY = MethodsTableModel.getKey(Metric.LINE);
    private static final String LOC_KEY = MethodsTableModel.getKey(Metric.LOC);
    private static final String COMPLEXITY_KEY = MethodsTableModel.getKey(Metric.CYCLOMATIC_COMPLEXITY);

    /**
     * Verifies that a file without methods provides only the method name and line number columns and no rows.
     */
    @Test
    void shouldProvideNoRowsForFileWithoutMethods() {
        var model = createModel(new FileNode("Empty.java", "pkg/Empty.java"));

        assertThat(model.getId()).isEqualTo(MethodsTableModel.TABLE_ID);
        assertThat(model.getColumns())
                .extracting(TableColumn::getHeaderLabel)
                .containsExactly(Messages.Column_Method(), Messages.Column_LineNumber());
        assertThat(model.getRows()).isEmpty();
    }

    /**
     * Verifies that only the metrics that are available for at least one method are shown as columns. The lines of
     * code are derived from the line coverage.
     */
    @Test
    void shouldShowColumnsOfAvailableMetricsOnly() {
        var model = createModel(createFile(createMethod("first", 10, 4, 1, 3), createMethod("second", 20, 0, 5, 1)));

        assertThat(model.getVisibleMetrics()).containsExactly(Metric.LINE, Metric.LOC, Metric.CYCLOMATIC_COMPLEXITY);
        assertThat(model.getColumns())
                .extracting(TableColumn::getHeaderLabel)
                .containsExactly(
                        Messages.Column_Method(),
                        Messages.Column_LineNumber(),
                        Metric.LINE.getLabel(),
                        Metric.LOC.getLabel(),
                        Metric.CYCLOMATIC_COMPLEXITY.getLabel());
    }

    /**
     * Verifies that the rows are sorted by the line number and contain the rendered values of the methods.
     */
    @Test
    void shouldCreateRowsSortedByLineNumber() {
        var model = createModel(createFile(createMethod("second", 20, 0, 5, 1), createMethod("first", 10, 4, 1, 3)));

        var rows = getRows(model);
        assertThat(rows).hasSize(2);

        var first = rows.get(0);
        assertThat(first)
                .containsOnlyKeys(
                        MethodsTableModel.METHOD_KEY,
                        MethodsTableModel.LINE_NUMBER_KEY,
                        LINE_KEY,
                        LOC_KEY,
                        COMPLEXITY_KEY);
        assertThat(getCell(first, MethodsTableModel.METHOD_KEY).getDisplay())
                .contains(">first<")
                .contains("title=\"()V\"");
        assertThat(getCell(first, MethodsTableModel.LINE_NUMBER_KEY).getDisplay())
                .contains("href=\"#10\"")
                .contains(">10<");
        assertThat(getCell(first, MethodsTableModel.LINE_NUMBER_KEY).getSort()).isEqualTo(10);
        assertThat(getCell(first, LINE_KEY).getSort()).isEqualTo(80.0);
        assertThat(getCell(first, LOC_KEY).getSort())
                .as("LOC derived from the line coverage")
                .isEqualTo(5.0);
        assertThat(getCell(first, COMPLEXITY_KEY).getDisplay()).contains(">3<");

        var second = rows.get(1);
        assertThat(getCell(second, MethodsTableModel.METHOD_KEY).getSort()).isEqualTo("second");
        assertThat(getCell(second, LINE_KEY).getSort()).isEqualTo(0.0);
    }

    /**
     * Verifies that methods without a valid line number are shown at the end of the table without a link.
     */
    @Test
    void shouldShowMethodsWithoutLineNumberAtTheEnd() {
        var withoutLine = new MethodNode("unknown", "()V");
        withoutLine.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 2));
        var model = createModel(createFile(withoutLine, createMethod("known", 5, 1, 1, 1)));

        var rows = getRows(model);
        assertThat(rows).hasSize(2);
        assertThat(getCell(rows.get(0), MethodsTableModel.METHOD_KEY).getSort()).isEqualTo("known");

        var last = rows.get(1);
        assertThat(getCell(last, MethodsTableModel.LINE_NUMBER_KEY).getDisplay())
                .isEqualTo("-");
        assertThat(getCell(last, LINE_KEY)).as("Method has no line coverage").isSameAs(CoverageTableModel.NO_COVERAGE);
    }

    private MethodsTableModel createModel(final FileNode file) {
        return new MethodsTableModel(file, ColorProviderFactory.createDefaultColorProvider());
    }

    /**
     * Returns the rows of the specified table model.
     *
     * @param model
     *         the table model
     *
     * @return the rows, each row maps the column keys to the cells
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getRows(final MethodsTableModel model) {
        return model.getRows().stream().map(row -> (Map<String, Object>) row).toList();
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

    /**
     * Creates a file with a single class that contains the specified methods.
     *
     * @param methods
     *         the methods of the class
     *
     * @return the created file
     */
    private FileNode createFile(final MethodNode... methods) {
        var file = new FileNode("File.java", "pkg/File.java");
        var classNode = new ClassNode("File");
        file.addChild(classNode);
        for (MethodNode method : methods) {
            classNode.addChild(method);
        }
        return file;
    }

    /**
     * Creates a method with line coverage and cyclomatic complexity.
     *
     * @param name
     *         the name of the method
     * @param line
     *         the line number where the method starts
     * @param covered
     *         the number of covered lines
     * @param missed
     *         the number of missed lines
     * @param complexity
     *         the cyclomatic complexity
     *
     * @return the created method
     */
    private MethodNode createMethod(
            final String name, final int line, final int covered, final int missed, final int complexity) {
        var method = new MethodNode(name, "()V", line);
        method.addValue(new CoverageBuilder(Metric.LINE)
                .withCovered(covered)
                .withMissed(missed)
                .build());
        method.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, complexity));
        return method;
    }
}
