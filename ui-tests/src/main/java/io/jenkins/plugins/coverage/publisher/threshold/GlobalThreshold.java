package io.jenkins.plugins.coverage.publisher.threshold;

import io.jenkins.plugins.coverage.publisher.CoveragePublisher;

/**
 * Global Threshold used in {@code CoveragePublisher}.
 */
public class GlobalThreshold extends AbstractThreshold {
    private final CoveragePublisher coveragePublisher;

    /**
     * Constructor of a GlobalThreshold in {@code CoveragePublisher}.
     *
     * @param coveragePublisher
     *         of threshold
     * @param path
     *         to threshold
     */
    public GlobalThreshold(final CoveragePublisher coveragePublisher, final String path) {
        super(coveragePublisher, path);
        this.coveragePublisher = coveragePublisher;
    }

    /**
     * Setter for target of Threshold using {@link GlobalThresholdTarget}.
     *
     * @param globalThresholdTarget
     *         of threshold
     */
    public void setThresholdTarget(final GlobalThresholdTarget globalThresholdTarget) {
        ensureAdvancedOptionsIsActivated();
        this.getThresholdTarget().select(globalThresholdTarget.getValue());
    }

    /**
     * Ensures advanced options are activated so that values can be set.
     */
    @Override
    public void ensureAdvancedOptionsIsActivated() {
        this.coveragePublisher.ensureAdvancedOptionsIsActivated();
    }

    /**
     * Enum for Options of {@link GlobalThreshold}.
     */
    public enum GlobalThresholdTarget {
        AGGREGATED_REPORT("Aggregated Report"),
        REPORT("Report"),
        GROUP("Group"),
        PACKAGE("Package"),
        DIRECTORY("Directory"),
        FILE("File"),
        CLASS("Class"),
        METHOD("Method"),
        INSTRUCTION("Instruction"),
        LINE("Line"),
        CONDITIONAL("Conditional");

        private final String value;

        /**
         * Constructor of enum.
         * @param value is value-attribute of option-tag.
         */
        GlobalThresholdTarget(final String value) {
            this.value = value;
        }

        /**
         * Get value of option-tag which should be selected.
         * @return value of option-tag to select.
         */
        public String getValue() {
            return value;
        }
    }
}
