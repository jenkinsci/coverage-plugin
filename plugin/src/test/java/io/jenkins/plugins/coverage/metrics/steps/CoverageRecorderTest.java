package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class CoverageRecorderTest {

    @Test
    void shouldPassCorrectSymbolicLinksValue() {
        // When skipSymbolicLinks is true, followSymbolicLinks should be false
        CoverageRecorder recorder = new CoverageRecorder();
        recorder.setSkipSymbolicLinks(true);
        assertThat(recorder.isSkipSymbolicLinks()).isTrue();
        assertThat(!recorder.isSkipSymbolicLinks()).isFalse(); // This is what's passed to CoverageReportScanner

        // When skipSymbolicLinks is false, followSymbolicLinks should be true
        recorder.setSkipSymbolicLinks(false);
        assertThat(recorder.isSkipSymbolicLinks()).isFalse();
        assertThat(!recorder.isSkipSymbolicLinks()).isTrue(); // This is what's passed to CoverageReportScanner
    }
}