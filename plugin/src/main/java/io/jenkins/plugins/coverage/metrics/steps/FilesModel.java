package io.jenkins.plugins.coverage.metrics.steps;

import org.kohsuke.stapler.bind.JavaScriptMethod;
import hudson.model.ModelObject;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.datatables.DefaultAsyncTableContentProvider;
import io.jenkins.plugins.datatables.TableModel;

/**
 * Server side model that provides the data for the "Files" tab of the coverage details view. Shows the coverage
 * results of all files, the coverage of the modified lines, and the indirect coverage changes as three separate
 * tables. Which of the three tables is currently shown can be switched inline on the client side using a select box,
 * see the corresponding {@code files-model.js}. This model simply delegates the actual table and source code lookups
 * to the owning {@link CoverageViewModel}. The layout of the associated view is defined in the corresponding
 * jelly view 'index.jelly'.
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
     * Checks whether source files are stored so that a table row selection shows the corresponding source code.
     *
     * @return {@code true} when source files are stored, {@code false} otherwise
     */
    public boolean hasSourceCode() {
        return parent.hasSourceCode();
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
     * Returns the table model that matches the given table ID. Overrides the abstract method from
     * {@link DefaultAsyncTableContentProvider}, whose {@code getTableRows(String)} is what the data-tables plugin's
     * client-side {@code tableDataProxy} calls (bound to "it" in the corresponding jelly view) to asynchronously
     * load the actual table rows.
     *
     * @param tableId
     *         ID of the table model
     *
     * @return the table model
     */
    @Override
    public TableModel getTableModel(final String tableId) {
        return parent.getTableModel(tableId);
    }

    /**
     * Gets the source code for a specific file and highlights it depending on the used table.
     *
     * @param fileHash
     *         the hash code of the file to get the source code for
     * @param tableId
     *         the ID of the table
     *
     * @return the source code, highlighted appropriately
     */
    @JavaScriptMethod
    @SuppressWarnings("unused")
    public String getSourceCode(final String fileHash, final String tableId) {
        return parent.getSourceCode(fileHash, tableId);
    }
}
