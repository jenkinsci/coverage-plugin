package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link HistogramBuckets}.
 *
 * @author Ullrich Hafner
 */
class HistogramBucketsTest {
    @Test
    void shouldCreateNoBucketsWithoutValues() {
        var buckets = HistogramBuckets.distribute(List.of());

        assertThat(buckets.getLabels()).isEmpty();
        assertThat(buckets.getCounts()).isEmpty();
    }

    @Test
    void shouldCreateSingleBucketForEqualValues() {
        var buckets = HistogramBuckets.distribute(List.of(5L, 5L, 5L));

        assertThat(buckets.getLabels()).containsExactly("5");
        assertThat(buckets.getCounts()).containsExactly(3);
    }

    @Test
    void shouldUseOneBucketPerValueForSmallRanges() {
        var buckets = HistogramBuckets.distribute(List.of(1L, 2L, 3L, 7L));

        assertThat(buckets.getLabels()).containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(buckets.getCounts()).containsExactly(1, 1, 1, 0, 0, 0, 1);
    }

    @Test
    void shouldUseNiceWidthForEvenlyDistributedValues() {
        var buckets = HistogramBuckets.distribute(List.of(0L, 10L, 20L, 25L, 30L, 40L, 55L, 60L, 70L, 95L));

        assertThat(buckets.getLabels())
                .hasSize(HistogramBuckets.MAX_BUCKETS)
                .startsWith("0–9", "10–19")
                .endsWith("90–99");
        assertThat(buckets.getCounts()).containsExactly(1, 1, 2, 1, 1, 1, 1, 1, 0, 1);
    }

    @Test
    void shouldStartLinearBucketsAtMinimum() {
        var buckets = HistogramBuckets.distribute(List.of(100L, 105L, 110L, 130L));

        assertThat(buckets.getLabels()).startsWith("100–104").endsWith("130–134");
    }

    @Test
    void shouldUseLogarithmicBucketsForSkewedValues() {
        var buckets =
                HistogramBuckets.distribute(List.of(1L, 1L, 1L, 2L, 2L, 3L, 4L, 6L, 8L, 12L, 15L, 30L, 45L, 80L, 200L));

        assertThat(buckets.getLabels())
                .containsExactly("1", "2–4", "5–9", "10–19", "20–49", "50–99", "100–199", "≥ 200");
        assertThat(buckets.getCounts()).containsExactly(3, 4, 2, 2, 2, 1, 0, 1);
    }

    @Test
    void shouldLimitNumberOfBuckets() {
        var values = LongStream.rangeClosed(0, 10_000).boxed().toList();

        var buckets = HistogramBuckets.distribute(values);

        assertThat(buckets.getLabels()).hasSizeLessThanOrEqualTo(HistogramBuckets.MAX_BUCKETS);
        assertThat(buckets.getCounts().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(values.size());
    }

    @Test
    void shouldUseLinearBucketsForNegativeValues() {
        var buckets = HistogramBuckets.distribute(List.of(-3L, 4L));

        assertThat(buckets.getLabels()).startsWith("-3").endsWith("4");
        assertThat(buckets.getCounts()).startsWith(1).endsWith(1);
    }
}
