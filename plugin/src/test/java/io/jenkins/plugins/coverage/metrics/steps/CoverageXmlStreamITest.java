package io.jenkins.plugins.coverage.metrics.steps; // NOPMD

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.xmlunit.builder.Input;
import org.xmlunit.builder.Input.Builder;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.coverage.parser.JacocoParser;
import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.SerializableTest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import hudson.XmlFile;
import hudson.model.FreeStyleBuild;
import hudson.util.XStream2;

import io.jenkins.plugins.coverage.metrics.Assertions;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.steps.CoverageXmlStream.IntegerLineMapConverter;
import io.jenkins.plugins.coverage.metrics.steps.CoverageXmlStream.IntegerSetConverter;
import io.jenkins.plugins.coverage.metrics.steps.CoverageXmlStream.MetricFractionMapConverter;
import io.jenkins.plugins.util.QualityGateResult;

import static edu.hm.hafner.coverage.Metric.*;
import static org.assertj.core.api.BDDAssertions.*;
import static org.mockito.Mockito.*;
import static org.xmlunit.assertj.XmlAssert.assertThat;

/**
 * Tests the class {@link CoverageXmlStream}.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
class CoverageXmlStreamITest extends SerializableTest<Node> {
    private static final String ACTION_QUALIFIED_NAME = "io.jenkins.plugins.coverage.metrics.steps.CoverageBuildAction";
    private static final String EMPTY = "[]";

    @Override
    protected Node createSerializable() {
        var fileName = "jacoco-codingstyle.xml";
        return new JacocoParser().parse(new InputStreamReader(asInputStream(fileName),
                        StandardCharsets.UTF_8), fileName, new FilteredLog("Errors"));
    }

    @Test
    void shouldRestoreAction() throws IOException {
        var xmlStream = new CoverageXmlStream();
        var file = getResourceAsFile("coverage-action-1.x.xml");
        var stream = xmlStream.getStream();
        var xmlFile = new XmlFile(stream, file.toFile());
        var restored = xmlFile.read();

        Assertions.assertThat(restored).isInstanceOfSatisfying(CoverageBuildAction.class,
                a -> Assertions.assertThat(a.getAllValues(Baseline.PROJECT))
                        .map(Object::toString).containsExactlyInAnyOrder("MODULE: 100.00% (1/1)",
                                "PACKAGE: 75.00% (3/4)", "FILE: 100.00% (32/32)", "CLASS: 94.23% (49/52)",
                                "METHOD: 95.79% (569/594)", "LINE: 96.35% (2164/2246)", "BRANCH: 92.92% (932/1003)",
                                "INSTRUCTION: 96.44% (10534/10923)", "TESTS: 305", "CYCLOMATIC_COMPLEXITY: 1105",
                                "LOC: 2246"));
    }

    @Test
    void shouldSaveAndRestoreTree() {
        Path saved = createTempFile();
        Node convertedNode = createSerializable();

        var xmlStream = new CoverageXmlStream();
        xmlStream.write(saved, convertedNode);
        Node restored = xmlStream.read(saved);

        Assertions.assertThat(restored).usingRecursiveComparison().isEqualTo(convertedNode);

        var xml = Input.from(saved);
        assertThat(xml).nodesByXPath("//file[./name = 'TreeStringBuilder.java']/values/*")
                .hasSize(4).extractingText()
                .containsExactly("INSTRUCTION: 229/233", "BRANCH: 17/18", "LINE: 51/53", "CYCLOMATIC_COMPLEXITY: 23");
        assertThat(xml).nodesByXPath("//file[./name = 'TreeStringBuilder.java']/coveredPerLine")
                .hasSize(1).extractingText()
                .containsExactly(
                        "[19: 1, 20: 1, 31: 1, 43: 1, 50: 1, 51: 1, 54: 1, 57: 1, 61: 0, 62: 0, 70: 1, 72: 1, 73: 1, 74: 1, 85: 2, 86: 1, 89: 1, 90: 2, 91: 1, 92: 2, 93: 2, 95: 1, 96: 1, 97: 1, 100: 1, 101: 1, 103: 1, 106: 1, 109: 1, 112: 1, 113: 1, 114: 1, 115: 1, 117: 1, 125: 2, 126: 1, 128: 1, 140: 1, 142: 1, 143: 1, 144: 1, 146: 1, 160: 1, 162: 2, 163: 2, 164: 1, 167: 1, 177: 1, 178: 2, 179: 1, 180: 1, 181: 1, 184: 1]");
        assertThat(xml).nodesByXPath("//file[./name = 'TreeStringBuilder.java']/missedPerLine")
                .hasSize(1).extractingText()
                .containsExactly(
                        "[19: 0, 20: 0, 31: 0, 43: 0, 50: 0, 51: 0, 54: 0, 57: 0, 61: 1, 62: 1, 70: 0, 72: 0, 73: 0, 74: 0, 85: 0, 86: 0, 89: 0, 90: 0, 91: 0, 92: 0, 93: 0, 95: 0, 96: 0, 97: 0, 100: 0, 101: 0, 103: 0, 106: 0, 109: 0, 112: 0, 113: 1, 114: 0, 115: 0, 117: 0, 125: 0, 126: 0, 128: 0, 140: 0, 142: 0, 143: 0, 144: 0, 146: 0, 160: 0, 162: 0, 163: 0, 164: 0, 167: 0, 177: 0, 178: 0, 179: 0, 180: 0, 181: 0, 184: 0]");
    }

    @Test
    void shouldStoreActionCompactly() throws IOException {
        Path saved = createTempFile();

        var stream = new XStream2();

        CoverageBuildAction.registerValueListConverters(stream);
        CoverageXmlStream.registerConverters(stream);

        var file = new XmlFile(stream, saved.toFile());
        var buildAction = createAction();
        file.write(buildAction);

        var xml = Input.from(saved);
        assertThat(xml).nodesByXPath("//" + ACTION_QUALIFIED_NAME + "/projectValues/*")
                .hasSize(10).extractingText()
                .containsExactly("MODULE: 1/1",
                        "PACKAGE: 1/1",
                        "FILE: 7/8",
                        "CLASS: 15/16",
                        "METHOD: 97/102",
                        "LINE: 294/323",
                        "BRANCH: 109/116",
                        "INSTRUCTION: 1260/1350",
                        "LOC: 323",
                        "CYCLOMATIC_COMPLEXITY: 160");

        assertThat(xml).nodesByXPath("//" + ACTION_QUALIFIED_NAME + "/projectValues/coverage")
                .hasSize(8).extractingText()
                .containsExactly("MODULE: 1/1",
                        "PACKAGE: 1/1",
                        "FILE: 7/8",
                        "CLASS: 15/16",
                        "METHOD: 97/102",
                        "LINE: 294/323",
                        "BRANCH: 109/116",
                        "INSTRUCTION: 1260/1350");

        assertThatDifferencesAreCorrectlyStored(xml, "differences");
        assertThatDifferencesAreCorrectlyStored(xml, "modifiedLinesDifferences");
        assertThatDifferencesAreCorrectlyStored(xml, "modifiedFilesDifferences");

        assertThatValuesAreCorrectlyStored(xml, "modifiedLinesCoverage");
        assertThatValuesAreCorrectlyStored(xml, "modifiedFilesCoverage");
        assertThatValuesAreCorrectlyStored(xml, "indirectCoverageChanges");

        var action = file.read();
        assertThat(action).isNotNull()
                .isInstanceOfSatisfying(CoverageBuildAction.class, this::assertThatActionIsCorrectlyDeserialized);
    }

    private void assertThatDifferencesAreCorrectlyStored(final Builder xml, final String name) {
        assertThat(xml).nodesByXPath("//" + ACTION_QUALIFIED_NAME + "/" + name + "/*")
                .hasSize(4).extractingText()
                .containsExactly("LINE: Δ10", "BRANCH: Δ-10", "LOC: Δ-50", "CYCLOMATIC_COMPLEXITY: Δ50");
    }

    private void assertThatValuesAreCorrectlyStored(final Builder xml, final String name) {
        assertThat(xml).nodesByXPath("//" + ACTION_QUALIFIED_NAME + "/" + name + "/*")
                .hasSize(4).extractingText()
                .containsExactly("LINE: 3/4", "BRANCH: 2/2", "MODULE: 1/1", "LOC: 123");
    }

    private void assertThatActionIsCorrectlyDeserialized(final CoverageBuildAction action) {
        Assertions.assertThat(serializeValues(action.getAllValues(Baseline.PROJECT)))
                .containsExactly("MODULE: 1/1", "PACKAGE: 1/1", "FILE: 7/8", "CLASS: 15/16",
                        "METHOD: 97/102", "LINE: 294/323", "BRANCH: 109/116", "INSTRUCTION: 1260/1350",
                        "LOC: 323", "CYCLOMATIC_COMPLEXITY: 160"
                );

        assertThatValuesAreCorrectlyDeserialized(action, Baseline.MODIFIED_FILES);
        assertThatValuesAreCorrectlyDeserialized(action, Baseline.MODIFIED_LINES);
        assertThatValuesAreCorrectlyDeserialized(action, Baseline.INDIRECT);
        assertThatDifferencesAreCorrectlyDeserialized(action, Baseline.PROJECT_DELTA);
        assertThatDifferencesAreCorrectlyDeserialized(action, Baseline.MODIFIED_FILES_DELTA);
        assertThatDifferencesAreCorrectlyDeserialized(action, Baseline.MODIFIED_LINES_DELTA);
    }

    private void assertThatValuesAreCorrectlyDeserialized(final CoverageBuildAction action,
            final Baseline baseline) {
        Assertions.assertThat(serializeValues(action.getAllValues(baseline)))
                .containsExactly("MODULE: 1/1", "LINE: 3/4", "BRANCH: 2/2", "LOC: 123");
    }

    private void assertThatDifferencesAreCorrectlyDeserialized(final CoverageBuildAction action,
            final Baseline baseline) {
        Assertions.assertThat(serializeValues(action.getAllDeltas(baseline)))
                .containsExactly("LINE: Δ10", "BRANCH: Δ-10", "LOC: Δ-50", "CYCLOMATIC_COMPLEXITY: Δ50");
    }

    private static List<String> serializeValues(final List<? extends Value> values) {
        return values.stream()
                .map(Value::serialize)
                .collect(Collectors.toList());
    }

    @Test
    void shouldConvertMetricMap2String() {
        NavigableMap<Metric, Value> map = new TreeMap<>();

        MetricFractionMapConverter converter = new MetricFractionMapConverter();

        assertThat(converter.marshal(map)).isEqualTo(EMPTY);

        map.put(BRANCH, new Value(BRANCH, 50, 100));
        assertThat(converter.marshal(map)).isEqualTo("[BRANCH: 50:100]");

        map.put(LINE, new Value(LINE, 3, 4));
        assertThat(converter.marshal(map)).isEqualTo("[LINE: 3:4, BRANCH: 50:100]");
    }

    @Test
    void shouldConvertString2MetricMap() {
        var converter = new MetricFractionMapConverter();

        Assertions.assertThat(converter.unmarshal(EMPTY)).isEmpty();
        Assertions.assertThat(converter.unmarshal("[BRANCH: 50/100]"))
                .containsExactly(entry(BRANCH, new Difference(BRANCH, 50, 1)));
        Assertions.assertThat(converter.unmarshal("[LINE: 3/4, BRANCH: -50/100]"))
                .containsExactly(
                        entry(LINE, new Difference(LINE, 75)),
                        entry(BRANCH, new Difference(BRANCH, -50)));
    }

    @Test
    void shouldConvertIntegerMap2String() {
        NavigableMap<Integer, Integer> map = new TreeMap<>();

        IntegerLineMapConverter converter = new IntegerLineMapConverter();

        assertThat(converter.marshal(map)).isEqualTo(EMPTY);

        map.put(10, 20);
        assertThat(converter.marshal(map)).isEqualTo("[10: 20]");

        map.put(15, 25);
        assertThat(converter.marshal(map)).isEqualTo("[10: 20, 15: 25]");
    }

    @Test
    void shouldConvertString2IntegerMap() {
        IntegerLineMapConverter converter = new IntegerLineMapConverter();

        Assertions.assertThat(converter.unmarshal(EMPTY)).isEmpty();
        Assertions.assertThat(converter.unmarshal("[15: 25]")).containsExactly(entry(15, 25));
        Assertions.assertThat(converter.unmarshal("[15:25, 10: 20]")).containsExactly(entry(10, 20), entry(15, 25));
    }

    @Test
    void shouldConvertIntegerSet2String() {
        NavigableSet<Integer> set = new TreeSet<>();

        IntegerSetConverter converter = new IntegerSetConverter();

        assertThat(converter.marshal(set)).isEqualTo(EMPTY);

        set.add(10);
        assertThat(converter.marshal(set)).isEqualTo("[10]");

        set.add(15);
        assertThat(converter.marshal(set)).isEqualTo("[10, 15]");
    }

    @Test
    void shouldConvertString2IntegerSet() {
        IntegerSetConverter converter = new IntegerSetConverter();

        Assertions.assertThat(converter.unmarshal(EMPTY)).isEmpty();
        Assertions.assertThat(converter.unmarshal("[15]")).containsExactly(15);
        Assertions.assertThat(converter.unmarshal("[15, 20]")).containsExactly(15, 20);
    }

    CoverageBuildAction createAction() {
        var tree = createSerializable();

        return new CoverageBuildAction(mock(FreeStyleBuild.class),
                CoverageRecorder.DEFAULT_ID, StringUtils.EMPTY, StringUtils.EMPTY,
                tree, new QualityGateResult(), new FilteredLog("Test"), "-",
                createDifferences(), createCoverages(),
                createDifferences(), createCoverages(),
                createDifferences(), createCoverages(),
                false);
    }

    private List<? extends Difference> createDifferences() {
        return List.of(
                new Difference(LINE, 10),
                new Difference(BRANCH, -10),
                new Difference(LOC, -50),
                new Difference(CYCLOMATIC_COMPLEXITY, 50));
    }

    private List<? extends Value> createCoverages() {
        return List.of(
                Value.valueOf("LINE: 3/4"),
                Value.valueOf("BRANCH: 2/2"),
                Value.valueOf("MODULE: 1/1"),
                Value.valueOf("LOC: 123")
                );
    }
} // NOPMD
