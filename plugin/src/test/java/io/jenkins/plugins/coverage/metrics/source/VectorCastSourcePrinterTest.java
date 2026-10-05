package io.jenkins.plugins.coverage.metrics.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xmlunit.assertj.XmlAssert;

import edu.hm.hafner.coverage.FileNode;

import static org.assertj.core.api.Assertions.*;

class VectorCastSourcePrinterTest {
    @ParameterizedTest
    @CsvSource({
            "0, 0, 3",
            "1, 0, 4",
            "0, 1, 4",
            "1, 1, 5"
    })
    void shouldAlignHeadersWithMetricColumns(final int calls, final int pairs, final int columns) {
        var file = new FileNode("example.c", "example.c");
        file.addCounters(1, 1, 0);
        file.addFunctionCallCounters(1, calls, 0);
        file.addMcdcPairCounters(1, pairs, 0);
        var printer = new VectorCastSourcePrinter(file);

        XmlAssert.assertThat(printer.getColumnHeader().replace("&nbsp;", "&#160;")).nodesByXPath("/tr/td").hasSize(columns);
        XmlAssert.assertThat(printer.renderLine(1, "statement;")).nodesByXPath("/tr/td").hasSize(columns);
        assertThat(printer.getColumnHeader().contains("FCall")).isEqualTo(calls > 0);
        assertThat(printer.getColumnHeader().contains("MC/DC")).isEqualTo(pairs > 0);
        if (calls > 0 && pairs > 0) {
            assertThat(printer.getColumnHeader().indexOf("FCall"))
                    .isLessThan(printer.getColumnHeader().indexOf("MC/DC"));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1, 0, 1, 0, 0, 1, coverNone",
            "1, 1, 1, 0, 1, 0, coverFull",
            "1, 0, 0, 1, 1, 1, coverPart",
            "0, 1, 1, 0, 0, 0, noCover"
    })
    void shouldColorMetricsIndependently(final int covered, final int missed,
            final int callsCovered, final int callsMissed,
            final int pairsCovered, final int pairsMissed, final String pairClass) {
        var file = new FileNode("example.c", "example.c");
        file.addCounters(1, covered, missed);
        file.addFunctionCallCounters(1, callsCovered, callsMissed);
        file.addMcdcPairCounters(1, pairsCovered, pairsMissed);
        file.addMcdcPairCounters(2, 1, 0);
        var printer = new VectorCastSourcePrinter(file);
        var row = printer.renderLine(1, "if (check()) {}");

        XmlAssert.assertThat(row).nodesByXPath("/tr/td[2]").extractingAttribute("class")
                .containsExactly("hits " + (covered == 0 ? "coverNone" : missed == 0 ? "coverFull" : "coverPart"));
        XmlAssert.assertThat(row).nodesByXPath("/tr/td[3]").extractingAttribute("class")
                .containsExactly("hits " + (callsCovered == 0 ? "coverNone" : "coverFull"));
        XmlAssert.assertThat(row).nodesByXPath("/tr/td[4]").extractingAttribute("class")
                .containsExactly("hits " + pairClass);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 0, Function calls covered: 1/1, MC/DC pairs covered: 1/1",
            "0, 1, Function calls not covered: 0/1, MC/DC pairs not covered: 0/1",
            "0, 2, No Function calls covered: 0/2, No MC/DC pairs covered: 0/2"
    })
    void shouldDescribeCoveredAndUncoveredItems(final int covered, final int missed,
            final String callTooltip, final String pairTooltip) {
        var file = new FileNode("example.c", "example.c");
        file.addCounters(1, 1, 0);
        file.addFunctionCallCounters(1, covered, missed);
        file.addMcdcPairCounters(1, covered, missed);
        var printer = new VectorCastSourcePrinter(file);
        var row = printer.renderLine(1, "if (check()) {}");

        XmlAssert.assertThat(row).nodesByXPath("/tr/td[3]").extractingAttribute("tooltip")
                .containsExactly(callTooltip);
        XmlAssert.assertThat(row).nodesByXPath("/tr/td[4]").extractingAttribute("tooltip")
                .containsExactly(pairTooltip);
        assertThat(printer.getTooltip(1)).contains(callTooltip, pairTooltip);
    }

    @Test
    void shouldLeaveInapplicableMetricsBlank() {
        var file = new FileNode("example.c", "example.c");
        file.addCounters(1, 1, 0);
        file.addCounters(2, 1, 0);
        file.addFunctionCallCounters(1, 0, 0);
        file.addMcdcPairCounters(1, 0, 0);
        file.addFunctionCallCounters(2, 1, 0);
        file.addMcdcPairCounters(2, 1, 0);
        var printer = new VectorCastSourcePrinter(file);

        for (int line : new int[] {1, 3}) {
            var row = printer.renderLine(line, "statement;");
            XmlAssert.assertThat(row).nodesByXPath("/tr/td").hasSize(5);
            XmlAssert.assertThat(row).nodesByXPath("/tr/td[3] | /tr/td[4]")
                    .extractingText().containsExactly("", "");
            XmlAssert.assertThat(row).nodesByXPath("/tr/td[3] | /tr/td[4]")
                    .extractingAttribute("class").containsExactly("hits noCover", "hits noCover");
            XmlAssert.assertThat(row).nodesByXPath("/tr/td[3]/@tooltip | /tr/td[4]/@tooltip").hasSize(0);
        }
    }
}

