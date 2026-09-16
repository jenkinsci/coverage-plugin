package io.jenkins.plugins.coverage.metrics.restapi;

import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Model class that describes the whole-file coverage of a single file. The file is identified by its fully qualified
 * name and each available coverage metric is mapped to its formatted percentage value, e.g. {@code {"line": "88.44%",
 * "branch": "82.19%"}}. Only metrics that actually have a value for this file are included.
 */
@ExportedBean
public class FileCoverage {
    private final String fullyQualifiedFileName;
    private final NavigableMap<String, String> metrics;

    FileCoverage(final String fullyQualifiedFileName, final NavigableMap<String, String> metrics) {
        this.fullyQualifiedFileName = fullyQualifiedFileName;
        this.metrics = new TreeMap<>(metrics);
    }

    @Exported(inline = true)
    public String getFullyQualifiedFileName() {
        return fullyQualifiedFileName;
    }

    @Exported(inline = true)
    public NavigableMap<String, String> getMetrics() {
        return metrics;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (FileCoverage) o;
        return Objects.equals(getFullyQualifiedFileName(), that.getFullyQualifiedFileName())
                && Objects.equals(getMetrics(), that.getMetrics());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFullyQualifiedFileName(), getMetrics());
    }
}
