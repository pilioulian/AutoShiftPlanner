package org.betaiotazeta.autoshiftplanner.persistence;

import ai.timefold.solver.persistence.common.api.domain.solution.SolutionFileIO;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.security.AnyTypePermission;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.betaiotazeta.autoshiftplanner.Solution;

/**
 * In-project replacement for OptaPlanner's removed {@code optaplanner-persistence-xstream} module.
 *
 * <p>Timefold dropped the XStream persistence module after the 0.8.x series, but this app's saved
 * solutions (the {@code data/*.xml} samples and any user-saved files) are XStream documents that use
 * {@link XStream#ID_REFERENCES id-reference} mode. This class is a faithful port of OptaPlanner
 * 8.44's {@code XStreamSolutionFileIO}, implementing Timefold's {@link SolutionFileIO} against the
 * same XStream configuration so the on-disk format is unchanged and existing files keep loading.
 *
 * <p>Security warning: the constructor allows all types (as OptaPlanner's did). Callers that read
 * untrusted input should tighten permissions via {@link #getXStream()} — {@code AspApp} and the test
 * fixtures do exactly that (NoTypePermission + a package wildcard). Only use the default setup with
 * XML from a trusted source.
 *
 * @param <Solution_> the {@code @PlanningSolution} type
 */
public class XStreamSolutionFileIO<Solution_> implements SolutionFileIO<Solution_> {

    protected final XStream xStream;

    public XStreamSolutionFileIO(Class<?>... xStreamAnnotatedClasses) {
        xStream = new XStream();
        xStream.setMode(XStream.ID_REFERENCES);
        xStream.processAnnotations(xStreamAnnotatedClasses);
        xStream.addPermission(new AnyTypePermission());
    }

    /** No-arg constructor for declarative use (e.g. the benchmark {@code solutionFileIOClass}). */
    public XStreamSolutionFileIO() {
        this(Solution.class);
    }

    public XStream getXStream() {
        return xStream;
    }

    @Override
    public String getInputFileExtension() {
        return "xml";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Solution_ read(File inputSolutionFile) {
        try (InputStream inputSolutionStream = Files.newInputStream(inputSolutionFile.toPath());
                Reader reader = new InputStreamReader(inputSolutionStream, StandardCharsets.UTF_8)) {
            return (Solution_) xStream.fromXML(reader);
        } catch (XStreamException | IOException e) {
            throw new IllegalArgumentException("Failed reading inputSolutionFile (" + inputSolutionFile + ").", e);
        }
    }

    @Override
    public void write(Solution_ solution, File outputSolutionFile) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputSolutionFile), StandardCharsets.UTF_8)) {
            xStream.toXML(solution, writer);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed writing outputSolutionFile (" + outputSolutionFile + ").", e);
        }
    }
}
