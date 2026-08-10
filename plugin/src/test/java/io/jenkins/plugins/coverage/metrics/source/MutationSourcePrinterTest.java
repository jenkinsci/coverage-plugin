package io.jenkins.plugins.coverage.metrics.source;

import org.junit.jupiter.api.Test;
import org.xmlunit.assertj.XmlAssert;

import edu.hm.hafner.coverage.parser.PitestParser;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;

import static org.assertj.core.api.Assertions.*;

class MutationSourcePrinterTest extends AbstractCoverageTest {
    /** CSS class emitted by the sentinel row to identify mutation coverage tables. */
    static final String MUTATION_CLASS = "mutation";

    @Test
    void shouldRenderLinesWithVariousMutations() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());

        var file = new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        assertThat(file.getColorClass(251)).isEqualTo(CoverageSourcePrinter.PARTIAL_COVERAGE);
        assertThat(file.getSummaryColumn(251)).isEqualTo("2/3");
        assertThat(file.getTooltip(251)).isEqualToIgnoringWhitespace(toString("tooltip.html"));

        assertThat(file.getColorClass(254)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
        assertThat(file.getSummaryColumn(254)).isEqualTo("0");
        assertThat(file.getTooltip(254)).isEqualTo("Not covered");

        assertThat(file.getColorClass(131)).isEqualTo(CoverageSourcePrinter.FULL_COVERAGE);
        assertThat(file.getSummaryColumn(131)).isEqualTo("1/1");
        assertThat(file.getTooltip(131)).contains("Killed Mutations:<ul><li>Replaced integer subtraction with addition (org.pitest.mutationtest.engine.gregor.mutators.MathMutator)</li></ul>");

        assertThat(file.getColorClass(137)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
        assertThat(file.getSummaryColumn(137)).isEqualTo("0/1");
        assertThat(file.getTooltip(137)).contains("Survived Mutations:<ul><li>negated conditional (org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator)</li></ul>");
    }

    @Test
    void shouldRenderWholeLine() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());

        var file = new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        var renderedLine = file.renderLine(137,
                "                    for (int line = 0; line < lines.size(); line++) {\n");

        XmlAssert.assertThat(renderedLine)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, "coverNone")
                .hasAttribute("data-html-tooltip");
        var assertThatColumns = XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td").exist().hasSize(3);
        assertThatColumns.extractingAttribute("class").containsExactly("line", "hits", "code");

        XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td[1]/a").exist().hasSize(1)
                .extractingAttribute("name").containsExactly("137");
        XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td[2]")
                .extractingText().containsExactly("0/1");
        XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td[3]")
                .extractingText().containsExactly(CoverageSourcePrinterTest.RENDERED_CODE);
    }

    @Test
    void shouldRenderDataHtmlTooltipOnAllMutationPaintedLines() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());
        var file = new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        var sourceSnippet = "return value;\n";

        var fullLine = file.renderLine(131, sourceSnippet);
        XmlAssert.assertThat(fullLine)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, CoverageSourcePrinter.FULL_COVERAGE)
                .hasAttribute("data-html-tooltip");
        XmlAssert.assertThat(fullLine)
                .nodesByXPath("/tr/@data-html-tooltip").exist()
                .extractingText().first().asString()
                .contains("Killed Mutations:");

        var survivedLine = file.renderLine(137, sourceSnippet);
        XmlAssert.assertThat(survivedLine)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, CoverageSourcePrinter.NO_COVERAGE)
                .hasAttribute("data-html-tooltip");
        XmlAssert.assertThat(survivedLine)
                .nodesByXPath("/tr/@data-html-tooltip").exist()
                .extractingText().first().asString()
                .contains("Survived Mutations:");

        var partialLine = file.renderLine(251, sourceSnippet);
        XmlAssert.assertThat(partialLine)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, CoverageSourcePrinter.PARTIAL_COVERAGE)
                .hasAttribute("data-html-tooltip");
        XmlAssert.assertThat(partialLine)
                .nodesByXPath("/tr/@data-html-tooltip").exist()
                .extractingText().first().asString()
                .contains("Killed Mutations:");
        XmlAssert.assertThat(partialLine)
                .nodesByXPath("/tr/@data-html-tooltip").exist()
                .extractingText().first().asString()
                .contains("Survived Mutations:");
    }

    @Test
    void shouldNotRenderTooltipOnUnpaintedMutationLines() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());
        var file = new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        var unpaintedLine = file.renderLine(1, "package edu.hm.hafner.coverage;\n");
        XmlAssert.assertThat(unpaintedLine)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, CoverageSourcePrinter.UNDEFINED)
                .doesNotHaveAttribute("data-html-tooltip");
    }

    @Test
    void shouldEmitMutationSentinelRowAsColumnHeader() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());
        var file = new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        var header = file.getColumnHeader();

        assertThat(header).isNotEmpty();
        XmlAssert.assertThat(header)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, MUTATION_CLASS);
    }
}
