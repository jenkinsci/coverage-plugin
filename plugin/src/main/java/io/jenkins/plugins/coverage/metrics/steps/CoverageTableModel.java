package io.jenkins.plugins.coverage.metrics.steps;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.Fraction;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.CyclomaticComplexity;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.FractionValue;
import edu.hm.hafner.coverage.LinesOfCode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.TestCount;

import j2html.tags.ContainerTag;

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

import static j2html.TagCreator.*;

/**
 * UI table model for the coverage details table.
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
class CoverageTableModel extends TableModel {
    private static final int NO_COVERAGE_SORT = -1_000;
    private static final SourceCodeFacade SOURCE_CODE_FACADE = new SourceCodeFacade();

    /**
     * The alpha value for colors to be used to highlight the coverage within the table view.
     */
    private static final int TABLE_COVERAGE_COLOR_ALPHA = 80;

    static final DetailedCell<Integer> NO_COVERAGE
            = new DetailedCell<>(Messages.Coverage_Not_Available(), NO_COVERAGE_SORT);

    private final ColorProvider colorProvider;
    private final Node root;
    private final RowRenderer renderer;
    private final String id;

    CoverageTableModel(final String id, final Node root, final RowRenderer renderer, final ColorProvider colors) {
        super();

        this.id = id;
        this.root = root;
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
        TableConfiguration tableConfiguration = new TableConfiguration();
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

        TableColumn fileHash = new ColumnBuilder().withHeaderLabel("Hash")
                .withDataPropertyKey("fileHash")
                .withHeaderClass(ColumnCss.HIDDEN)
                .build();
        columns.add(fileHash);
        TableColumn modified = new ColumnBuilder().withHeaderLabel("Modified")
                .withDataPropertyKey("modified")
                .withHeaderClass(ColumnCss.HIDDEN)
                .build();
        columns.add(modified);
        TableColumn fileName = new ColumnBuilder().withHeaderLabel(Messages.Column_File())
                .withDataPropertyKey("fileName")
                .withDetailedCell()
                .withResponsivePriority(1)
                .build();
        columns.add(fileName);
        TableColumn packageName = new ColumnBuilder().withHeaderLabel(Messages.Column_Package())
                .withDataPropertyKey("packageName")
                .withResponsivePriority(50_000)
                .build();
        columns.add(packageName);

        configureValueColumn("lineCoverage", Metric.LINE, Messages.Column_LineCoverage(),
                Messages.Column_DeltaLineCoverage("Δ"), columns);
        configureValueColumn("branchCoverage", Metric.BRANCH, Messages.Column_BranchCoverage(),
                Messages.Column_DeltaBranchCoverage("Δ"), columns);

        /* VectorCAST metrics */
        configureValueColumn("mcdcPairCoverage", Metric.MCDC_PAIR, Messages.Column_MCDCPairs(),
                "", columns);
        configureValueColumn("functionCallCoverage", Metric.FUNCTION_CALL, Messages.Column_FunctionCall(),
                "", columns);

        configureValueColumn("mutationCoverage", Metric.MUTATION, Messages.Column_MutationCoverage(),
                Messages.Column_DeltaMutationCoverage("Δ"), columns);
        configureValueColumn("testStrength", Metric.TEST_STRENGTH, Messages.Column_TestStrength(),
                Messages.Column_DeltaTestStrength("Δ"), columns);

        List<Object[]> columnsEntries = List.of(
                new Object[] {Metric.LOC, Messages.Column_LinesOfCode(), "loc", 200},
                new Object[] {Metric.TESTS, Messages.Column_Tests(), "tests", 500},
                new Object[] {Metric.COMPLEXITY, Messages.Column_Complexity(), "complexity", 500},
                new Object[] {Metric.COMPLEXITY_MAXIMUM, Messages.Column_MaxComplexity(), "maxComplexity", 900},
                new Object[] {Metric.COMPLEXITY_DENSITY, Messages.Column_ComplexityDensity(), "density", 700},
                new Object[] {Metric.COGNITIVE_COMPLEXITY, Messages.Column_CognitiveComplexity(), "cognitiveComplexity", 500},
                new Object[] {Metric.NPATH_COMPLEXITY, Messages.Column_NPath(), "npathComplexity", 500},
                new Object[] {Metric.NCSS, Messages.Column_Ncss(), "ncss", 500});

        for (Object[] column : columnsEntries) {
            if (root.containsMetric((Metric) column[0])) {
                TableColumn tmp = new ColumnBuilder().withHeaderLabel((String) column[1])
                        .withDataPropertyKey((String) column[2])
                        .withResponsivePriority((Integer) column[3])
                        .withType(ColumnType.NUMBER)
                        .build();
                columns.add(tmp);
            }
        }
        return columns;
    }

    private void configureValueColumn(final String key, final Metric metric, final String headerLabel,
            final String deltaHeaderLabel, final List<TableColumn> columns) {
        if (root.containsMetric(metric)) {
            TableColumn lineCoverage = new ColumnBuilder().withHeaderLabel(headerLabel)
                    .withDataPropertyKey(key)
                    .withDetailedCell()
                    .withType(ColumnType.NUMBER)
                    .withResponsivePriority(1)
                    .build();
            columns.add(lineCoverage);
            if (StringUtils.isNotEmpty(deltaHeaderLabel)) {
                TableColumn lineCoverageDelta = new ColumnBuilder().withHeaderLabel(deltaHeaderLabel)
                        .withDataPropertyKey(key + "Delta")
                        .withDetailedCell()
                        .withType(ColumnType.NUMBER)
                        .withResponsivePriority(2)
                        .build();
                columns.add(lineCoverageDelta);
            }
        }
    }

    @Override
    public List<Object> getRows() {
        Locale browserLocale = Functions.getCurrentLocale();
        return root.getAllFileNodes().stream()
                .map(file -> new CoverageRow(file, browserLocale, renderer, colorProvider))
                .collect(Collectors.toList());
    }

    protected Node getRoot() {
        return root;
    }

    protected ColorProvider getColorProvider() {
        return colorProvider;
    }

    /**
     * UI row model for the coverage details table.
     */
    @SuppressWarnings("PMD.DataClass")
    static class CoverageRow {
        private static final String COVERAGE_COLUMN_OUTER = "coverage-cell-outer float-end";
        private static final String COVERAGE_COLUMN_INNER = "coverage-jenkins-cell-inner";
        private static final ElementFormatter FORMATTER = new ElementFormatter();
        private static final FractionValue ZERO_DENSITY = new FractionValue(Metric.COMPLEXITY_DENSITY, 0, 1);
        private static final LinesOfCode ZERO_LOC = new LinesOfCode(0);
        private static final TestCount ZERO_TESTS = new TestCount(0);
        private static final CyclomaticComplexity ZERO_COMPLEXITY = new CyclomaticComplexity(0);
        private static final CyclomaticComplexity ZERO_COG_COMPLEXITY = new CyclomaticComplexity(0,
                Metric.COGNITIVE_COMPLEXITY);
        private static final CyclomaticComplexity ZERO_NPATH_COMPLEXITY = new CyclomaticComplexity(0,
                Metric.NPATH_COMPLEXITY);
        private static final CyclomaticComplexity ZERO_NCSS = new CyclomaticComplexity(0, Metric.NCSS);
        private final FileNode file;
        private final Locale browserLocale;
        private final RowRenderer renderer;
        private final ColorProvider colorProvider;

        CoverageRow(final FileNode file, final Locale browserLocale, final RowRenderer renderer,
                final ColorProvider colors) {
            this.file = file;
            this.browserLocale = browserLocale;
            this.renderer = renderer;
            colorProvider = colors;
        }

        public String getFileHash() {
            return String.valueOf(file.getRelativePath().hashCode());
        }

        @SuppressWarnings("PMD.BooleanGetMethodName")
        public boolean getModified() {
            return file.hasModifiedLines();
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

        Coverage getCoverageOfNode(final Metric metric) {
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
            return file.getTypedValue(Metric.LOC, ZERO_LOC).getValue();
        }

        public int getTests() {
            return file.getTypedValue(Metric.TESTS, ZERO_TESTS).getValue();
        }

        public int getComplexity() {
            return file.getTypedValue(Metric.COMPLEXITY, ZERO_COMPLEXITY).getValue();
        }

        public int getMaxComplexity() {
            return file.getTypedValue(Metric.COMPLEXITY_MAXIMUM, ZERO_COMPLEXITY).getValue();
        }

        public DetailedCell<?> getDensity() {
            double complexityDensity = file.getTypedValue(Metric.COMPLEXITY_DENSITY, ZERO_DENSITY)
                    .getFraction()
                    .doubleValue();
            return new DetailedCell<>(String.format("%.2f", complexityDensity), complexityDensity);
        }

        public int getCognitiveComplexity() {
            return file.getTypedValue(Metric.COGNITIVE_COMPLEXITY, ZERO_COG_COMPLEXITY).getValue();
        }

        public int getNpathComplexity() {
            return file.getTypedValue(Metric.NPATH_COMPLEXITY, ZERO_NPATH_COMPLEXITY).getValue();
        }

        public int getNcss() {
            return file.getTypedValue(Metric.NCSS, ZERO_NCSS).getValue();
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
            if (coverage.isSet()) {
                double percentage = coverage.getCoveredPercentage().toDouble();
                DisplayColors colors = CoverageLevel.getDisplayColorsOfCoverageLevel(percentage, colorProvider);
                String cell = div()
                        .withClasses(COVERAGE_COLUMN_OUTER).with(
                                div().withClasses(COVERAGE_COLUMN_INNER)
                                        .withStyle(String.format(
                                                "background-image: linear-gradient(90deg, %s %f%%, transparent %f%%);",
                                                colors.getFillColorAsRGBAHex(TABLE_COVERAGE_COLOR_ALPHA),
                                                percentage, percentage))
                                        .attr("data-bs-toggle", "tooltip")
                                        .attr("data-bs-placement", "top")
                                        .withTitle(FORMATTER.formatAdditionalInformation(coverage))
                                        .withText(FORMATTER.formatPercentage(coverage, browserLocale)))
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
        protected DetailedCell<?> createColoredCoverageDeltaColumn(final Metric metric, final Fraction delta) {
            double percentage = delta.doubleValue() * 100.0;
            DisplayColors colors = CoverageChangeTendency.getDisplayColorsForTendency(percentage, colorProvider);
            String cell = div().withClasses(COVERAGE_COLUMN_OUTER).with(
                            div().withClasses(COVERAGE_COLUMN_INNER)
                                    .withStyle(String.format("background-color:%s;", colors.getFillColorAsRGBAHex(
                                            TABLE_COVERAGE_COLOR_ALPHA)))
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
            if (file.hasDelta(metric)) {
                return createColoredCoverageDeltaColumn(metric, file.getDelta(metric));
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
            }
            else {
                cell = div().withText(fileName);
            }
            return renderWithToolTip(cell, path);
        }

        static String renderWithToolTip(final ContainerTag cell, final String path) {
            return cell.attr("data-bs-toggle", "tooltip")
                    .attr("data-bs-placement", "top")
                    .withTitle(path).render();
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
