package io.jenkins.plugins.coverage.metrics.steps;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Run;

/**
 * Registers the converters used by persisted coverage actions before Jenkins starts loading jobs and builds in
 * parallel. This must run before {@link InitMilestone#SYSTEM_CONFIG_ADAPTED}: converter registration can modify
 * XStream's TreeMap-backed converter list, which can throw a {@link java.util.ConcurrentModificationException} if
 * another thread is looking up converters at the same time.
 */
public final class CoverageXStreamInitializer {
    private CoverageXStreamInitializer() {
        // utility class
    }

    /**
     * Registers coverage converters and aliases on the shared build stream.
     */
    @Initializer(before = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public static void registerConverters() {
        CoverageXmlStream.registerConverters(Run.XSTREAM2);
        CoverageBuildAction.registerValueListConverters(Run.XSTREAM2);
    }
}
