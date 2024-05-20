package io.jenkins.plugins.coverage.metrics.source;

import java.io.Serializable;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.FileNode;

import io.jenkins.plugins.prism.Sanitizer;

import static j2html.TagCreator.*;

/**
 * Provides all required information for a {@link FileNode} so that its source code can be rendered together with the
 * line and branch coverage in HTML.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity"})
class CoverageSourcePrinter implements Serializable {
    private static final long serialVersionUID = -6044649044983631852L;
    private static final Sanitizer SANITIZER = new Sanitizer();

    static final String UNDEFINED = "noCover";
    static final String NO_COVERAGE = "coverNone";
    static final String FULL_COVERAGE = "coverFull";
    static final String PARTIAL_COVERAGE = "coverPart";
    private static final String NBSP = "&nbsp;";

    private final String path;
    private final int[] linesToPaint;
    private final int[] coveredPerLine;
    private final int[] missedPerLine;

    private final int[] mcdcPairCoveredPerLine;
    private final int[] mcdcPairMissedPerLine;

    private final int[] functionCallCoveredPerLine;
    private final int[] functionCallMissedPerLine;

    CoverageSourcePrinter(final FileNode file) {
        path = file.getRelativePath();

        linesToPaint = file.getLinesWithCoverage().stream().mapToInt(i -> i).toArray();
        coveredPerLine = file.getCoveredCounters();
        missedPerLine = file.getMissedCounters();
        
        mcdcPairCoveredPerLine = file.getMcdcPairCoveredCounters();
        mcdcPairMissedPerLine = file.getMcdcPairMissedCounters();
        
        functionCallCoveredPerLine = file.getFunctionCallCoveredCounters();
        functionCallMissedPerLine = file.getFunctionCallMissedCounters();
    }
    
    private String getColumnHeader(final String third) {
        return tr().withClass(CoverageSourcePrinter.UNDEFINED).with(
                td().withClass("line").with(text("Line")),
                td().withClass("line").with(text("St/Br")),
                td().withClass("line").with(text(third)),
                td().withClass("line").with(text(NBSP))
            ).render();
    }
    
    private String getColumnHeader(final String third, final String fourth) {
        return tr().withClass(CoverageSourcePrinter.UNDEFINED).with(
                td().withClass("line").with(text("Line")),
                td().withClass("line").with(text("St/Br")),
                td().withClass("line").with(text(third)),
                td().withClass("line").with(text(fourth)),
                td().withClass("line").with(text(NBSP))
            ).render();
    }
    
    // adding column header so show what is being presented in the file view
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
            trString = tr().withClass(CoverageSourcePrinter.UNDEFINED).with(
                td().withClass("line").with(text("Line ")),
                td().withClass("line").with(text("St/Br")),
                td().withClass("line").with(text(NBSP))
            ).render();
        }            
                
        return trString;    
    }

    private String getTr(final int line, final String sourceCode, final boolean isPainted) {
        return tr()
            .withClass(isPainted ? getColorClass(line) : CoverageSourcePrinter.UNDEFINED)
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
    
    private String getTr(final int line, final String sourceCode, final boolean isPainted, final String third, final String fouth) {
        return tr()
            .withClass(isPainted ? getColorClass(line) : CoverageSourcePrinter.UNDEFINED)
            .condAttr(isPainted, "data-html-tooltip", isPainted ? getTooltip(line) : StringUtils.EMPTY)
            .with(
                    td().withClass("line")
                            .with(a().withName(String.valueOf(line)).withText(String.valueOf(line))),
                    td().withClass("hits")
                            .with(isPainted ? text(getSummaryColumn(line)) : text(StringUtils.EMPTY)),
                    td().withClass("hits")
                            .with(isPainted ? text(third) : text(StringUtils.EMPTY)),
                    td().withClass("hits")
                            .with(isPainted ? text(fouth) : text(StringUtils.EMPTY)),
                    td().withClass("code")
                            .with(rawHtml(SANITIZER.render(cleanupCode(sourceCode)))))
            .render();
    }
    
    private String getTr(final int line, final String sourceCode, final boolean isPainted, final String third) {
        return tr().withClass(isPainted ? getColorClass(line) : CoverageSourcePrinter.UNDEFINED)
                .condAttr(isPainted, "data-html-tooltip", isPainted ? getTooltip(line) : StringUtils.EMPTY)
                .with(td().withClass("line").with(a().withName(String.valueOf(line)).withText(String.valueOf(line))),
                    td().withClass("hits")
                            .with(isPainted ? text(getSummaryColumn(line)) : text(StringUtils.EMPTY)),
                    td().withClass("hits")
                            .with(isPainted ? text(third) : text(StringUtils.EMPTY)),
                    td().withClass("code")
                            .with(rawHtml(SANITIZER.render(cleanupCode(sourceCode)))))
            .render();    
    }
    
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

    private String cleanupCode(final String content) {
        return content.replace("\n", StringUtils.EMPTY)
                .replace("\r", StringUtils.EMPTY)
                .replace(" ", NBSP)
                .replace("\t", NBSP.repeat(8));
    }

    final int size() {
        return linesToPaint.length;
    }

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
    
    // Tooltip for line/branch coverage
    public String getLineBranchTooltop(final int line) {
        var covered = getCovered(line);
        var missed = getMissed(line);
        if (covered + missed > 1) {
            if (missed == 0) {
                return "All branches covered";
            }
            if (covered == 0) {
                return "No branches covered";
            }
            return String.format("Partially covered, branch coverage: %d/%d", covered, covered + missed);
        }
        else if (covered == 1) {
            return "Covered at least once";
        }
        else {
            return "Not covered";
        }
    }
    
    // Tooltip for MC/DC Pair coverage
    public String getMcdcPairTooltop(final int line) {
        var mcdcPairCovered = getMcdcPairCovered(line);
        var mcdcPairMissed  = getMcdcPairMissed(line);
        String mcdcPairTooltip = "";
        
        if (mcdcPairCovered + mcdcPairMissed > 1) {
            if (mcdcPairMissed == 0) {
                mcdcPairTooltip = String.format("All MC/DC pairs covered: %d/%d", mcdcPairCovered, mcdcPairCovered + mcdcPairMissed);
            } 
            else {
                mcdcPairTooltip = String.format("MC/DC pairs partially covered: %d/%d", mcdcPairCovered, mcdcPairCovered + mcdcPairMissed);
            }
        }
        return mcdcPairTooltip;
    }
    
    // Tooltip for function call coverage
    public String getfunctionCallTooltop(final int line) {
        var functionCallCovered = getFunctionCallCovered(line);
        var functionCallMissed  = getFunctionCallMissed(line);
        String functionCallTooltip = "";
        if (functionCallCovered + functionCallMissed > 1) {
            if (functionCallMissed == 0) {
                functionCallTooltip = String.format("All function calls covered: %d/%d", functionCallCovered, functionCallCovered + functionCallMissed);
            } 
            else {
                functionCallTooltip = String.format("Function calls partially covered: %d/%d", functionCallCovered, functionCallCovered + functionCallMissed);
            }
        } 
        else if (functionCallCovered == 1) {
            functionCallTooltip = "Function call covered";
        }
        return functionCallTooltip;
    }
    
    // Updated to incoorporate all coverage types present
    public String getTooltip(final int line) {
        String toolTipString = "";
        
        String lineBranchToolTipString   = getLineBranchTooltop(line);
        String mcdcPairToolTipString     = getMcdcPairTooltop(line);
        String functionCallToolTipString = getfunctionCallTooltop(line);
        
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

    public String getSummaryColumn(final int line) {
        var covered = getCovered(line);
        var missed = getMissed(line);
        if (covered + missed > 1) {
            return String.format("%d/%d", covered, covered + missed);
        }
        return String.valueOf(covered);
    }

    // Column for MC/DC Pair coverage
    public String getMcdcPairSummaryColumn(final int line) {
        var covered = getMcdcPairCovered(line);
        var missed = getMcdcPairMissed(line);
        if (covered + missed > 1) {
            return String.format("%d/%d", covered, covered + missed);
        }
        return String.valueOf(covered);
    }
    
    // Column for function call coverage
    public String getFunctionCallSummaryColumn(final int line) {
        var covered = getFunctionCallCovered(line);
        var missed = getFunctionCallMissed(line);
        if (covered + missed > 1) {
            return String.format("%d/%d", covered, covered + missed);
        }
        return String.valueOf(covered);
    }

    public final String getPath() {
        return path;
    }

    public boolean isPainted(final int line) {
        return findIndexOfLine(line) >= 0;
    }

    int findIndexOfLine(final int line) {
        return Arrays.binarySearch(linesToPaint, line);
    }
    
    public Boolean hasAnyMcdcPairCoverage() {
        Boolean hasMcDc = false;
        for (int i = 0; i < mcdcPairCoveredPerLine.length && !hasMcDc; i++) {
            if ((mcdcPairCoveredPerLine[i] + mcdcPairMissedPerLine[i]) > 0) {
                hasMcDc = true;
            }
        }
        
        return hasMcDc;
    }

    public Boolean hasAnyFunctionCallCoverage() {
        Boolean hasFc = false;
        for (int i = 0; i < functionCallMissedPerLine.length && !hasFc; i++) {
            if ((functionCallCoveredPerLine[i] + functionCallMissedPerLine[i]) > 0) {
                hasFc = true;
            }
        }
        
        return hasFc;        
    }

    public int getCovered(final int line) {
        return getCounter(line, coveredPerLine);
    }

    public int getMissed(final int line) {
        return getCounter(line, missedPerLine);
    }

    public int getMcdcPairCovered(final int line) {
        return getCounter(line, mcdcPairCoveredPerLine);
    }

    public int getMcdcPairMissed(final int line) {
        return getCounter(line, mcdcPairMissedPerLine);
    }

    public int getFunctionCallCovered(final int line) {
        return getCounter(line, functionCallCoveredPerLine);
    }

    public int getFunctionCallMissed(final int line) {
        return getCounter(line, functionCallMissedPerLine);
    }

    int getCounter(final int line, final int... counters) {
        var index = findIndexOfLine(line);
        if (index >= 0 && counters.length > 0) {
            return counters[index];
        }
        return 0;
    }
}
