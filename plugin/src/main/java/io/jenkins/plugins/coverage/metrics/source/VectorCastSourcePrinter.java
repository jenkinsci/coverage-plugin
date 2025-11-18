package io.jenkins.plugins.coverage.metrics.source;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.FileNode;

import java.io.Serial;

import static j2html.TagCreator.*;

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
    private String getTr(final int line, final String sourceCode, final boolean isPainted, final String third, final String fouth) {
        var trData = tr()
                .withClasses(isPainted ? getColorClass(line) : UNDEFINED, getModifiedClass(line))
                .condAttr(isPainted, "data-html-tooltip", isPainted ? getTooltip(line) : StringUtils.EMPTY);

        trData.with(
                td().withClass("line").with(a().withName(String.valueOf(line)).withText(String.valueOf(line))),
                td().withClass("hits").with(isPainted ? text(getSummaryColumn(line)) : text(StringUtils.EMPTY))
        );

        if (!third.equals(StringUtils.EMPTY)) {
            trData.with(td().withClass("hits").with(isPainted ? text(third) : text(StringUtils.EMPTY)));
        }
        if (!fouth.equals(StringUtils.EMPTY)) {
            trData.with(td().withClass("hits").with(isPainted ? text(fouth) : text(StringUtils.EMPTY)));
        }

        trData.with(td().withClass("code").with(rawHtml(SANITIZER.render(cleanupCode(sourceCode)))));

        return trData.render();
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
    public String renderLine(final int line, final String sourceCode) {
        var isPainted = isPainted(line);
        var hasMcdc = hasAnyMcdcPairCoverage();
        var hasFc   = hasAnyFunctionCallCoverage();

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
            trString = getTr(line, sourceCode, isPainted, getFunctionCallSummaryColumn(line), getMcdcPairSummaryColumn(line));
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
        return getColumnHeader(third, NBSP);
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
        return tr().withClass(UNDEFINED).with(
                td().withClass("line").with(text("Line")),
                td().withClass("line").with(text("St/Br")),
                td().withClass("line").with(text(third)),
                td().withClass("line").with(text(fourth)),
                td().withClass("line").with(text(NBSP))
            ).render();
    }

    /**
     * Gets the source code column header.
     *
     * @return string of the column header
     *         
     */
    @Override
    public String getColumnHeader() {
        var hasMcdc = hasAnyMcdcPairCoverage();
        var hasFc   = hasAnyFunctionCallCoverage();
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
            trString = tr().withClass(UNDEFINED).with(
                td().withClass("line").with(text("Line ")),
                td().withClass("line").with(text("St/Br")),
                td().withClass("line").with(rawHtml(NBSP))
            ).render();
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
    public String getColorClass(final int line) {
        if (getCovered(line) == 0 && getMcdcPairCovered(line) == 0 && getFunctionCallCovered(line) == 0) {
            return NO_COVERAGE;
        }
        else if (getMissed(line) == 0 && getMcdcPairMissed(line) == 0 && getFunctionCallMissed(line) == 0) {
            return FULL_COVERAGE;
        }
        else {
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
    public String getMcdcPairTooltip(final int line) {
        var mcdcPairCovered = getMcdcPairCovered(line);
        var mcdcPairMissed  = getMcdcPairMissed(line);

        return getTooltip(mcdcPairCovered, mcdcPairMissed, false, "MC/DC pairs");
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
    
    public String getfunctionCallTooltip(final int line) {
        var functionCallCovered = getFunctionCallCovered(line);
        var functionCallMissed  = getFunctionCallMissed(line);

        return getTooltip(functionCallCovered, functionCallMissed, true, "Function calls");
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
     * @param checkAny
     *         boolean to indicate if code should check for any covered bits
     *
     * @param description
     *         Description of the coverage - MCDC/FCC
     *
     * @return 
     *         string of the tooltip
     */
    public String getTooltip(final int covered, final int missed, final boolean checkAny, final String description) {
        var tooltip = "";

        if (covered + missed > 1) {
            if (missed == 0) {
                tooltip = "All %s covered: %d/%d".formatted(description, covered, covered + missed);
            }
            else {
                tooltip = "%s partially covered: %d/%d".formatted(description, covered, covered + missed);
            }
        }
        else if (checkAny && covered == 1)  {
            tooltip = "%s covered".formatted(description);
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
    public String getTooltip(final int line) {
        var toolTipString = "";

        var lineBranchToolTipString = super.getTooltip(line);
        var mcdcPairToolTipString = getMcdcPairTooltip(line);
        var functionCallToolTipString = getfunctionCallTooltip(line);

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
    public boolean hasAnyMcdcPairCoverage() {
        boolean hasMcDc = false;
        for (int i = 0; i < mcdcPairCoveredPerLine.length && !hasMcDc; i++) {
            if ((mcdcPairCoveredPerLine[i] + mcdcPairMissedPerLine[i]) > 0) {
                hasMcDc = true;
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

    public boolean hasAnyFunctionCallCoverage() {
        boolean hasFc = false;
        for (int i = 0; i < functionCallMissedPerLine.length && !hasFc; i++) {
            if ((functionCallCoveredPerLine[i] + functionCallMissedPerLine[i]) > 0) {
                hasFc = true;
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
    public int getMcdcPairCovered(final int line) {
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
    public int getMcdcPairMissed(final int line) {
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
    public int getFunctionCallCovered(final int line) {
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
    public int getFunctionCallMissed(final int line) {
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
    public String getMcdcPairSummaryColumn(final int line) {
        var covered = getMcdcPairCovered(line);
        var missed = getMcdcPairMissed(line);
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
    public String getFunctionCallSummaryColumn(final int line) {
        var covered = getFunctionCallCovered(line);
        var missed = getFunctionCallMissed(line);
        if (covered + missed > 1) {
            return "%d/%d".formatted(covered, covered + missed);
        }
        return String.valueOf(covered);
    }
}
