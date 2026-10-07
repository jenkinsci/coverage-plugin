package io.jenkins.plugins.coverage.metrics.restapi;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.ModuleNode;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FileCoverageApiModel}.
 */
class FileCoverageApiModelTest {
    @Test
    void shouldExposeApiNodeAndDisplayName() {
        var node = new ModuleNode("root");
        var model = new FileCoverageApiModel(node);

        assertThat(model.getNode()).isSameAs(node);
        assertThat(model.getDisplayName()).isEqualTo("Coverage of 'root'");
        assertThat(model.getApi()).isNotNull();
        assertThat(model.getApi().bean).isInstanceOf(FileCoverageApi.class);
    }
}
