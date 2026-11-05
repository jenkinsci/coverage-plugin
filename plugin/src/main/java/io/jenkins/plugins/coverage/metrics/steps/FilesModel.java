package io.jenkins.plugins.coverage.metrics.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTableModel.DetailsRowRenderer;
import io.jenkins.plugins.datatables.DefaultAsyncTableContentProvider;
import io.jenkins.plugins.datatables.TableModel;

/**
 * Server side model that provides the data for the "Files" tab of the coverage details view. Shows the coverage
 * results of all files, the coverage of the modified lines, and the indirect coverage changes as three separate
 * tables. Which of the three tables is currently shown can be switched inline on the client side using a select box,
 * see the corresponding {@code files-model.js}. The file names in the tables link to the details page of each file,
 * see {@link FileDetailsModel}. This model simply delegates the actual table lookups to the owning
 * {@link CoverageViewModel}. The layout of the associated view is defined in the corresponding jelly view
 * 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class FilesModel extends DefaultAsyncTableContentProvider implements ModelObject {
    private final CoverageViewModel parent;

    FilesModel(final CoverageViewModel parent) {
        super();

        this.parent = parent;
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverageFilesModel_displayName();
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
     * Checks whether modified lines coverage exists, i.e., whether the corresponding table should be offered.
     *
     * @return {@code true} whether modified lines coverage exists, else {@code false}
     */
    public boolean hasModifiedLinesCoverage() {
        return parent.hasModifiedLinesCoverage();
    }

    /**
     * Checks whether indirect coverage changes exist, i.e., whether the corresponding table should be offered.
     *
     * @return {@code true} whether indirect coverage changes exist, else {@code false}
     */
    public boolean hasIndirectCoverageChanges() {
        return parent.hasIndirectCoverageChanges();
    }

    /**
     * Checks whether there are files with modified lines, i.e., whether the option to show only the modified files
     * of the table of all files should be offered.
     *
     * @return {@code true} if there are modified files, {@code false} otherwise
     */
    public boolean hasModifiedFiles() {
        return parent.hasModifiedFiles();
    }

    /**
     * Checks whether the deltas of the files with respect to the reference build are available, i.e., whether the
     * column mode selection (values, deltas, or both) should be offered.
     *
     * @return {@code true} if file deltas are available, {@code false} otherwise
     */
    public boolean hasFileDeltas() {
        return parent.hasFileDeltas();
    }

    /**
     * Returns the table model that matches the given table ID. The model provides value, delta, and combined columns
     * for all metrics, see {@link FilesTableModel}. The file names link to the details page of each file. Overrides
     * the abstract method from {@link DefaultAsyncTableContentProvider}, whose {@code getTableRows(String)} is what
     * the data-tables plugin's client-side {@code tableDataProxy} calls (bound to "it" in the corresponding jelly
     * view) to asynchronously load the actual table rows.
     *
     * @param tableId
     *         ID of the table model
     *
     * @return the table model
     */
    @Override
    public TableModel getTableModel(final String tableId) {
        return new FilesTableModel(parent.createTableModel(tableId, new DetailsRowRenderer()));
    }

    /**
     * Delegates any link that is not one of this model's own URLs to the owning {@link CoverageViewModel}. This is
     * needed in particular for the file links of the tables (see {@link DetailsRowRenderer}): these links are
     * relative to this tab's own URL, so if the URL ends with a slash they would resolve to a non-existent page
     * here instead of reaching {@link CoverageViewModel#getDynamic} and its file-hash lookup.
     *
     * @param link
     *         the link to identify the subpage to show
     *
     * @return the resolved subpage, or {@code null} if the link cannot be resolved
     */
    @SuppressWarnings("unused") // Called by Stapler
    @CheckForNull
    public Object getDynamic(final String link) {
        return parent.getDynamic(link);
    }
}
