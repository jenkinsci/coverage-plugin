package io.jenkins.plugins.coverage.metrics.steps;

import java.util.*;
import java.util.function.Consumer;

import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import hudson.tasks.Recorder;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.util.LogHandler;

/**
 * Expands a list of given {@link CoverageTool}'s into the actual CoverageTool's set that {@link CoverageRecorder} uses when attempting to look for and parse coverage files.
 * <p>
 * The CoverageTool to its working set mapping use the following rules:
 * - Empty or null tool set -&gt; set of all possible coverage parsers with null patterns (which will default to their default patterns)
 * - Tool with a Pattern no {@link Parser} -&gt; set of the Pattern paired with every possible Parser
 * - Tool with a {@link Parser} but no pattern -&gt; set of just the input tool
 * - Tool with both a {@link Parser} and Pattern -&gt; set of just the input tool
 * </p>
 * <p>
 * Use {@code hasNextTool() } and {@code useNextTool() } methods to iterate through the mappings.
 * </p>
 *
 * @author Pierson Yieh
 */
public class CoverageToolExpander {
    HashMap<CoverageTool, CoverageToolSet> toolMap;
    FilteredLog log;
    LogHandler logHandler;

    CoverageTool currTool;
    Iterator<CoverageTool> toolIter;

    public final static CoverageToolSet DEFAULT_TOOLS = new CoverageToolSet() {
        {
            this.isExpanded = true;
            for (Parser parser: Parser.values()) {
                if (!(parser == Parser.JUNIT || parser == Parser.NUNIT || parser == Parser.XUNIT)) {
                    tools.add(new CoverageTool(parser, null));
                }
            }
        }
    };

    public CoverageToolExpander(List<CoverageTool> tools, FilteredLog log, LogHandler logHandler) {
        this.toolMap = new HashMap<>();
        this.log = log;
        this.logHandler = logHandler;
        initTools(tools);
    }

    /**
     * Initializes the current {@code CoverageToolExpander} with the expanded tools' mapping.
     *
     * @param tools
     *          Input list of {@code CoverageTool}'s to expand
     */
    public void initTools(List<CoverageTool> tools) {
        this.toolMap.clear();
        addExpandedTools(tools);
    }

    private void logInfo(String msg) {
        log.logInfo(msg);
        logHandler.log(log);
    }

    /**
     * Does the actual expansion of each CoverageTool and maps its expansion in {@code toolMap}
     * @param tools
     *          Input list of {@code CoverageTool}'s to expand
     */
    public void addExpandedTools(List<CoverageTool> tools) {
        if (tools == null || tools.isEmpty()) { // empty or no tools -> DEFAULT_TOOLS
            logInfo("No tool defined, trying all possible parsers.");
            toolMap.put(null, DEFAULT_TOOLS);
        } else {
            for (CoverageTool tool: tools) {
                if (tool.getParser() == null) {
                    if (tool.getPattern() == null || tool.getPattern().isBlank()) { // empty tool -> DEFAULT_TOOLS
                        logInfo("Empty tool given, trying all possible parsers.");
                        toolMap.put(tool, DEFAULT_TOOLS);
                    } else { // tool has just pattern -> try input pattern with each parser
                        logInfo(String.format("No parser defined for pattern [%s], trying with all possible parsers.", tool.getPattern()));
                        toolMap.put(tool, patternWithAllParsers(tool.getPattern()));
                    }
                } else { // tool has parser , leave as is (regardless if has pattern or not)
                    toolMap.put(tool, new CoverageToolSet(Arrays.asList(tool), false));
                }
            }
        }
        toolIter = toolMap.keySet().iterator();
    }

    public CoverageToolSet patternWithAllParsers(String pattern) {
        CoverageToolSet ret = new CoverageToolSet();
        ret.isExpanded = true;
        for (Parser p: Parser.values()) {
            ret.addTool(new CoverageTool(p, pattern));
        }
        return ret;
    }

    public boolean hasNextTool() {
        return toolIter.hasNext();
    }

    public CoverageTool getNextTool() {
        currTool = toolIter.next();
        return currTool;
    }

    public CoverageToolSet getCoverageToolSet(CoverageTool tool) {
        return toolMap.get(tool);
    }

    /**
     * A list of {@link CoverageTool}'s that represent an expansion of a given input CoverageTool
     */
    public static class CoverageToolSet implements Iterable<CoverageTool> {
        List<CoverageTool> tools;
        boolean isExpanded;
        int index = 0;

        public CoverageToolSet() {
            this.tools = new ArrayList<>();
        }

        public CoverageToolSet(List<CoverageTool> tools, boolean isExpanded) {
            this.tools = tools;
            this.isExpanded = isExpanded;
        }

        public List<CoverageTool> getTools() {
            return tools;
        }

        public void setTools(List<CoverageTool> tools) {
            this.tools = tools;
        }

        public void addTool(CoverageTool tool) {
            if (this.tools == null) {
                this.tools = new ArrayList<>();
            }
            this.tools.add(tool);
        }

        public boolean isExpanded() {
            return isExpanded;
        }

        public void setExpanded(boolean expanded) {
            isExpanded = expanded;
        }

        public boolean hasNext() {
            return index < tools.size();
        }

        public CoverageTool getNext() {
            return tools.get(index++);
        }

        @Override
        public Iterator<CoverageTool> iterator() {
            return new CoverageToolIterator();
        }

        private class CoverageToolIterator implements Iterator<CoverageTool> {
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
                return currentIndex < tools.size();
            }

            @Override
            public CoverageTool next() {
                return tools.get(currentIndex++);
            }
        }

    }
}