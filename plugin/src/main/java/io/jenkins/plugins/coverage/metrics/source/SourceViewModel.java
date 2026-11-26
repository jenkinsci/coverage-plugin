package io.jenkins.plugins.coverage.metrics.source;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Node;
import hudson.model.ModelObject;
import hudson.model.Run;
import java.io.IOException;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Server side model that provides the data for the source code view of the coverage results. The layout of the
 * associated view is defined corresponding jelly view 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class SourceViewModel implements ModelObject {
    private static final SourceCodeFacade SOURCE_CODE_FACADE = new SourceCodeFacade();

    private final Run<?, ?> owner;
    private final String id;
    private final Node rootNode;
    private final FileNode fileNode;
    private final ChangeSet changes;

    /**
     * Creates a new source view model instance.
     *
     * @param owner
     *         the owner of this view
     * @param id
     *         the ID that is used to store the coverage sources
     * @param rootNode
     *         the root of the coverage tree, used to determine the highlighted metrics
     * @param fileNode
     *         the selected file node of the coverage tree
     * @param changes
     *         the code changes with respect to the reference build
     */
    public SourceViewModel(
            final Run<?, ?> owner,
            final String id,
            final Node rootNode,
            final FileNode fileNode,
            final ChangeSet changes) {
        this.owner = owner;
        this.id = id;
        this.rootNode = rootNode;
        this.fileNode = fileNode;
        this.changes = changes;
    }

    public Run<?, ?> getOwner() {
        return owner;
    }

    public FileNode getNode() {
        return fileNode;
    }

    /**
     * Returns the source file rendered in HTML.
     *
     * @return the colored source code as HTML document
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getSourceFileContent() {
        try {
            return SOURCE_CODE_FACADE.readHtml(getOwner().getRootDir(), id, rootNode, fileNode, changes);
        } catch (IOException | InterruptedException exception) {
            return ExceptionUtils.getStackTrace(exception);
        }
    }

    /**
     * Returns whether the source file is available in Jenkins build folder.
     *
     * @return {@code true} if the source file is available, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean isSourceFileAvailable() {
        return SOURCE_CODE_FACADE.canRead(getOwner().getRootDir(), id, fileNode.getRelativePath());
    }

    @Override
    public String getDisplayName() {
        return Messages.Coverage_Title(getNode().getName());
    }
}
