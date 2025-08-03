package io.jenkins.plugins.coverage.metrics;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import jenkins.appearance.AppearanceCategory;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Global appearance configuration for Coverage Metrics.
 */
@Extension
@Symbol("coverage")
public class CoverageAppearanceGlobalConfiguration extends GlobalConfiguration {
    private boolean enableColumnByDefault = true;

    /**
     * Get global configuration instance.
     * @return global configuration
     * @throws IllegalStateException if configuration not available
     */
    @NonNull
    public static CoverageAppearanceGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(CoverageAppearanceGlobalConfiguration.class);
    }

    /**
     * Create global coverage appearance configuration.
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR", justification = "GlobalConfiguration instructs subclasses to call load()")
    @DataBoundConstructor
    public CoverageAppearanceGlobalConfiguration() {
        super();
        load();
    }

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(AppearanceCategory.class);
    }

    /**
     * Is the metric column enabled by default.
     * @return {@code true} if coverage metric column should be displayed by default
     */
    public boolean isEnableColumnByDefault() {
        return enableColumnByDefault;
    }

    /**
     * Enable/disable metric column display by default.
     * @param enableColumnByDefault {@code true} to enable metric column by default.
     */
    @SuppressWarnings("unused") // Called by jelly view
    @DataBoundSetter
    public void setEnableColumnByDefault(final boolean enableColumnByDefault) {
        this.enableColumnByDefault = enableColumnByDefault;
        save();
    }
}
