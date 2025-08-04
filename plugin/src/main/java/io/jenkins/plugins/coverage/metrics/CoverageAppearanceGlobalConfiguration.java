package io.jenkins.plugins.coverage.metrics;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import io.jenkins.plugins.util.GlobalConfigurationFacade;
import io.jenkins.plugins.util.GlobalConfigurationItem;
import jenkins.appearance.AppearanceCategory;
import jenkins.model.GlobalConfigurationCategory;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Global appearance configuration for Coverage Metrics.
 */
@Extension
@Symbol("coverage")
public class CoverageAppearanceGlobalConfiguration extends GlobalConfigurationItem {
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
    @SuppressWarnings("unused")
    @DataBoundConstructor
    public CoverageAppearanceGlobalConfiguration() {
        super();
        load();
    }

    /**
     * Constructor for unit testing.
     * @param facade global configuration facade
     */
    @VisibleForTesting
    CoverageAppearanceGlobalConfiguration(final GlobalConfigurationFacade facade) {
        super(facade);
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
