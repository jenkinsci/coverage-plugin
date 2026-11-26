package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.FileNode;
import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.source.CoverageMarkerFactory;
import io.jenkins.plugins.coverage.metrics.source.SourceCodeFacade;
import io.jenkins.plugins.prism.SourceCodeViewModel;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Server side model for the source code page of a single file in the run tab of the coverage results. The page is a
 * sub page of the details page of the file (see {@link FileDetailsModel}) and answers the question: "Which lines of
 * the file are covered by tests?". The source code is rendered by the {@link SourceCodeViewModel} of the Prism API
 * plugin: the coverage of the lines is shown by markers and by annotations of the lines (see
 * {@link CoverageMarkerFactory}). The client side script {@code source-model.js} filters the markers by the baseline
 * that is selected in the app bar: the whole file, the modified lines, or the indirect coverage changes.
 *
 * <p>
 * Builds of previous versions of the plugin contain the source code as painted HTML files only. For these builds,
 * the stored HTML is shown in the same way as in the classic source code view.
 * </p>
 *
 * @author Ullrich Hafner
 */
public class SourceModel implements ModelObject {
    static final String URL = "source";

    private static final SourceCodeFacade SOURCE_CODE_FACADE = new SourceCodeFacade();

    private final CoverageViewModel parent;
    private final FileNode file;

    /**
     * Creates a new instance of {@link SourceModel}.
     *
     * @param parent
     *         the coverage results that contain the file
     * @param file
     *         the file to show
     */
    SourceModel(final CoverageViewModel parent, final FileNode file) {
        this.parent = parent;
        this.file = file;
    }

    @Override
    public String getDisplayName() {
        return file.getName();
    }

    public String getId() {
        return parent.getId();
    }

    public Run<?, ?> getObject() {
        return parent.getOwner();
    }

    public RunTab getTab() {
        return new RunTab(parent.getOwner());
    }

    /**
     * Returns the relative path of the file.
     *
     * @return the relative path
     */
    public String getPath() {
        return file.getRelativePath();
    }

    /**
     * Returns the URL of the details page of the file.
     *
     * @return the URL, relative to the context path of Jenkins
     */
    public String getDetailsUrl() {
        return getObject().getUrl() + getId() + "/" + getPath().hashCode();
    }

    /**
     * Returns whether the current user has the permission to view the source code.
     *
     * @return {@code true} if the source code can be shown, {@code false} otherwise
     */
    public boolean hasPermissionToViewSourceCode() {
        return SourceCodeViewModel.hasPermissionToViewSourceCode(getObject());
    }

    /**
     * Returns whether the source file is available in the Jenkins build folder: either as original file or as painted
     * HTML file of an older build.
     *
     * @return {@code true} if the source file is available, {@code false} otherwise
     */
    public boolean isSourceFileAvailable() {
        return parent.isSourceFileAvailable(file);
    }

    /**
     * Returns whether the source file is available as painted HTML file of an older build only.
     *
     * @return {@code true} if only the painted HTML file is available, {@code false} otherwise
     */
    public boolean isPaintedSourceFile() {
        return isSourceFileAvailable() && !SOURCE_CODE_FACADE.hasSourceFile(getRootDir(), getId(), getPath());
    }

    /**
     * Returns whether the file contains modified lines with respect to the reference build.
     *
     * @return {@code true} if the file contains modified lines, {@code false} otherwise
     */
    public boolean hasModifiedLines() {
        return !parent.getChanges().getModifiedLines(getPath()).isEmpty();
    }

    /**
     * Returns whether the file contains indirect coverage changes with respect to the reference build.
     *
     * @return {@code true} if the file contains indirect coverage changes, {@code false} otherwise
     */
    public boolean hasIndirectCoverageChanges() {
        return !parent.getChanges().getIndirectCoverageChanges(getPath()).isEmpty();
    }

    /**
     * Returns the view model that renders the source code of the file with the coverage of the lines. If the current
     * user has no permission to see the source code, a view model with a corresponding message is returned instead.
     * Both models provide the fragment {@code content.jelly}.
     *
     * @return the view model of the source code
     */
    public ModelObject getSourceCode() {
        var builder = new SourceCodeViewModel.Builder(getObject(), getPath());
        try {
            var lines = SOURCE_CODE_FACADE.readLines(getRootDir(), getId(), getPath());
            var coverage = new CoverageMarkerFactory().create(file, parent.getChanges());
            return builder.withMarkers(coverage.markers())
                    .withLineAnnotations(coverage.annotations())
                    .build(lines.stream());
        } catch (IOException exception) {
            return builder.withPlainText()
                    .build(Stream.of(ExceptionUtils.getStackTrace(exception).split("\\R")));
        }
    }

    /**
     * Returns the source code of an older build as painted HTML (see {@link #isPaintedSourceFile()}).
     *
     * @return the painted source code as HTML
     */
    public String getPaintedSourceCode() {
        try {
            return SOURCE_CODE_FACADE.readHtml(getRootDir(), getId(), parent.getNode(), file, parent.getChanges());
        } catch (IOException | InterruptedException exception) {
            return ExceptionUtils.getStackTrace(exception);
        }
    }

    private File getRootDir() {
        return getObject().getRootDir();
    }
}
