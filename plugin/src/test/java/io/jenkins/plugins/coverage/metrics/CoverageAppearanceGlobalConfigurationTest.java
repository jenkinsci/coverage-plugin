package io.jenkins.plugins.coverage.metrics;

import io.jenkins.plugins.util.GlobalConfigurationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link CoverageAppearanceGlobalConfiguration}.
 */
class CoverageAppearanceGlobalConfigurationTest {
    private CoverageAppearanceGlobalConfiguration configuration;
    private GlobalConfigurationFacade globalConfigurationFacade;

    @BeforeEach
    void setup() {
        globalConfigurationFacade = mock(GlobalConfigurationFacade.class);
        configuration = new CoverageAppearanceGlobalConfiguration(globalConfigurationFacade);
    }

    @Test
    void shouldInitializeThemes() {
        assertThat(configuration.isEnableColumnByDefault()).isTrue();
        configuration.setEnableColumnByDefault(false);
        assertThat(configuration.isEnableColumnByDefault()).isFalse();
        InOrder inOrder = inOrder(globalConfigurationFacade);
        inOrder.verify(globalConfigurationFacade).load();
        inOrder.verify(globalConfigurationFacade).save();
        inOrder.verifyNoMoreInteractions();
    }
}
