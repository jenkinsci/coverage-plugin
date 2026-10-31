package io.jenkins.plugins.coverage.metrics.restapi;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.Node;
import hudson.model.Api;
import hudson.model.ModelObject;
import io.jenkins.plugins.coverage.metrics.source.Messages;

/**
 * Server side model that provides the data for modified lines coverage results.
 *
 * <p>
 * Note that this class has no UI representation yet and primarily is used to provide an access point for the remote
 * API.
 * </p>
 */
public class ModifiedLinesCoverageApiModel implements ModelObject {
    private final Node node;
    private final ChangeSet changes;

    /**
     * Creates a new instance of {@link ModifiedLinesCoverageApiModel}.
     *
     * @param node
     *         {@link Node} object
     * @param changes
     *         the changes of the build
     */
    public ModifiedLinesCoverageApiModel(final Node node, final ChangeSet changes) {
        this.node = node;
        this.changes = changes;
    }

    /**
     * Gets the remote API for the details of modified line coverage results.
     *
     * @return the remote API
     */
    public Api getApi() {
        return new Api(new ModifiedLinesCoverageApi(getNode(), changes));
    }

    public Node getNode() {
        return node;
    }

    @Override
    public String getDisplayName() {
        return Messages.Coverage_Title(getNode().getName());
    }
}
