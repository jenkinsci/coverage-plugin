package io.jenkins.plugins.coverage.metrics.steps;

import static j2html.TagCreator.a;
import static j2html.TagCreator.span;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.MethodNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import hudson.Functions;
import io.jenkins.plugins.coverage.metrics.color.ColorProvider;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTableModel.CoverageRow;
import io.jenkins.plugins.datatables.DetailedCell;
import io.jenkins.plugins.datatables.TableColumn;
import io.jenkins.plugins.datatables.TableColumn.ColumnBuilder;
import io.jenkins.plugins.datatables.TableColumn.ColumnType;
import io.jenkins.plugins.datatables.TableConfiguration;
import io.jenkins.plugins.datatables.TableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Table model for the methods of a single file, see {@link FileDetailsModel}. The table shows one row for each method
 * of the file: the name of the method, the line number where the method starts, and the values of all metrics that
 * are available for at least one method of the file (e.g., the line and branch coverage or the cyclomatic
 * complexity). The line number links to the corresponding line of the source code that is shown below the table.
 *
 * @author Ullrich Hafner
 */
class MethodsTableModel extends TableModel {
    /** The ID of the methods table. */
    static final String TABLE_ID = "methods";
    /** The key of the column with the method name. */
    static final String METHOD_KEY = "method";
    /** The key of the column with the line number of the method. */
    static final String LINE_NUMBER_KEY = "lineNumber";

    private static final String NO_LINE_NUMBER = "-";
    private static final int NO_LINE_NUMBER_SORT = Integer.MAX_VALUE;
    private static final int FIRST_METRIC_PRIORITY = 10;
    private static final int PRIORITY_STEP = 10;

    private final FileNode file;
    private final ColorProvider colorProvider;

    /**
     * Creates a new instance of {@link MethodsTableModel}.
     *
     * @param file
     *         the file that contains the methods to show
     * @param colorProvider
     *         the color provider to use for the coverage cells
     */
    MethodsTableModel(final FileNode file, final ColorProvider colorProvider) {
        super();

        this.file = file;
        this.colorProvider = colorProvider;
    }

    @Override
    public String getId() {
        return TABLE_ID;
    }

    @Override
    public TableConfiguration getTableConfiguration() {
        return new TableConfiguration().responsive();
    }

    @Override
    public List<TableColumn> getColumns() {
        List<TableColumn> columns = new ArrayList<>();

        columns.add(new ColumnBuilder()
                .withHeaderLabel(Messages.Column_Method())
                .withDataPropertyKey(METHOD_KEY)
                .withDetailedCell()
                .withResponsivePriority(1)
                .build());
        columns.add(new ColumnBuilder()
                .withHeaderLabel(Messages.Column_LineNumber())
                .withDataPropertyKey(LINE_NUMBER_KEY)
                .withDetailedCell()
                .withType(ColumnType.NUMBER)
                .withResponsivePriority(2)
                .build());

        var priority = FIRST_METRIC_PRIORITY;
        for (Metric metric : getVisibleMetrics()) {
            columns.add(new ColumnBuilder()
                    .withHeaderLabel(metric.getLabel())
                    .withDataPropertyKey(getKey(metric))
                    .withDetailedCell()
                    .withType(ColumnType.NUMBER)
                    .withResponsivePriority(priority)
                    .build());
            priority += PRIORITY_STEP;
        }

        return columns;
    }

    @Override
    public List<Object> getRows() {
        var locale = Functions.getCurrentLocale();
        var metrics = getVisibleMetrics();
        return getMethods().stream()
                .map(method -> createRow(method, metrics, locale))
                .map(Object.class::cast)
                .toList();
    }

    private Map<String, Object> createRow(final MethodNode method, final List<Metric> metrics, final Locale locale) {
        Map<String, Object> cells = new LinkedHashMap<>();
        cells.put(METHOD_KEY, createMethodCell(method));
        cells.put(LINE_NUMBER_KEY, createLineNumberCell(method));
        for (Metric metric : metrics) {
            cells.put(getKey(metric), createValueCell(method, metric, locale));
        }
        return cells;
    }

    private DetailedCell<?> createMethodCell(final MethodNode method) {
        var cell = span().withTitle(method.getSignature())
                .withText(method.getMethodName())
                .render();
        return new DetailedCell<>(cell, method.getMethodName());
    }

    private DetailedCell<?> createLineNumberCell(final MethodNode method) {
        if (method.hasValidLineNumber()) {
            var line = method.getLineNumber();
            var cell = a().withHref("#" + line).withText(String.valueOf(line)).render();
            return new DetailedCell<>(cell, line);
        }
        return new DetailedCell<>(NO_LINE_NUMBER, NO_LINE_NUMBER_SORT);
    }

    private DetailedCell<?> createValueCell(final MethodNode method, final Metric metric, final Locale locale) {
        return getMethodValue(method, metric)
                .<DetailedCell<?>>map(value -> renderValue(value, locale))
                .orElse(CoverageTableModel.NO_COVERAGE);
    }

    private DetailedCell<?> renderValue(final Value value, final Locale locale) {
        if (value instanceof Coverage coverage) {
            return CoverageRow.createCoverageCell(coverage, colorProvider, locale);
        }
        return CoverageRow.createNumberCell(value, locale);
    }

    /**
     * Returns the methods of the file, sorted by their line numbers. Methods without a valid line number are shown at
     * the end of the table.
     *
     * @return the methods of the file
     */
    List<MethodNode> getMethods() {
        return file.getAllMethodNodes().stream()
                .sorted(Comparator.comparingInt(MethodsTableModel::getSortableLineNumber)
                        .thenComparing(MethodNode::getMethodName))
                .toList();
    }

    private static int getSortableLineNumber(final MethodNode method) {
        return method.hasValidLineNumber() ? method.getLineNumber() : NO_LINE_NUMBER_SORT;
    }

    /**
     * Returns the metrics that are shown in the table. A metric is shown only if at least one method of the file has
     * a value for this metric. The order of the metrics is the same as in the files table, see
     * {@link FilesTableModel#METRICS}.
     *
     * @return the metrics to show
     */
    List<Metric> getVisibleMetrics() {
        var methods = file.getAllMethodNodes();
        return FilesTableModel.METRICS.stream()
                .filter(metric -> methods.stream()
                        .anyMatch(method -> getMethodValue(method, metric).isPresent()))
                .toList();
    }

    private static Optional<Value> getMethodValue(final MethodNode method, final Metric metric) {
        return method.getValue(metric).filter(value -> !(value instanceof Coverage coverage) || coverage.isSet());
    }

    /**
     * Returns the key of the column that shows the values of the specified metric.
     *
     * @param metric
     *         the metric to get the key for
     *
     * @return the key of the column
     */
    static String getKey(final Metric metric) {
        return FilesTableModel.getKey(metric, FilesTableModel.VALUE_SUFFIX);
    }
}
