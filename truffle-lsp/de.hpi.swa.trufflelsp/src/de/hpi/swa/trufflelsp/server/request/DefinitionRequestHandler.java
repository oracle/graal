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
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NearestSections;

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
            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, SourceUtils.zeroBasedLineToOneBasedLine(line, source), character);
            SourceSection containsSection = nearestSections.getContainsSourceSection();
            InstrumentableNode containsNode = nearestSections.getContainsNode();
            if (containsSection != null && containsNode != null) {
                System.out.println(nearestSections.getContainsNode().getClass().getSimpleName() + " " + containsSection);

                // First try: dynamic approach
                System.out.println("Trying run-to-section eval...");

                EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(surrogate, (Node) containsNode);
                if (evalResult.isEvaluationDone() && !evalResult.isError()) {
                    SourceSection sourceSection = SourceUtils.findSourceLocation(env, surrogate.getLangId(), evalResult.getResult());
                    if (sourceSection != null) {
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

// if (locations.isEmpty()) {
// // Fallback: static approach
// System.out.println("Trying static declaration search...");
// env.getInstrumenter().attachLoadSourceSectionListener(
// SourceSectionFilter.newBuilder().sourceIs(surrogate.getSourceWrapper().getSource()).tagIs(DeclarationTag.class).build(),
// new LoadSourceSectionListener() {
//
// public void onLoad(LoadSourceSectionEvent event) {
// Node eventNode = event.getNode();
// if (!(eventNode instanceof InstrumentableNode)) {
// return;
// }
// InstrumentableNode instrumentableNode = (InstrumentableNode) eventNode;
// Object declarationNodeObject = instrumentableNode.getNodeObject();
// if (!(declarationNodeObject instanceof TruffleObject)) {
// return;
// }
// Map<Object, Object> declarationMap = ObjectStructures.asMap(new MessageNodes(), (TruffleObject)
// declarationNodeObject);
// String declarationName = declarationMap.get(DeclarationTag.NAME).toString();
// if (name.equals(declarationName)) {
// Range range = TruffleAdapter.sourceSectionToRange(eventNode.getSourceSection());
// locations.add(new Location(uri.toString(), range));
// }
// }
// }, true).dispose();
// }
            }
        }
        return locations;
    }
}
