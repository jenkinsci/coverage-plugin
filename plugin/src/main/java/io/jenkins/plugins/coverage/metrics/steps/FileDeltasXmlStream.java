package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.FileDeltas;
import hudson.util.XStream2;
import io.jenkins.plugins.util.AbstractXmlStream;

/**
 * Configures the XML stream for the {@link FileDeltas} of a build. The deltas are stored in a separate file (a so called
 * sidecar file) next to the coverage tree so that the tree does not depend on the reference build.
 */
class FileDeltasXmlStream extends AbstractXmlStream<FileDeltas> {
    /**
     * Creates an XML stream for {@link FileDeltas}.
     */
    FileDeltasXmlStream() {
        super(FileDeltas.class);
    }

    @Override
    protected void configureXStream(final XStream2 xStream) {
        CoverageXmlStream.registerConverters(xStream);

        xStream.alias("deltas", FileDeltas.class);
    }

    @Override
    protected FileDeltas createDefaultValue() {
        return FileDeltas.empty();
    }
}
