package io.jenkins.plugins.coverage;

import org.junit.Test;

import org.jenkinsci.test.acceptance.po.FreeStyleJob;

/**
 * Smoke Test for the most used features of the coverage plugin.
 */
public class SmokeTests extends UiTest {
    /** Verifies that the toggle for failing builds is working when there are no reports. */
    @Test
    public void shouldToggleFailingIfThereAreNoReportsFound() {
        FreeStyleJob job = jenkins.getJobs().create(FreeStyleJob.class);
        var publisher = job.addPublisher(CoveragePublisher.class);
        job.save();

        buildSuccessfully(job);

        job.configure(() -> publisher.setFailOnError(true));

        buildWithErrors(job);
    }

    /** Verifies that the toggle for ignoring parsing errors is working when a report with errors is read. */
    @Test
    public void shouldToggleFailingIfTheCoverageFileIsInvalid() {
        FreeStyleJob job = jenkins.getJobs().create(FreeStyleJob.class);

        job.copyResource("/cobertura-duplicate-methods.xml");

        var publisher = job.addPublisher(CoveragePublisher.class);
        publisher.setTool("Cobertura", "cobertura-*.xml");
        job.save();

        buildWithErrors(job);

        job.configure(() -> publisher.setIgnoreParsingErrors(true));

        buildSuccessfully(job);
    }
}

