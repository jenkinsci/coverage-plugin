package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTableModel.CoverageRow;
import io.jenkins.plugins.datatables.DetailedCell;
import io.jenkins.plugins.datatables.TableColumn;
import io.jenkins.plugins.datatables.TableColumn.ColumnBuilder;
import io.jenkins.plugins.datatables.TableColumn.ColumnCss;
import io.jenkins.plugins.datatables.TableColumn.ColumnType;
import io.jenkins.plugins.datatables.TableConfiguration;
import io.jenkins.plugins.datatables.TableModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.text.CaseUtils;

/**
 * Table model for the "Files" tab of the coverage results. In contrast to the {@link CoverageTableModel} of the legacy
 * view, this model provides the same set of columns for all metrics: for each metric, a column with the value, a
 * column with the delta with respect to the reference build, and a column that combines both in a single cell. Which
 * of these columns are actually visible is decided on the client side by the selected column mode (see
 * {@code files-model.js}). The column keys use the suffixes {@link #VALUE_SUFFIX}, {@link #DELTA_SUFFIX}, and
 * {@link #COMBINED_SUFFIX} so that the client can identify the columns of each mode.
 *
 * <p>
 * The rows are created by the wrapped {@link CoverageTableModel} so that the special semantics of the modified lines
 * and indirect coverage tables (e.g., the delta of the coverage of the modified lines) are retained.
 * </p>
 *
 * @author Ullrich Hafner
 */
class FilesTableModel extends TableModel {
    static final String VALUE_SUFFIX = "Value";
    static final String DELTA_SUFFIX = "Delta";
    static final String COMBINED_SUFFIX = "Combined";

    /** The metrics that are shown in the table (if available), in the order of the columns. */
    private static final List<Metric> METRICS = List.of(
            Metric.LINE,
            Metric.BRANCH,
            Metric.MCDC_PAIR,
            Metric.FUNCTION_CALL,
            Metric.STMT_DC,
            Metric.STMT_CC,
            Metric.CONDITION,
            Metric.BYTES,
            Metric.MUTATION,
            Metric.TEST_STRENGTH,
            Metric.LOC,
            Metric.NCSS,
            Metric.TESTS,
            Metric.CYCLOMATIC_COMPLEXITY,
            Metric.COGNITIVE_COMPLEXITY,
            Metric.NPATH_COMPLEXITY);

    private static final int FIRST_METRIC_PRIORITY = 10;
    private static final int PRIORITY_STEP = 10;

    private final CoverageTableModel rowProvider;

    /**
     * Creates a new instance of {@link FilesTableModel}.
     *
     * @param rowProvider
     *         the table model that provides the rows, the delta information, and the table configuration
     */
    FilesTableModel(final CoverageTableModel rowProvider) {
        super();

        this.rowProvider = rowProvider;
    }

    @Override
    public String getId() {
        return rowProvider.getId();
    }

    @Override
    public TableConfiguration getTableConfiguration() {
        return rowProvider.getTableConfiguration();
    }

    @Override
    public List<TableColumn> getColumns() {
        List<TableColumn> columns = new ArrayList<>();

        columns.add(new ColumnBuilder()
                .withHeaderLabel("Hash")
                .withDataPropertyKey("fileHash")
                .withHeaderClass(ColumnCss.HIDDEN)
                .build());
        columns.add(new ColumnBuilder()
                .withHeaderLabel("Modified")
                .withDataPropertyKey("modified")
                .withHeaderClass(ColumnCss.HIDDEN)
                .build());
        columns.add(new ColumnBuilder()
                .withHeaderLabel(Messages.Column_File())
                .withDataPropertyKey("fileName")
                .withDetailedCell()
                .withResponsivePriority(1)
                .build());
        columns.add(new ColumnBuilder()
                .withHeaderLabel(Messages.Column_Package())
                .withDataPropertyKey("packageName")
                .withResponsivePriority(50_000)
                .build());

        var priority = FIRST_METRIC_PRIORITY;
        for (Metric metric : getVisibleMetrics()) {
            var label = getLabel(metric);
            columns.add(createMetricColumn(label, metric, VALUE_SUFFIX, priority));
            if (hasDelta(metric)) {
                columns.add(createMetricColumn(Messages.Column_MetricDelta(label), metric, DELTA_SUFFIX, priority + 1));
                columns.add(createMetricColumn(label, metric, COMBINED_SUFFIX, priority));
            }
            priority += PRIORITY_STEP;
        }

        return columns;
    }

    private TableColumn createMetricColumn(
            final String label, final Metric metric, final String suffix, final int priority) {
        return new ColumnBuilder()
                .withHeaderLabel(label)
                .withDataPropertyKey(getKey(metric, suffix))
                .withDetailedCell()
                .withType(ColumnType.NUMBER)
                .withResponsivePriority(priority)
                .build();
    }

    @Override
    public List<Object> getRows() {
        var metrics = getVisibleMetrics();
        return rowProvider.createRows().stream()
                .map(row -> createRow(row, metrics))
                .map(Object.class::cast)
                .toList();
    }

    private Map<String, Object> createRow(final CoverageRow row, final List<Metric> metrics) {
        Map<String, Object> cells = new LinkedHashMap<>();
        cells.put("fileHash", row.getFileHash());
        cells.put("modified", row.getModified());
        cells.put("fileName", row.getFileName());
        cells.put("packageName", row.getPackageName());
        for (Metric metric : metrics) {
            addCell(cells, metric, VALUE_SUFFIX, row::createValueCell);
            if (hasDelta(metric)) {
                addCell(cells, metric, DELTA_SUFFIX, row::createDeltaCell);
                addCell(cells, metric, COMBINED_SUFFIX, row::createCombinedCell);
            }
        }
        return cells;
    }

    private void addCell(
            final Map<String, Object> cells,
            final Metric metric,
            final String suffix,
            final Function<Metric, DetailedCell<?>> cellFactory) {
        cells.put(getKey(metric, suffix), cellFactory.apply(metric));
    }

    private List<Metric> getVisibleMetrics() {
        return METRICS.stream().filter(rowProvider.getRoot()::containsMetric).toList();
    }

    private boolean hasDelta(final Metric metric) {
        return rowProvider.getDeltas().hasDelta(metric);
    }

    static String getKey(final Metric metric, final String suffix) {
        return CaseUtils.toCamelCase(metric.name(), false, '_') + suffix;
    }

    private static String getLabel(final Metric metric) {
        if (FileMetricAggregation.of(metric) == MetricAggregation.MAXIMUM) {
            return Messages.Column_MetricMaximum(metric.getLabel());
        }
        return metric.getLabel();
    }
}
