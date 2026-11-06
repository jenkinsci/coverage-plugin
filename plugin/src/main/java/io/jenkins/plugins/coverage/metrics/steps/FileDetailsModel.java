package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.FileNode;
import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.coverage.metrics.source.SourceViewModel;
import io.jenkins.plugins.datatables.DefaultAsyncTableContentProvider;
import io.jenkins.plugins.datatables.TableModel;
import io.jenkins.plugins.prism.SourceCodeViewModel;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Server side model for the details page of a single file in the run tab of the coverage results. The page shows a
 * summary of all metrics of the file (along with the deltas with respect to the reference build), a table with the
 * metrics of all methods of the file (see {@link MethodsTableModel}), and the source code of the file with the
 * coverage highlighted. The page is reached by the file links of the other run tab pages (e.g., the "Files" or the
 * "Hotspots" tab), see {@link CoverageViewModel#getDynamic(String)}. The layout of the associated view is defined in
 * the corresponding jelly view 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class FileDetailsModel extends DefaultAsyncTableContentProvider implements ModelObject {
    private final CoverageViewModel parent;
    private final FileNode file;

    /**
     * Creates a new instance of {@link FileDetailsModel}.
     *
     * @param parent
     *         the coverage results that contain the file
     * @param file
     *         the file to show
     */
    FileDetailsModel(final CoverageViewModel parent, final FileNode file) {
        super();

        this.parent = parent;
        this.file = file;
    }

    @Override
    public String getDisplayName() {
        return file.getName();
    }

    public String getId() {
        return parent.getId();
    }

    public Run<?, ?> getObject() {
        return parent.getOwner();
    }

    public RunTab getTab() {
        return new RunTab(parent.getOwner());
    }

    public ElementFormatter getFormatter() {
        return parent.getFormatter();
    }

    /**
     * Returns the relative path of the file.
     *
     * @return the relative path
     */
    public String getPath() {
        return file.getRelativePath();
    }

    /**
     * Returns a summary of all metrics that are available for the file. Each summary contains the rendered value of
     * the metric along with the delta with respect to the reference build (if available). The metrics are shown in
     * the same order as in the files table.
     *
     * @return the summaries of the metrics, may be empty
     */
    public List<MetricSummary> getSummaries() {
        var row = parent.createFileRow(file);
        return FilesTableModel.METRICS.stream()
                .filter(metric -> row.getMetricValue(metric).isPresent())
                .map(metric -> new MetricSummary(
                        FilesTableModel.getLabel(metric),
                        getFormatter().getDisplayName(metric),
                        row.createCombinedCell(metric).getDisplay()))
                .toList();
    }

    /**
     * Returns whether the file contains methods, i.e., whether the methods table should be shown.
     *
     * @return {@code true} if the file contains methods, {@code false} otherwise
     */
    public boolean hasMethods() {
        return !file.getAllMethodNodes().isEmpty();
    }

    /**
     * Returns the table model of the methods table. Overrides the abstract method from
     * {@link DefaultAsyncTableContentProvider}, whose {@code getTableRows(String)} is called by the client side of
     * the data-tables plugin to asynchronously load the rows of the table.
     *
     * @param tableId
     *         ID of the table model, must be {@link MethodsTableModel#TABLE_ID}
     *
     * @return the table model
     * @throws NoSuchElementException
     *         if there is no table with the specified ID
     */
    @Override
    public TableModel getTableModel(final String tableId) {
        if (MethodsTableModel.TABLE_ID.equals(tableId)) {
            return new MethodsTableModel(file, parent.getColorProvider());
        }
        throw new NoSuchElementException("No such table with id " + tableId);
    }

    /**
     * Returns whether the current user has the permission to view the source code.
     *
     * @return {@code true} if the source code can be shown, {@code false} otherwise
     */
    public boolean hasPermissionToViewSourceCode() {
        return SourceCodeViewModel.hasPermissionToViewSourceCode(getObject());
    }

    /**
     * Returns whether the source file is available in the Jenkins build folder.
     *
     * @return {@code true} if the source file is available, {@code false} otherwise
     */
    public boolean isSourceFileAvailable() {
        return parent.isSourceFileAvailable(file);
    }

    /**
     * Returns the source code of the file rendered in HTML, the coverage is highlighted using the corresponding CSS
     * classes.
     *
     * @return the colored source code as HTML document
     */
    public String getSourceFileContent() {
        return new SourceViewModel(getObject(), getId(), file).getSourceFileContent();
    }

    /**
     * Summary of a single metric of the file.
     *
     * @param label
     *         the short label of the metric
     * @param name
     *         the display name of the metric (used as tooltip)
     * @param cell
     *         the rendered HTML of the value (and the delta) of the metric
     */
    public record MetricSummary(String label, String name, String cell) {}
}
