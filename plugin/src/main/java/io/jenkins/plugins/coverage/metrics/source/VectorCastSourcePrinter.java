package io.jenkins.plugins.coverage.metrics.source;

import java.io.Serializable;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.FileNode;

import io.jenkins.plugins.prism.Sanitizer;

import static j2html.TagCreator.*;

/**
 * Provides all required information for a {@link FileNode} so that its source code can be rendered together with the
 * line and mutation coverage in HTML.
 */
class VectorCastSourcePrinter extends CoverageSourcePrinter {
    private static final long serialVersionUID = -2215657894423024907L;

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
    
    private String getColumnHeader(final String third) {
        return getColumnHeader(third, NBSP);
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

    public String getTooltip(final int line, final int covered, final int missed, final boolean checkAny, final String description) {
        String tooltip = "";
        
        if (covered + missed > 1) {
            if (missed == 0) {
                tooltip = String.format("All %s covered: %d/%d", description, covered, covered + missed);
            }
            else {
                tooltip = String.format("%s partially covered: %d/%d", description, covered, covered + missed);
                return "No branches covered";
            }
        }
        else if (checkAny && (covered == 1))  {
            tooltip = String.format("%s covered", description, covered,  missed);
        }
        
        return tooltip;
    }
    
    private String getTr(final int line, final String sourceCode, final boolean isPainted, final String third, final String fouth) {
        var trData = tr()
            .withClass(isPainted ? getColorClass(line) : CoverageSourcePrinter.UNDEFINED)
            .condAttr(isPainted, "data-html-tooltip", isPainted ? getTooltip(line) : StringUtils.EMPTY);
            
        trData
            .with(
                    td().withClass("line").with(a().withName(String.valueOf(line)).withText(String.valueOf(line))),
                    td().withClass("hits").with(isPainted ? text(getSummaryColumn(line)) : text(StringUtils.EMPTY))
            );
                    
        if (!third.equals(StringUtils.EMPTY)) {
            trData.with (td().withClass("hits").with(isPainted ? text(third) : text(StringUtils.EMPTY)));
        }
        if (!fouth.equals(StringUtils.EMPTY)) {
            trData.with (td().withClass("hits").with(isPainted ? text(fouth) : text(StringUtils.EMPTY)));
        }
        
        trData.with (td().withClass("code").with(rawHtml(SANITIZER.render(cleanupCode(sourceCode)))));
            
        return trData.render();
    }
    
    private String getTr(final int line, final String sourceCode, final boolean isPainted) {
        return getTr(line, sourceCode, isPainted, StringUtils.EMPTY, StringUtils.EMPTY);        
    }
    
    private String getTr(final int line, final String sourceCode, final boolean isPainted, final String third) {
        return getTr(line, sourceCode, isPainted, third, StringUtils.EMPTY);
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

    // Tooltip for MC/DC Pair coverage
    public String getMcdcPairTooltop(final int line) {
        var mcdcPairCovered = getMcdcPairCovered(line);
        var mcdcPairMissed  = getMcdcPairMissed(line);

        return getTooltip(line, mcdcPairCovered, mcdcPairMissed, false, "MC/DC pairs");
    }
    
    // Tooltip for function call coverage
    public String getfunctionCallTooltop(final int line) {
        var functionCallCovered = getFunctionCallCovered(line);
        var functionCallMissed  = getFunctionCallMissed(line);
        
        return getTooltip(line, functionCallCovered, functionCallMissed, true, "Function calls");        
    }

    // Updated to incoorporate all coverage types present
    public String getTooltip(final int line) {
        String toolTipString = "";
        
        String lineBranchToolTipString   = super.getTooltip(line);
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


}
