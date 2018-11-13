package org.graalvm.tools.lsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.interop.ObjectStructures;
import org.graalvm.tools.lsp.interop.ObjectStructures.MessageNodes;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.DeclarationTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public class SymbolRequestHandler extends AbstractRequestHandler {

    public SymbolRequestHandler(Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor) {
        super(env, surrogateMap, contextAwareExecutor);
    }

    public List<? extends SymbolInformation> documentSymbolWithEnteredContext(URI uri) {
        SourcePredicate srcPredicate = newDefaultSourcePredicateBuilder().uriOrTruffleName(uri).build();
        return symbolWithEnteredContext(srcPredicate);
    }

    List<? extends SymbolInformation> symbolWithEnteredContext(SourcePredicate srcPredicate) {
        Set<SymbolInformation> symbolInformation = new LinkedHashSet<>();
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().sourceIs(srcPredicate).tagIs(DeclarationTag.class).build();
        env.getInstrumenter().attachLoadSourceSectionListener(
                        filter,
                        new LoadSourceSectionListener() {

                            public void onLoad(LoadSourceSectionEvent event) {
                                Node node = event.getNode();
                                if (!(node instanceof InstrumentableNode)) {
                                    return;
                                }
                                InstrumentableNode instrumentableNode = (InstrumentableNode) node;
                                Object nodeObject = instrumentableNode.getNodeObject();
                                if (!(nodeObject instanceof TruffleObject)) {
                                    return;
                                }
                                Map<Object, Object> map = ObjectStructures.asMap(new MessageNodes(), (TruffleObject) nodeObject);
                                String name = map.get(DeclarationTag.NAME).toString();
                                SymbolKind kind = map.containsKey(DeclarationTag.KIND) ? declarationKindToSmybolKind(map.get(DeclarationTag.KIND)) : null;
                                Range range = SourceUtils.sourceSectionToRange(node.getSourceSection());
                                String container = map.containsKey(DeclarationTag.CONTAINER) ? map.get(DeclarationTag.CONTAINER).toString() : "";
                                URI fixedUri = SourceUtils.getOrFixFileUri(node.getSourceSection().getSource());
                                SymbolInformation si = new SymbolInformation(name, kind != null ? kind : SymbolKind.Null, new Location(fixedUri.toString(), range),
                                                container);
                                symbolInformation.add(si);
                            }

                            private SymbolKind declarationKindToSmybolKind(Object kindObj) {
                                if (kindObj == null) {
                                    return null;
                                }
                                String kind = kindObj.toString();
                                return Arrays.stream(SymbolKind.values()).filter(sk -> sk.name().toLowerCase().equals(kind.toLowerCase())).findFirst().orElse(null);
                            }
                        }, true).dispose();

        // Fallback: search for generic RootTags
        if (symbolInformation.isEmpty()) {
            env.getInstrumenter().attachLoadSourceSectionListener(
                            SourceSectionFilter.newBuilder().sourceIs(srcPredicate).tagIs(StandardTags.RootTag.class).build(),
                            new LoadSourceSectionListener() {

                                public void onLoad(LoadSourceSectionEvent event) {
                                    if (!event.getSourceSection().isAvailable()) {
                                        return;
                                    }

                                    Node node = event.getNode();
                                    SymbolKind kind = SymbolKind.Function;
                                    Range range = SourceUtils.sourceSectionToRange(node.getSourceSection());
                                    URI fixedUri = SourceUtils.getOrFixFileUri(node.getSourceSection().getSource());
                                    SymbolInformation si = new SymbolInformation(node.getRootNode().getName(), kind, new Location(fixedUri.toString(), range));
                                    symbolInformation.add(si);
                                }
                            }, true).dispose();
        }

        return new ArrayList<>(symbolInformation);
    }

    public List<? extends SymbolInformation> workspaceSymbolWithEnteredContext(@SuppressWarnings("unused") String query) {
        SourcePredicate srcPredicate = newDefaultSourcePredicateBuilder().build();
        return symbolWithEnteredContext(srcPredicate);
    }
}
