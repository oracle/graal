package org.graalvm.tools.lsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public class ReferencesRequestHandler extends AbstractRequestHandler {

    private final HighlightRequestHandler highlightHandler;

    public ReferencesRequestHandler(Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor, HighlightRequestHandler highlightHandler) {
        super(env, surrogateMap, contextAwareExecutor);
        this.highlightHandler = highlightHandler;
    }

    public List<? extends Location> referencesWithEnteredContext(URI uri, int line, int character) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, character, StandardTags.CallTag.class, StandardTags.ReadVariableTag.class, StandardTags.WriteVariableTag.class);
        if (nodeAtCaret != null) {
            if (nodeAtCaret.hasTag(StandardTags.CallTag.class)) {
                return referencesForCallTaggedNode(surrogate, nodeAtCaret);
            } else if (nodeAtCaret.hasTag(StandardTags.ReadVariableTag.class) ||
                            nodeAtCaret.hasTag(StandardTags.WriteVariableTag.class)) {
                return referencesForVariableNode(surrogate, nodeAtCaret);
            }
        }
        return Collections.emptyList();
    }

    private List<? extends Location> referencesForCallTaggedNode(TextDocumentSurrogate surrogate, InstrumentableNode nodeAtCaret) {
        List<Location> locations = new ArrayList<>();
        SourceSection sourceSection = ((Node) nodeAtCaret).getSourceSection();
        String symbol = sourceSection.getCharacters().toString();
        String normalizedSymbolToFindRef = InteropUtils.getNormalizedSymbolName(nodeAtCaret.getNodeObject(), symbol);
        SourcePredicate srcPredicate = newDefaultSourcePredicateBuilder().language(surrogate.getLanguageInfo()).build();

        env.getInstrumenter().attachLoadSourceSectionListener(
                        SourceSectionFilter.newBuilder().sourceIs(srcPredicate).tagIs(StandardTags.CallTag.class).build(),
                        new LoadSourceSectionListener() {

                            public void onLoad(LoadSourceSectionEvent event) {
                                if (!event.getSourceSection().isAvailable()) {
                                    return;
                                }

                                Node node = event.getNode();
                                String sectionText = node.getSourceSection().getCharacters().toString();
                                String normalizedSymbol = InteropUtils.getNormalizedSymbolName(((InstrumentableNode) node).getNodeObject(), sectionText);
                                if (normalizedSymbolToFindRef.equals(normalizedSymbol)) {
                                    Range range = SourceUtils.sourceSectionToRange(node.getSourceSection());
                                    URI fixedUri = SourceUtils.getOrFixFileUri(node.getSourceSection().getSource());
                                    locations.add(new Location(fixedUri.toString(), range));
                                }
                            }
                        }, true).dispose();
        return locations;
    }

    private List<? extends Location> referencesForVariableNode(TextDocumentSurrogate surrogate, InstrumentableNode nodeAtCaret) {
        List<? extends DocumentHighlight> readOrWrites = highlightHandler.findOtherReadOrWrites(surrogate, nodeAtCaret);
        //@formatter:off
        return readOrWrites.stream()
                        .map(highlight -> new Location(surrogate.getUri().toString(), highlight.getRange()))
                        .collect(Collectors.toList());
        //@formatter:on
    }

}
