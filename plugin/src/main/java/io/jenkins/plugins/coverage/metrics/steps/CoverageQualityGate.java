package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.util.VisibleForTesting;

import java.io.Serial;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;
import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.coverage.metrics.model.MetricAggregation;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.QualityGate;

/**
 * Defines a quality gate based on a specific threshold of code coverage in the current build. After a build has been
 * finished, a set of {@link CoverageQualityGate quality gates} will be evaluated and the overall quality gate status will be
 * reported in Jenkins UI.
 *
 * @author Johannes Walter
 */
@SuppressWarnings("PMD.DataClass") 
public class CoverageQualityGate extends QualityGate {
    @Serial
    private static final long serialVersionUID = -397278599489426668L;

    private static final ElementFormatter FORMATTER = new ElementFormatter();

    private final Metric metric;
    private Baseline baseline = Baseline.PROJECT;
    private MetricAggregation aggregation = MetricAggregation.TOTAL;

    /**
     * Creates a new instance of {@link CoverageQualityGate}.
     *
     * @param metric
     *         the metric to compare
     */
    @DataBoundConstructor
    @SuppressWarnings("this-escape")
    public CoverageQualityGate(final Metric metric) {
        this(metric, 0.0);
    }

    @SuppressWarnings("this-escape")
    CoverageQualityGate(final Metric metric, final double threshold) {
        super();

        setThreshold(threshold);

        this.metric = metric;
    }

    CoverageQualityGate(final double threshold, final Metric metric,
            final Baseline baseline, final QualityGateCriticality criticality) {
        this(metric, threshold);

        setBaseline(baseline);
        setCriticality(criticality);
    }

    CoverageQualityGate(final double threshold, final Metric metric,
            final Baseline baseline, final QualityGateCriticality criticality,
            final MetricAggregation aggregation) {
        this(metric, threshold);

        setBaseline(baseline);
        setCriticality(criticality);
        setAggregation(aggregation);
    }

    /**
     * Sets the baseline that will be used for the quality gate evaluation.
     *
     * @param baseline
     *         the baseline to use
     */
    @DataBoundSetter
    public final void setBaseline(final Baseline baseline) {
        this.baseline = baseline;
    }

    /**
     * Sets the aggregation mode for software metrics (total, maximum, or average). This is only applicable for
     * software metrics like cyclomatic complexity. For coverage metrics, this setting is ignored.
     *
     * @param aggregation
     *         the aggregation mode to use
     */
    @DataBoundSetter
    public final void setAggregation(final MetricAggregation aggregation) {
        this.aggregation = aggregation;
    }

    /**
     * Returns a human-readable name of the quality gate.
     *
     * @return a human-readable name
     */
    @Override
    public String getName() {
        if (MetricAggregation.isSupported(metric) && aggregation != MetricAggregation.TOTAL) {
            return "%s - %s (%s)".formatted(FORMATTER.getDisplayName(getBaseline()),
                    FORMATTER.getDisplayName(getMetric()), aggregation);
        }
        return "%s - %s".formatted(FORMATTER.getDisplayName(getBaseline()),
                FORMATTER.getDisplayName(getMetric()));
    }

    public Metric getMetric() {
        return metric;
    }

    public Baseline getBaseline() {
        return baseline;
    }

    public MetricAggregation getAggregation() {
        return aggregation;
    }

    /**
     * Descriptor of the {@link CoverageQualityGate}.
     */
    @Extension
    public static class Descriptor extends QualityGateDescriptor {
        private final JenkinsFacade jenkins;

        @VisibleForTesting
        Descriptor(final JenkinsFacade jenkinsFacade) {
            super();

            jenkins = jenkinsFacade;
        }

        /**
         * Creates a new descriptor.
         */
        @SuppressWarnings("unused") // Required for Jenkins Extensions
        public Descriptor() {
            this(new JenkinsFacade());
        }

        /**
         * Returns a model with all {@link Metric metrics} that can be used in quality gates.
         *
         * @return a model with all {@link Metric metrics}.
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillMetricItems() {
            if (jenkins.hasPermission(Jenkins.READ)) {
                return FORMATTER.getMetricItems();
            }
            return new ListBoxModel();
        }

        /**
         * Returns a model with all {@link Metric metrics} that can be used in quality gates.
         *
         * @return a model with all {@link Metric metrics}.
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillBaselineItems() {
            if (jenkins.hasPermission(Jenkins.READ)) {
                return FORMATTER.getBaselineItems();
            }
            return new ListBoxModel();
        }

        /**
         * Returns a model with all {@link MetricAggregation aggregation modes}.
         *
         * @return a model with all {@link MetricAggregation aggregation modes}.
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillAggregationItems() {
            if (jenkins.hasPermission(Jenkins.READ)) {
                return FORMATTER.getAggregationItems();
            }
            return new ListBoxModel();
        }
    }
}
