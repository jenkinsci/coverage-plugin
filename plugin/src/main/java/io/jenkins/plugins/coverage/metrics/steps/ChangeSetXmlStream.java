package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.ChangeSet;
import hudson.util.XStream2;
import io.jenkins.plugins.util.AbstractXmlStream;

/**
 * Configures the XML stream for the {@link ChangeSet} of a build. The change set is stored in a separate file (a so
 * called sidecar file) next to the coverage tree so that the tree does not need to be loaded to access the changes and
 * vice versa.
 */
class ChangeSetXmlStream extends AbstractXmlStream<ChangeSet> {
    /**
     * Creates an XML stream for {@link ChangeSet}.
     */
    ChangeSetXmlStream() {
        super(ChangeSet.class);
    }

    @Override
    protected void configureXStream(final XStream2 xStream) {
        xStream.alias("changes", ChangeSet.class);
    }

    @Override
    protected ChangeSet createDefaultValue() {
        return ChangeSet.empty();
    }
}
