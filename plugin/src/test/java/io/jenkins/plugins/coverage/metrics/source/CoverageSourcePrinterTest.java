package io.jenkins.plugins.coverage.metrics.source;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj.XmlAssert;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.parser.JacocoParser;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;

import static org.assertj.core.api.Assertions.*;

class CoverageSourcePrinterTest extends AbstractCoverageTest {
    static final String CLASS = "class";
    static final String RENDERED_CODE = "\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0"
            + "for\u00a0(int\u00a0line\u00a0=\u00a00;\u00a0line\u00a0<\u00a0lines.size();\u00a0line++)\u00a0{";

    @Test
    void shouldRenderLinesWithVariousCoverages() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());

        var file = new CoverageSourcePrinter(tree.findFile("TreeStringBuilder.java").get());

        assertThat(file.getColorClass(0)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
        assertThat(file.getSummaryColumn(0)).isEqualTo("0");
        assertThat(file.getTooltip(0)).isEqualTo("Not covered");

        assertThat(file.getColorClass(113)).isEqualTo(CoverageSourcePrinter.PARTIAL_COVERAGE);
        assertThat(file.getSummaryColumn(113)).isEqualTo("1/2");
        assertThat(file.getTooltip(113)).isEqualToIgnoringWhitespace("Partially covered, branch coverage: 1/2");

        assertThat(file.getColorClass(61)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
        assertThat(file.getSummaryColumn(61)).isEqualTo("0");
        assertThat(file.getTooltip(61)).isEqualTo("Not covered");

        assertThat(file.getColorClass(19)).isEqualTo(CoverageSourcePrinter.FULL_COVERAGE);
        assertThat(file.getSummaryColumn(19)).isEqualTo("1");
        assertThat(file.getTooltip(19)).isEqualTo("Covered at least once");

        var anotherFile = new CoverageSourcePrinter(tree.findFile("StringContainsUtils.java").get());

        assertThat(anotherFile.getColorClass(43)).isEqualTo(CoverageSourcePrinter.FULL_COVERAGE);
        assertThat(anotherFile.getSummaryColumn(43)).isEqualTo("2/2");
        assertThat(anotherFile.getTooltip(43)).isEqualTo("All branches covered");
    }

    @Test
    void shouldRenderLinesWithModifiedLines() throws Exception {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());

        var node = tree.findFile("TreeStringBuilder.java").get();
        node.addModifiedLines(0, 113, 61, 18, 19);
        var file = new CoverageSourcePrinter(node);

        var line0 = file.renderLine(0,
                "                    for (int line = 0; line < lines.size(); line++) {\n");
        XmlAssert.assertThat(line0)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, "noCover modified");

        var line113 = file.renderLine(113,
                "                    for (int line = 0; line < lines.size(); line++) {\n");
        XmlAssert.assertThat(line113)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, "coverPart modified");

        var line61 = file.renderLine(61,
                "                    for (int line = 0; line < lines.size(); line++) {\n");
        XmlAssert.assertThat(line61)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, "coverNone modified");

        var line19 = file.renderLine(19,
                "                    for (int line = 0; line < lines.size(); line++) {\n");
        XmlAssert.assertThat(line19)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, "coverFull modified");
    }

    @Test
    void shouldRenderNoBranchCoverage() {
        var tree = readResult("../steps/jacoco-analysis-model.xml", new JacocoParser());

        var file = new CoverageSourcePrinter(tree.findFile("LineRangeList.java").get());

        assertThat(file.getSummaryColumn(265)).isEqualTo("0/2");
        assertThat(file.getTooltip(265)).isEqualTo("No branches covered");
        assertThat(file.getColorClass(265)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
    }

    @Test
    void shouldRenderWholeLine() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());

        var file = new CoverageSourcePrinter(tree.findFile("TreeStringBuilder.java").get());

        var renderedLine = file.renderLine(61,
                "                    for (int line = 0; line < lines.size(); line++) {\n");

        XmlAssert.assertThat(renderedLine)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, CoverageSourcePrinter.NO_COVERAGE)
                .hasAttribute("data-html-tooltip", "Not covered");
        var assertThatColumns = XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td").exist().hasSize(3);
        assertThatColumns.extractingAttribute("class").containsExactly("line", "hits", "code");

        XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td[1]/a").exist().hasSize(1)
                .extractingAttribute("name").containsExactly("61");
        XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td[2]")
                .extractingText().containsExactly("0");
        XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td[3]")
                .extractingText().containsExactly(RENDERED_CODE);

        var skippedLine = file.renderLine(1, "package io.jenkins.plugins.coverage.metrics.source;");

        var assertThatSkippedColumns = XmlAssert.assertThat(renderedLine).nodesByXPath("/tr/td").exist().hasSize(3);
        assertThatSkippedColumns.extractingAttribute("class").containsExactly("line", "hits", "code");

        XmlAssert.assertThat(skippedLine)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, CoverageSourcePrinter.UNDEFINED)
                .doesNotHaveAttribute("data-html-tooltip");

        XmlAssert.assertThat(skippedLine).nodesByXPath("/tr/td[1]/a").exist().hasSize(1)
                .extractingAttribute("name").containsExactly("1");
        XmlAssert.assertThat(skippedLine).nodesByXPath("/tr/td[2]")
                .extractingText().containsExactly(StringUtils.EMPTY);
        XmlAssert.assertThat(skippedLine).nodesByXPath("/tr/td[3]")
                .extractingText().containsExactly("package\u00a0io.jenkins.plugins.coverage.metrics.source;");
    }

    @Test
    void shouldRenderXmlSymbols() {
        var printer = new CoverageSourcePrinter(new FileNode("Test", "Path"));

        assertThat(printer.cleanupCode("#include <string>")).isEqualTo("#include&nbsp;&lt;string&gt;");
        assertThat(printer.cleanupCode("int a; int *p = &a;")).isEqualTo("int&nbsp;a;&nbsp;int&nbsp;*p&nbsp;=&nbsp;&amp;a;");
    }

    @Test
    void shouldHaveCorrectCssClassConstants() {
        assertThat(CoverageSourcePrinter.FULL_COVERAGE).isEqualTo("coverFull");
        assertThat(CoverageSourcePrinter.PARTIAL_COVERAGE).isEqualTo("coverPart");
        assertThat(CoverageSourcePrinter.NO_COVERAGE).isEqualTo("coverNone");
        assertThat(CoverageSourcePrinter.UNDEFINED).isEqualTo("noCover");
        assertThat(CoverageSourcePrinter.MODIFIED).isEqualTo("modified");
    }

    @Test
    void shouldRenderTdClassesRequiredByCssOverlays() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());
        var node = tree.findFile("TreeStringBuilder.java").get();
        node.addModifiedLines(19, 61);
        var file = new CoverageSourcePrinter(node);

        var coverFull = file.renderLine(19, "// covered\n");
        var fullColumns = XmlAssert.assertThat(coverFull).nodesByXPath("/tr/td").exist().hasSize(3);
        fullColumns.extractingAttribute("class").containsExactly("line", "hits", "code");

        var coverNone = file.renderLine(61, "// uncovered\n");
        var noneColumns = XmlAssert.assertThat(coverNone).nodesByXPath("/tr/td").exist().hasSize(3);
        noneColumns.extractingAttribute("class").containsExactly("line", "hits", "code");

        var coverPart = file.renderLine(113, "// partial\n");
        var partColumns = XmlAssert.assertThat(coverPart).nodesByXPath("/tr/td").exist().hasSize(3);
        partColumns.extractingAttribute("class").containsExactly("line", "hits", "code");

        var noCover = file.renderLine(1, "package x;\n");
        var noCoverColumns = XmlAssert.assertThat(noCover).nodesByXPath("/tr/td").exist().hasSize(3);
        noCoverColumns.extractingAttribute("class").containsExactly("line", "hits", "code");
    }

    @Test
    void shouldRenderModifiedClassOnConsecutiveLines() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());
        var node = tree.findFile("TreeStringBuilder.java").get();

        node.addModifiedLines(61, 0);
        var file = new CoverageSourcePrinter(node);

        var modLine61 = file.renderLine(61, "int x = 0;\n");
        XmlAssert.assertThat(modLine61)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, CoverageSourcePrinter.NO_COVERAGE + " " + CoverageSourcePrinter.MODIFIED);

        var modLine0 = file.renderLine(0, "int x = 0;\n");
        XmlAssert.assertThat(modLine0)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, CoverageSourcePrinter.UNDEFINED + " " + CoverageSourcePrinter.MODIFIED);

        var unmodLine19 = file.renderLine(19, "return true;\n");
        XmlAssert.assertThat(unmodLine19)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, CoverageSourcePrinter.FULL_COVERAGE);
        
        XmlAssert.assertThat(unmodLine19)
                .nodesByXPath("/tr/@class").exist()
                .extractingText()
                .containsExactly(CoverageSourcePrinter.FULL_COVERAGE);

        var unmodLine113 = file.renderLine(113, "return x;\n");
        XmlAssert.assertThat(unmodLine113)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, CoverageSourcePrinter.PARTIAL_COVERAGE);
        XmlAssert.assertThat(unmodLine113)
                .nodesByXPath("/tr/@class").exist()
                .extractingText()
                .containsExactly(CoverageSourcePrinter.PARTIAL_COVERAGE);
    }

    @Test
    void shouldRenderUndefinedClassForUnpaintedLine() {
        var printer = new CoverageSourcePrinter(new FileNode("Test.java", "src/Test.java"));

        var rendered = printer.renderLine(42, "// no coverage data");

        XmlAssert.assertThat(rendered)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, CoverageSourcePrinter.UNDEFINED)
                .doesNotHaveAttribute("data-html-tooltip");
    }

    @Test
    void shouldNotRenderModifiedClassForUnmodifiedLine() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());
        var file = new CoverageSourcePrinter(tree.findFile("TreeStringBuilder.java").get());

        var rendered = file.renderLine(61,
                "                    for (int line = 0; line < lines.size(); line++) {\n");

        XmlAssert.assertThat(rendered)
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS, CoverageSourcePrinter.NO_COVERAGE);

        assertThat(rendered).doesNotContain("modified");
    }

    @Test
    void shouldReturnCorrectModifiedClassString() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());
        var node = tree.findFile("TreeStringBuilder.java").get();
        node.addModifiedLines(19, 61);
        var file = new CoverageSourcePrinter(node);

        assertThat(file.getModifiedClass(19)).isEqualTo(CoverageSourcePrinter.MODIFIED);
        assertThat(file.getModifiedClass(61)).isEqualTo(CoverageSourcePrinter.MODIFIED);
        assertThat(file.getModifiedClass(113)).isEqualTo(StringUtils.EMPTY);
        assertThat(file.getModifiedClass(0)).isEqualTo(StringUtils.EMPTY);
    }

    @Test
    void shouldRenderAllModifiedCoverageClassCombinations() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());
        var node = tree.findFile("TreeStringBuilder.java").get();

        node.addModifiedLines(19, 61, 113, 1);
        var file = new CoverageSourcePrinter(node);

        var sourceSnippet = "// some code\n";

        XmlAssert.assertThat(file.renderLine(19, sourceSnippet))
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS,
                        CoverageSourcePrinter.FULL_COVERAGE + " " + CoverageSourcePrinter.MODIFIED);

        XmlAssert.assertThat(file.renderLine(61, sourceSnippet))
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS,
                        CoverageSourcePrinter.NO_COVERAGE + " " + CoverageSourcePrinter.MODIFIED);

        XmlAssert.assertThat(file.renderLine(113, sourceSnippet))
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS,
                        CoverageSourcePrinter.PARTIAL_COVERAGE + " " + CoverageSourcePrinter.MODIFIED);

        XmlAssert.assertThat(file.renderLine(1, sourceSnippet))
                .nodesByXPath("/tr").exist().hasSize(1)
                .singleElement()
                .hasAttribute(CLASS,
                        CoverageSourcePrinter.UNDEFINED + " " + CoverageSourcePrinter.MODIFIED);
    }

    @Test
    void shouldRenderBranchCoverageTooltips() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());
        var file = new CoverageSourcePrinter(tree.findFile("StringContainsUtils.java").get());

        assertThat(file.getTooltip(43)).isEqualTo("All branches covered");
        assertThat(file.getSummaryColumn(43)).isEqualTo("2/2");
        assertThat(file.getColorClass(43)).isEqualTo(CoverageSourcePrinter.FULL_COVERAGE);

        var treeTwo = readResult("../steps/jacoco-analysis-model.xml", new JacocoParser());
        var fileTwo = new CoverageSourcePrinter(treeTwo.findFile("LineRangeList.java").get());

        assertThat(fileTwo.getTooltip(265)).isEqualTo("No branches covered");
        assertThat(fileTwo.getSummaryColumn(265)).isEqualTo("0/2");
        assertThat(fileTwo.getColorClass(265)).isEqualTo(CoverageSourcePrinter.NO_COVERAGE);
    }

    @Test
    void shouldReturnEmptyColumnHeader() {
        var printer = new CoverageSourcePrinter(new FileNode("Test.java", "src/Test.java"));

        assertThat(printer.getColumnHeader()).isEmpty();
    }

    @Test
    void shouldReportIsPaintedCorrectly() {
        var tree = readResult("../steps/jacoco-codingstyle.xml", new JacocoParser());
        var file = new CoverageSourcePrinter(tree.findFile("TreeStringBuilder.java").get());

        assertThat(file.isPainted(19)).isTrue();
        assertThat(file.isPainted(61)).isTrue();
        assertThat(file.isPainted(113)).isTrue();

        assertThat(file.isPainted(1)).isFalse();
    }

    @Test
    void shouldCleanupCodeForHtmlRendering() {
        var printer = new CoverageSourcePrinter(new FileNode("Test.java", "src/Test.java"));

        assertThat(printer.cleanupCode("line\n")).isEqualTo("line");
        assertThat(printer.cleanupCode("line\r\n")).isEqualTo("line");

        assertThat(printer.cleanupCode("a b")).isEqualTo("a&nbsp;b");

        assertThat(printer.cleanupCode("\tcode"))
                .isEqualTo("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;code");

        assertThat(printer.cleanupCode("<div>")).isEqualTo("&lt;div&gt;");
        assertThat(printer.cleanupCode("a & b")).isEqualTo("a&nbsp;&amp;&nbsp;b");
    }
}
