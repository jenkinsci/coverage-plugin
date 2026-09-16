package io.jenkins.plugins.coverage.metrics.restapi;

import edu.hm.hafner.coverage.Node;

import hudson.model.Api;
import hudson.model.ModelObject;

import io.jenkins.plugins.coverage.metrics.source.Messages;

/**
 * Server side model that provides the data for the whole-file coverage results.
 */
public class FileCoverageApiModel implements ModelObject {
    private final Node node;

    /**
     * Creates a new instance of {@link FileCoverageApiModel}.
     *
     * @param node
     *         {@link Node} object
     */
    public FileCoverageApiModel(final Node node) {
        this.node = node;
    }

    /**
     * Gets the remote API for the whole-file coverage results.
     *
     * @return the remote API
     */
    public Api getApi() {
        return new Api(new FileCoverageApi(getNode()));
    }

    public Node getNode() {
        return node;
    }

    @Override
    public String getDisplayName() {
        return Messages.Coverage_Title(getNode().getName());
    }
}
