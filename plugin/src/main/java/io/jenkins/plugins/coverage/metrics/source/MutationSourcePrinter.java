package io.jenkins.plugins.coverage.metrics.source;

import static j2html.TagCreator.*;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Mutation;
import j2html.tags.ContainerTag;
import j2html.tags.UnescapedText;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Provides all required information for a {@link FileNode} so that its source code can be rendered together with the
 * line and mutation coverage in HTML.
 */
final class MutationSourcePrinter extends CoverageSourcePrinter {
    @Serial
    private static final long serialVersionUID = -2215657894423024907L;

    private final int[] survivedPerLine;
    private final int[] killedPerLine;
    private final int[] notCoveredPerLine;
    private final String[] tooltipPerLine;

    MutationSourcePrinter(final FileNode file) {
        this(file, ChangeSet.empty());
    }

    MutationSourcePrinter(final FileNode file, final ChangeSet changes) {
        super(file, changes);

        survivedPerLine = new int[size()];
        killedPerLine = new int[size()];
        notCoveredPerLine = new int[size()];
        tooltipPerLine = new String[size()];
        Arrays.fill(tooltipPerLine, StringUtils.EMPTY);

        extractMutationDetails(file.getMutationsPerLine());

        for (Mutation mutation : file.getMutations()) {
            if (mutation.hasSurvived()) {
                survivedPerLine[findIndexOfLine(mutation.getLine())]++;
            } else if (mutation.isKilled()) {
                killedPerLine[findIndexOfLine(mutation.getLine())]++;
            } else if (mutation.isMissed()) {
                notCoveredPerLine[findIndexOfLine(mutation.getLine())]++;
            }
        }
    }

    private void extractMutationDetails(final NavigableMap<Integer, List<Mutation>> mutationsPerLine) {
        for (Entry<Integer, List<Mutation>> entry : mutationsPerLine.entrySet()) {
            var indexOfLine = findIndexOfLine(entry.getKey());

            tooltipPerLine[indexOfLine] = createInfo(entry.getValue());
        }
    }

    private String createInfo(final List<Mutation> allMutations) {
        var killedContainer = listMutations(allMutations, Mutation::isKilled, "Killed Mutations:");
        var survivedContainer = listMutations(allMutations, Mutation::hasSurvived, "Survived Mutations:");
        if (killedContainer.getNumChildren() == 0 && survivedContainer.getNumChildren() == 0) {
            return "Not covered";
        }
        var info = div().with(killedContainer, survivedContainer);
        var notCoveredContainer = listMutations(allMutations, Mutation::isMissed, "Not Covered Mutations:");
        if (notCoveredContainer.getNumChildren() > 0) {
            info.with(notCoveredContainer);
        }
        return info.render();
    }

    private ContainerTag listMutations(
            final List<Mutation> allMutations, final Predicate<Mutation> predicate, final String title) {
        var filtered = div();
        var killed = asBulletPoints(allMutations, predicate);
        if (!killed.isEmpty()) {
            filtered.with(div().with(new UnescapedText(title), ul().with(killed)));
        }
        return filtered;
    }

    private List<ContainerTag> asBulletPoints(final List<Mutation> mutations, final Predicate<Mutation> predicate) {
        return mutations.stream()
                .filter(predicate)
                .map(mutation -> li().withText("%s (%s)".formatted(mutation.getDescription(), mutation.getMutator())))
                .collect(Collectors.toList());
    }

    int getSurvived(final int line) {
        return getCounter(line, survivedPerLine);
    }

    int getKilled(final int line) {
        return getCounter(line, killedPerLine);
    }

    int getNotCovered(final int line) {
        return getCounter(line, notCoveredPerLine);
    }

    /**
     * Returns the number of mutations in the specified line that have not been killed, i.e., mutations that survived
     * or that have not been covered by any test.
     *
     * @param line
     *         the 1-based line number
     *
     * @return the number of mutations that have not been killed
     */
    private int getNotKilled(final int line) {
        return getSurvived(line) + getNotCovered(line);
    }

    /**
     * Returns a hidden sentinel row that marks this table as a mutation coverage view.
     *
     * @return HTML string for the sentinel mutation row
     */
    @Override
    String getColumnHeader() {
        return tr().withClass("mutation").render();
    }

    /**
     * Renders a source line with the mutation detail tooltip placed on the {@code td.hits} cell only.
     *
     * @param line
     *         the 1-based line number
     * @param sourceCode
     *         the raw source code text for this line
     *
     * @return the rendered HTML {@code <tr>} element
     */
    @Override
    String renderLine(final int line, final String sourceCode) {
        var isPainted = isPainted(line);
        return tr().withClasses(isPainted ? getColorClass(line) : UNDEFINED, getModifiedClass(line))
                .with(
                        td().withClass("line")
                                .with(a().withName(String.valueOf(line)).withText(String.valueOf(line))),
                        td().withClass("hits")
                                .condAttr(
                                        isPainted,
                                        "data-html-tooltip",
                                        isPainted ? getTooltip(line) : StringUtils.EMPTY)
                                .with(isPainted ? text(getSummaryColumn(line)) : text(StringUtils.EMPTY)),
                        td().withClass("code").with(rawHtml(SANITIZER.render(cleanupCode(sourceCode)))))
                .render();
    }

    @Override
    String getColorClass(final int line) {
        if (getKilled(line) == 0) {
            return NO_COVERAGE;
        } else if (getNotKilled(line) == 0) {
            return FULL_COVERAGE;
        } else {
            return PARTIAL_COVERAGE;
        }
    }

    @Override
    String getTooltip(final int line) {
        return StringUtils.defaultIfBlank(tooltipPerLine[findIndexOfLine(line)], super.getTooltip(line));
    }

    @Override
    String getSummaryColumn(final int line) {
        var killed = getKilled(line);
        var total = killed + getNotKilled(line);
        if (total > 0) {
            return "%d/%d".formatted(killed, total);
        }
        return String.valueOf(killed);
    }
}
