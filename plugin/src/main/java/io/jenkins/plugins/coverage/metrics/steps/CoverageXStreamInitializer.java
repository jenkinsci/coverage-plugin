package io.jenkins.plugins.coverage.metrics.steps;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Run;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers the converters used by persisted coverage actions before Jenkins starts loading jobs and builds in
 * parallel. This must run before {@link InitMilestone#SYSTEM_CONFIG_ADAPTED}: converter registration can modify
 * XStream's TreeMap-backed converter list, which can throw a {@link java.util.ConcurrentModificationException} if
 * another thread is looking up converters at the same time.
 *
 * <p>
 * Since {@link Run#XSTREAM2} is a static instance that lives as long as the JVM, the converters are registered only
 * once per JVM, even if Jenkins is started several times (e.g., in integration tests). Otherwise, each start would
 * add the same converters again and would open a new window for concurrent modifications of the converter list by
 * other initializers that run in parallel. Such concurrent writes corrupt the underlying {@link java.util.TreeMap}
 * (e.g., a {@link NullPointerException} in {@code TreeMap.rotateLeft}).
 * </p>
 */
public final class CoverageXStreamInitializer {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private CoverageXStreamInitializer() {
        // utility class
    }

    /**
     * Registers coverage converters and aliases on the shared build stream. Subsequent invocations are ignored.
     */
    @Initializer(before = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public static void registerConverters() {
        if (REGISTERED.compareAndSet(false, true)) {
            CoverageXmlStream.registerConverters(Run.XSTREAM2);
            CoverageBuildAction.registerValueListConverters(Run.XSTREAM2);
        }
    }
}
