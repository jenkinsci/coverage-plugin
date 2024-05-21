package io.jenkins.plugins.coverage.metrics.steps;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.CoverageParser;
import edu.hm.hafner.coverage.CoverageParser.ProcessingMode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.registry.ParserRegistry;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.jvnet.localizer.Localizable;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildableItem;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;

import io.jenkins.plugins.prism.SourceCodeRetention;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.ValidationUtilities;

/**
 * A coverage tool that can produce a {@link Node coverage tree} by parsing a given report file.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.DataClass")
public class CoverageTool extends AbstractDescribableImpl<CoverageTool> implements Serializable {
    private static final long serialVersionUID = -8612521458890553037L;
    private static final ValidationUtilities VALIDATION_UTILITIES = new ValidationUtilities();

    private JenkinsFacade jenkins = new JenkinsFacade();

    private String pattern = StringUtils.EMPTY;
    private Parser parser = Parser.JACOCO;

    /**
     * Creates a new {@link io.jenkins.plugins.coverage.metrics.steps.CoverageTool}.
     */
    @DataBoundConstructor
    public CoverageTool() {
        super();
        // empty for stapler
    }

    CoverageTool(final Parser parser, final String pattern) {
        super();

        this.pattern = pattern;
        this.parser = parser;
    }

    public Parser getParser() {
        return parser;
    }

    /**
     * Sets the parser to be used to read the input files.
     *
     * @param parser the parser to use
     */
    @DataBoundSetter
    public void setParser(final Parser parser) {
        this.parser = parser;
    }

    @VisibleForTesting
    void setJenkinsFacade(final JenkinsFacade jenkinsFacade) {
        jenkins = jenkinsFacade;
    }

    /**
     * Called after de-serialization to retain backward compatibility.
     *
     * @return this
     */
    protected Object readResolve() {
        jenkins = new JenkinsFacade();

        return this;
    }

    /**
     * Sets the Ant file-set pattern of files to work with. If the pattern is undefined, then the console log is
     * scanned.
     *
     * @param pattern
     *         the pattern to use
     */
    @DataBoundSetter
    public void setPattern(final String pattern) {
        this.pattern = pattern;
    }

    @CheckForNull
    public String getPattern() {
        return pattern;
    }

    /**
     * Returns the actual pattern to work with. If no user-defined pattern is given, then the default pattern is
     * returned.
     *
     * @return the name
     * @see #setPattern(String)
     */
    public String getActualPattern() {
        return StringUtils.defaultIfBlank(pattern, parser.getDefaultPattern());
    }

    @Override
    public String toString() {
        return String.format("%s (pattern: %s)", getParser(), getActualPattern());
    }

    @Override
    public CoverageToolDescriptor getDescriptor() {
        return (CoverageToolDescriptor) jenkins.getDescriptorOrDie(getClass());
    }

    public String getDisplayName() {
        return getParser().getDisplayName();
    }

    /** Descriptor for {@link CoverageTool}. **/
    @Extension
    public static class CoverageToolDescriptor extends Descriptor<CoverageTool> {
        private static final JenkinsFacade JENKINS = new JenkinsFacade();

        /**
         * Returns a model with all {@link SourceCodeRetention} strategies.
         *
         * @return a model with all {@link SourceCodeRetention} strategies.
         */
        @POST
        public ListBoxModel doFillParserItems() {
            if (!JENKINS.hasPermission(Jenkins.READ)) {
                return new ListBoxModel();
            }

            List<Option> options = Stream.of(Parser.values())
                    .map(p -> new Option(p.getDisplayName(), p.name()))
                    .collect(Collectors.toList());
            return new ListBoxModel(options);
        }

        /**
         * Performs on-the-fly validation of the ID.
         *
         * @param project
         *         the project that is configured
         * @param id
         *         the ID of the tool
         *
         * @return the validation result
         */
        @POST
        public FormValidation doCheckId(@AncestorInPath final BuildableItem project,
                @QueryParameter final String id) {
            if (!new JenkinsFacade().hasPermission(Item.CONFIGURE, project)) {
                return FormValidation.ok();
            }

            return VALIDATION_UTILITIES.validateId(id);
        }

        /**
         * Returns an optional help text that can provide useful hints on how to configure the coverage tool so that
         * Jenkins could parse the report files. This help can be a plain text message or an HTML snippet.
         *
         * @return the help
         */
        public String getHelp() {
            return StringUtils.EMPTY;
        }

        /**
         * Returns an optional URL to the homepage of the coverage tool.
         *
         * @return the help
         */
        public String getUrl() {
            return StringUtils.EMPTY;
        }
    }

    /**
     * Parser types.
     */
    public enum ParserType {
        COVERAGE,
        TEST
    }

    /**
     * Supported coverage parsers.
     */
    public enum Parser {
        COBERTURA(Messages._Parser_Cobertura(), ParserType.COVERAGE,
                "**/cobertura.xml",
                "symbol-footsteps-outline plugin-ionicons-api"),
        JACOCO(Messages._Parser_JaCoCo(), ParserType.COVERAGE,
                "**/jacoco.xml",
                "symbol-footsteps-outline plugin-ionicons-api"),
        OPENCOVER(Messages._Parser_OpenCover(), ParserType.COVERAGE,
                "**/*opencover.xml",
                "symbol-footsteps-outline plugin-ionicons-api"),
        PIT(Messages._Parser_PIT(), ParserType.COVERAGE,
                "**/mutations.xml",
                "symbol-solid/virus-slash plugin-font-awesome-api"),
        JUNIT(Messages._Parser_Junit(), ParserType.TEST,
                "**/TEST-*.xml",
                "symbol-solid/list-check plugin-font-awesome-api"),
        NUNIT(Messages._Parser_Nunit(), ParserType.TEST,
                "**/nunit.xml,**/TestResult.xml",
                "symbol-solid/list-check plugin-font-awesome-api"),
        XUNIT(Messages._Parser_Xunit(), ParserType.TEST,
                "**/xunit.xml,**/TestResult.xml",
                "symbol-solid/list-check plugin-font-awesome-api");

        private final Localizable displayName;
        private final ParserType parserType;
        private final String defaultPattern;
        private final String icon;

        Parser(final Localizable displayName, final ParserType parserType,
                final String defaultPattern, final String icon) {
            this.displayName = displayName;
            this.parserType = parserType;
            this.defaultPattern = defaultPattern;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName.toString();
        }

        public ParserType getParserType() {
            return parserType;
        }

        public String getDefaultPattern() {
            return defaultPattern;
        }

        public String getIcon() {
            return icon;
        }

        /**
         * Creates a new parser to read the report XML files into a Java object model of {@link Node} instances.
         *
         * @param processingMode
         *         determines whether to ignore errors
         *
         * @return the parser
         */
        public CoverageParser createParser(final ProcessingMode processingMode) {
            return new ParserRegistry().get(name(), processingMode);
        }
    }
}
