package io.jenkins.plugins.coverage.metrics.source;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Mutation;
import edu.hm.hafner.coverage.Mutation.MutationBuilder;
import edu.hm.hafner.coverage.MutationStatus;
import edu.hm.hafner.coverage.parser.PitestParser;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj.XmlAssert;

class MutationSourcePrinterTest extends AbstractCoverageTest {
    /** CSS class emitted by the sentinel row to identify mutation coverage tables. */
    static final String MUTATION_CLASS = "mutation";

    @Test
    void shouldRenderLinesWithVariousMutations() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());

        var file =
                new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        assertThat(file.getColorClass(251)).isEqualTo(CoverageSourcePrinter.PARTIAL_COVERAGE);
        assertThat(file.getSummaryColumn(251)).isEqualTo("2/3");
        assertThat(file.getTooltip(251)).isEqualToIgnoringWhitespace(toString("tooltip.html"));

        assertThat(file.getColorClass(254)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
        assertThat(file.getSummaryColumn(254)).isEqualTo("0/1");
        assertThat(file.getTooltip(254)).isEqualTo("Not covered");

        assertThat(file.getColorClass(131)).isEqualTo(CoverageSourcePrinter.FULL_COVERAGE);
        assertThat(file.getSummaryColumn(131)).isEqualTo("1/1");
        assertThat(file.getTooltip(131))
                .contains(
                        "Killed Mutations:<ul><li>Replaced integer subtraction with addition (org.pitest.mutationtest.engine.gregor.mutators.MathMutator)</li></ul>");

        assertThat(file.getColorClass(137)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
        assertThat(file.getSummaryColumn(137)).isEqualTo("0/1");
        assertThat(file.getTooltip(137))
                .contains(
                        "Survived Mutations:<ul><li>negated conditional (org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator)</li></ul>");
    }

    @Test
    void shouldRenderLineWithKilledSurvivedAndNotCoveredMutationsAsPartiallyCovered() {
        var tree = readResult("mutations-mixed-line.xml", new PitestParser());

        var file = new MutationSourcePrinter(tree.findFile("Mixed.java").get());

        assertThat(file.getColorClass(10)).isEqualTo(CoverageSourcePrinter.PARTIAL_COVERAGE);
        assertThat(file.getSummaryColumn(10)).isEqualTo("1/3");
        assertThat(file.getTooltip(10))
                .contains("Killed Mutations:<ul><li>replaced int return with 0 for edu/hm/hafner/Mixed::compute")
                .contains("Survived Mutations:<ul><li>changed conditional boundary")
                .contains("Not Covered Mutations:<ul><li>Replaced integer addition with subtraction");

        assertThat(file.getColorClass(20)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
        assertThat(file.getSummaryColumn(20)).isEqualTo("0/1");
        assertThat(file.getTooltip(20)).isEqualTo("Not covered");

        assertThat(file.getColorClass(30)).isEqualTo(CoverageSourcePrinter.FULL_COVERAGE);
        assertThat(file.getSummaryColumn(30)).isEqualTo("1/1");
        assertThat(file.getTooltip(30))
                .contains("Killed Mutations:")
                .doesNotContain("Survived Mutations:", "Not Covered Mutations:");
    }

    @Test
    void shouldIgnoreMutationsInLinesWithoutCoverageCounters() {
        var fileNode = new FileNode("Mixed.java", "edu/hm/hafner/Mixed.java");
        fileNode.addCounters(10, 1, 0);
        fileNode.addMutation(createMutation(10, MutationStatus.KILLED));
        fileNode.addMutation(createMutation(20, MutationStatus.SURVIVED));
        fileNode.addMutation(createMutation(30, MutationStatus.NO_COVERAGE));

        var file = new MutationSourcePrinter(fileNode);

        assertThat(file.isPainted(10)).isTrue();
        assertThat(file.getColorClass(10)).isEqualTo(CoverageSourcePrinter.FULL_COVERAGE);
        assertThat(file.getSummaryColumn(10)).isEqualTo("1/1");

        assertThat(file.isPainted(20)).isFalse();
        assertThat(file.getSurvived(20)).isZero();
        assertThat(file.isPainted(30)).isFalse();
        assertThat(file.getNotCovered(30)).isZero();
    }

    @Test
    void shouldHandleFileWithMutationsButWithoutAnyCoverageCounters() {
        var fileNode = new FileNode("Empty.java", "edu/hm/hafner/Empty.java");
        fileNode.addMutation(createMutation(10, MutationStatus.SURVIVED));

        var file = new MutationSourcePrinter(fileNode);

        assertThat(file.isPainted(10)).isFalse();
        assertThat(file.renderLine(10, "return 42;")).contains("noCover");
    }

    private static Mutation createMutation(final int line, final MutationStatus status) {
        return new MutationBuilder()
                .withLine(line)
                .withStatus(status)
                .withIsDetected(status == MutationStatus.KILLED)
                .withMutator("mutator")
                .withDescription("description")
                .build();
    }

    @Test
    void shouldRenderWholeLine() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());

        var file =
                new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        var renderedLine =
                file.renderLine(137, "                    for (int line = 0; line < lines.size(); line++) {\n");

        XmlAssert.assertThat(renderedLine)
                .nodesByXPath("/tr")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, "coverNone")
                .doesNotHaveAttribute("data-html-tooltip");
        var assertThatColumns = XmlAssert.assertThat(renderedLine)
                .nodesByXPath("/tr/td")
                .exist()
                .hasSize(3);
        assertThatColumns.extractingAttribute("class").containsExactly("line", "hits", "code");

        XmlAssert.assertThat(renderedLine)
                .nodesByXPath("/tr/td[1]/a")
                .exist()
                .hasSize(1)
                .extractingAttribute("name")
                .containsExactly("137");
        XmlAssert.assertThat(renderedLine)
                .nodesByXPath("/tr/td[2]")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute("data-html-tooltip");
        XmlAssert.assertThat(renderedLine)
                .nodesByXPath("/tr/td[2]")
                .extractingText()
                .containsExactly("0/1");
        XmlAssert.assertThat(renderedLine)
                .nodesByXPath("/tr/td[3]")
                .extractingText()
                .containsExactly(CoverageSourcePrinterTest.RENDERED_CODE);
    }

    @Test
    void shouldRenderDataHtmlTooltipOnHitsCellForAllMutationPaintedLines() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());
        var file =
                new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        var sourceSnippet = "return value;\n";

        var fullLine = file.renderLine(131, sourceSnippet);
        XmlAssert.assertThat(fullLine)
                .nodesByXPath("/tr")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, CoverageSourcePrinter.FULL_COVERAGE)
                .doesNotHaveAttribute("data-html-tooltip");
        XmlAssert.assertThat(fullLine)
                .nodesByXPath("/tr/td[2]")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute("data-html-tooltip");
        XmlAssert.assertThat(fullLine)
                .nodesByXPath("/tr/td[2]/@data-html-tooltip")
                .exist()
                .extractingText()
                .first()
                .asString()
                .contains("Killed Mutations:");

        var survivedLine = file.renderLine(137, sourceSnippet);
        XmlAssert.assertThat(survivedLine)
                .nodesByXPath("/tr")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, CoverageSourcePrinter.NO_COVERAGE)
                .doesNotHaveAttribute("data-html-tooltip");
        XmlAssert.assertThat(survivedLine)
                .nodesByXPath("/tr/td[2]")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute("data-html-tooltip");
        XmlAssert.assertThat(survivedLine)
                .nodesByXPath("/tr/td[2]/@data-html-tooltip")
                .exist()
                .extractingText()
                .first()
                .asString()
                .contains("Survived Mutations:");

        var partialLine = file.renderLine(251, sourceSnippet);
        XmlAssert.assertThat(partialLine)
                .nodesByXPath("/tr")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, CoverageSourcePrinter.PARTIAL_COVERAGE)
                .doesNotHaveAttribute("data-html-tooltip");
        XmlAssert.assertThat(partialLine)
                .nodesByXPath("/tr/td[2]")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute("data-html-tooltip");
        XmlAssert.assertThat(partialLine)
                .nodesByXPath("/tr/td[2]/@data-html-tooltip")
                .exist()
                .extractingText()
                .first()
                .asString()
                .contains("Killed Mutations:");
        XmlAssert.assertThat(partialLine)
                .nodesByXPath("/tr/td[2]/@data-html-tooltip")
                .exist()
                .extractingText()
                .first()
                .asString()
                .contains("Survived Mutations:");
    }

    @Test
    void shouldNotRenderTooltipOnUnpaintedMutationLines() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());
        var file =
                new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        var unpaintedLine = file.renderLine(1, "package edu.hm.hafner.coverage;\n");
        XmlAssert.assertThat(unpaintedLine)
                .nodesByXPath("/tr")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, CoverageSourcePrinter.UNDEFINED)
                .doesNotHaveAttribute("data-html-tooltip");
    }

    @Test
    void shouldEmitMutationSentinelRowAsColumnHeader() {
        var tree = readResult("../steps/mutations.xml", new PitestParser());
        var file =
                new MutationSourcePrinter(tree.findFile("CoberturaParser.java").get());

        var header = file.getColumnHeader();

        assertThat(header).isNotEmpty();
        XmlAssert.assertThat(header)
                .nodesByXPath("/tr")
                .exist()
                .hasSize(1)
                .singleElement()
                .hasAttribute(CoverageSourcePrinterTest.CLASS, MUTATION_CLASS);
    }
}
