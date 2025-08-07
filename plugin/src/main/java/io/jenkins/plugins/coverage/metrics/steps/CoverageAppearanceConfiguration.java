package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.appearance.AppearanceCategory;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;

import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.util.GlobalConfigurationFacade;
import io.jenkins.plugins.util.GlobalConfigurationItem;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Global appearance configuration for the Coverage Plugin.
 */
@Extension
@Symbol("coverage")
public class CoverageAppearanceConfiguration extends GlobalConfigurationItem {
    private static final ElementFormatter FORMATTER = new ElementFormatter();

    /**
     * Returns the singleton instance of this {@link CoverageAppearanceConfiguration}.
     *
     * @return the singleton instance
     */
    public static CoverageAppearanceConfiguration getInstance() {
        return Objects.requireNonNull(all().get(CoverageAppearanceConfiguration.class));
    }

    private boolean enableColumnByDefault = true;
    private Metric defaultMetric = Metric.LINE;
    private String defaultName = Messages.Coverage_Column();

    private final JenkinsFacade jenkins;

    /**
     * Creates the global configuration and loads the initial values from the corresponding
     * XML file.
     */
    @DataBoundConstructor
    public CoverageAppearanceConfiguration() {
        super();

        jenkins =  new JenkinsFacade();

        load();
    }

    @VisibleForTesting
    CoverageAppearanceConfiguration(final GlobalConfigurationFacade facade, final JenkinsFacade jenkins) {
        super(facade);

        this.jenkins = jenkins;

        load();
    }

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(AppearanceCategory.class);
    }

    /**
     * Returns whether the coverage column should be displayed by default.
     *
     * @return {@code true} if the coverage column is shown by default, {@code false} otherwise
     */
    public boolean isEnableColumnByDefault() {
        return enableColumnByDefault;
    }

    /**
     * Enables or disables the coverage column by default.
     *
     * @param enableColumnByDefault
     *         {@code true} to enable the coverage column by default, {@code false} to disable it
     */
    @DataBoundSetter
    public void setEnableColumnByDefault(final boolean enableColumnByDefault) {
        this.enableColumnByDefault = enableColumnByDefault;

        save();
    }

    /**
     * Returns the default metric to be used.
     *
     * @return the default metric
     */
    public Metric getDefaultMetric() {
        return defaultMetric;
    }

    /**
     * Sets the default metric to be used.
     *
     * @param defaultMetric
     *         the default metric to use
     */
    @DataBoundSetter
    public void setDefaultMetric(final Metric defaultMetric) {
        this.defaultMetric = defaultMetric;

        save();
    }

    /**
     * Returns the default name for the coverage column.
     *
     * @return the default name for the coverage column
     */
    public String getDefaultName() {
        return defaultName;
    }

    /**
     * Sets the default name for the coverage column.
     *
     * @param defaultName
     *         the default name for the coverage column
     */
    @DataBoundSetter
    public void setDefaultName(final String defaultName) {
        this.defaultName = defaultName;

        save();
    }

    /**
     * Returns a model with all {@link Metric metrics} that can be used in the column.
     *
     * @return a model with all {@link Metric metrics}.
     */
    @POST
    @SuppressWarnings("unused") // used by Stapler view data binding
    public ListBoxModel doFillDefaultMetricItems() {
        if (jenkins.hasPermission(Jenkins.READ)) {
            return FORMATTER.getMetricItems();
        }
        return new ListBoxModel();
    }
}
