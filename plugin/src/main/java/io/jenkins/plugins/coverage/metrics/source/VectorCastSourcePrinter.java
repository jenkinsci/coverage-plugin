package io.jenkins.plugins.coverage.metrics.source;

import static j2html.TagCreator.*;

import edu.hm.hafner.coverage.FileNode;
import j2html.tags.ContainerTag;
import java.io.Serial;
import org.apache.commons.lang3.StringUtils;

/**
 * Provides all required information for a {@link FileNode} so that its source code can be rendered together with the
 * line and mutation coverage in HTML.
 */
@SuppressWarnings("PMD.GodClass")
public class VectorCastSourcePrinter extends CoverageSourcePrinter {
    @Serial
    private static final long serialVersionUID = 7204367145168517936L;

    private final int[] mcdcPairCoveredPerLine;
    private final int[] mcdcPairMissedPerLine;

    private final int[] functionCallCoveredPerLine;
    private final int[] functionCallMissedPerLine;

    VectorCastSourcePrinter(final FileNode file) {
        super(file);
        mcdcPairCoveredPerLine = file.getMcdcPairCoveredCounters();
        mcdcPairMissedPerLine = file.getMcdcPairMissedCounters();

        functionCallCoveredPerLine = file.getFunctionCallCoveredCounters();
        functionCallMissedPerLine = file.getFunctionCallMissedCounters();
    }

    /**
     * Gets the tr HTML tag for this source line. Used for case where both MCDC and FCC are present.
     *
     * @param line
     *         line number for the summary data
     *
     * @param sourceCode
     *         line of source code
     *
     * @param isPainted
     *         indicator of if the line should be painted
     *
     * @param third
     *         third column string.
     *
     * @param fouth
     *         fouth column string.
     *
     * @return string for the html row
     *
     */
    private String getTr(
            final int line, final String sourceCode, final boolean isPainted, final String third, final String fouth) {
        var coverageSummary = isPainted ? getTooltip(line) : StringUtils.EMPTY;
        var trData = tr().withClasses(isPainted ? getColorClass(line) : UNDEFINED, getModifiedClass(line))
                .condAttr(!coverageSummary.isEmpty(), "data-block-label", coverageSummary);

        trData.with(
                td().withClass("line").with(a().withName(String.valueOf(line)).withText(String.valueOf(line))),
                createMetricCell(getSummaryColumn(line), super.getTooltip(line), isPainted, super.getColorClass(line)));

        if (hasAnyFunctionCallCoverage()) {
            trData.with(createMetricCell(
                    third,
                    getFunctionCallTooltip(line),
                    isPainted,
                    getMetricColorClass(getFunctionCallCovered(line), getFunctionCallMissed(line))));
        }
        if (hasAnyMcdcPairCoverage()) {
            var summary = hasAnyFunctionCallCoverage() ? fouth : third;
            trData.with(createMetricCell(
                    summary,
                    getMcdcPairTooltip(line),
                    isPainted,
                    getMetricColorClass(getMcdcPairCovered(line), getMcdcPairMissed(line))));
        }

        trData.with(td().withClass("code").with(rawHtml(SANITIZER.render(cleanupCode(sourceCode)))));

        return trData.render();
    }

    private ContainerTag createMetricCell(
            final String summary, final String tooltip, final boolean isPainted, final String colorClass) {
        return td().withClasses("hits", isPainted ? colorClass : UNDEFINED)
                .condAttr(isPainted && !tooltip.isEmpty(), "data-html-tooltip", tooltip)
                .with(isPainted ? text(summary) : text(StringUtils.EMPTY));
    }

    private String getMetricColorClass(final int covered, final int missed) {
        if (covered + missed == 0) {
            return UNDEFINED;
        }
        if (covered == 0) {
            return NO_COVERAGE;
        }
        return missed == 0 ? FULL_COVERAGE : PARTIAL_COVERAGE;
    }

    /**
     * Gets the tr HTML tag for this source line. Used for case where neither MCDC or FCC  are present.
     *
     * @param line
     *         line number for the summary data
     *
     * @param sourceCode
     *         line of source code
     *
     * @param isPainted
     *         indicator of if the line should be painted
     *
     * @return string for the html row
     *
     */
    private String getTr(final int line, final String sourceCode, final boolean isPainted) {
        return getTr(line, sourceCode, isPainted, StringUtils.EMPTY, StringUtils.EMPTY);
    }

    /**
     * Gets the tr HTML tag for this source line. Used for MCDC or FCC but not both.
     *
     * @param line
     *         line number for the summary data
     *
     * @param sourceCode
     *         line of source code
     *
     * @param isPainted
     *         indicator of if the line should be painted
     *
     * @param third
     *         third column string.
     *
     * @return string for the html row
     *
     */
    private String getTr(final int line, final String sourceCode, final boolean isPainted, final String third) {
        return getTr(line, sourceCode, isPainted, third, StringUtils.EMPTY);
    }

    /**
     * Main call to render the source line in HTML table format.
     *
     * @param line
     *         line number for the summary data
     *
     * @param sourceCode
     *         line of source code
     *
     * @return string of the source code line in HTML format
     *
     */
    @Override
    String renderLine(final int line, final String sourceCode) {
        var isPainted = isPainted(line);
        var hasMcdc = hasAnyMcdcPairCoverage();
        var hasFc = hasAnyFunctionCallCoverage();

        String trString;

        // If this file only has Line, St/Br, and FunctionCall
        if (!hasMcdc && hasFc) {
            trString = getTr(line, sourceCode, isPainted, getFunctionCallSummaryColumn(line));
        }
        // If this file only has Line, St/Br, and MCDC
        else if (hasMcdc && !hasFc) {
            trString = getTr(line, sourceCode, isPainted, getMcdcPairSummaryColumn(line));
        }
        // If this file only has Line, St/Br, FunctionCall and MCDC
        else if (hasMcdc && hasFc) {
            trString = getTr(
                    line, sourceCode, isPainted, getFunctionCallSummaryColumn(line), getMcdcPairSummaryColumn(line));
        }
        // If this file only has Line and St/Br
        else {
            trString = getTr(line, sourceCode, isPainted);
        }
        return trString;
    }

    /**
     * Gets the column header given MCDC or FCC but not both.
     *
     * @param third
     *         third column string.
     *
     * @return string for the column header
     *
     */
    private String getColumnHeader(final String third) {
        return getColumnHeader(third, StringUtils.EMPTY);
    }

    /**
     * Gets the column header.
     *
     * @param third
     *         third column string.
     *
     * @param fourth
     *         fourth column string.
     *
     * @return string for the column header
     *
     */
    private String getColumnHeader(final String third, final String fourth) {
        var header = tr().withClass(UNDEFINED)
                .with(
                        td().withClass("line").with(text("Line")),
                        td().withClass("hits").with(text("St/Br")));
        if (!third.isEmpty()) {
            header.with(td().withClass("hits").with(text(third)));
        }
        if (!fourth.isEmpty()) {
            header.with(td().withClass("hits").with(text(fourth)));
        }
        return header.with(td().withClass("code").with(rawHtml(NBSP))).render();
    }

    /**
     * Gets the source code column header.
     *
     * @return string of the column header
     *
     */
    @Override
    String getColumnHeader() {
        var hasMcdc = hasAnyMcdcPairCoverage();
        var hasFc = hasAnyFunctionCallCoverage();
        String trString;

        // If this file only has Line, St/Br, and FunctionCall
        if (!hasMcdc && hasFc) {
            trString = getColumnHeader("FCall");
        }
        // If this file only has Line, St/Br, and MCDC
        else if (hasMcdc && !hasFc) {
            trString = getColumnHeader("MC/DC");
        }
        // If this file only has Line, St/Br, FunctionCall and MCDC
        else if (hasMcdc && hasFc) {
            trString = getColumnHeader("FCall", "MC/DC");
        }
        // If this file only has Line and St/Br
        else {
            // this is the original metrics so maybe don't print header?
            trString = getColumnHeader(StringUtils.EMPTY, StringUtils.EMPTY);
        }

        return trString;
    }

    /**
     * Gets the color class depending on coverage types.
     *
     * @param line
     *         line number for the summary data
     *
     * @return string of the color
     *
     */
    @Override
    String getColorClass(final int line) {
        if (getCovered(line) == 0 && getMcdcPairCovered(line) == 0 && getFunctionCallCovered(line) == 0) {
            return NO_COVERAGE;
        } else if (getMissed(line) == 0 && getMcdcPairMissed(line) == 0 && getFunctionCallMissed(line) == 0) {
            return FULL_COVERAGE;
        } else {
            return PARTIAL_COVERAGE;
        }
    }

    /**
     * Constructs the tooltip depending on the coverage.
     *
     * @param line
     *         line number for the summary data
     *
     * @return the MCDC Pair tooltip
     *
     */
    private String getMcdcPairTooltip(final int line) {
        var mcdcPairCovered = getMcdcPairCovered(line);
        var mcdcPairMissed = getMcdcPairMissed(line);

        return getTooltip(mcdcPairCovered, mcdcPairMissed, "MC/DC pairs");
    }

    /**
     * Constructs the tooltip depending on the coverage.
     *
     * @param line
     *         line number for the summary data
     *
     * @return the function call tooltip
     *
     */
    private String getFunctionCallTooltip(final int line) {
        var functionCallCovered = getFunctionCallCovered(line);
        var functionCallMissed = getFunctionCallMissed(line);

        return getTooltip(functionCallCovered, functionCallMissed, "Function calls");
    }

    /**
     * Constructs the tooltip depending on the coverage.
     *
     * @param covered
     *         count of the covered coverage
     *
     * @param missed
     *         count of the missed coverage
     *
     * @param description
     *         Description of the coverage - MCDC/FCC
     *
     * @return
     *         string of the tooltip
     */
    private String getTooltip(final int covered, final int missed, final String description) {
        var tooltip = "";

        if (covered + missed > 1) {
            if (missed == 0) {
                tooltip = "All %s covered: %d/%d".formatted(description, covered, covered + missed);
            } else if (covered == 0) {
                tooltip = "No %s covered: 0/%d".formatted(description, missed);
            } else {
                tooltip = "%s partially covered: %d/%d".formatted(description, covered, covered + missed);
            }
        } else if (covered + missed == 1) {
            tooltip = "%s %s: %d/1".formatted(description, covered == 1 ? "covered" : "not covered", covered);
        }

        return tooltip;
    }

    /**
     * Constructs the tooltip depending on the coverage.
     *
     * @param line
     *         line number for the summary data
     *
     * @return
     *         string of the tooltip
     */
    @Override
    String getTooltip(final int line) {
        var toolTipString = "";

        var lineBranchToolTipString = super.getTooltip(line);
        var mcdcPairToolTipString = getMcdcPairTooltip(line);
        var functionCallToolTipString = getFunctionCallTooltip(line);

        if (lineBranchToolTipString.length() > 0) {
            toolTipString += lineBranchToolTipString;
        }
        if (mcdcPairToolTipString.length() > 0) {
            if (toolTipString.length() > 0) {
                toolTipString += " | ";
            }
            toolTipString += mcdcPairToolTipString;
        }
        if (functionCallToolTipString.length() > 0) {
            if (toolTipString.length() > 0) {
                toolTipString += " | ";
            }
            toolTipString += functionCallToolTipString;
        }

        return toolTipString;
    }

    /**
     * Returns true if there is any MCDC Pair coverage.
     *
     * @return
     *         true if there is any MCDC Pair coverage
     */
    private boolean hasAnyMcdcPairCoverage() {
        boolean hasMcDc = false;
        for (int i = 0; i < mcdcPairCoveredPerLine.length && !hasMcDc; i++) {
            if ((mcdcPairCoveredPerLine[i] + mcdcPairMissedPerLine[i]) > 0) {
                hasMcDc = true;
                break;
            }
        }

        return hasMcDc;
    }

    /**
     * Returns true if there is any Function Call coverage.
     *
     * @return
     *         true if there is any Function Call coverage
     */
    private boolean hasAnyFunctionCallCoverage() {
        boolean hasFc = false;
        for (int i = 0; i < functionCallMissedPerLine.length && !hasFc; i++) {
            if ((functionCallCoveredPerLine[i] + functionCallMissedPerLine[i]) > 0) {
                hasFc = true;
                break;
            }
        }

        return hasFc;
    }

    /**
     * Returns the count of covered MCDC Pairs on a line.
     *
     * @param line
     *         line number for the summary data
     * @return
     *         the number of covered MCDC Pairs on a line
     */
    private int getMcdcPairCovered(final int line) {
        return getCounter(line, mcdcPairCoveredPerLine);
    }

    /**
     * Returns the count of missed MCDC Pairs on a line.
     *
     * @param line
     *         line number for the summary data
     * @return
     *         the number of missed MCDC Pairs on a line
     */
    private int getMcdcPairMissed(final int line) {
        return getCounter(line, mcdcPairMissedPerLine);
    }

    /**
     * Returns the count of covered Function Calls on a line.
     *
     * @param line
     *         line number for the summary data
     * @return
     *         the number of covered Function Calls on a line
     */
    private int getFunctionCallCovered(final int line) {
        return getCounter(line, functionCallCoveredPerLine);
    }

    /**
     * Returns the count of missed Function Calls on a line.
     *
     * @param line
     *         line number for the summary data
     * @return
     *         the number of missed Function Calls on a line
     */
    private int getFunctionCallMissed(final int line) {
        return getCounter(line, functionCallMissedPerLine);
    }

    /**
     * Creates the MCDC Pairs summary column.
     *
     * @param line
     *         line number for the summary data
     * @return
     *         string of the MCDC Pairs summary column
     */
    private String getMcdcPairSummaryColumn(final int line) {
        var covered = getMcdcPairCovered(line);
        var missed = getMcdcPairMissed(line);
        if (covered + missed == 0) {
            return StringUtils.EMPTY;
        }
        if (covered + missed > 1) {
            return "%d/%d".formatted(covered, covered + missed);
        }
        return String.valueOf(covered);
    }

    /**
     * Creates the function call summary column.
     *
     * @param line
     *         line number for the summary data
     * @return
     *         string of the function call summary column
     */
    private String getFunctionCallSummaryColumn(final int line) {
        var covered = getFunctionCallCovered(line);
        var missed = getFunctionCallMissed(line);
        if (covered + missed == 0) {
            return StringUtils.EMPTY;
        }
        if (covered + missed > 1) {
            return "%d/%d".formatted(covered, covered + missed);
        }
        return String.valueOf(covered);
    }
}
