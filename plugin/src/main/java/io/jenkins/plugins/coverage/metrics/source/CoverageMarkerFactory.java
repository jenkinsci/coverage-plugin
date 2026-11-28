package io.jenkins.plugins.coverage.metrics.source;

import static j2html.TagCreator.b;
import static j2html.TagCreator.each;
import static j2html.TagCreator.li;
import static j2html.TagCreator.text;
import static j2html.TagCreator.ul;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Mutation;
import io.jenkins.plugins.prism.LineAnnotation;
import io.jenkins.plugins.prism.Marker;
import io.jenkins.plugins.prism.Marker.MarkerBuilder;
import io.jenkins.plugins.prism.Marker.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * Creates the markers and the annotations of the lines that show the coverage of a source code file in the source
 * code view of the Prism API plugin.
 *
 * <ul>
 *     <li>Consecutive lines with the same coverage are combined into a single marker without a box: covered lines are
 *     shown in green, partially covered lines in orange, and lines without coverage in red. The partially covered and
 *     the not covered blocks are part of the navigation of the source code view.</li>
 *     <li>The indirect coverage changes (lines that have not been modified but have a different coverage than in
 *     the reference build) are shown by additional markers: increased coverage in green, decreased coverage in
 *     red.</li>
 *     <li>Lines with mutations that have not been killed get a box with all mutations of the line.</li>
 *     <li>Each line with coverage gets an annotation: the number of covered branches (or 1 if the line has been
 *     covered at least once and 0 otherwise).</li>
 * </ul>
 *
 * <p>
 * Each marker provides the data {@code coverage} (the coverage of the lines, see {@link LineCoverage}),
 * {@code baseline} ({@value #BASELINE_PROJECT} or {@value #BASELINE_INDIRECT}), and {@code modified} (whether the
 * lines have been modified with respect to the reference build). The client side script of the source code page
 * uses this data to filter the markers by the selected baseline.
 * </p>
 *
 * @author Ullrich Hafner
 */
public class CoverageMarkerFactory {
    static final String DATA_COVERAGE = "coverage";
    static final String DATA_BASELINE = "baseline";
    static final String DATA_MODIFIED = "modified";
    static final String BASELINE_PROJECT = "project";
    static final String BASELINE_INDIRECT = "indirect";

    private static final String SEPARATOR = ", ";

    /**
     * Creates the markers and the annotations of the lines of the specified file.
     *
     * @param file
     *         the file with the coverage information
     * @param changes
     *         the code changes with respect to the reference build
     *
     * @return the markers and the annotations of the lines
     */
    public CoverageMarkers create(final FileNode file, final ChangeSet changes) {
        var path = file.getRelativePath();
        Set<Integer> modifiedLines = changes.getModifiedLines(path);
        Map<Integer, Integer> indirectChanges = changes.getIndirectCoverageChanges(path);

        var markers = new ArrayList<Marker>();
        group(file.getLinesWithCoverage(), line -> new LineState(getCoverage(file, line), modifiedLines.contains(line)))
                .forEach(block -> markers.add(createMarker(block, BASELINE_PROJECT)));
        group(
                        new TreeSet<>(indirectChanges.keySet()),
                        line -> new LineState(
                                indirectChanges.get(line) > 0 ? LineCoverage.INCREASED : LineCoverage.DECREASED,
                                modifiedLines.contains(line)))
                .forEach(block -> markers.add(createMarker(block, BASELINE_INDIRECT)));
        file.getMutationsPerLine()
                .forEach((line, mutations) -> createMutationMarker(line, mutations, modifiedLines.contains(line))
                        .ifPresent(markers::add));

        var annotations = file.getLinesWithCoverage().stream()
                .map(line -> createAnnotation(file, line, indirectChanges))
                .toList();
        return new CoverageMarkers(markers, annotations);
    }

    private LineCoverage getCoverage(final FileNode file, final int line) {
        return LineCoverage.fromCounters(file.getCoveredOfLine(line), file.getMissedOfLine(line));
    }

    /**
     * Groups the specified lines into blocks of consecutive lines with the same state.
     *
     * @param lines
     *         the lines to group
     * @param stateOfLine
     *         the function that computes the state of a line
     *
     * @return the blocks, sorted by their first line
     */
    private List<Block> group(final NavigableSet<Integer> lines, final IntFunction<LineState> stateOfLine) {
        var blocks = new ArrayList<Block>();
        for (int line : lines) {
            var state = stateOfLine.apply(line);
            int last = blocks.size() - 1;
            if (last >= 0 && blocks.get(last).canExtend(line, state)) {
                blocks.set(last, blocks.get(last).extend(line));
            } else {
                blocks.add(new Block(line, line, state));
            }
        }
        return blocks;
    }

    private Marker createMarker(final Block block, final String baseline) {
        var coverage = block.state().coverage();
        var builder = new MarkerBuilder()
                .withId("%s-L%d-%d".formatted(baseline, block.start(), block.end()))
                .withLineStart(block.start())
                .withLineEnd(block.end())
                .withSeverity(coverage.getSeverity())
                .withData(DATA_COVERAGE, coverage.getName())
                .withData(DATA_BASELINE, baseline)
                .withData(DATA_MODIFIED, String.valueOf(block.state().isModified()));
        if (coverage.isNavigable()) {
            builder.withNavigation();
        }
        return builder.build();
    }

    private Optional<Marker> createMutationMarker(
            final int line, final List<Mutation> mutations, final boolean isModified) {
        long killed = count(mutations, Mutation::isKilled);
        long survived = count(mutations, Mutation::hasSurvived);
        long notCovered = count(mutations, Mutation::isMissed);
        if (survived + notCovered == 0) {
            return Optional.empty();
        }
        return Optional.of(new MarkerBuilder()
                .withId("mutations-L" + line)
                .withLineStart(line)
                .withSeverity(killed == 0 ? Severity.ERROR : Severity.WARNING)
                .withTitle(Messages.SourceCode_Mutations_Title(killed, survived, notCovered))
                .withDescription(createMutationList(mutations))
                .withData(DATA_COVERAGE, "mutation")
                .withData(DATA_BASELINE, BASELINE_PROJECT)
                .withData(DATA_MODIFIED, String.valueOf(isModified))
                .build());
    }

    private long count(final List<Mutation> mutations, final Predicate<Mutation> predicate) {
        return mutations.stream().filter(predicate).count();
    }

    private String createMutationList(final List<Mutation> mutations) {
        return ul(each(
                        mutations,
                        mutation -> li(
                                b(getStatus(mutation)),
                                text(": %s (%s)".formatted(mutation.getDescription(), mutation.getMutator())))))
                .render();
    }

    private String getStatus(final Mutation mutation) {
        if (mutation.isKilled()) {
            return Messages.SourceCode_Mutation_Killed();
        }
        if (mutation.hasSurvived()) {
            return Messages.SourceCode_Mutation_Survived();
        }
        return Messages.SourceCode_Mutation_NotCovered();
    }

    private LineAnnotation createAnnotation(
            final FileNode file, final int line, final Map<Integer, Integer> indirectChanges) {
        int covered = file.getCoveredOfLine(line);
        int missed = file.getMissedOfLine(line);
        var tooltip = new ArrayList<String>();
        tooltip.add(getTooltip(covered, missed));
        if (indirectChanges.containsKey(line)) {
            tooltip.add(Messages.SourceCode_IndirectChange("%+d".formatted(indirectChanges.get(line))));
        }
        var text = covered + missed > 1 ? "%d/%d".formatted(covered, covered + missed) : String.valueOf(covered);
        return new LineAnnotation(line, text, String.join(SEPARATOR, tooltip));
    }

    private String getTooltip(final int covered, final int missed) {
        if (covered + missed > 1) {
            if (missed == 0) {
                return Messages.SourceCode_Branches_Covered(covered);
            }
            if (covered == 0) {
                return Messages.SourceCode_Branches_Missed(missed);
            }
            return Messages.SourceCode_Branches_Partial(covered, covered + missed);
        }
        if (covered > 0) {
            return Messages.SourceCode_Line_Covered();
        }
        return Messages.SourceCode_Line_Missed();
    }

    /**
     * The coverage of a block of lines. The coverage defines the color of the lines and whether the lines are part of
     * the navigation of the source code view.
     */
    enum LineCoverage {
        /** All branches of the lines have been covered. */
        COVERED(Severity.SUCCESS, false),
        /** Some branches of the lines have been covered. */
        PARTIAL(Severity.WARNING, true),
        /** The lines have not been covered. */
        MISSED(Severity.ERROR, true),
        /** The coverage of the lines has been increased (indirect coverage change). */
        INCREASED(Severity.SUCCESS, true),
        /** The coverage of the lines has been decreased (indirect coverage change). */
        DECREASED(Severity.ERROR, true);

        private final Severity severity;
        private final boolean navigable;

        LineCoverage(final Severity severity, final boolean navigable) {
            this.severity = severity;
            this.navigable = navigable;
        }

        static LineCoverage fromCounters(final int covered, final int missed) {
            if (covered == 0) {
                return MISSED;
            }
            if (missed == 0) {
                return COVERED;
            }
            return PARTIAL;
        }

        Severity getSeverity() {
            return severity;
        }

        boolean isNavigable() {
            return navigable;
        }

        String getName() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }

    /**
     * The state of a line: its coverage and whether the line has been modified.
     *
     * @param coverage
     *         the coverage of the line
     * @param isModified
     *         determines whether the line has been modified with respect to the reference build
     */
    private record LineState(LineCoverage coverage, boolean isModified) {}

    /**
     * A block of consecutive lines with the same state.
     *
     * @param start
     *         the first line of the block
     * @param end
     *         the last line of the block
     * @param state
     *         the state of all lines of the block
     */
    private record Block(int start, int end, LineState state) {
        boolean canExtend(final int line, final LineState other) {
            return line == end + 1 && state.equals(other);
        }

        Block extend(final int line) {
            return new Block(start, line, state);
        }
    }

    /**
     * The markers and the annotations of the lines of a source code file.
     *
     * @param markers
     *         the markers that highlight the coverage of the lines
     * @param annotations
     *         the annotations of the lines
     */
    public record CoverageMarkers(List<Marker> markers, List<LineAnnotation> annotations) {}
}
