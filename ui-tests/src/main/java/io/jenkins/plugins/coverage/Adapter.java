package io.jenkins.plugins.coverage;

import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.PageArea;
import org.jenkinsci.test.acceptance.po.PageAreaImpl;

import io.jenkins.plugins.coverage.AdapterThreshold.AdapterThresholdTarget;

/**
 * Adapter which can be added in the configuration of the {@link CoveragePublisher} of a FreeStyle Project.
 */
public class Adapter extends PageAreaImpl {
    private final Control reportFilePath = control("path");
    private final Control threshold = control("repeatable-add");
    private final Control mergeToOneReport = control("mergeToOneReport");

    private final Control delete = control("repeatable-delete");
    private final Control advancedOptions = control("advanced-button");

    /**
     * Constructor to create {@link Adapter} for {@link CoveragePublisher}.
     *
     * @param reportPublisher
     *         which should be created, f. e. jacoco or cobertura
     * @param path
     *         of parent page
     */
    public Adapter(PageArea reportPublisher, String path) {
        super(reportPublisher, path);
    }

    /**
     * Setter for path of report file.
     *
     * @param reportFilePath
     *         path to report file.
     */
    public void setReportFilePath(String reportFilePath) {
        this.reportFilePath.set(reportFilePath);
    }

    /**
     * Setter for merging to one report.
     *
     * @param mergeReports
     *         boolean for merging to one report
     */
    public void setMergeToOneReport(boolean mergeReports) {
        ensureAdvancedOptionsIsActivated();
        mergeToOneReport.check(mergeReports);
    }

    /**
     * Adds empty {@link AdapterThreshold}.
     * @return new Threshold to Adapter
     */
    public AdapterThreshold createThresholdsPageArea() {
        ensureAdvancedOptionsIsActivated();
        String path = createPageArea("thresholds", () -> this.threshold.click());
        return new AdapterThreshold(this, path);
    }

    /**
     * Adds {@link AdapterThreshold} with values.
     * @param thresholdTarget value using {@link AdapterThresholdTarget}
     * @param unhealthyThreshold value to be set
     * @param unstableThreshold value to be set
     * @param failUnhealthy value for setting if build should fail on unhealthy
     * @return threshold
     */
    public AdapterThreshold createThresholdsPageArea(AdapterThresholdTarget thresholdTarget, double unhealthyThreshold,
            double unstableThreshold, boolean failUnhealthy) {
        ensureAdvancedOptionsIsActivated();
        String path = createPageArea("thresholds", () -> this.threshold.click());
        AdapterThreshold adapterThreshold = new AdapterThreshold(this, path);
        adapterThreshold.setThresholdTarget(thresholdTarget);
        adapterThreshold.setUnhealthyThreshold(unhealthyThreshold);
        adapterThreshold.setUnstableThreshold(unstableThreshold);
        adapterThreshold.setFailUnhealthy(failUnhealthy);
        return adapterThreshold;
    }

    /**
     * Activates advanced options to use setters of {@link Adapter}.
     */
    public void ensureAdvancedOptionsIsActivated() {
        if (advancedOptions.exists()) {
            advancedOptions.click();
        }
    }

    /**
     * Removes adapter.
     */
    public void deleteAdapter() {
        this.delete.click();
    }
}
