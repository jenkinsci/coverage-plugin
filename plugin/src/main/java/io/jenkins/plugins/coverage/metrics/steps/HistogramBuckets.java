package io.jenkins.plugins.coverage.metrics.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Distributes the absolute values of a software metric (e.g., the cyclomatic complexity of the files) into the buckets
 * of a histogram. In contrast to the coverage, these values have no upper limit and are typically skewed: most files
 * have small values while only a few files have very large values. Therefore, two different bucket layouts are
 * evaluated, and the layout that distributes the values more evenly (i.e., the layout with the smaller largest bucket)
 * is used:
 * <ul>
 *     <li>linear: buckets of the same width, the width is a "nice" number (1, 2, 5, 10, 20, 50, ...)</li>
 *     <li>logarithmic: buckets with the "nice" lower bounds 0, 1, 2, 5, 10, 20, 50, ... where the last bucket has no
 *     upper bound</li>
 * </ul>
 * In both layouts, the number of buckets is limited to {@link #MAX_BUCKETS}.
 *
 * @author Ullrich Hafner
 */
final class HistogramBuckets {
    /** The maximum number of buckets. */
    static final int MAX_BUCKETS = 10;

    private static final int[] NICE_FACTORS = {1, 2, 5};
    private static final int DECIMAL = 10;

    private final List<String> labels;
    private final List<Integer> counts;

    /**
     * Distributes the specified values into the buckets of a histogram.
     *
     * @param values
     *         the values to distribute
     *
     * @return the buckets of the histogram, empty if there are no values
     */
    static HistogramBuckets distribute(final Collection<Long> values) {
        if (values.isEmpty()) {
            return new HistogramBuckets(List.of(), List.of());
        }

        var min = values.stream().mapToLong(Long::longValue).min().orElseThrow();
        var max = values.stream().mapToLong(Long::longValue).max().orElseThrow();

        var linear = createLinearLayout(min, max);
        if (min < 0) {
            return linear.distribute(values);
        }
        var linearBuckets = linear.distribute(values);
        var logarithmicBuckets = createLogarithmicLayout(min, max).distribute(values);
        if (logarithmicBuckets.getLargestCount() < linearBuckets.getLargestCount()) {
            return logarithmicBuckets;
        }
        return linearBuckets;
    }

    private static Layout createLinearLayout(final long min, final long max) {
        for (long step = 1; ; step *= DECIMAL) {
            for (int factor : NICE_FACTORS) {
                var width = step * factor;
                var lower = Math.floorDiv(min, width) * width;
                if ((max - lower) / width + 1 <= MAX_BUCKETS) {
                    var bounds = LongStream.range(0, (max - lower) / width + 1)
                            .map(index -> lower + index * width)
                            .boxed()
                            .toList();
                    return new Layout(bounds, width, false);
                }
            }
        }
    }

    private static Layout createLogarithmicLayout(final long min, final long max) {
        var niceBounds = createNiceBounds(max);
        var start = niceBounds.stream()
                .filter(bound -> bound <= min)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
        var bounds = niceBounds.stream()
                .filter(bound -> bound >= start && bound <= max)
                .limit(MAX_BUCKETS)
                .toList();
        return new Layout(bounds, 1, true);
    }

    /**
     * Creates the sequence 0, 1, 2, 5, 10, 20, 50, ... up to the first value that is larger than the specified maximum.
     *
     * @param max
     *         the maximum value
     *
     * @return the nice bounds
     */
    private static List<Long> createNiceBounds(final long max) {
        var bounds = new ArrayList<Long>();
        bounds.add(0L);
        for (long step = 1; bounds.get(bounds.size() - 1) <= max; step *= DECIMAL) {
            for (int factor : NICE_FACTORS) {
                bounds.add(step * factor);
            }
        }
        return bounds;
    }

    private HistogramBuckets(final List<String> labels, final List<Integer> counts) {
        this.labels = labels;
        this.counts = counts;
    }

    /**
     * Returns the labels of the buckets.
     *
     * @return the labels
     */
    List<String> getLabels() {
        return labels;
    }

    /**
     * Returns the number of values in each bucket.
     *
     * @return the counts
     */
    List<Integer> getCounts() {
        return counts;
    }

    private int getLargestCount() {
        return counts.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * The layout of the buckets.
     *
     * @param lowerBounds
     *         the lower bounds of the buckets (ascending)
     * @param width
     *         the width of the last bucket (only used if the last bucket is closed)
     * @param openEnd
     *         determines whether the last bucket has no upper bound
     */
    private record Layout(List<Long> lowerBounds, long width, boolean openEnd) {
        HistogramBuckets distribute(final Collection<Long> values) {
            var counts = new int[lowerBounds.size()];
            values.forEach(value -> counts[getBucket(value)]++);

            return new HistogramBuckets(
                    createLabels(), Arrays.stream(counts).boxed().toList());
        }

        private int getBucket(final long value) {
            for (int bucket = lowerBounds.size() - 1; bucket > 0; bucket--) {
                if (value >= lowerBounds.get(bucket)) {
                    return bucket;
                }
            }
            return 0;
        }

        private List<String> createLabels() {
            var labels = new ArrayList<String>();
            var last = lowerBounds.size() - 1;
            for (int bucket = 0; bucket < last; bucket++) {
                labels.add(createRange(lowerBounds.get(bucket), lowerBounds.get(bucket + 1) - 1));
            }
            var lastBound = lowerBounds.get(last);
            if (openEnd) {
                labels.add("≥ " + lastBound);
            } else {
                labels.add(createRange(lastBound, lastBound + width - 1));
            }
            return labels;
        }

        private static String createRange(final long from, final long to) {
            if (from == to) {
                return String.valueOf(from);
            }
            return from + "–" + to;
        }
    }
}
