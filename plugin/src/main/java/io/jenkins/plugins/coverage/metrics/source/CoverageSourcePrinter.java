package io.jenkins.plugins.coverage.metrics.source;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import edu.hm.hafner.coverage.FileNode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;

import io.jenkins.plugins.prism.Sanitizer;

import static j2html.TagCreator.*;

/**
 * Provides all required information for a {@link FileNode} so that its source code can be rendered together with the
 * line and branch coverage in HTML.
 */
class CoverageSourcePrinter implements Serializable {
    @Serial
    private static final long serialVersionUID = -6044649044983631852L;

    static final Sanitizer SANITIZER = new Sanitizer();
    static final String MODIFIED = "modified";
    static final String UNDEFINED = "noCover";
    static final String NO_COVERAGE = "coverNone";
    static final String FULL_COVERAGE = "coverFull";
    static final String PARTIAL_COVERAGE = "coverPart";
    static final String NBSP = "&nbsp;";

    private final String path;
    private final int[] linesToPaint;
    private final int[] coveredPerLine;

    private final int[] missedPerLine;

    private final Set<Integer> modifiedLines;

    CoverageSourcePrinter(final FileNode file) {
        path = file.getRelativePath();

        linesToPaint = file.getLinesWithCoverage().stream().mapToInt(i -> i).toArray();
        coveredPerLine = file.getCoveredCounters();
        missedPerLine = file.getMissedCounters();
        modifiedLines = file.getModifiedLines();
    }

    public String renderLine(final int line, final String sourceCode) {
        var isPainted = isPainted(line);
        return tr()
                .withClasses(isPainted ? getColorClass(line) : UNDEFINED, getModifiedClass(line))
                .condAttr(isPainted, "data-html-tooltip", isPainted ? getTooltip(line) : StringUtils.EMPTY)
                .with(
                        td().withClass("line")
                                .with(a().withName(String.valueOf(line)).withText(String.valueOf(line))),
                        td().withClass("hits")
                                .with(isPainted ? text(getSummaryColumn(line)) : text(StringUtils.EMPTY)),
                        td().withClass("code")
                                .with(rawHtml(SANITIZER.render(cleanupCode(sourceCode)))))
                .render();
    }

    protected String cleanupCode(final String content) {
        var escaped = StringEscapeUtils.escapeHtml4(content);
        return escaped.replace("\n", StringUtils.EMPTY)
                .replace("\r", StringUtils.EMPTY)
                .replace(" ", NBSP)
                .replace("\t", NBSP.repeat(8));
    }

    final int size() {
        return linesToPaint.length;
    }

    String getModifiedClass(final int line) {
        return isModified(line) ? MODIFIED : StringUtils.EMPTY;
    }

    public String getColorClass(final int line) {
        if (getCovered(line) == 0) {
            return NO_COVERAGE;
        }
        else if (getMissed(line) == 0) {
            return FULL_COVERAGE;
        }
        else {
            return PARTIAL_COVERAGE;
        }
    }

    public String getTooltip(final int line) {
        var covered = getCovered(line);
        var missed = getMissed(line);
        if (covered + missed > 1) {
            if (missed == 0) {
                return "All branches covered";
            }
            if (covered == 0) {
                return "No branches covered";
            }
            return "Partially covered, branch coverage: %d/%d".formatted(covered, covered + missed);
        }
        else if (covered == 1) {
            return "Covered at least once";
        }
        else {
            return "Not covered";
        }
    }

    public String getSummaryColumn(final int line) {
        var covered = getCovered(line);
        var missed = getMissed(line);
        if (covered + missed > 1) {
            return "%d/%d".formatted(covered, covered + missed);
        }
        return String.valueOf(covered);
    }

    public final String getPath() {
        return path;
    }

    public boolean isPainted(final int line) {
        return findIndexOfLine(line) >= 0;
    }

    private boolean isModified(final int line) {
        return modifiedLines.contains(line);
    }

    int findIndexOfLine(final int line) {
        return Arrays.binarySearch(linesToPaint, line);
    }

    public int getCovered(final int line) {
        return getCounter(line, coveredPerLine);
    }

    public int getMissed(final int line) {
        return getCounter(line, missedPerLine);
    }

    int getCounter(final int line, final int... counters) {
        var index = findIndexOfLine(line);
        if (index >= 0) {
            return counters[index];
        }
        return 0;
    }

    public String getColumnHeader() {
        return StringUtils.EMPTY;
    }
}
