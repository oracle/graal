package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.server.utils.InteropUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class ReferencesRequestHandler extends AbstractRequestHandler {

    public ReferencesRequestHandler(Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor) {
        super(env, uri2TextDocumentSurrogate, contextAwareExecutor);
    }

    public List<? extends Location> referencesWithEnteredContext(URI uri, int line, int character) {
        List<Location> locations = new ArrayList<>();
        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, character, StandardTags.CallTag.class);
        if (nodeAtCaret != null) {
            SourceSection sourceSection = ((Node) nodeAtCaret).getSourceSection();
            String symbol = sourceSection.getCharacters().toString();
            String normalizedSymbolToFindRef = InteropUtils.getNormalizedSymbolName(nodeAtCaret.getNodeObject(), symbol);
            SourcePredicate srcPredicate = SourceUtils.createLanguageFilterPredicate(surrogate.getLanguageInfo());
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
        }
        return locations;
    }

}
