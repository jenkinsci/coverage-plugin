package io.jenkins.plugins.coverage.metrics.steps;

import static j2html.TagCreator.a;
import static j2html.TagCreator.div;
import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.span;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileDeltas;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Metric.MetricTendency;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import hudson.Functions;
import io.jenkins.plugins.coverage.metrics.color.ColorProvider;
import io.jenkins.plugins.coverage.metrics.color.ColorProvider.DisplayColors;
import io.jenkins.plugins.coverage.metrics.color.CoverageChangeTendency;
import io.jenkins.plugins.coverage.metrics.color.CoverageLevel;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.coverage.metrics.source.SourceCodeFacade;
import io.jenkins.plugins.datatables.DetailedCell;
import io.jenkins.plugins.datatables.TableColumn;
import io.jenkins.plugins.datatables.TableColumn.ColumnBuilder;
import io.jenkins.plugins.datatables.TableColumn.ColumnCss;
import io.jenkins.plugins.datatables.TableColumn.ColumnType;
import io.jenkins.plugins.datatables.TableConfiguration;
import io.jenkins.plugins.datatables.TableConfiguration.SelectStyle;
import io.jenkins.plugins.datatables.TableModel;
import j2html.tags.ContainerTag;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;

/**
 * UI table model for the coverage details table.
 */
class CoverageTableModel extends TableModel {
    private static final int NO_COVERAGE_SORT = -1_000;
    private static final SourceCodeFacade SOURCE_CODE_FACADE = new SourceCodeFacade();

    /**
     * The alpha value for colors to be used to highlight the coverage within the table view.
     */
    private static final int TABLE_COVERAGE_COLOR_ALPHA = 80;

    static final DetailedCell<Integer> NO_COVERAGE =
            new DetailedCell<>(Messages.Coverage_Not_Available(), NO_COVERAGE_SORT);
    private static final String SKIP_DELTA = "";

    private final ColorProvider colorProvider;
    private final Node root;
    private final ChangeSet changes;
    private final FileDeltas deltas;
    private final RowRenderer renderer;
    private final String id;

    CoverageTableModel(
            final String id,
            final Node root,
            final ChangeSet changes,
            final FileDeltas deltas,
            final RowRenderer renderer,
            final ColorProvider colors) {
        super();

        this.id = id;
        this.root = root;
        this.changes = changes;
        this.deltas = deltas;
        this.renderer = renderer;
        colorProvider = colors;
    }

    RowRenderer getRenderer() {
        return renderer;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public TableConfiguration getTableConfiguration() {
        var tableConfiguration = new TableConfiguration();
        tableConfiguration.responsive();
        if (getId().contains("inline")) {
            tableConfiguration.select(SelectStyle.SINGLE);
        }
        renderer.configureTable(tableConfiguration);
        return tableConfiguration;
    }

    @Override
    public List<TableColumn> getColumns() {
        List<TableColumn> columns = new ArrayList<>();

        var fileHash = new ColumnBuilder()
                .withHeaderLabel("Hash")
                .withDataPropertyKey("fileHash")
                .withHeaderClass(ColumnCss.HIDDEN)
                .build();
        columns.add(fileHash);
        var modified = new ColumnBuilder()
                .withHeaderLabel("Modified")
                .withDataPropertyKey("modified")
                .withHeaderClass(ColumnCss.HIDDEN)
                .build();
        columns.add(modified);
        var fileName = new ColumnBuilder()
                .withHeaderLabel(Messages.Column_File())
                .withDataPropertyKey("fileName")
                .withDetailedCell()
                .withResponsivePriority(1)
                .build();
        columns.add(fileName);
        var packageName = new ColumnBuilder()
                .withHeaderLabel(Messages.Column_Package())
                .withDataPropertyKey("packageName")
                .withResponsivePriority(50_000)
                .build();
        columns.add(packageName);

        configureValueColumn(
                "lineCoverage",
                Metric.LINE,
                Messages.Column_LineCoverage(),
                Messages.Column_DeltaLineCoverage("Δ"),
                columns);
        configureValueColumn(
                "branchCoverage",
                Metric.BRANCH,
                Messages.Column_BranchCoverage(),
                Messages.Column_DeltaBranchCoverage("Δ"),
                columns);

        /* VectorCAST metrics */
        configureValueColumn("mcdcPairCoverage", Metric.MCDC_PAIR, Messages.Column_MCDCPairs(), SKIP_DELTA, columns);
        configureValueColumn(
                "functionCallCoverage", Metric.FUNCTION_CALL, Messages.Column_FunctionCall(), SKIP_DELTA, columns);

        configureValueColumn("stmtDcCoverage", Metric.STMT_DC, Metric.STMT_DC.getDisplayName(), SKIP_DELTA, columns);
        configureValueColumn("stmtCcCoverage", Metric.STMT_CC, Metric.STMT_CC.getDisplayName(), SKIP_DELTA, columns);
        configureValueColumn(
                "conditionCoverage", Metric.CONDITION, Metric.CONDITION.getDisplayName(), SKIP_DELTA, columns);
        configureValueColumn("bytesCoverage", Metric.BYTES, Metric.BYTES.getDisplayName(), SKIP_DELTA, columns);

        configureValueColumn(
                "mutationCoverage",
                Metric.MUTATION,
                Messages.Column_MutationCoverage(),
                Messages.Column_DeltaMutationCoverage("Δ"),
                columns);
        configureValueColumn(
                "testStrength",
                Metric.TEST_STRENGTH,
                Messages.Column_TestStrength(),
                Messages.Column_DeltaTestStrength("Δ"),
                columns);

        var entries = new EnumMap<>(Map.of(
                Metric.LOC, 200,
                Metric.TESTS, 500,
                Metric.CYCLOMATIC_COMPLEXITY, 500,
                Metric.COGNITIVE_COMPLEXITY, 500,
                Metric.NPATH_COMPLEXITY, 500,
                Metric.NCSS, 500));

        for (var column : entries.entrySet()) {
            var metric = column.getKey();
            if (root.containsMetric(metric)) {
                var tmp = new ColumnBuilder()
                        .withHeaderLabel(metric.getLabel())
                        .withDataPropertyKey(CaseUtils.toCamelCase(metric.name(), false, '_'))
                        .withResponsivePriority(column.getValue())
                        .withType(ColumnType.NUMBER)
                        .build();
                columns.add(tmp);
            }
        }
        return columns;
    }

    private void configureValueColumn(
            final String key,
            final Metric metric,
            final String headerLabel,
            final String deltaHeaderLabel,
            final List<TableColumn> columns) {
        if (root.containsMetric(metric)) {
            var lineCoverage = new ColumnBuilder()
                    .withHeaderLabel(headerLabel)
                    .withDataPropertyKey(key)
                    .withDetailedCell()
                    .withType(ColumnType.NUMBER)
                    .withResponsivePriority(1)
                    .build();
            columns.add(lineCoverage);
            if (StringUtils.isNotEmpty(deltaHeaderLabel) && hasDelta(metric)) {
                var lineCoverageDelta = new ColumnBuilder()
                        .withHeaderLabel(deltaHeaderLabel)
                        .withDataPropertyKey(key + "Delta")
                        .withDetailedCell()
                        .withType(ColumnType.NUMBER)
                        .withResponsivePriority(2)
                        .build();
                columns.add(lineCoverageDelta);
            }
        }
    }

    private boolean hasDelta(final Metric metric) {
        return deltas.hasDelta(metric);
    }

    @Override
    public List<Object> getRows() {
        return new ArrayList<>(createRows());
    }

    /**
     * Creates the rows of this table, one row for each file.
     *
     * @return the rows of this table
     */
    List<CoverageRow> createRows() {
        Locale browserLocale = Functions.getCurrentLocale();
        return root.getAllFileNodes().stream()
                .map(file -> new CoverageRow(file, changes, deltas, browserLocale, renderer, colorProvider))
                .collect(Collectors.toList());
    }

    protected Node getRoot() {
        return root;
    }

    protected ChangeSet getChanges() {
        return changes;
    }

    protected FileDeltas getDeltas() {
        return deltas;
    }

    protected ColorProvider getColorProvider() {
        return colorProvider;
    }

    /**
     * UI row model for the coverage details table.
     */
    @SuppressWarnings("PMD.PublicMemberInNonPublicType")
    static class CoverageRow {
        private static final String COVERAGE_COLUMN_OUTER = "coverage-cell-outer float-end";
        private static final String COVERAGE_COLUMN_INNER = "coverage-jenkins-cell-inner";
        private static final String COMBINED_CELL = "coverage-cell-combined";
        private static final String DELTA_BADGE = "coverage-cell-delta";
        private static final ElementFormatter FORMATTER = new ElementFormatter();

        private static final Value ZERO_LOC = new Value(Metric.LOC, 0);
        private static final Value ZERO_TESTS = new Value(Metric.TESTS, 0);
        private static final Value ZERO_CYCLOMATIC_COMPLEXITY = new Value(Metric.CYCLOMATIC_COMPLEXITY, 0);
        private static final Value ZERO_COGNITIVE_COMPLEXITY = new Value(Metric.COGNITIVE_COMPLEXITY, 0);
        private static final Value ZERO_NPATH_COMPLEXITY = new Value(Metric.NPATH_COMPLEXITY, 0);
        private static final Value ZERO_NCSS = new Value(Metric.NCSS, 0);

        private final FileNode file;
        private final ChangeSet changes;
        private final FileDeltas deltas;
        private final Locale browserLocale;
        private final RowRenderer renderer;
        private final ColorProvider colorProvider;

        CoverageRow(
                final FileNode file,
                final ChangeSet changes,
                final FileDeltas deltas,
                final Locale browserLocale,
                final RowRenderer renderer,
                final ColorProvider colors) {
            this.file = file;
            this.changes = changes;
            this.deltas = deltas;
            this.browserLocale = browserLocale;
            this.renderer = renderer;
            colorProvider = colors;
        }

        ChangeSet getChanges() {
            return changes;
        }

        FileDeltas getDeltas() {
            return deltas;
        }

        public String getFileHash() {
            return String.valueOf(file.getRelativePath().hashCode());
        }

        @SuppressWarnings("PMD.BooleanGetMethodName")
        public boolean getModified() {
            return changes.hasModifiedLines(file.getRelativePath());
        }

        public DetailedCell<?> getFileName() {
            return new DetailedCell<>(renderer.renderFileName(file.getName(), file.getRelativePath()), file.getName());
        }

        public String getPackageName() {
            return file.getParentName();
        }

        public DetailedCell<?> getLineCoverage() {
            return createColoredCoverageColumn(getCoverageOfNode(Metric.LINE));
        }

        public DetailedCell<?> getBranchCoverage() {
            return createColoredCoverageColumn(getCoverageOfNode(Metric.BRANCH));
        }

        public DetailedCell<?> getMethodCoverage() {
            return createColoredCoverageColumn(getCoverageOfNode(Metric.METHOD));
        }

        public DetailedCell<?> getMcdcPairCoverage() {
            return createColoredCoverageColumn(getCoverageOfNode(Metric.MCDC_PAIR));
        }

        public DetailedCell<?> getFunctionCallCoverage() {
            return createColoredCoverageColumn(getCoverageOfNode(Metric.FUNCTION_CALL));
        }

        public DetailedCell<?> getMutationCoverage() {
            return createColoredCoverageColumn(getCoverageOfNode(Metric.MUTATION));
        }

        public DetailedCell<?> getTestStrength() {
            return createColoredCoverageColumn(getCoverageOfNode(Metric.TEST_STRENGTH));
        }

        public Coverage getCoverageOfNode(final Metric metric) {
            return file.getTypedValue(metric, Coverage.nullObject(metric));
        }

        public DetailedCell<?> getLineCoverageDelta() {
            return createColoredFileCoverageDeltaColumn(Metric.LINE);
        }

        public DetailedCell<?> getBranchCoverageDelta() {
            return createColoredFileCoverageDeltaColumn(Metric.BRANCH);
        }

        public DetailedCell<?> getMutationCoverageDelta() {
            return createColoredFileCoverageDeltaColumn(Metric.MUTATION);
        }

        public DetailedCell<?> getTestStrengthDelta() {
            return createColoredFileCoverageDeltaColumn(Metric.TEST_STRENGTH);
        }

        public int getLoc() {
            return file.getTypedValue(Metric.LOC, ZERO_LOC).asInteger();
        }

        public int getTests() {
            return file.getTypedValue(Metric.TESTS, ZERO_TESTS).asInteger();
        }

        public int getCyclomaticComplexity() {
            return file.getTypedValue(Metric.CYCLOMATIC_COMPLEXITY, ZERO_CYCLOMATIC_COMPLEXITY)
                    .asInteger();
        }

        public int getCognitiveComplexity() {
            return file.getTypedValue(Metric.COGNITIVE_COMPLEXITY, ZERO_COGNITIVE_COMPLEXITY)
                    .asInteger();
        }

        public int getNpathComplexity() {
            return file.getTypedValue(Metric.NPATH_COMPLEXITY, ZERO_NPATH_COMPLEXITY)
                    .asInteger();
        }

        public int getNcss() {
            return file.getTypedValue(Metric.NCSS, ZERO_NCSS).asInteger();
        }

        /**
         * Returns the value of the specified metric for the file of this row. The value is aggregated using the
         * {@link FileMetricAggregation aggregation for files}, e.g., the maximum for method metrics like the
         * cyclomatic complexity. Coverage values without any covered or missed items are treated as missing values.
         *
         * @param metric
         *         the metric to get the value for
         *
         * @return the value of the metric, or an empty result if the file has no such value
         */
        Optional<Value> getMetricValue(final Metric metric) {
            return file.getValue(metric, FileMetricAggregation.of(metric))
                    .filter(value -> !(value instanceof Coverage coverage) || coverage.isSet());
        }

        /**
         * Returns the delta of the specified metric for the file of this row with respect to the reference build.
         *
         * @param metric
         *         the metric to get the delta for
         *
         * @return the delta of the metric, or an empty result if no delta is available
         */
        Optional<Value> getMetricDelta(final Metric metric) {
            if (deltas.hasDelta(file.getRelativePath(), metric)) {
                return Optional.of(deltas.getDelta(file.getRelativePath(), metric));
            }
            return Optional.empty();
        }

        /**
         * Creates a table cell that shows the value of the specified metric. Coverage values are visualized with a
         * colored bar, all other values are shown as plain numbers.
         *
         * @param metric
         *         the metric to show
         *
         * @return the created {@link DetailedCell}
         */
        DetailedCell<?> createValueCell(final Metric metric) {
            return getMetricValue(metric).map(this::renderValue).orElse(NO_COVERAGE);
        }

        private DetailedCell<?> renderValue(final Value value) {
            if (value instanceof Coverage coverage) {
                return createColoredCoverageColumn(coverage);
            }
            return createNumberCell(value, browserLocale);
        }

        /**
         * Creates a table cell that shows the specified (non-coverage) value as a plain, right-aligned number.
         *
         * @param value
         *         the value to show
         * @param locale
         *         the locale to use to render the value
         *
         * @return the created {@link DetailedCell}
         */
        static DetailedCell<?> createNumberCell(final Value value, final Locale locale) {
            var cell = div().withClasses("text-end")
                    .withText(FORMATTER.format(value, locale))
                    .render();
            return new DetailedCell<>(cell, value.asDouble());
        }

        /**
         * Creates a table cell that shows the delta of the specified metric with respect to the reference build.
         *
         * @param metric
         *         the metric to show
         *
         * @return the created {@link DetailedCell}
         */
        DetailedCell<?> createDeltaCell(final Metric metric) {
            return getMetricDelta(metric)
                    .<DetailedCell<?>>map(delta -> createColoredCoverageDeltaColumn(metric, delta))
                    .orElse(NO_COVERAGE);
        }

        /**
         * Creates a table cell that shows the value of the specified metric along with a small badge that shows
         * the delta with respect to the reference build. The cell is sorted by the value.
         *
         * @param metric
         *         the metric to show
         *
         * @return the created {@link DetailedCell}
         */
        DetailedCell<?> createCombinedCell(final Metric metric) {
            var valueCell = createValueCell(metric);
            var delta = getMetricDelta(metric);
            if (getMetricValue(metric).isEmpty() || delta.isEmpty()) {
                return valueCell;
            }
            var cell = div().withClasses(COMBINED_CELL)
                    .with(rawHtml(valueCell.getDisplay()))
                    .with(span().withClasses(DELTA_BADGE)
                            .withStyle("background-color:%s;".formatted(getDeltaColor(metric, delta.get())))
                            .withText(FORMATTER.formatDelta(metric, delta.get(), browserLocale)))
                    .render();
            return new DetailedCell<>(cell, valueCell.getSort());
        }

        private String getDeltaColor(final Metric metric, final Value delta) {
            double change = delta.asRounded();
            if (metric.getTendency() == MetricTendency.SMALLER_IS_BETTER) {
                change = -change;
            }
            return CoverageChangeTendency.getDisplayColorsForTendency(change, colorProvider)
                    .getFillColorAsRGBAHex(TABLE_COVERAGE_COLOR_ALPHA);
        }

        /**
         * Creates a table cell which colorizes the shown coverage dependent on the coverage percentage.
         *
         * @param coverage
         *         the coverage of the element
         *
         * @return the new {@link DetailedCell}
         */
        protected DetailedCell<?> createColoredCoverageColumn(final Coverage coverage) {
            return createCoverageCell(coverage, colorProvider, browserLocale);
        }

        /**
         * Creates a table cell which colorizes the shown coverage dependent on the coverage percentage. The cell
         * shows the percentage as a colored bar.
         *
         * @param coverage
         *         the coverage of the element
         * @param colors
         *         the color provider to use
         * @param locale
         *         the locale to use to render the percentage
         *
         * @return the new {@link DetailedCell}
         */
        static DetailedCell<?> createCoverageCell(
                final Coverage coverage, final ColorProvider colors, final Locale locale) {
            if (coverage.isSet()) {
                double percentage = coverage.asRounded();
                DisplayColors displayColors = CoverageLevel.getDisplayColorsOfCoverageLevel(percentage, colors);
                var style = String.format(
                        Locale.ENGLISH,
                        "background-image: linear-gradient(90deg, %s %f%%, transparent %f%%);",
                        displayColors.getFillColorAsRGBAHex(TABLE_COVERAGE_COLOR_ALPHA),
                        percentage,
                        percentage);
                var cell = div().withClasses(COVERAGE_COLUMN_OUTER)
                        .with(div().withClasses(COVERAGE_COLUMN_INNER)
                                .withStyle(style)
                                .attr("data-bs-toggle", "tooltip")
                                .attr("data-bs-placement", "top")
                                .withTitle(FORMATTER.formatAdditionalInformation(coverage))
                                .withText(FORMATTER.formatPercentage(coverage, locale)))
                        .render();
                return new DetailedCell<>(cell, percentage);
            }
            return NO_COVERAGE;
        }

        /**
         * Creates a table cell which colorizes the tendency of the shown coverage delta.
         *
         * @param metric
         *         the metric to use
         * @param delta
         *         The coverage delta as percentage
         *
         * @return the created {@link DetailedCell}
         */
        protected DetailedCell<?> createColoredCoverageDeltaColumn(final Metric metric, final Value delta) {
            double percentage = delta.asRounded();
            var cell = div().withClasses(COVERAGE_COLUMN_OUTER)
                    .with(div().withClasses(COVERAGE_COLUMN_INNER)
                            .withStyle("background-color:%s;".formatted(getDeltaColor(metric, delta)))
                            .withText(FORMATTER.formatDelta(metric, delta, browserLocale)))
                    .render();
            return new DetailedCell<>(cell, percentage);
        }

        protected FileNode getFile() {
            return file;
        }

        /**
         * Creates a colored column for visualizing the file coverage delta against a reference for the passed
         * {@link Metric}.
         *
         * @param metric
         *         the coverage metric
         *
         * @return the created {@link DetailedCell}
         */
        private DetailedCell<?> createColoredFileCoverageDeltaColumn(final Metric metric) {
            if (deltas.hasDelta(file.getRelativePath(), metric)) {
                return createColoredCoverageDeltaColumn(metric, deltas.getDelta(file.getRelativePath(), metric));
            }
            return NO_COVERAGE;
        }
    }

    /**
     * Renders filenames with links. Selection will be handled by opening a new page using the provided link.
     */
    static class LinkedRowRenderer implements RowRenderer {
        private final File buildFolder;
        private final String resultsId;

        LinkedRowRenderer(final File buildFolder, final String resultsId) {
            this.buildFolder = buildFolder;
            this.resultsId = resultsId;
        }

        @Override
        public void configureTable(final TableConfiguration tableConfiguration) {
            // nothing required
        }

        @Override
        public String renderFileName(final String fileName, final String path) {
            ContainerTag cell;
            if (SOURCE_CODE_FACADE.canRead(buildFolder, resultsId, path)) {
                cell = a().withHref(String.valueOf(path.hashCode())).withText(fileName);
            } else {
                cell = div().withText(fileName);
            }
            return renderWithToolTip(cell, path);
        }

        static String renderWithToolTip(final ContainerTag cell, final String path) {
            return cell.attr("data-bs-toggle", "tooltip")
                    .attr("data-bs-placement", "top")
                    .withTitle(path)
                    .render();
        }
    }

    /**
     * Renders filenames with links to the details page of the file in the run tab, see {@link FileDetailsModel}. In
     * contrast to the {@link LinkedRowRenderer}, the link is always rendered since the details page also shows the
     * metrics of the file and its methods if the source code is not available.
     */
    static class DetailsRowRenderer implements RowRenderer {
        @Override
        public void configureTable(final TableConfiguration tableConfiguration) {
            // nothing required
        }

        @Override
        public String renderFileName(final String fileName, final String path) {
            return LinkedRowRenderer.renderWithToolTip(
                    a().withHref(String.valueOf(path.hashCode())).withText(fileName), path);
        }
    }

    /**
     * Renders filenames without links. Selection will be handled using the table select events.
     */
    static class InlineRowRenderer implements RowRenderer {
        @Override
        public void configureTable(final TableConfiguration tableConfiguration) {
            tableConfiguration.select(SelectStyle.SINGLE);
        }

        @Override
        public String renderFileName(final String fileName, final String path) {
            return LinkedRowRenderer.renderWithToolTip(div().withText(fileName), path);
        }
    }

    /**
     * Renders filenames in table cells.
     */
    interface RowRenderer {
        void configureTable(TableConfiguration tableConfiguration);

        String renderFileName(String fileName, String path);
    }
}
