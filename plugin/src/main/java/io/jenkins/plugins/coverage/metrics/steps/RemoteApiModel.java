package io.jenkins.plugins.coverage.metrics.steps;

import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;

/**
 * Server side model that provides the data for the "Remote API" tab of the coverage details view. The view calls the
 * remote API of the coverage results (i.e., the URL {@code coverage/api/json}) in the browser and shows the returned
 * JSON document. Additionally, the view links to the other formats and endpoints of the remote API so that users can
 * see which data is available for scripts and other tools. The layout of the associated view is defined in the
 * corresponding jelly view 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class RemoteApiModel implements ModelObject {
    private final CoverageViewModel parent;

    RemoteApiModel(final CoverageViewModel parent) {
        this.parent = parent;
    }

    @Override
    public String getDisplayName() {
        return Messages.RemoteApiModel_displayName();
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
     * Returns the URL of the remote API of the coverage results, relative to the root URL of Jenkins.
     *
     * @return the URL of the remote API (without a trailing slash and without the format)
     */
    public String getApiUrl() {
        return getObject().getUrl() + getId() + "/api";
    }
}
