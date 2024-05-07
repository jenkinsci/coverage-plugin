package io.jenkins.plugins.coverage.metrics.steps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.ClassNode;
import edu.hm.hafner.coverage.CoverageParser.ProcessingMode;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.PackageNode;
import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.TreeStringBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tools.ToolDescriptor;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.ParserType;
import io.jenkins.plugins.prism.SourceCodeDirectory;
import io.jenkins.plugins.prism.SourceCodeRetention;
import io.jenkins.plugins.util.AgentFileVisitor.FileVisitorResult;
import io.jenkins.plugins.util.EnvironmentResolver;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.LogHandler;
import io.jenkins.plugins.util.ResultHandler;
import io.jenkins.plugins.util.RunResultHandler;
import io.jenkins.plugins.util.ValidationUtilities;

/**
 * A pipeline {@code Step} or Freestyle or Maven {@link Recorder} that reads and parses coverage results in a build and
 * adds the results to the persisted build results.
 * <p>
 * Stores the created issues in a {@link Node}. This result is then attached to the {@link Run build} by registering a
 * {@link CoverageBuildAction}.
 * </p>
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"PMD.GodClass", "checkstyle:ClassFanOutComplexity", "checkstyle:ClassDataAbstractionCoupling"})
public class CoverageRecorder extends Recorder {
    static final String CHECKS_DEFAULT_NAME = "Code Coverage";

    static final String DEFAULT_ID = "coverage";
    private static final ValidationUtilities VALIDATION_UTILITIES = new ValidationUtilities();
    /** The coverage report symbol from the Ionicons plugin. */
    private static final String ICON = "symbol-footsteps-outline plugin-ionicons-api";

    private List<CoverageTool> tools = new ArrayList<>();
    private List<CoverageQualityGate> qualityGates = new ArrayList<>();
    private String id = StringUtils.EMPTY;
    private String name = StringUtils.EMPTY;
    private boolean skipPublishingChecks = false;
    private String checksName = StringUtils.EMPTY;
    private ChecksAnnotationScope checksAnnotationScope = ChecksAnnotationScope.MODIFIED_LINES;
    private boolean ignoreParsingErrors = false;
    private boolean failOnError = false;
    private boolean enabledForFailure = false;
    private boolean skipSymbolicLinks = false;
    private String scm = StringUtils.EMPTY;
    private String sourceCodeEncoding = StringUtils.EMPTY;
    private Set<SourceCodeDirectory> sourceDirectories = new HashSet<>();
    private SourceCodeRetention sourceCodeRetention = SourceCodeRetention.LAST_BUILD;

    /**
     * Creates a new instance of {@link  CoverageRecorder}.
     */
    @DataBoundConstructor
    public CoverageRecorder() {
        super();

        // empty constructor required for Stapler
    }

    /**
     * Sets the coverage tools that will scan files and create coverage reports.
     *
     * @param tools
     *         the coverage tools
     */
    @DataBoundSetter
    public void setTools(final List<CoverageTool> tools) {
        this.tools = List.copyOf(tools);
    }

    public List<CoverageTool> getTools() {
        return tools;
    }

    /**
     * Defines the optional list of quality gates.
     *
     * @param qualityGates
     *         the quality gates
     */
    @SuppressWarnings("unused") // used by Stapler view data binding
    @DataBoundSetter
    public void setQualityGates(final List<CoverageQualityGate> qualityGates) {
        this.qualityGates = List.copyOf(qualityGates);
    }

    @SuppressWarnings("unused") // used by Stapler view data binding
    public List<CoverageQualityGate> getQualityGates() {
        return qualityGates;
    }

    /**
     * Overrides the default ID of the results. The ID is used as URL of the results and as identifier in UI elements.
     * If no ID is given, then the default ID "coverage".
     *
     * @param id
     *         the ID of the results
     *
     * @see ToolDescriptor#getId()
     */
    @DataBoundSetter
    public void setId(final String id) {
        VALIDATION_UTILITIES.ensureValidId(id);

        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * Returns the actual ID of the results. If no user-defined ID is given, then the default ID {@link #DEFAULT_ID} is
     * returned.
     *
     * @return the ID
     * @see #setId(String)
     */
    public String getActualId() {
        return StringUtils.defaultIfBlank(id, DEFAULT_ID);
    }

    /**
     * Overrides the name of the results. The name is used for all labels in the UI. If no name is given, then the
     * default name is used.
     *
     * @param name
     *         the name of the results
     *
     * @see #getName()
     */
    @DataBoundSetter
    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return StringUtils.defaultString(name);
    }

    /**
     * Sets whether publishing checks should be skipped or not.
     *
     * @param skipPublishingChecks
     *         {@code true} if publishing checks should be skipped, {@code false} otherwise
     */
    @DataBoundSetter
    public void setSkipPublishingChecks(final boolean skipPublishingChecks) {
        this.skipPublishingChecks = skipPublishingChecks;
    }

    public boolean isSkipPublishingChecks() {
        return skipPublishingChecks;
    }

    /**
     * Changes the default name for the SCM checks report.
     *
     * @param checksName
     *         the name that should be used for the SCM checks report
     */
    @DataBoundSetter
    public void setChecksName(final String checksName) {
        this.checksName = checksName;
    }

    public String getChecksName() {
        return StringUtils.defaultIfBlank(checksName,
                StringUtils.defaultIfBlank(getName(), CHECKS_DEFAULT_NAME));
    }

    /**
     * Sets the scope of the annotations that should be published to SCM checks.
     *
     * @param checksAnnotationScope
     *         the scope to use
     */
    @DataBoundSetter
    public void setChecksAnnotationScope(final ChecksAnnotationScope checksAnnotationScope) {
        this.checksAnnotationScope = checksAnnotationScope;
    }

    public ChecksAnnotationScope getChecksAnnotationScope() {
        return checksAnnotationScope;
    }

    /**
     * Specify if traversal of symbolic links will be skipped during directory scanning for coverage reports.
     *
     * @param skipSymbolicLinks
     *         if symbolic links should be skipped during directory scanning
     */
    @DataBoundSetter
    public void setSkipSymbolicLinks(final boolean skipSymbolicLinks) {
        this.skipSymbolicLinks = skipSymbolicLinks;
    }

    public boolean isSkipSymbolicLinks() {
        return skipSymbolicLinks;
    }

    /**
     * Specify if parsing errors should be ignored and logged instead of throwing an exception.
     *
     * @param ignoreParsingErrors
     *         if parsing errors should be ignored and logged instead of throwing an exception
     */
    @DataBoundSetter
    public void setIgnoreParsingErrors(final boolean ignoreParsingErrors) {
        this.ignoreParsingErrors = ignoreParsingErrors;
    }

    public boolean isIgnoreParsingErrors() {
        return ignoreParsingErrors;
    }

    /**
     * Determines whether to fail the build on errors during the step of recording coverage reports.
     *
     * @param failOnError
     *         if {@code true} then the build will be failed on errors, {@code false} then errors are only reported in
     *         the UI
     */
    @DataBoundSetter
    @SuppressWarnings("unused") // Used by Stapler
    public void setFailOnError(final boolean failOnError) {
        this.failOnError = failOnError;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * Returns whether recording should be enabled for failed builds as well.
     *
     * @param enabledForFailure
     *         {@code true} if recording should be enabled for failed builds as well, {@code false} if recording is
     *         enabled for successful or unstable builds only
     */
    @DataBoundSetter
    public void setEnabledForFailure(final boolean enabledForFailure) {
        this.enabledForFailure = enabledForFailure;
    }

    public boolean isEnabledForFailure() {
        return enabledForFailure;
    }

    /**
     * Sets the encoding to use to read source files.
     *
     * @param sourceCodeEncoding
     *         the encoding, e.g. "ISO-8859-1"
     */
    @DataBoundSetter
    public void setSourceCodeEncoding(final String sourceCodeEncoding) {
        this.sourceCodeEncoding = sourceCodeEncoding;
    }

    public String getSourceCodeEncoding() {
        return sourceCodeEncoding;
    }

    /**
     * Sets the paths to the directories that contain the source code. If not relative and thus not part of the
     * workspace, then these directories need to be added in Jenkins global configuration to prevent accessing of
     * forbidden resources.
     *
     * @param sourceCodeDirectories
     *         directories containing the source code
     */
    @DataBoundSetter
    public void setSourceDirectories(final List<SourceCodeDirectory> sourceCodeDirectories) {
        sourceDirectories = Set.copyOf(sourceCodeDirectories);
    }

    public Set<SourceCodeDirectory> getSourceDirectories() {
        return sourceDirectories;
    }

    private Set<String> getSourceDirectoriesPaths() {
        Set<String> paths = sourceDirectories.stream()
                .map(SourceCodeDirectory::getPath)
                .collect(Collectors.toSet());
        paths.add("src/main/java");
        return paths;
    }

    /**
     * Defines the retention strategy for source code files.
     *
     * @param sourceCodeRetention
     *         the retention strategy for source code files
     */
    @DataBoundSetter
    public void setSourceCodeRetention(final SourceCodeRetention sourceCodeRetention) {
        this.sourceCodeRetention = sourceCodeRetention;
    }

    public SourceCodeRetention getSourceCodeRetention() {
        return sourceCodeRetention;
    }

    /**
     * Sets the SCM that should be used to find the reference build for. The reference recorder will select the SCM
     * based on a substring comparison, there is no need to specify the full name.
     *
     * @param scm
     *         the ID of the SCM to use (a substring of the full ID)
     */
    @DataBoundSetter
    public void setScm(final String scm) {
        this.scm = scm;
    }

    public String getScm() {
        return scm;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new IOException("No workspace found for " + build);
        }

        perform(build, workspace, listener, new RunResultHandler(build));

        return true;
    }

    void perform(final Run<?, ?> run, final FilePath workspace, final TaskListener taskListener,
            final ResultHandler resultHandler) throws InterruptedException {
        Result overallResult = run.getResult();
        LogHandler logHandler = new LogHandler(taskListener, "Coverage");
        if (enabledForFailure || overallResult == null || overallResult.isBetterOrEqualTo(Result.UNSTABLE)) {
            FilteredLog log = new FilteredLog("Errors while recording code coverage:");
            log.logInfo("Recording coverage results");

            var validation = VALIDATION_UTILITIES.validateId(getId());
            if (validation.kind != Kind.OK) {
                failStage(resultHandler, logHandler, log, validation.getLocalizedMessage());
            }
            if (tools.isEmpty()) {
                failStage(resultHandler, logHandler, log,
                        "No tools defined that will record the coverage files");
            }
            else {
                perform(run, workspace, taskListener, resultHandler, log, logHandler);
            }
        }
        else {
            logHandler.log("Skipping execution of coverage recorder since overall result is '%s'", overallResult);
        }
    }

    private void perform(final Run<?, ?> run, final FilePath workspace, final TaskListener taskListener,
            final ResultHandler resultHandler, final FilteredLog log, final LogHandler logHandler) throws InterruptedException {
        var results = recordCoverageResults(run, workspace, resultHandler, log, logHandler);
        Node aggregatedResult = aggregateResults(log, results);

        if (!aggregatedResult.isEmpty()) {
            CoverageReporter reporter = new CoverageReporter();

            var sources = aggregatedResult.getSourceFolders();
            sources.addAll(getSourceDirectoriesPaths());

            resolveAbsolutePaths(aggregatedResult, workspace, sources, log);
            logHandler.log(log);

            var action = reporter.publishAction(getActualId(), getName(), getIcon(), aggregatedResult, run,
                    workspace, taskListener, getQualityGates(), getScm(),
                    getSourceCodeEncoding(), getSourceCodeRetention(), resultHandler, log);
            logHandler.log(log);

            if (!skipPublishingChecks) {
                var checksPublisher = new CoverageChecksPublisher(action, aggregatedResult, getChecksName(), getChecksAnnotationScope());
                checksPublisher.publishCoverageReport(taskListener);
            }
        }
    }

    private void resolveAbsolutePaths(final Node rootNode, final FilePath workspace, final Set<String> sources,
            final FilteredLog log) throws InterruptedException {
        log.logInfo("Resolving source code files...");
        var pathMapping = new PathResolver().resolvePaths(rootNode.getFiles(), sources, workspace, log);

        if (!pathMapping.isEmpty()) {
            log.logInfo("Making paths of " + pathMapping.size() + " source code files relative to workspace root...");
            var builder = new TreeStringBuilder();
            rootNode.getAllFileNodes().stream()
                    .filter(file -> pathMapping.containsKey(file.getRelativePath()))
                    .forEach(file -> file.setRelativePath(builder.intern(pathMapping.get(file.getRelativePath()))));
            builder.dedup();
        }
    }

    private String getIcon() {
        var icons = tools.stream()
                .map(CoverageTool::getParser)
                .filter(parser -> parser.getParserType() != ParserType.TEST)
                .map(Parser::getIcon)
                .collect(Collectors.toSet());
        if (icons.size() == 1) {
            return icons.iterator().next(); // unique icon
        }
        return ICON;
    }

    private static void failStage(final ResultHandler resultHandler, final LogHandler logHandler,
            final FilteredLog log, final String message) {
        log.logError(message);
        resultHandler.publishResult(Result.FAILURE, message);
        logHandler.log(log);
    }

    private Map<Parser, List<ModuleNode>> recordCoverageResults(final Run<?, ?> run, final FilePath workspace,
            final ResultHandler resultHandler, final FilteredLog log, final LogHandler logHandler) throws InterruptedException {
        Map<Parser, List<ModuleNode>> results = new HashMap<>();

        for (CoverageTool tool : tools) {
            Parser parser = tool.getParser();
            log.logInfo("Creating parser for %s", tool.getDisplayName());
            if (StringUtils.isBlank(tool.getPattern())) {
                log.logInfo("Using default pattern '%s' since user defined pattern is not set",
                        parser.getDefaultPattern());
            }

            String expandedPattern = expandPattern(run, tool.getActualPattern());
            if (!expandedPattern.equals(tool.getActualPattern())) {
                log.logInfo("Expanding pattern '%s' to '%s'", tool.getActualPattern(), expandedPattern);
            }

            try {
                FileVisitorResult<ModuleNode> result = workspace.act(
                        new CoverageReportScanner(parser, expandedPattern, "UTF-8", isSkipSymbolicLinks(),
                                ignoreErrors()));
                log.merge(result.getLog());

                var coverageResults = result.getResults();
                if (result.hasErrors()) {
                    if (isFailOnError()) {
                        var errorMessage = "Failing build due to some errors during recording of the coverage";
                        log.logInfo(errorMessage);
                        resultHandler.publishResult(Result.FAILURE, errorMessage);
                    }
                    else {
                        log.logInfo("Ignore errors and continue processing");
                    }
                }
                results.put(tool.getParser(), coverageResults);
            }
            catch (IOException exception) {
                log.logException(exception, "Exception while parsing with tool " + tool);
            }

            logHandler.log(log);
        }

        return results;
    }

    private Node aggregateResults(final FilteredLog log, final Map<Parser, List<ModuleNode>> results) {
        if (isEmpty(results)) {
            log.logError("No coverage results were found! Configuration error?");

            return new ModuleNode("Empty");
        }
        else {
            var testCases = results.entrySet().stream().filter(entry -> entry.getKey().getParserType() == ParserType.TEST)
                    .map(Entry::getValue)
                    .flatMap(Collection::stream)
                    .map(Node::getAllClassNodes)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            var coverageNodes = results.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().getParserType() == ParserType.COVERAGE)
                    .map(Entry::getValue)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            if (coverageNodes.isEmpty()) {
                log.logError("No coverage results were found, just tests! Configuration error?");

                var tests = new ModuleNode("Tests");
                tests.addAllChildren(testCases);
                return tests;
            }

            var coverageTree = Node.merge(coverageNodes);
            var unmappedNodes = coverageTree.mergeTests(testCases);

            unmappedNodes.forEach(node -> mapTests(node, coverageTree));

            return coverageTree;
        }
    }

    private void mapTests(final ClassNode classNode, final Node coverageTree) {
        var normalizedPackageName = PackageNode.normalizePackageName(classNode.getPackageName());

        coverageTree.findPackage(normalizedPackageName)
                .orElseGet(() -> createPackage(coverageTree, normalizedPackageName)).addChild(classNode);
    }

    private PackageNode createPackage(final Node coverageTree, final String normalizedPackageName) {
        var packageNode = new PackageNode(normalizedPackageName);
        coverageTree.addChild(packageNode);
        return packageNode;
    }

    private boolean isEmpty(final Map<Parser, List<ModuleNode>> results) {
        return results.values().stream().mapToInt(Collection::size).sum() == 0;
    }

    private ProcessingMode ignoreErrors() {
        return isIgnoreParsingErrors() ? ProcessingMode.IGNORE_ERRORS : ProcessingMode.FAIL_FAST;
    }

    private String expandPattern(final Run<?, ?> run, final String actualPattern) {
        try {
            EnvironmentResolver environmentResolver = new EnvironmentResolver();

            return environmentResolver.expandEnvironmentVariables(
                    run.getEnvironment(TaskListener.NULL), actualPattern);
        }
        catch (IOException | InterruptedException ignore) {
            return actualPattern; // fallback, no expansion
        }
    }

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    }

    /**
     * Descriptor for this step: defines the context and the UI elements.
     */
    @Extension
    @Symbol("recordCoverage")
    public static class Descriptor extends BuildStepDescriptor<Publisher> {
        private static final JenkinsFacade JENKINS = new JenkinsFacade();

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Recorder_Name();
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        /**
         * Returns a model with all {@link SourceCodeRetention} strategies.
         *
         * @return a model with all {@link SourceCodeRetention} strategies.
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillSourceCodeRetentionItems() {
            if (JENKINS.hasPermission(Jenkins.READ)) {
                return SourceCodeRetention.fillItems();
            }
            return new ListBoxModel();
        }

        /**
         * Returns a model with all {@link ChecksAnnotationScope} scopes.
         *
         * @return a model with all {@link ChecksAnnotationScope} scopes.
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillChecksAnnotationScopeItems() {
            if (JENKINS.hasPermission(Jenkins.READ)) {
                return ChecksAnnotationScope.fillItems();
            }
            return new ListBoxModel();
        }

        /**
         * Returns a model with all available charsets.
         *
         * @return a model with all available charsets
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ComboBoxModel doFillSourceCodeEncodingItems() {
            if (JENKINS.hasPermission(Jenkins.READ)) {
                return VALIDATION_UTILITIES.getAllCharsets();
            }
            return new ComboBoxModel();
        }

        /**
         * Performs on-the-fly validation on the character encoding.
         *
         * @param project
         *         the project that is configured
         * @param sourceCodeEncoding
         *         the character encoding
         *
         * @return the validation result
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public FormValidation doCheckSourceCodeEncoding(@AncestorInPath final BuildableItem project,
                @QueryParameter final String sourceCodeEncoding) {
            if (!JENKINS.hasPermission(Item.CONFIGURE, project)) {
                return FormValidation.ok();
            }

            return VALIDATION_UTILITIES.validateCharset(sourceCodeEncoding);
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
            if (!JENKINS.hasPermission(Item.CONFIGURE, project)) {
                return FormValidation.ok();
            }

            return VALIDATION_UTILITIES.validateId(id);
        }
    }

    /**
     * Defines the scope of SCM checks annotations.
     */
    public enum ChecksAnnotationScope {
        /** No annotations are created. */
        SKIP,
        /** Only changed lines are annotated. */
        MODIFIED_LINES,
        /** All lines are annotated. */
        ALL_LINES;

        static ListBoxModel fillItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(Messages.ChecksAnnotationScope_Skip(), ChecksAnnotationScope.SKIP.name());
            items.add(Messages.ChecksAnnotationScope_ModifiedLines(), ChecksAnnotationScope.MODIFIED_LINES.name());
            items.add(Messages.ChecksAnnotationScope_AllLines(), ChecksAnnotationScope.ALL_LINES.name());
            return items;
        }
    }
}
