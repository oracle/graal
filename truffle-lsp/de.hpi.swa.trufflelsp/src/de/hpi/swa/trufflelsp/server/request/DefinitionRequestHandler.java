package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NearestSections;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class DefinitionRequestHandler extends AbstractRequestHandler {

    private final SourceCodeEvaluator sourceCodeEvaluator;

    public DefinitionRequestHandler(Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor, SourceCodeEvaluator evaluator) {
        super(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.sourceCodeEvaluator = evaluator;
    }

    public List<? extends Location> definitionWithEnteredContext(URI uri, int line, int character) throws DiagnosticsNotification {
        List<Location> locations = new ArrayList<>();

        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        if (sourceWrapper.isParsingSuccessful()) {
            Source source = sourceWrapper.getSource();
            int oneBasedLineNumber = SourceUtils.zeroBasedLineToOneBasedLine(line, source);
            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, oneBasedLineNumber, character, StandardTags.CallTag.class);
            SourceSection definitionSearchSection = nearestSections.getContainsSourceSection();
            InstrumentableNode definitionSearchNode = nearestSections.getContainsNode();
            if (definitionSearchSection == null && nearestSections.getNextSourceSection() != null) {
                SourceSection nextNodeSection = nearestSections.getNextSourceSection();
                if (nextNodeSection.getStartLine() == oneBasedLineNumber && nextNodeSection.getStartColumn() == character + 1) {
                    // nextNodeSection is directly before the caret, so we use that one as fallback
                    definitionSearchSection = nextNodeSection;
                    definitionSearchNode = nearestSections.getNextNode();
                }
            }
            if (definitionSearchSection != null) {
                System.out.println(definitionSearchNode.getClass().getSimpleName() + " " + definitionSearchSection);

                System.out.println("Trying run-to-section eval...");

                SourceSectionFilter.Builder builder = SourceUtils.createSourceSectionFilter(surrogate, definitionSearchSection);
                SourceSectionFilter eventFilter = builder.tagIs(StandardTags.CallTag.class).build();
                SourceSectionFilter inputFilter = SourceSectionFilter.ANY;
// SourceSectionFilter inputFilter =
// SourceSectionFilter.newBuilder().tagIs(StandardTags.CallTag.class).build();
                EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(surrogate, definitionSearchSection, eventFilter, inputFilter);
                if (evalResult.isEvaluationDone() && !evalResult.isError()) {
                    SourceSection sourceSection = SourceUtils.findSourceLocation(env, surrogate.getLangId(), evalResult.getResult());
                    if (sourceSection != null && sourceSection.isAvailable()) {
                        Range range = SourceUtils.sourceSectionToRange(sourceSection);
                        String definitionUri;
                        if (!sourceSection.getSource().getURI().getScheme().equals("file")) {
                            // We assume, that the source name is a valid file path if
                            // the URI has no file scheme
                            Path path = Paths.get(sourceSection.getSource().getName());
                            definitionUri = path.toUri().toString();
                        } else {
                            definitionUri = sourceSection.getSource().getURI().toString();
                        }
                        locations.add(new Location(definitionUri, range));
                    }
                }
            }
        }
        return locations;
    }
}
